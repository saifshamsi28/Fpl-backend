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
                with ranked as (
                    select id,
                           row_number() over (
                               order by matches_won desc,
                                        total_runs desc,
                                        matches_played desc,
                                        created_at asc
                           ) as rank_pos
                    from users
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

    @Cacheable(value = "leaderboard", key = "#page + '-' + #size")
    public List<LeaderboardRow> leaderboard(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = safePage * safeSize;

        return jdbc.query("""
                with ranked as (
                    select u.id,
                           u.username,
                           u.full_name,
                           u.city,
                           u.favorite_player,
                           u.matches_played,
                           u.matches_won,
                           u.total_runs,
                           t.name as team_name,
                           row_number() over (
                               order by u.matches_won desc,
                                        u.total_runs desc,
                                        u.matches_played desc,
                                        u.created_at asc
                           ) as rank_pos,
                           case
                               when u.matches_played = 0 then 0
                               else round((u.matches_won::numeric * 100.0) / u.matches_played, 1)
                           end as win_rate
                    from users u
                    left join teams t on t.id = u.team_id
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
                """, (rs, i) -> new LeaderboardRow(
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
        Long count = jdbc.queryForObject("select count(*) from users", Long.class);
        return count == null ? 0L : count;
    }
}
