package com.zpl.handcricket.controller;

import com.zpl.handcricket.repository.UserRepository;
import com.zpl.handcricket.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final AuthService auth;
    private final UserRepository users;

    public record LeaderboardEntryDto(
            int rank,
            String userId,
            String username,
            String fullName,
            String teamName,
            String city,
            String favoritePlayer,
            int matchesPlayed,
            int matchesWon,
            int totalRuns,
            double winRate,
            boolean you
    ) {}

    public record PageResponseDto<T>(
            List<T> items,
            int page,
            int size,
            int totalPages,
            long totalItems
    ) {}

    @GetMapping
    public PageResponseDto<LeaderboardEntryDto> list(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "all_time") String period
    ) {
        var me = auth.requireUser(authorization);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        long totalItems = users.leaderboardCount(period);
        int totalPages = (int) Math.max(1, Math.ceil((double) totalItems / safeSize));

        List<LeaderboardEntryDto> rows = users.leaderboard(safePage, safeSize, period).stream()
                .map(r -> new LeaderboardEntryDto(
                        r.rank(),
                        r.userId().toString(),
                        r.username(),
                        r.fullName(),
                        r.teamName(),
                        r.city(),
                        r.favoritePlayer(),
                        r.matchesPlayed(),
                        r.matchesWon(),
                        r.totalRuns(),
                        r.winRate(),
                        r.userId().equals(me.getId())
                ))
                .toList();

        return new PageResponseDto<>(rows, safePage, safeSize, totalPages, totalItems);
    }
}
