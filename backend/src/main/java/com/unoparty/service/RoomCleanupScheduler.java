package com.unoparty.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RoomCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoomCleanupScheduler.class);
    private final GameService gameService;
    private final SimpMessagingTemplate messaging;

    public RoomCleanupScheduler(GameService gameService, SimpMessagingTemplate messaging) {
        this.gameService = gameService;
        this.messaging = messaging;
    }

    @Scheduled(fixedRate = 5_000)
    public void purgeDisconnects() {
        List<String> changed = gameService.purgeTimedOutDisconnects();
        for (String code : changed) {
            if (!gameService.roomExists(code)) continue;
            try {
                broadcast(code);
            } catch (Exception e) {
                log.debug("Broadcast after timeout purge failed for {}: {}", code, e.getMessage());
            }
        }
    }

    @Scheduled(fixedRate = 300_000)
    public void cleanupIdleRooms() {
        int removed = gameService.purgeIdleRooms();
        if (removed > 0) {
            log.info("Purged {} idle room(s)", removed);
        }
    }

    private void broadcast(String code) {
        for (var snap : gameService.addressedStates(code)) {
            messaging.convertAndSend("/topic/room/" + code + "/player/" + snap.playerId(), snap.view());
        }
    }
}
