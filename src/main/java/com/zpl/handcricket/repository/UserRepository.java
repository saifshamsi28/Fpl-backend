package com.zpl.handcricket.repository;

import com.zpl.handcricket.model.User;
import lombok.RequiredArgsConstructor;
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

    public User save(String username, String passwordHash) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, username, password_hash) values (?, ?, ?)",
                id, username, passwordHash);
        return findById(id).orElseThrow();
    }

    public void setTeam(UUID userId, int teamId) {
        jdbc.update("update users set team_id = ? where id = ?", teamId, userId);
    }

    public void decrementMatchesLeft(UUID userId) {
        jdbc.update("update users set matches_left_today = greatest(matches_left_today - 1, 0) where id = ?", userId);
    }

    public void addResult(UUID userId, int runs, boolean won) {
        jdbc.update("""
                update users
                   set total_runs = total_runs + ?,
                       matches_played = matches_played + 1,
                       matches_won = matches_won + ?
                 where id = ?
                """, runs, won ? 1 : 0, userId);
    }

    public void resetDailyIfNeeded() {
        jdbc.update("""
                update users
                   set matches_left_today = 3,
                       last_reset_at = now()
                 where last_reset_at < (now() - interval '24 hours')
                """);
    }
}
