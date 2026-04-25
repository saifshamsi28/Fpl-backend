package com.zpl.handcricket.ws;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.util.UUID;

/**
 * A live per-player connection. We keep a thin state object attached to the WebSocketSession.
 */
@Getter
@Setter
public class GameSession {
    private final WebSocketSession ws;
    private UUID userId;
    private String username;
    private Integer teamId;
    private String teamName;
    private String landmark;
    private String primaryColor;

    // Current match context
    private UUID matchId;
    private UUID opponentUserId;

    // Current ball state
    private Integer currentPick; // 1..6 or null if not picked yet
    private long currentBallStartedAtMs;

    public GameSession(WebSocketSession ws) {
        this.ws = ws;
    }
}
