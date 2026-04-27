package com.zpl.handcricket.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    private String fullName;
    private String email;
    private String city;
    private String favoritePlayer;
    @JsonIgnore
    private String passwordHash;
    private Integer teamId;
    private int totalRuns;
    private int matchesPlayed;
    private int matchesWon;
    private int matchesLeftToday;
    private Integer rank;
    private OffsetDateTime lastResetAt;
    private OffsetDateTime createdAt;
}
