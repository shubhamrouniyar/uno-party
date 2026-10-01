package com.unoparty.presence;

import com.unoparty.dto.GameStateView;
import com.unoparty.dto.PlayerView;
import com.unoparty.service.GameService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketPresenceListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketPresenceListener.class);

    private final PresenceService presence;
    private final GameService gameService;
    private final SimpMessagingTemplate messaging;

    public WebSocketPresenceListener(PresenceService presence, GameService gameService,
                                     SimpMessagingTemplate messaging) {
        this.presence = presence;
        this.gameService = gameService;
        this.messaging = messaging;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        presence.unbind(sessionId).ifPresent(seat -> {
            // Reconnect race: new STOMP session may already be bound — do NOT mark offline.
            if (presence.hasActiveSession(seat.roomCode(), seat.playerId())) {
                log.info("WS disconnect ignored (still active) session={} room={} player={}",
                        sessionId, seat.roomCode(), seat.playerId());
                return;
            }
            log.info("WS disconnect session={} room={} player={}", sessionId, seat.roomCode(), seat.playerId());
            boolean changed = gameService.markDisconnected(seat.roomCode(), seat.playerId());
            if (changed) {
                broadcast(seat.roomCode());
            }
        });
    }

    private void broadcast(String code) {
        try {
            GameStateView probe = gameService.getStateForPlayer(code, null);
            if (probe.getPlayers() == null) return;
            for (PlayerView pv : probe.getPlayers()) {
                GameStateView personal = gameService.getStateForPlayer(code, pv.getId());
                messaging.convertAndSend("/topic/room/" + code + "/player/" + pv.getId(), personal);
            }
            messaging.convertAndSend("/topic/room/" + code, probe);
        } catch (Exception e) {
            log.debug("Broadcast after disconnect failed for {}: {}", code, e.getMessage());
        }
    }
}
