package com.zpl.handcricket.service;

import com.zpl.handcricket.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DailyResetJob {

    private final UserRepository users;

    // Every 10 minutes, reset matches_left for users whose last_reset was >24h ago.
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    public void run() {
        users.resetDailyIfNeeded();
    }
}
