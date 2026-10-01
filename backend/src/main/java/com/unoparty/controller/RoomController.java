package com.unoparty.controller;

import com.unoparty.dto.*;
import com.unoparty.presence.PresenceService;
import com.unoparty.service.GameService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class RoomController {

    private final GameService gameService;
    private final SimpMessagingTemplate messaging;
    private final PresenceService presence;

    public RoomController(GameService gameService, SimpMessagingTemplate messaging,
                          PresenceService presence) {
        this.gameService = gameService;
        this.messaging = messaging;
        this.presence = presence;
    }

    private static final long STARTED_AT_MS = System.currentTimeMillis();

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "uno-party",
                "uptimeMs", System.currentTimeMillis() - STARTED_AT_MS,
                "rooms", gameService.allRooms().size()
        );
    }

    /** Explicit wake/ping used by SPA before create/join (Railway cold starts). */
    @GetMapping({"/health/warm", "/warm"})
    public Map<String, Object> warm() {
        return health();
    }

    @PostMapping("/rooms")
    public JoinResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        GameService.JoinResult result = gameService.createRoom(request.getDisplayName());
        return toResponse(result);
    }

    @PostMapping("/rooms/{code}/join")
    public JoinResponse joinRoom(@PathVariable String code,
                                 @Valid @RequestBody JoinRoomRequest request) {
        GameService.JoinResult result = gameService.joinRoom(code, request.getDisplayName());
        broadcastPersonalized(result.roomCode());
        return toResponse(result);
    }

    @PostMapping("/rooms/{code}/rejoin")
    public JoinResponse rejoinRoom(@PathVariable String code,
                                   @Valid @RequestBody RejoinRoomRequest request) {
        GameService.JoinResult result = gameService.rejoinRoom(code, request.getPlayerId());
        broadcastPersonalized(result.roomCode());
        return toResponse(result);
    }

    @PostMapping("/rooms/{code}/leave")
    public ResponseEntity<GameStateView> leaveRoom(@PathVariable String code,
                                                   @Valid @RequestBody LeaveRoomRequest request) {
        String normalized = GameService.normalizeCode(code);
        GameStateView view = gameService.leaveRoomRest(code, request.getPlayerId());
        presence.clearPlayer(normalized, request.getPlayerId());
        if (gameService.roomExists(normalized)) {
            broadcastPersonalized(normalized);
        }
        return ResponseEntity.ok(view);
    }

    @GetMapping("/rooms/{code}")
    public ResponseEntity<GameStateView> getRoom(
            @PathVariable String code,
            @RequestParam(required = false) String playerId) {
        return ResponseEntity.ok(gameService.getStateForPlayer(code, playerId));
    }

    private JoinResponse toResponse(GameService.JoinResult result) {
        return new JoinResponse(result.playerId(), result.roomCode(), result.displayName(),
                result.host(), result.gameState());
    }

    private void broadcastPersonalized(String code) {
        GameStateView probe = gameService.getStateForPlayer(code, null);
        if (probe.getPlayers() == null) return;
        for (PlayerView pv : probe.getPlayers()) {
            GameStateView personal = gameService.getStateForPlayer(code, pv.getId());
            messaging.convertAndSend("/topic/room/" + code + "/player/" + pv.getId(), personal);
        }
        messaging.convertAndSend("/topic/room/" + code, probe);
    }
}
