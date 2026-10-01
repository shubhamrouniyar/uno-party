package com.unoparty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RoomCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupScheduler.class);
    private final GameService gameService;

    public RoomCleanupScheduler(GameService gameService) {
        this.gameService = gameService;
    }

    @Scheduled(fixedRate = 300_000) // every 5 minutes
    public void cleanup() {
        int removed = gameService.purgeIdleRooms();
        if (removed > 0) {
            log.info("Purged {} idle room(s)", removed);
        }
    }
}
