package com.unoparty.presence;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps STOMP session IDs to (roomCode, playerId) so disconnect events can mark seats offline.
 */
@Component
public class PresenceService {

    public record Seat(String roomCode, String playerId) {}

    private final ConcurrentHashMap<String, Seat> bySession = new ConcurrentHashMap<>();

    public void bind(String stompSessionId, String roomCode, String playerId) {
        if (stompSessionId == null || roomCode == null || playerId == null) return;
        bySession.put(stompSessionId, new Seat(roomCode.toUpperCase(), playerId));
    }

    public Optional<Seat> unbind(String stompSessionId) {
        if (stompSessionId == null) return Optional.empty();
        return Optional.ofNullable(bySession.remove(stompSessionId));
    }

    public Optional<Seat> find(String stompSessionId) {
        if (stompSessionId == null) return Optional.empty();
        return Optional.ofNullable(bySession.get(stompSessionId));
    }

    /** Drop any session bindings for a player (e.g. after permanent leave). */
    public void clearPlayer(String roomCode, String playerId) {
        String code = roomCode == null ? null : roomCode.toUpperCase();
        bySession.entrySet().removeIf(e ->
                e.getValue().playerId().equals(playerId)
                        && (code == null || e.getValue().roomCode().equals(code)));
    }

    public Map<String, Seat> snapshot() {
        return Map.copyOf(bySession);
    }
}
