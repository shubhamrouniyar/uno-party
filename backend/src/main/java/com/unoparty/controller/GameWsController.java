package com.unoparty.controller;

import com.unoparty.dto.GameAction;
import com.unoparty.dto.GameStateView;
import com.unoparty.dto.PlayerView;
import com.unoparty.presence.PresenceService;
import com.unoparty.service.GameService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class GameWsController {

    private final GameService gameService;
    private final SimpMessagingTemplate messaging;
    private final PresenceService presence;

    public GameWsController(GameService gameService, SimpMessagingTemplate messaging,
                            PresenceService presence) {
        this.gameService = gameService;
        this.messaging = messaging;
        this.presence = presence;
    }

    @MessageMapping("/room/{code}/action")
    public void handleAction(@DestinationVariable String code, @Payload GameAction action,
                             SimpMessageHeaderAccessor headers) {
        String upper;
        try {
            upper = GameService.normalizeCode(code);
        } catch (Exception e) {
            return;
        }

        if (action.getPlayerId() != null) {
            presence.bind(headers.getSessionId(), upper, action.getPlayerId());
        }

        boolean isLeave = action.getType() != null
                && "LEAVE".equalsIgnoreCase(action.getType());

        GameStateView actorView = gameService.handleAction(upper, action);

        if (isLeave && action.getPlayerId() != null) {
            presence.clearPlayer(upper, action.getPlayerId());
        }

        try {
            if (gameService.roomExists(upper)) {
                broadcastPersonalized(upper);
            }
        } catch (Exception ignored) {
            // room may have been deleted on leave
        }

        if (actorView.getError() != null && action.getPlayerId() != null) {
            messaging.convertAndSend(
                    "/topic/room/" + upper + "/player/" + action.getPlayerId(),
                    actorView);
        }
    }

    @MessageMapping("/room/{code}/sync")
    public void sync(@DestinationVariable String code, @Payload GameAction action,
                     SimpMessageHeaderAccessor headers) {
        String upper;
        try {
            upper = GameService.normalizeCode(code);
        } catch (Exception e) {
            return;
        }
        if (action.getPlayerId() != null) {
            presence.bind(headers.getSessionId(), upper, action.getPlayerId());
            gameService.markConnected(upper, action.getPlayerId());
            if (!gameService.roomExists(upper)) return;
            GameStateView view = gameService.getStateForPlayer(upper, action.getPlayerId());
            messaging.convertAndSend(
                    "/topic/room/" + upper + "/player/" + action.getPlayerId(),
                    view);
            broadcastPersonalized(upper);
        }
    }

    private void broadcastPersonalized(String code) {
        GameStateView probe = gameService.getStateForPlayer(code, null);
        if (probe.getPlayers() == null) return;

        for (PlayerView pv : probe.getPlayers()) {
            GameStateView personal = gameService.getStateForPlayer(code, pv.getId());
            messaging.convertAndSend(
                    "/topic/room/" + code + "/player/" + pv.getId(),
                    personal);
        }
        messaging.convertAndSend("/topic/room/" + code, probe);
    }
}
