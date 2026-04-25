package com.zpl.handcricket.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private UUID id;
    private String username;
    private String passwordHash;
    private Integer teamId;
    private int totalRuns;
    private int matchesPlayed;
    private int matchesWon;
    private int matchesLeftToday;
    private OffsetDateTime lastResetAt;
    private OffsetDateTime createdAt;
}
