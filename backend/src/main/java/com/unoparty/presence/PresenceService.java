package com.unoparty.presence;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps STOMP session IDs to (roomCode, playerId) so disconnect events can mark seats offline.
 * A player may briefly have two sessions during reconnect — ignore disconnect of the old one.
 */
@Component
public class PresenceService {

    public record Seat(String roomCode, String playerId) {}

    private final ConcurrentHashMap<String, Seat> bySession = new ConcurrentHashMap<>();

    public void bind(String stompSessionId, String roomCode, String playerId) {
        if (stompSessionId == null || roomCode == null || playerId == null) return;
        bySession.put(stompSessionId, new Seat(normalize(roomCode), playerId));
    }

    public Optional<Seat> unbind(String stompSessionId) {
        if (stompSessionId == null) return Optional.empty();
        return Optional.ofNullable(bySession.remove(stompSessionId));
    }

    public Optional<Seat> find(String stompSessionId) {
        if (stompSessionId == null) return Optional.empty();
        return Optional.ofNullable(bySession.get(stompSessionId));
    }

    /** True if any STOMP session is still bound to this seat (e.g. after reconnect). */
    public boolean hasActiveSession(String roomCode, String playerId) {
        if (playerId == null) return false;
        String code = roomCode == null ? null : normalize(roomCode);
        for (Seat seat : bySession.values()) {
            if (seat.playerId().equals(playerId)
                    && (code == null || seat.roomCode().equals(code))) {
                return true;
            }
        }
        return false;
    }

    /** Drop any session bindings for a player (e.g. after permanent leave). */
    public void clearPlayer(String roomCode, String playerId) {
        String code = roomCode == null ? null : normalize(roomCode);
        bySession.entrySet().removeIf(e ->
                e.getValue().playerId().equals(playerId)
                        && (code == null || e.getValue().roomCode().equals(code)));
    }

    public Map<String, Seat> snapshot() {
        return Map.copyOf(bySession);
    }

    private static String normalize(String roomCode) {
        return roomCode.trim().toUpperCase(Locale.ROOT);
    }
}
