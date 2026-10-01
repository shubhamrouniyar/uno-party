package com.unoparty.presence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PresenceServiceTest {

    @Test
    void reconnectKeepsActiveSessionAfterOldUnbind() {
        PresenceService presence = new PresenceService();
        presence.bind("old-session", "ABC123", "player-1");
        presence.bind("new-session", "ABC123", "player-1");

        assertTrue(presence.hasActiveSession("ABC123", "player-1"));

        var seat = presence.unbind("old-session");
        assertTrue(seat.isPresent());
        // Critical: after old WS disconnect, player still has new session — no false kick
        assertTrue(presence.hasActiveSession("abc123", "player-1"));

        presence.unbind("new-session");
        assertFalse(presence.hasActiveSession("ABC123", "player-1"));
    }

    @Test
    void clearPlayerRemovesAllBindings() {
        PresenceService presence = new PresenceService();
        presence.bind("s1", "ZZZZZZ", "p1");
        presence.bind("s2", "ZZZZZZ", "p1");
        presence.clearPlayer("zzzzzz", "p1");
        assertFalse(presence.hasActiveSession("ZZZZZZ", "p1"));
    }
}
