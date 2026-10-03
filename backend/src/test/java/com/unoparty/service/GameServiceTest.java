package com.unoparty.service;

import com.unoparty.dto.GameAction;
import com.unoparty.dto.GameStateView;
import com.unoparty.model.Card;
import com.unoparty.model.CardColor;
import com.unoparty.model.CardType;
import com.unoparty.model.GameStatus;
import com.unoparty.model.Player;
import com.unoparty.model.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

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

    @Test
    void fourPlayersAdvanceClockwiseThenReverseThenSkip() {
        Room room = scriptedRoom("Alex", "Blair", "Casey", "Drew");
        List<Player> seats = new ArrayList<>(room.getPlayers());
        assertEquals(4, seats.size());
        Player a = seats.get(0);
        Player b = seats.get(1);
        Player c = seats.get(2);
        Player d = seats.get(3);

        // Deterministic red numbers so every play is legal and is NOT skip/reverse.
        Card a5 = give(a, CardType.NUMBER, 5);
        Card b2 = give(b, CardType.NUMBER, 2);
        Card c3 = give(c, CardType.NUMBER, 3);
        Card d4 = give(d, CardType.NUMBER, 4);
        Card rev = give(a, CardType.REVERSE, null);
        Card d6 = give(d, CardType.NUMBER, 6);
        Card skip = give(c, CardType.SKIP, null);
        Card a9 = give(a, CardType.NUMBER, 9);

        assertTurn(room, a);
        play(room, a, a5);
        assertTurn(room, b);
        play(room, b, b2);
        assertTurn(room, c);
        play(room, c, c3);
        assertTurn(room, d);
        play(room, d, d4);
        assertTurn(room, a); // full lap A -> B -> C -> D -> A

        play(room, a, rev);
        assertEquals(-1, room.getDirection());
        assertTurn(room, d); // reverse: next is previous seat

        play(room, d, d6);
        assertTurn(room, c);

        play(room, c, skip);
        // counter-clockwise skip: Blair is skipped, Alex plays
        assertTurn(room, a);
        assertTrue(room.getEventLog().stream().anyMatch(e -> e.contains("Blair") && e.toLowerCase().contains("skip")));

        play(room, a, a9);
        assertTurn(room, d);

        // Exactly one current flag, and it matches currentPlayerId for every viewer.
        for (Player viewer : seats) {
            GameStateView view = game.getStateForPlayer(room.getCode(), viewer.getId());
            assertEquals(d.getId(), view.getCurrentPlayerId());
            assertEquals("Drew", view.getCurrentPlayerName());
            assertEquals(viewer.getId().equals(d.getId()), view.isYourTurn());
            long currents = view.getPlayers().stream().filter(pv -> pv.isCurrent()).count();
            assertEquals(1, currents);
        }
    }

    @Test
    void drawAndPassAdvanceToNextConnectedPlayer() {
        Room room = scriptedRoom("Alex", "Blair", "Casey", "Drew");
        List<Player> seats = new ArrayList<>(room.getPlayers());
        Player a = seats.get(0);
        Player b = seats.get(1);
        // Unplayable draw (blue on red) ends the turn immediately.
        room.getDrawPile().add(new Card(CardColor.BLUE, CardType.NUMBER, 9));
        draw(room, a);
        assertTurn(room, b);

        // Playable draw then pass.
        room.setCurrentPlayerIndex(0);
        room.getDrawPile().add(new Card(CardColor.RED, CardType.NUMBER, 8));
        draw(room, a);
        GameStateView mid = game.getStateForPlayer(room.getCode(), a.getId());
        assertTrue(mid.isMustDrawOrPlay());
        assertEquals(a.getId(), mid.getCurrentPlayerId());
        pass(room, a);
        assertTurn(room, b);
    }

    @Test
    void offlinePlayerIsSkippedDeterministicallyAndReconnectDoesNotResetTurn() {
        Room room = scriptedRoom("Alex", "Blair", "Casey", "Drew");
        List<Player> seats = new ArrayList<>(room.getPlayers());
        Player a = seats.get(0);
        Player b = seats.get(1);
        Player c = seats.get(2);
        Card a5 = give(a, CardType.NUMBER, 5);

        int indexBefore = room.getCurrentPlayerIndex();
        game.markDisconnected(room.getCode(), a.getId());
        game.markConnected(room.getCode(), a.getId());
        assertEquals(indexBefore, room.getCurrentPlayerIndex());
        assertEquals(a.getId(), game.getStateForPlayer(room.getCode(), a.getId()).getCurrentPlayerId());

        game.markDisconnected(room.getCode(), b.getId());
        play(room, a, a5);
        assertTurn(room, c); // Blair offline, so Casey is next — not a random seat

        // Casey is current. Blair reconnecting must not steal or reset the turn.
        int caseyIndex = room.getCurrentPlayerIndex();
        game.markConnected(room.getCode(), b.getId());
        assertEquals(caseyIndex, room.getCurrentPlayerIndex());
        assertEquals(c.getId(), game.getStateForPlayer(room.getCode(), c.getId()).getCurrentPlayerId());
    }

    @Test
    void duplicateActionIdDoesNotAdvanceTurnTwice() {
        Room room = scriptedRoom("Alex", "Blair", "Casey", "Drew");
        Player a = room.getPlayers().get(0);
        Player b = room.getPlayers().get(1);
        Card a5 = give(a, CardType.NUMBER, 5);
        GameAction action = playAction(a, a5);
        action.setActionId("play-once");
        game.handleAction(room.getCode(), action);
        assertTurn(room, b);
        int index = room.getCurrentPlayerIndex();
        long version = room.getStateVersion();
        game.handleAction(room.getCode(), action); // same STOMP frame redelivered
        assertEquals(index, room.getCurrentPlayerIndex());
        assertEquals(b.getId(), game.getStateForPlayer(room.getCode(), b.getId()).getCurrentPlayerId());
        assertEquals(version, room.getStateVersion());
    }

    private Room scriptedRoom(String... names) {
        var host = game.createRoom(names[0]);
        for (int i = 1; i < names.length; i++) {
            game.joinRoom(host.roomCode(), names[i]);
        }
        Room room = game.roomForTest(host.roomCode());
        room.setStatus(GameStatus.PLAYING);
        room.setDirection(1);
        room.setCurrentPlayerIndex(0);
        room.setActiveColor(CardColor.RED);
        room.getDrawPile().clear();
        room.getDiscardPile().clear();
        room.getDiscardPile().add(new Card(CardColor.RED, CardType.NUMBER, 0));
        room.setMustDrawOrPlay(false);
        room.setLastDrawnCardId(null);
        for (Player p : room.getPlayers()) {
            p.getHand().clear();
            // Anchor card so a single scripted play cannot empty the hand and end the game.
            p.getHand().add(new Card(CardColor.GREEN, CardType.NUMBER, 7));
            p.markConnected();
        }
        return room;
    }

    private static Card give(Player player, CardType type, Integer number) {
        Card card = new Card(CardColor.RED, type, number);
        player.getHand().add(card);
        return card;
    }

    private void play(Room room, Player player, Card card) {
        game.handleAction(room.getCode(), playAction(player, card));
    }

    private static GameAction playAction(Player player, Card card) {
        GameAction action = new GameAction();
        action.setPlayerId(player.getId());
        action.setType("PLAY");
        action.setCardId(card.getId());
        return action;
    }

    private void draw(Room room, Player player) {
        GameAction action = new GameAction();
        action.setPlayerId(player.getId());
        action.setType("DRAW");
        game.handleAction(room.getCode(), action);
    }

    private void pass(Room room, Player player) {
        GameAction action = new GameAction();
        action.setPlayerId(player.getId());
        action.setType("PASS");
        game.handleAction(room.getCode(), action);
    }

    private void assertTurn(Room room, Player expected) {
        GameStateView view = game.getStateForPlayer(room.getCode(), expected.getId());
        assertEquals(expected.getId(), view.getCurrentPlayerId(),
                () -> "expected " + expected.getName() + " but events=" + view.getEventLog());
        assertEquals(expected.getName(), view.getCurrentPlayerName());
        assertTrue(view.isYourTurn());
        assertEquals(room.getPlayers().indexOf(expected), room.getCurrentPlayerIndex());
    }

}
