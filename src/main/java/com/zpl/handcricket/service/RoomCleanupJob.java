package com.zpl.handcricket.service;

import com.zpl.handcricket.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoomCleanupJob {

    private final RoomRepository rooms;

    // Bulk-close abandoned waiting/ready rooms once a day.
    @Scheduled(fixedDelay = 24 * 60 * 60 * 1000L)
    public void run() {
        int closed = rooms.closeStaleRooms();
        if (closed > 0) {
            log.info("Closed {} stale room(s)", closed);
        }
    }
}
