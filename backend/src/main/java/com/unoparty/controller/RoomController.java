package com.unoparty.controller;

import com.unoparty.dto.CreateRoomRequest;
import com.unoparty.dto.GameStateView;
import com.unoparty.dto.JoinResponse;
import com.unoparty.dto.JoinRoomRequest;
import com.unoparty.dto.PlayerView;
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

    public RoomController(GameService gameService, SimpMessagingTemplate messaging) {
        this.gameService = gameService;
        this.messaging = messaging;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok", "service", "uno-party");
    }

    @PostMapping("/rooms")
    public JoinResponse createRoom(@Valid @RequestBody CreateRoomRequest request) {
        GameService.JoinResult result = gameService.createRoom(request.getDisplayName());
        return new JoinResponse(result.playerId(), result.roomCode(), result.displayName(),
                result.host(), result.gameState());
    }

    @PostMapping("/rooms/{code}/join")
    public JoinResponse joinRoom(@PathVariable String code,
                                 @Valid @RequestBody JoinRoomRequest request) {
        GameService.JoinResult result = gameService.joinRoom(code, request.getDisplayName());
        broadcastPersonalized(result.roomCode());
        return new JoinResponse(result.playerId(), result.roomCode(), result.displayName(),
                result.host(), result.gameState());
    }

    @GetMapping("/rooms/{code}")
    public ResponseEntity<GameStateView> getRoom(
            @PathVariable String code,
            @RequestParam(required = false) String playerId) {
        return ResponseEntity.ok(gameService.getStateForPlayer(code, playerId));
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
