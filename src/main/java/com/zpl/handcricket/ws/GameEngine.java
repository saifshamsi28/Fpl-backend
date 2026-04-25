package com.zpl.handcricket.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zpl.handcricket.model.Ball;
import com.zpl.handcricket.model.Match;
import com.zpl.handcricket.model.Team;
import com.zpl.handcricket.model.User;
import com.zpl.handcricket.repository.MatchRepository;
import com.zpl.handcricket.repository.TeamRepository;
import com.zpl.handcricket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand-Cricket rules (mirroring ZPL):
 *  - Each ball: both players pick a number 1..6 within a 3-second window.
 *  - If the batter does not pick in time, batter pick is treated as 0 runs.
 *  - If the bowler does not pick in time, innings still continues.
 *  - WICKET only when BOTH players picked and picks are equal.
 *  - Otherwise batter scores runs equal to the batter pick (or 0 if not picked).
 *  - After first innings wicket (or 6 balls max? ZPL uses 6 balls per innings per screenshots:
 *    the top-score dots show 6 circles) -> switch innings.
 *  - Target = player1Runs + 1 in second innings; once batter reaches target -> they win.
 *  - Otherwise after innings 2 ends (wicket OR all balls done) -> compare scores.
 *  - 6 balls per innings.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GameEngine {

    public static final int BALLS_PER_INNINGS = 6;
    public static final long PICK_TIMEOUT_MS = 4500L;
    private static final long INTRO_DELAY_MS = 3000L;
    private static final long NORMAL_NEXT_BALL_DELAY_MS = 900L;
    private static final long FINISH_DELAY_MS = 1200L;

    private final MatchRepository matches;
    private final UserRepository users;
    private final TeamRepository teams;
    private final ObjectMapper mapper = new ObjectMapper();

    // matchId -> live state
    private final Map<UUID, LiveMatch> live = new ConcurrentHashMap<>();
    private final Random random = new Random();

    public static class LiveMatch {
        public UUID matchId;
        public GameSession p1; // player1
        public GameSession p2; // player2
        public int innings = 1;
        public int ballNo = 0;
        public int p1Runs = 0;
        public int p2Runs = 0;
        public UUID batterId;
        public UUID bowlerId;
        public boolean friendly;
        public PendingAction pendingAction;
        public long pendingActionAtMs;
    }

    public enum PendingAction {
        START_NEXT_BALL,
        FINISH_MATCH
    }

    public void startMatch(GameSession a, GameSession b, boolean friendly, UUID roomId) {
        // Toss
        boolean aBatsFirst = random.nextBoolean();
        UUID batter = aBatsFirst ? a.getUserId() : b.getUserId();
        UUID bowler = aBatsFirst ? b.getUserId() : a.getUserId();
        UUID tossWinner = batter;

        Match m = matches.create(a.getUserId(), b.getUserId(), roomId, friendly, tossWinner, batter, bowler);

        LiveMatch lm = new LiveMatch();
        lm.matchId = m.getId();
        lm.p1 = a;
        lm.p2 = b;
        lm.batterId = batter;
        lm.bowlerId = bowler;
        lm.friendly = friendly;
        live.put(m.getId(), lm);

        a.setMatchId(m.getId());
        b.setMatchId(m.getId());
        a.setOpponentUserId(b.getUserId());
        b.setOpponentUserId(a.getUserId());

        // Enrich team data
        enrichTeam(a);
        enrichTeam(b);

        // Send "match_found"
        Map<String, Object> payloadA = matchFoundPayload(lm, a, b, tossWinner, batter);
        Map<String, Object> payloadB = matchFoundPayload(lm, b, a, tossWinner, batter);
        send(a, "match_found", payloadA);
        send(b, "match_found", payloadB);

        // Let the client show the intro banner before the first ball.
        scheduleAction(lm, PendingAction.START_NEXT_BALL, System.currentTimeMillis() + INTRO_DELAY_MS);
    }

    private void enrichTeam(GameSession s) {
        if (s.getTeamId() == null) {
            users.findById(s.getUserId()).ifPresent(u -> s.setTeamId(u.getTeamId()));
        }
        if (s.getTeamId() != null) {
            teams.findById(s.getTeamId()).ifPresent(t -> {
                s.setTeamName(t.getName());
                s.setLandmark(t.getLandmark());
                s.setPrimaryColor(t.getPrimaryColor());
            });
        }
    }

    private Map<String, Object> matchFoundPayload(LiveMatch lm, GameSession self, GameSession opp,
                                                   UUID tossWinner, UUID firstBatter) {
        Map<String, Object> m = new HashMap<>();
        m.put("matchId", lm.matchId.toString());
        m.put("youBatFirst", firstBatter.equals(self.getUserId()));
        m.put("tossWinnerIsYou", tossWinner.equals(self.getUserId()));
        m.put("you", playerPayload(self));
        m.put("opponent", playerPayload(opp));
        m.put("ballsPerInnings", BALLS_PER_INNINGS);
        return m;
    }

    private Map<String, Object> playerPayload(GameSession s) {
        Map<String, Object> p = new HashMap<>();
        p.put("userId", s.getUserId().toString());
        p.put("username", s.getUsername());
        p.put("teamId", s.getTeamId());
        p.put("teamName", s.getTeamName());
        p.put("landmark", s.getLandmark());
        p.put("color", s.getPrimaryColor());
        return p;
    }

    private void startNewBall(LiveMatch lm) {
        lm.ballNo++;
        lm.p1.setCurrentPick(null);
        lm.p2.setCurrentPick(null);
        long now = System.currentTimeMillis();
        lm.p1.setCurrentBallStartedAtMs(now);
        lm.p2.setCurrentBallStartedAtMs(now);

        Map<String, Object> payload = new HashMap<>();
        payload.put("innings", lm.innings);
        payload.put("ballNo", lm.ballNo);
        payload.put("batterId", lm.batterId.toString());
        payload.put("bowlerId", lm.bowlerId.toString());
        payload.put("timerMs", PICK_TIMEOUT_MS);

        send(lm.p1, "ball_start", payload);
        send(lm.p2, "ball_start", payload);
    }

    public void onPick(GameSession s, int pick) {
        if (pick < 1 || pick > 6) return;
        UUID matchId = s.getMatchId();
        if (matchId == null) return;
        LiveMatch lm = live.get(matchId);
        if (lm == null) return;

        s.setCurrentPick(pick);

        // Notify opponent that you have locked a pick (without revealing value)
        GameSession opp = lm.p1 == s ? lm.p2 : lm.p1;
        send(opp, "opponent_locked", Map.of("ballNo", lm.ballNo));

        // Do not resolve immediately; reveal happens only after timer completes.
    }

    /** Called periodically by the scheduler; resolves balls where one side timed out. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (LiveMatch lm : live.values()) {
            if (lm.pendingAction != null && now >= lm.pendingActionAtMs) {
                PendingAction action = lm.pendingAction;
                lm.pendingAction = null;
                if (action == PendingAction.START_NEXT_BALL) {
                    startNewBall(lm);
                } else if (action == PendingAction.FINISH_MATCH) {
                    finish(lm);
                }
                continue;
            }

            long started = lm.p1.getCurrentBallStartedAtMs();
            if (started <= 0) continue;
            if (now - started >= PICK_TIMEOUT_MS) {
                resolveBall(lm);
            }
        }
    }

    private void resolveBall(LiveMatch lm) {
        // Guard against repeated tick() calls before the delayed next-ball action fires.
        if (lm.p1.getCurrentBallStartedAtMs() <= 0) {
            return;
        }
        lm.p1.setCurrentBallStartedAtMs(0);
        lm.p2.setCurrentBallStartedAtMs(0);

        Integer p1PickRaw = lm.p1.getCurrentPick();
        Integer p2PickRaw = lm.p2.getCurrentPick();
        int p1Pick = p1PickRaw == null ? 0 : p1PickRaw;
        int p2Pick = p2PickRaw == null ? 0 : p2PickRaw;

        boolean p1IsBatter = lm.batterId.equals(lm.p1.getUserId());
        int batterPick = p1IsBatter ? p1Pick : p2Pick;
        int bowlerPick = p1IsBatter ? p2Pick : p1Pick;
        boolean batterSubmitted = p1IsBatter ? p1PickRaw != null : p2PickRaw != null;
        boolean bowlerSubmitted = p1IsBatter ? p2PickRaw != null : p1PickRaw != null;
        boolean wicket = batterSubmitted && bowlerSubmitted && batterPick == bowlerPick;
        int runs = (batterSubmitted && !wicket) ? batterPick : 0;

        // Update score
        if (!wicket) {
            if (p1IsBatter) lm.p1Runs += runs;
            else lm.p2Runs += runs;
            try {
                matches.updateRuns(lm.matchId,
                        p1IsBatter ? lm.p1.getUserId() : lm.p2.getUserId(),
                        runs, p1IsBatter);
            } catch (Exception ex) {
                log.error("Failed to update runs for match {}", lm.matchId, ex);
            }
        }

        // Persist the ball
        int storedBatterPick = batterSubmitted ? batterPick : 0;
        int storedBowlerPick = bowlerSubmitted ? bowlerPick : 0;
        try {
            matches.insertBall(Ball.builder()
                    .matchId(lm.matchId)
                    .innings(lm.innings)
                    .ballNo(lm.ballNo)
                    .batterPick(storedBatterPick)
                    .bowlerPick(storedBowlerPick)
                    .runsScored(runs)
                    .wicket(wicket)
                    .build());
        } catch (Exception ex) {
            // Do not freeze gameplay because of a persistence issue.
            log.error("Failed to persist ball result for match {} innings {} ball {}",
                    lm.matchId, lm.innings, lm.ballNo, ex);
        }

        // Broadcast the revealed ball
        Map<String, Object> payload = new HashMap<>();
        payload.put("innings", lm.innings);
        payload.put("ballNo", lm.ballNo);
        payload.put("batterPick", batterPick);
        payload.put("bowlerPick", bowlerPick);
        payload.put("wicket", wicket);
        payload.put("runs", runs);
        payload.put("p1Runs", lm.p1Runs);
        payload.put("p2Runs", lm.p2Runs);
        payload.put("batterId", lm.batterId.toString());
        payload.put("batterSubmitted", batterSubmitted);
        payload.put("bowlerSubmitted", bowlerSubmitted);
        send(lm.p1, "ball_result", payload);
        send(lm.p2, "ball_result", payload);

        lm.p1.setCurrentPick(null);
        lm.p2.setCurrentPick(null);

        // Determine what's next
        boolean inningsEnded = wicket || lm.ballNo >= BALLS_PER_INNINGS;
        boolean chaseCompleted = lm.innings == 2 && (
                (lm.batterId.equals(lm.p1.getUserId()) ? lm.p1Runs : lm.p2Runs)
                        > (lm.batterId.equals(lm.p1.getUserId()) ? lm.p2Runs : lm.p1Runs)
        );

        if (chaseCompleted) {
            scheduleAction(lm, PendingAction.FINISH_MATCH, System.currentTimeMillis() + FINISH_DELAY_MS);
        } else if (inningsEnded) {
            if (lm.innings == 1) {
                // Switch innings
                lm.innings = 2;
                lm.ballNo = 0;
                UUID newBatter = lm.bowlerId;
                UUID newBowler = lm.batterId;
                lm.batterId = newBatter;
                lm.bowlerId = newBowler;
                matches.switchInnings(lm.matchId, newBatter, newBowler);

                Map<String, Object> inn = new HashMap<>();
                inn.put("target", (lm.batterId.equals(lm.p1.getUserId()) ? lm.p2Runs : lm.p1Runs) + 1);
                inn.put("newBatterId", newBatter.toString());
                inn.put("newBowlerId", newBowler.toString());
                send(lm.p1, "innings_switch", inn);
                send(lm.p2, "innings_switch", inn);

                scheduleAction(lm, PendingAction.START_NEXT_BALL, System.currentTimeMillis() + INTRO_DELAY_MS);
            } else {
                scheduleAction(lm, PendingAction.FINISH_MATCH, System.currentTimeMillis() + FINISH_DELAY_MS);
            }
        } else {
            scheduleAction(lm, PendingAction.START_NEXT_BALL, System.currentTimeMillis() + NORMAL_NEXT_BALL_DELAY_MS);
        }
    }

    private void finish(LiveMatch lm) {
        UUID winnerId;
        if (lm.p1Runs > lm.p2Runs) winnerId = lm.p1.getUserId();
        else if (lm.p2Runs > lm.p1Runs) winnerId = lm.p2.getUserId();
        else winnerId = null; // tie

        matches.finish(lm.matchId, winnerId);
        users.addResult(lm.p1.getUserId(), lm.p1Runs, winnerId != null && winnerId.equals(lm.p1.getUserId()));
        users.addResult(lm.p2.getUserId(), lm.p2Runs, winnerId != null && winnerId.equals(lm.p2.getUserId()));

        Map<String, Object> payload = new HashMap<>();
        payload.put("matchId", lm.matchId.toString());
        payload.put("p1Runs", lm.p1Runs);
        payload.put("p2Runs", lm.p2Runs);
        payload.put("player1Id", lm.p1.getUserId().toString());
        payload.put("player2Id", lm.p2.getUserId().toString());
        payload.put("winnerId", winnerId == null ? null : winnerId.toString());
        send(lm.p1, "match_end", payload);
        send(lm.p2, "match_end", payload);

        lm.p1.setMatchId(null);
        lm.p2.setMatchId(null);
        live.remove(lm.matchId);
    }

    public void handleDisconnect(GameSession s) {
        UUID matchId = s.getMatchId();
        if (matchId == null) return;
        LiveMatch lm = live.get(matchId);
        if (lm == null) return;
        GameSession opp = lm.p1 == s ? lm.p2 : lm.p1;

        // Opponent auto-wins
        UUID winnerId = opp.getUserId();
        matches.finish(matchId, winnerId);
        send(opp, "opponent_left", Map.of("matchId", matchId.toString(), "winnerId", winnerId.toString()));
        live.remove(matchId);
    }

    private void send(GameSession s, String type, Object data) {
        try {
            Map<String, Object> env = new HashMap<>();
            env.put("type", type);
            env.put("data", data);
            String json = mapper.writeValueAsString(env);
            if (s.getWs().isOpen()) s.getWs().sendMessage(new TextMessage(json));
        } catch (IOException e) {
            log.warn("ws send failed", e);
        }
    }

    private void scheduleAction(LiveMatch lm, PendingAction action, long atMs) {
        lm.pendingAction = action;
        lm.pendingActionAtMs = atMs;
    }
}
