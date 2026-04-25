package com.zpl.handcricket.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MatchHistoryRepository {

    private final JdbcTemplate jdbc;

    public record MatchSummaryRow(
            UUID id,
            String myName,
            String myTeam,
            String opponentName,
            String opponentTeam,
            int myRuns,
            int opponentRuns,
            boolean won,
            String mode,
            OffsetDateTime playedAt
    ) {}

    public record MatchDetailRow(
            UUID id,
            UUID player1Id,
            UUID player2Id,
            UUID winnerId,
            UUID firstInningsBatterId,
            String player1Name,
            String player2Name,
            String player1Team,
            String player2Team,
            int player1Runs,
            int player2Runs,
            boolean friendly,
            OffsetDateTime playedAt
    ) {}

    public record BallDetailRow(
            int innings,
            int ballNo,
            String batterName,
            String bowlerName,
            int batterPick,
            int bowlerPick,
            int runs,
            boolean wicket
    ) {}

    public List<MatchSummaryRow> recent(UUID userId, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        String sql = baseSummarySelect()
                + " where m.status = 'FINISHED' and (m.player1_id = ? or m.player2_id = ?)"
                + " order by coalesce(m.finished_at, m.started_at) desc"
                + " limit ?";

        return jdbc.query(sql, (rs, i) -> new MatchSummaryRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("my_name"),
                        rs.getString("my_team"),
                        rs.getString("opponent_name"),
                        rs.getString("opponent_team"),
                        rs.getInt("my_runs"),
                        rs.getInt("opponent_runs"),
                        rs.getBoolean("won"),
                        rs.getString("mode"),
                        toOdt(rs.getTimestamp("played_at"))
                ),
                userId, userId,
                userId, userId,
                userId, userId,
                userId,
                userId, userId,
                boundedLimit);
    }

    public List<MatchSummaryRow> history(UUID userId, int page, int size, String q, String filter, String sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 50));
        String safeQuery = q == null ? "" : q.trim();

        StringBuilder sql = new StringBuilder(baseSummarySelect())
                .append(" where m.status = 'FINISHED'")
                .append(" and (m.player1_id = ? or m.player2_id = ?)");

        List<Object> args = new ArrayList<>();
        addBaseArgs(args, userId);
        args.add(userId);
        args.add(userId);

        appendFilter(sql, args, userId, filter);

        if (!safeQuery.isEmpty()) {
            sql.append(" and (")
                    .append(" lower(case when m.player1_id = ? then u2.username else u1.username end) like ?")
                    .append(" or lower(coalesce(case when m.player1_id = ? then t2.name else t1.name end, '')) like ?")
                    .append(" )");
            args.add(userId);
            args.add("%" + safeQuery.toLowerCase() + "%");
            args.add(userId);
            args.add("%" + safeQuery.toLowerCase() + "%");
        }

        sql.append(orderBy(sort));
        sql.append(" limit ? offset ?");
        args.add(safeSize);
        args.add(safePage * safeSize);

        return jdbc.query(sql.toString(), (rs, i) -> new MatchSummaryRow(
                rs.getObject("id", UUID.class),
                rs.getString("my_name"),
                rs.getString("my_team"),
                rs.getString("opponent_name"),
                rs.getString("opponent_team"),
                rs.getInt("my_runs"),
                rs.getInt("opponent_runs"),
                rs.getBoolean("won"),
                rs.getString("mode"),
                toOdt(rs.getTimestamp("played_at"))
        ), args.toArray());
    }

    public long historyCount(UUID userId, String q, String filter) {
        String safeQuery = q == null ? "" : q.trim();
        StringBuilder sql = new StringBuilder("""
                select count(*)
                  from matches m
                  join users u1 on u1.id = m.player1_id
                  join users u2 on u2.id = m.player2_id
                  left join teams t1 on t1.id = u1.team_id
                  left join teams t2 on t2.id = u2.team_id
                 where m.status = 'FINISHED'
                   and (m.player1_id = ? or m.player2_id = ?)
                """);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.add(userId);

        appendFilter(sql, args, userId, filter);

        if (!safeQuery.isEmpty()) {
            sql.append(" and (")
                    .append(" lower(case when m.player1_id = ? then u2.username else u1.username end) like ?")
                    .append(" or lower(coalesce(case when m.player1_id = ? then t2.name else t1.name end, '')) like ?")
                    .append(" )");
            args.add(userId);
            args.add("%" + safeQuery.toLowerCase() + "%");
            args.add(userId);
            args.add("%" + safeQuery.toLowerCase() + "%");
        }

        Long count = jdbc.queryForObject(sql.toString(), Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    public Optional<MatchDetailRow> detail(UUID userId, UUID matchId) {
        String sql = """
                select m.id,
                       m.player1_id,
                       m.player2_id,
                       m.winner_id,
                       m.toss_winner_id,
                       u1.username as player1_name,
                       u2.username as player2_name,
                       t1.name as player1_team,
                       t2.name as player2_team,
                       m.player1_runs,
                       m.player2_runs,
                       m.is_friendly,
                       coalesce(m.finished_at, m.started_at) as played_at
                  from matches m
                  join users u1 on u1.id = m.player1_id
                  join users u2 on u2.id = m.player2_id
                  left join teams t1 on t1.id = u1.team_id
                  left join teams t2 on t2.id = u2.team_id
                 where m.id = ?
                   and (m.player1_id = ? or m.player2_id = ?)
                 limit 1
                """;

        return jdbc.query(sql, (rs, i) -> new MatchDetailRow(
                rs.getObject("id", UUID.class),
                rs.getObject("player1_id", UUID.class),
                rs.getObject("player2_id", UUID.class),
                (UUID) rs.getObject("winner_id"),
                rs.getObject("toss_winner_id", UUID.class),
                rs.getString("player1_name"),
                rs.getString("player2_name"),
                rs.getString("player1_team"),
                rs.getString("player2_team"),
                rs.getInt("player1_runs"),
                rs.getInt("player2_runs"),
                rs.getBoolean("is_friendly"),
                toOdt(rs.getTimestamp("played_at"))
        ), matchId, userId, userId).stream().findFirst();
    }

    public List<BallDetailRow> ballsFor(UUID matchId, UUID firstInningsBatterId,
                                        UUID player1Id, UUID player2Id,
                                        String player1Name, String player2Name) {
        UUID secondInningsBatterId = firstInningsBatterId.equals(player1Id) ? player2Id : player1Id;

        String sql = """
                select innings, ball_no, batter_pick, bowler_pick, runs_scored, is_wicket
                  from balls
                 where match_id = ?
                 order by innings asc, ball_no asc
                """;

        return jdbc.query(sql, (rs, i) -> {
            int innings = rs.getInt("innings");
            boolean firstInnings = innings == 1;
            UUID batterId = firstInnings ? firstInningsBatterId : secondInningsBatterId;
            String batterName = batterId.equals(player1Id) ? player1Name : player2Name;
            String bowlerName = batterId.equals(player1Id) ? player2Name : player1Name;
            return new BallDetailRow(
                    innings,
                    rs.getInt("ball_no"),
                    batterName,
                    bowlerName,
                    rs.getInt("batter_pick"),
                    rs.getInt("bowler_pick"),
                    rs.getInt("runs_scored"),
                    rs.getBoolean("is_wicket")
            );
        }, matchId);
    }

    private String baseSummarySelect() {
        return """
                select m.id,
                       case when m.player1_id = ? then u1.username else u2.username end as my_name,
                       case when m.player1_id = ? then t1.name else t2.name end as my_team,
                       case when m.player1_id = ? then u2.username else u1.username end as opponent_name,
                       case when m.player1_id = ? then t2.name else t1.name end as opponent_team,
                       case when m.player1_id = ? then m.player1_runs else m.player2_runs end as my_runs,
                       case when m.player1_id = ? then m.player2_runs else m.player1_runs end as opponent_runs,
                       (m.winner_id = ?) as won,
                       case when m.is_friendly then 'FRIENDLY' else 'RANKED' end as mode,
                       coalesce(m.finished_at, m.started_at) as played_at
                  from matches m
                  join users u1 on u1.id = m.player1_id
                  join users u2 on u2.id = m.player2_id
                  left join teams t1 on t1.id = u1.team_id
                  left join teams t2 on t2.id = u2.team_id
                """;
    }

    private void addBaseArgs(List<Object> args, UUID userId) {
        args.add(userId);
        args.add(userId);
        args.add(userId);
        args.add(userId);
        args.add(userId);
        args.add(userId);
        args.add(userId);
    }

    private void appendFilter(StringBuilder sql, List<Object> args, UUID userId, String filterRaw) {
        String filter = filterRaw == null ? "all" : filterRaw.toLowerCase();
        switch (filter) {
            case "won" -> {
                sql.append(" and m.winner_id = ?");
                args.add(userId);
            }
            case "lost" -> {
                sql.append(" and (m.winner_id is null or m.winner_id <> ?)");
                args.add(userId);
            }
            case "ranked" -> sql.append(" and m.is_friendly = false");
            case "friendly" -> sql.append(" and m.is_friendly = true");
            default -> {
                // all
            }
        }
    }

    private String orderBy(String sortRaw) {
        String sort = sortRaw == null ? "latest" : sortRaw.toLowerCase();
        return switch (sort) {
            case "oldest" -> " order by coalesce(m.finished_at, m.started_at) asc";
            case "highest" -> " order by my_runs desc, coalesce(m.finished_at, m.started_at) desc";
            default -> " order by coalesce(m.finished_at, m.started_at) desc";
        };
    }

    private OffsetDateTime toOdt(Timestamp ts) {
        return ts == null ? null : ts.toInstant().atOffset(ZoneOffset.UTC);
    }
}
