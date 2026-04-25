package com.zpl.handcricket.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ball {
    private Long id;
    private UUID matchId;
    private int innings;
    private int ballNo;
    private int batterPick;
    private int bowlerPick;
    private int runsScored;
    private boolean wicket;
}
