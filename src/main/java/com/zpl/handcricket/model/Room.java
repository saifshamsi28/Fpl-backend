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
public class Room {
    private UUID id;
    private String code;
    private UUID hostUserId;
    private UUID guestUserId;
    private String status; // WAITING | READY | CLOSED
    private OffsetDateTime createdAt;
}
