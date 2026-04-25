package com.zpl.handcricket.repository;

import com.zpl.handcricket.model.Ball;
import com.zpl.handcricket.model.Match;
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
public class MatchRepository {

    private final JdbcTemplate jdbc;

    private final RowMapper<Match> mapper = (rs, i) -> Match.builder()
            .id(rs.getObject("id", UUID.class))
            .roomId((UUID) rs.getObject("room_id"))
            .player1Id(rs.getObject("player1_id", UUID.class))
            .player2Id(rs.getObject("player2_id", UUID.class))
            .player1Runs(rs.getInt("player1_runs"))
            .player2Runs(rs.getInt("player2_runs"))
            .winnerId((UUID) rs.getObject("winner_id"))
            .friendly(rs.getBoolean("is_friendly"))
            .currentInnings(rs.getInt("current_innings"))
            .batterId((UUID) rs.getObject("batter_id"))
            .bowlerId((UUID) rs.getObject("bowler_id"))
            .tossWinnerId((UUID) rs.getObject("toss_winner_id"))
            .status(rs.getString("status"))
            .startedAt(toOdt(rs.getTimestamp("started_at")))
            .finishedAt(toOdt(rs.getTimestamp("finished_at")))
            .build();

    private OffsetDateTime toOdt(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    public Match create(UUID p1, UUID p2, UUID roomId, boolean friendly,
                        UUID tossWinner, UUID firstBatter, UUID firstBowler) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into matches (id, room_id, player1_id, player2_id, is_friendly,
                                     toss_winner_id, batter_id, bowler_id, current_innings)
                values (?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, id, roomId, p1, p2, friendly, tossWinner, firstBatter, firstBowler);
        return findById(id).orElseThrow();
    }

    public Optional<Match> findById(UUID id) {
        return jdbc.query("select * from matches where id = ?", mapper, id).stream().findFirst();
    }

    public void updateRuns(UUID matchId, UUID playerId, int addRuns, boolean forPlayer1) {
        String col = forPlayer1 ? "player1_runs" : "player2_runs";
        jdbc.update("update matches set " + col + " = " + col + " + ? where id = ?", addRuns, matchId);
    }

    public void switchInnings(UUID matchId, UUID newBatter, UUID newBowler) {
        jdbc.update("""
                update matches
                   set current_innings = 2,
                       batter_id = ?,
                       bowler_id = ?
                 where id = ?
                """, newBatter, newBowler, matchId);
    }

    public void finish(UUID matchId, UUID winnerId) {
        jdbc.update("""
                update matches
                   set status = 'FINISHED',
                       winner_id = ?,
                       finished_at = now()
                 where id = ?
                """, winnerId, matchId);
    }

    // Balls
    public void insertBall(Ball b) {
        jdbc.update("""
                insert into balls (match_id, innings, ball_no, batter_pick, bowler_pick, runs_scored, is_wicket)
                values (?, ?, ?, ?, ?, ?, ?)
                """, b.getMatchId(), b.getInnings(), b.getBallNo(),
                b.getBatterPick(), b.getBowlerPick(), b.getRunsScored(), b.isWicket());
    }

    public List<Ball> ballsFor(UUID matchId, int innings) {
        return jdbc.query("select * from balls where match_id = ? and innings = ? order by ball_no",
                (rs, i) -> Ball.builder()
                        .id(rs.getLong("id"))
                        .matchId(rs.getObject("match_id", UUID.class))
                        .innings(rs.getInt("innings"))
                        .ballNo(rs.getInt("ball_no"))
                        .batterPick(rs.getInt("batter_pick"))
                        .bowlerPick(rs.getInt("bowler_pick"))
                        .runsScored(rs.getInt("runs_scored"))
                        .wicket(rs.getBoolean("is_wicket"))
                        .build(),
                matchId, innings);
    }
}
