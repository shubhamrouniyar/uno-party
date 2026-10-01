package com.unoparty.service;

import com.unoparty.dto.GameAction;
import com.unoparty.dto.GameStateView;
import com.unoparty.model.GameStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class GameServiceTest {

    private GameService game;

    @BeforeEach
    void setUp() {
        game = new GameService();
    }

    @Test
    void normalizeCodeUppercasesAndRejectsBad() {
        assertEquals("ABCDEF", GameService.normalizeCode("abcdef"));
        assertEquals("AB12CD", GameService.normalizeCode(" ab12cd "));
        assertThrows(ResponseStatusException.class, () -> GameService.normalizeCode("AB"));
        assertThrows(ResponseStatusException.class, () -> GameService.normalizeCode(null));
    }

    @Test
    void joinRejectsDuplicateNamesAndStartedGames() {
        var host = game.createRoom("Alice");
        game.joinRoom(host.roomCode(), "Bob");
        assertThrows(ResponseStatusException.class, () -> game.joinRoom(host.roomCode(), "alice"));

        GameAction start = new GameAction();
        start.setPlayerId(host.playerId());
        start.setType("START");
        game.handleAction(host.roomCode(), start);

        assertThrows(ResponseStatusException.class, () -> game.joinRoom(host.roomCode(), "Carol"));
    }

    @Test
    void rejoinRestoresSeatAfterDisconnect() {
        var host = game.createRoom("Alice");
        var bob = game.joinRoom(host.roomCode(), "Bob");
        game.markDisconnected(host.roomCode(), bob.playerId());
        GameStateView before = game.getStateForPlayer(host.roomCode(), bob.playerId());
        assertFalse(before.getPlayers().stream().filter(p -> p.getId().equals(bob.playerId())).findFirst().orElseThrow().isConnected());

        var again = game.rejoinRoom(host.roomCode(), bob.playerId());
        assertEquals(bob.playerId(), again.playerId());
        assertTrue(again.gameState().getPlayers().stream()
                .filter(p -> p.getId().equals(bob.playerId())).findFirst().orElseThrow().isConnected());
    }

    @Test
    void midGameLeaveContinuesWithRemainingPlayers() {
        var host = game.createRoom("Alice");
        var bob = game.joinRoom(host.roomCode(), "Bob");
        var carol = game.joinRoom(host.roomCode(), "Carol");

        GameAction start = new GameAction();
        start.setPlayerId(host.playerId());
        start.setType("START");
        game.handleAction(host.roomCode(), start);

        GameAction leave = new GameAction();
        leave.setPlayerId(bob.playerId());
        leave.setType("LEAVE");
        game.handleAction(host.roomCode(), leave);

        GameStateView state = game.getStateForPlayer(host.roomCode(), host.playerId());
        assertEquals(2, state.getPlayers().size());
        assertEquals(GameStatus.PLAYING, state.getStatus());
        assertTrue(state.getPlayers().stream().noneMatch(p -> p.getId().equals(bob.playerId())));
        assertTrue(state.getPlayers().stream().anyMatch(p -> p.getId().equals(carol.playerId())));
    }

    @Test
    void leaveDownToOnePausesGame() {
        var host = game.createRoom("Alice");
        var bob = game.joinRoom(host.roomCode(), "Bob");

        GameAction start = new GameAction();
        start.setPlayerId(host.playerId());
        start.setType("START");
        game.handleAction(host.roomCode(), start);

        GameAction leave = new GameAction();
        leave.setPlayerId(bob.playerId());
        leave.setType("LEAVE");
        game.handleAction(host.roomCode(), leave);

        GameStateView state = game.getStateForPlayer(host.roomCode(), host.playerId());
        assertEquals(1, state.getPlayers().size());
        assertEquals(GameStatus.PAUSED, state.getStatus());
    }

    @Test
    void hostLeaveTransfersHost() {
        var host = game.createRoom("Alice");
        var bob = game.joinRoom(host.roomCode(), "Bob");

        GameAction leave = new GameAction();
        leave.setPlayerId(host.playerId());
        leave.setType("LEAVE");
        game.handleAction(host.roomCode(), leave);

        GameStateView state = game.getStateForPlayer(host.roomCode(), bob.playerId());
        assertEquals(1, state.getPlayers().size());
        assertTrue(state.getPlayers().get(0).isHost());
        assertEquals(bob.playerId(), state.getPlayers().get(0).getId());
    }

    @Test
    void purgeDoesNotRemoveWithinGrace() {
        var host = game.createRoom("Alice");
        var bob = game.joinRoom(host.roomCode(), "Bob");
        game.markDisconnected(host.roomCode(), bob.playerId());
        var changed = game.purgeTimedOutDisconnects();
        assertTrue(changed.isEmpty());
        assertEquals(2, game.getStateForPlayer(host.roomCode(), host.playerId()).getPlayers().size());
    }

    @Test
    void markDisconnectedIsIdempotentWhenAlreadyOffline() {
        var host = game.createRoom("Alice");
        assertTrue(game.markDisconnected(host.roomCode(), host.playerId()));
        assertFalse(game.markDisconnected(host.roomCode(), host.playerId()));
    }

}
