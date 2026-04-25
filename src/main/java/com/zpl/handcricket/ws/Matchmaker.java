package com.zpl.handcricket.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Simple FIFO matchmaking queue. Two players in queue -> start a ranked match.
 * Also holds a waiting-room map for friendly matches keyed by roomId.
 */
@Component
@RequiredArgsConstructor
public class Matchmaker {

    private final GameEngine engine;

    private final ConcurrentLinkedQueue<GameSession> rankedQueue = new ConcurrentLinkedQueue<>();
    // roomId -> host session (waiting for guest)
    private final Map<UUID, GameSession> friendlyHosts = new ConcurrentHashMap<>();

    public void enqueueRanked(GameSession s) {
        rankedQueue.removeIf(x -> x.getUserId().equals(s.getUserId()));
        rankedQueue.add(s);
        drain();
    }

    public void cancel(GameSession s) {
        rankedQueue.removeIf(x -> x.getUserId().equals(s.getUserId()));
        friendlyHosts.entrySet().removeIf(e -> e.getValue() == s);
    }

    public void hostFriendly(UUID roomId, GameSession host) {
        friendlyHosts.put(roomId, host);
    }

    public boolean hasFriendlyHost(UUID roomId) {
        GameSession host = friendlyHosts.get(roomId);
        return host != null && host.getWs().isOpen();
    }

    public boolean joinFriendly(UUID roomId, GameSession guest) {
        GameSession host = friendlyHosts.remove(roomId);
        if (host != null && host.getWs().isOpen()) {
            engine.startMatch(host, guest, true, roomId);
            return true;
        }
        return false;
    }

    private synchronized void drain() {
        while (rankedQueue.size() >= 2) {
            GameSession a = rankedQueue.poll();
            GameSession b = rankedQueue.poll();
            if (a == null || b == null) return;
            if (!a.getWs().isOpen()) { if (b != null) rankedQueue.add(b); return; }
            if (!b.getWs().isOpen()) { rankedQueue.add(a); return; }
            engine.startMatch(a, b, false, null);
        }
    }

    @Scheduled(fixedDelay = 500)
    public void tickTimeouts() {
        engine.tick();
    }
}
