package com.zpl.handcricket.controller;

import com.zpl.handcricket.repository.MatchHistoryRepository;
import com.zpl.handcricket.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class MatchController {

    private final AuthService auth;
    private final MatchHistoryRepository history;

    public record MatchSummaryDto(
            String id,
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

    public record BallDetailDto(
            int innings,
            int ballNo,
            String batterName,
            String bowlerName,
            int batterPick,
            int bowlerPick,
            int runs,
            boolean wicket
    ) {}

    public record MatchDetailDto(
            String id,
            String myName,
            String myTeam,
            String opponentName,
            String opponentTeam,
            int myRuns,
            int opponentRuns,
            boolean won,
            String mode,
            OffsetDateTime playedAt,
            List<BallDetailDto> balls
    ) {}

    public record PageResponseDto<T>(
            List<T> items,
            int page,
            int size,
            int totalPages,
            long totalItems
    ) {}

    @GetMapping("/recent")
    public List<MatchSummaryDto> recent(@RequestHeader("Authorization") String authorization,
                                        @RequestParam(defaultValue = "3") int limit) {
        UUID userId = auth.requireUser(authorization).getId();
        return history.recent(userId, limit).stream()
                .map(this::toSummary)
                .toList();
    }

    @GetMapping
    public PageResponseDto<MatchSummaryDto> list(@RequestHeader("Authorization") String authorization,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "15") int size,
                                                 @RequestParam(defaultValue = "") String q,
                                                 @RequestParam(defaultValue = "all") String filter,
                                                 @RequestParam(defaultValue = "latest") String sort) {
        UUID userId = auth.requireUser(authorization).getId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 50));
        long totalItems = history.historyCount(userId, q, filter);
        int totalPages = (int) Math.max(1, (long) Math.ceil((double) totalItems / safeSize));
        List<MatchSummaryDto> items = history.history(userId, safePage, safeSize, q, filter, sort)
                .stream()
                .map(this::toSummary)
                .toList();
        return new PageResponseDto<>(items, safePage, safeSize, totalPages, totalItems);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> detail(@RequestHeader("Authorization") String authorization,
                                    @PathVariable String id) {
        UUID userId = auth.requireUser(authorization).getId();
        UUID matchId;
        try {
            matchId = UUID.fromString(id);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid match id"));
        }

        var detailOpt = history.detail(userId, matchId);
        if (detailOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Match not found"));
        }

        var m = detailOpt.get();
        boolean meIsP1 = userId.equals(m.player1Id());
        String myName = meIsP1 ? m.player1Name() : m.player2Name();
        String myTeam = meIsP1 ? m.player1Team() : m.player2Team();
        String oppName = meIsP1 ? m.player2Name() : m.player1Name();
        String oppTeam = meIsP1 ? m.player2Team() : m.player1Team();
        int myRuns = meIsP1 ? m.player1Runs() : m.player2Runs();
        int oppRuns = meIsP1 ? m.player2Runs() : m.player1Runs();
        boolean won = userId.equals(m.winnerId());

        List<BallDetailDto> balls = history.ballsFor(
                        m.id(),
                        m.firstInningsBatterId(),
                        m.player1Id(),
                        m.player2Id(),
                        m.player1Name(),
                        m.player2Name())
                .stream()
                .map(b -> new BallDetailDto(
                        b.innings(),
                        b.ballNo(),
                        b.batterName(),
                        b.bowlerName(),
                        b.batterPick(),
                        b.bowlerPick(),
                        b.runs(),
                        b.wicket()))
                .toList();

        MatchDetailDto dto = new MatchDetailDto(
                m.id().toString(),
                myName,
                myTeam,
                oppName,
                oppTeam,
                myRuns,
                oppRuns,
                won,
                m.friendly() ? "FRIENDLY" : "RANKED",
                m.playedAt(),
                balls
        );
        return ResponseEntity.ok(dto);
    }

    private MatchSummaryDto toSummary(MatchHistoryRepository.MatchSummaryRow s) {
        return new MatchSummaryDto(
                s.id().toString(),
                s.myName(),
                s.myTeam(),
                s.opponentName(),
                s.opponentTeam(),
                s.myRuns(),
                s.opponentRuns(),
                s.won(),
                s.mode(),
                s.playedAt()
        );
    }
}
