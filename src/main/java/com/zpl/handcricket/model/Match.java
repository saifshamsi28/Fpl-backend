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
public class Match {
    private UUID id;
    private UUID roomId;
    private UUID player1Id;
    private UUID player2Id;
    private int player1Runs;
    private int player2Runs;
    private UUID winnerId;
    private boolean friendly;
    private int currentInnings;
    private UUID batterId;
    private UUID bowlerId;
    private UUID tossWinnerId;
    private String status; // ONGOING | FINISHED
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
}
