package com.zpl.handcricket.repository;

import com.zpl.handcricket.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<User> mapper = (rs, i) -> User.builder()
            .id(rs.getObject("id", UUID.class))
            .username(rs.getString("username"))
            .fullName(rs.getString("full_name"))
            .email(rs.getString("email"))
            .city(rs.getString("city"))
            .favoritePlayer(rs.getString("favorite_player"))
            .passwordHash(rs.getString("password_hash"))
            .teamId((Integer) rs.getObject("team_id"))
            .totalRuns(rs.getInt("total_runs"))
            .matchesPlayed(rs.getInt("matches_played"))
            .matchesWon(rs.getInt("matches_won"))
            .matchesLeftToday(rs.getInt("matches_left_today"))
            .lastResetAt(toOdt(rs.getTimestamp("last_reset_at")))
            .createdAt(toOdt(rs.getTimestamp("created_at")))
            .build();

    private OffsetDateTime toOdt(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    public Optional<User> findByUsername(String username) {
        List<User> list = jdbc.query("select * from users where username = ?", mapper, username);
        return list.stream().findFirst();
    }

    public Optional<User> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        List<User> list = jdbc.query(
                "select * from users where lower(email) = ?",
                mapper,
                email.trim().toLowerCase(Locale.ROOT)
        );
        return list.stream().findFirst();
    }

    public Optional<User> findById(UUID id) {
        List<User> list = jdbc.query("select * from users where id = ?", mapper, id);
        return list.stream().findFirst();
    }

    public User save(String username,
                     String fullName,
                     String email,
                     String city,
                     String favoritePlayer,
                     String passwordHash) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into users (id, username, full_name, email, city, favorite_player, password_hash)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                id, username, fullName, email, city, favoritePlayer, passwordHash);
        return findById(id).orElseThrow();
    }

    public void setTeam(UUID userId, int teamId) {
        jdbc.update("update users set team_id = ? where id = ?", teamId, userId);
    }

    @CacheEvict(value = "leaderboard", allEntries = true)
    public void updateProfile(UUID userId,
                              String fullName,
                              String email,
                              String city,
                              String favoritePlayer) {
        jdbc.update("""
                update users
                   set full_name = ?,
                       email = ?,
                       city = ?,
                       favorite_player = ?
                 where id = ?
                """, fullName, email, city, favoritePlayer, userId);
    }

    @CacheEvict(value = "leaderboard", allEntries = true)
    public void addResult(UUID userId, int runs, boolean won) {
        jdbc.update("""
                update users
                   set total_runs = total_runs + ?,
                       matches_played = matches_played + 1,
                       matches_won = matches_won + ?
                 where id = ?
                """, runs, won ? 1 : 0, userId);
    }

    public int findRank(UUID userId) {
        Integer rank = jdbc.queryForObject("""
                with ranked_stats as (
                    select p.user_id,
                           sum(p.runs) as ranked_total_runs,
                           count(*) as ranked_matches_played,
                           sum(case when m.winner_id = p.user_id then 1 else 0 end) as ranked_matches_won
                      from matches m
                      join lateral (
                           values (m.player1_id, m.player1_runs),
                                  (m.player2_id, m.player2_runs)
                      ) as p(user_id, runs) on true
                     where m.status = 'FINISHED'
                       and m.is_friendly = false
                     group by p.user_id
                ),
                ranked as (
                    select u.id,
                           row_number() over (
                               order by coalesce(rs.ranked_matches_won, 0) desc,
                                        coalesce(rs.ranked_total_runs, 0) desc,
                                        coalesce(rs.ranked_matches_played, 0) desc,
                                        u.created_at asc
                           ) as rank_pos
                    from users u
                    left join ranked_stats rs on rs.user_id = u.id
                )
                select rank_pos
                from ranked
                where id = ?
                """, Integer.class, userId);
        return rank == null ? 0 : rank;
    }

    public record LeaderboardRow(
            int rank,
            UUID userId,
            String username,
            String fullName,
            String teamName,
            String city,
            String favoritePlayer,
            int matchesPlayed,
            int matchesWon,
            int totalRuns,
            double winRate
    ) {}

    @Cacheable(value = "leaderboard", key = "'all_time-' + #page + '-' + #size")
    public List<LeaderboardRow> leaderboard(int page, int size) {
        return leaderboard(page, size, "all_time");
    }

    @Cacheable(value = "leaderboard", key = "#period + '-' + #page + '-' + #size")
    public List<LeaderboardRow> leaderboard(int page, int size, String period) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = safePage * safeSize;
        String normalizedPeriod = normalizeLeaderboardPeriod(period);
        String periodClause = leaderboardPeriodClause(normalizedPeriod);
        String rankedStatsJoin = "all_time".equals(normalizedPeriod)
                ? "left join ranked_stats rs on rs.user_id = u.id"
                : "join ranked_stats rs on rs.user_id = u.id";

        String sql = """
                with ranked_stats as (
                    select p.user_id,
                           sum(p.runs) as ranked_total_runs,
                           count(*) as ranked_matches_played,
                           sum(case when m.winner_id = p.user_id then 1 else 0 end) as ranked_matches_won
                      from matches m
                      join lateral (
                           values (m.player1_id, m.player1_runs),
                                  (m.player2_id, m.player2_runs)
                      ) as p(user_id, runs) on true
                     where m.status = 'FINISHED'
                       and m.is_friendly = false
                       %s
                     group by p.user_id
                ),
                ranked as (
                    select u.id,
                           u.username,
                           u.full_name,
                           u.city,
                           u.favorite_player,
                           coalesce(rs.ranked_matches_played, 0) as matches_played,
                           coalesce(rs.ranked_matches_won, 0) as matches_won,
                           coalesce(rs.ranked_total_runs, 0) as total_runs,
                           t.name as team_name,
                           row_number() over (
                               order by coalesce(rs.ranked_matches_won, 0) desc,
                                        coalesce(rs.ranked_total_runs, 0) desc,
                                        coalesce(rs.ranked_matches_played, 0) desc,
                                        u.created_at asc
                           ) as rank_pos,
                           case
                               when coalesce(rs.ranked_matches_played, 0) = 0 then 0
                               else round((coalesce(rs.ranked_matches_won, 0)::numeric * 100.0)
                                           / coalesce(rs.ranked_matches_played, 1), 1)
                           end as win_rate
                    from users u
                    left join teams t on t.id = u.team_id
                    %s
                )
                select rank_pos,
                       id,
                       username,
                       full_name,
                       team_name,
                       city,
                       favorite_player,
                       matches_played,
                       matches_won,
                       total_runs,
                       win_rate
                from ranked
                order by rank_pos
                limit ? offset ?
                """.formatted(periodClause, rankedStatsJoin);

        return jdbc.query(sql, (rs, i) -> new LeaderboardRow(
                rs.getInt("rank_pos"),
                rs.getObject("id", UUID.class),
                rs.getString("username"),
                rs.getString("full_name"),
                rs.getString("team_name"),
                rs.getString("city"),
                rs.getString("favorite_player"),
                rs.getInt("matches_played"),
                rs.getInt("matches_won"),
                rs.getInt("total_runs"),
                rs.getDouble("win_rate")
        ), safeSize, offset);
    }

    public long leaderboardCount() {
        return leaderboardCount("all_time");
    }

    public long leaderboardCount(String period) {
        String normalizedPeriod = normalizeLeaderboardPeriod(period);
        if ("all_time".equals(normalizedPeriod)) {
            Long count = jdbc.queryForObject("select count(*) from users", Long.class);
            return count == null ? 0L : count;
        }

        String periodClause = leaderboardPeriodClause(normalizedPeriod);
        String sql = """
                select count(distinct p.user_id)
                  from matches m
                  join lateral (
                      values (m.player1_id),
                             (m.player2_id)
                  ) as p(user_id) on true
                 where m.status = 'FINISHED'
                   and m.is_friendly = false
                   %s
                """.formatted(periodClause);
        Long count = jdbc.queryForObject(sql, Long.class);
        return count == null ? 0L : count;
    }

    private String normalizeLeaderboardPeriod(String rawPeriod) {
        if (rawPeriod == null) {
            return "all_time";
        }
        String period = rawPeriod.trim().toLowerCase(Locale.ROOT);
        return switch (period) {
            case "weekly", "monthly" -> period;
            default -> "all_time";
        };
    }

    private String leaderboardPeriodClause(String period) {
        return switch (period) {
            case "weekly" -> "and coalesce(m.finished_at, m.started_at) >= date_trunc('week', now())";
            case "monthly" -> "and coalesce(m.finished_at, m.started_at) >= date_trunc('month', now())";
            default -> "";
        };
    }
}
