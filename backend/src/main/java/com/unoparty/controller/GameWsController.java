package com.unoparty.controller;

import com.unoparty.dto.GameAction;
import com.unoparty.dto.GameStateView;
import com.unoparty.service.GameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class GameWsController {

    private final GameService gameService;
    private final SimpMessagingTemplate messaging;

    public GameWsController(GameService gameService, SimpMessagingTemplate messaging) {
        this.gameService = gameService;
        this.messaging = messaging;
    }

    @MessageMapping("/room/{code}/action")
    public void handleAction(@DestinationVariable String code, @Payload GameAction action) {
        String upper = code.toUpperCase();
        GameStateView actorView = gameService.handleAction(upper, action);

        // If leave emptied the room, nothing to broadcast
        try {
            broadcastPersonalized(upper);
        } catch (Exception ignored) {
            // room may have been deleted on leave
        }

        // Also send personal error echo if any
        if (actorView.getError() != null && action.getPlayerId() != null) {
            messaging.convertAndSend(
                    "/topic/room/" + upper + "/player/" + action.getPlayerId(),
                    actorView);
        }
    }

    @MessageMapping("/room/{code}/sync")
    public void sync(@DestinationVariable String code, @Payload GameAction action) {
        String upper = code.toUpperCase();
        if (action.getPlayerId() != null) {
            gameService.markConnected(upper, action.getPlayerId());
            GameStateView view = gameService.getStateForPlayer(upper, action.getPlayerId());
            messaging.convertAndSend(
                    "/topic/room/" + upper + "/player/" + action.getPlayerId(),
                    view);
            broadcastPersonalized(upper);
        }
    }

    private void broadcastPersonalized(String code) {
        // Send each player their personalized state (with their hand)
        // We iterate via a public-ish snapshot by asking GameService
        GameStateView probe = gameService.getStateForPlayer(code, null);
        if (probe.getPlayers() == null) return;

        for (var pv : probe.getPlayers()) {
            GameStateView personal = gameService.getStateForPlayer(code, pv.getId());
            messaging.convertAndSend(
                    "/topic/room/" + code + "/player/" + pv.getId(),
                    personal);
        }
        // Also send lobby-safe public update (no hands)
        messaging.convertAndSend("/topic/room/" + code, probe);
    }
}
