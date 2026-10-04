package com.unoparty.service;

import com.unoparty.dto.GameAction;
import com.unoparty.dto.GameStateView;
import com.unoparty.dto.PlayerView;
import com.unoparty.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GameService {

    private static final Logger log = LoggerFactory.getLogger(GameService.class);

    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 6;
    private static final int STARTING_HAND = 7;
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(45);
    /** Keep seat after WS drop (~3 min); then permanently remove and continue/pause. */
    private static final Duration DISCONNECT_GRACE = Duration.ofSeconds(180);
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public synchronized JoinResult createRoom(String displayName) {
        String code = generateUniqueCode();
        Room room = new Room(code);
        String playerId = UUID.randomUUID().toString();
        Player host = new Player(playerId, sanitizeName(displayName));
        host.setHost(true);
        host.markConnected();
        room.getPlayers().add(host);
        room.setCreatedBy(playerId);
        room.touch();
        rooms.put(code, room);
        room.addEvent(host.getName() + " created the room");
        room.bumpState();
        return new JoinResult(playerId, code, host.getName(), true, toView(room, playerId));
    }

    public synchronized JoinResult joinRoom(String rawCode, String displayName) {
        String code = normalizeCode(rawCode);
        Room room = requireRoom(code);
        if (room.getStatus() != GameStatus.LOBBY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Game already started — late join is only allowed in the lobby");
        }
        if (room.getPlayers().size() >= MAX_PLAYERS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Room is full (max " + MAX_PLAYERS + " players)");
        }
        String name = sanitizeName(displayName);
        boolean nameTaken = room.getPlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
        if (nameTaken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Display name already taken in this room — pick another");
        }
        String playerId = UUID.randomUUID().toString();
        Player player = new Player(playerId, name);
        player.markConnected();
        room.getPlayers().add(player);
        room.touch();
        room.addEvent(name + " joined the party");
        room.bumpState();
        return new JoinResult(playerId, room.getCode(), name, false, toView(room, playerId));
    }

    /**
     * Restore a seat after refresh / reconnect using the persisted playerId.
     */
    public synchronized JoinResult rejoinRoom(String rawCode, String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "playerId required");
        }
        String code = normalizeCode(rawCode);
        Room room = requireRoom(code);
        Player player = room.findPlayer(playerId);
        if (player == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Seat expired or unknown — join the lobby as a new player if the game has not started");
        }
        boolean wasOffline = !player.isConnected();
        player.markConnected();
        room.touch();
        if (wasOffline) {
            room.addEvent(player.getName() + " reconnected");
        }
        maybeResumeFromPause(room);
        room.bumpState();
        log.info("rejoin room={} playerId={} currentPlayerId={} index={} (turn not reset)",
                room.getCode(), playerId, currentId(room), room.getCurrentPlayerIndex());
        return new JoinResult(playerId, room.getCode(), player.getName(), player.isHost(),
                toView(room, playerId));
    }

    public synchronized GameStateView leaveRoomRest(String rawCode, String playerId) {
        String code = normalizeCode(rawCode);
        Room room = requireRoom(code);
        Player actor = room.findPlayer(playerId);
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "You are not in this room");
        }
        long versionBefore = room.getStateVersion();
        leaveRoom(room, actor);
        if (rooms.containsKey(code) && room.getStateVersion() == versionBefore) {
            room.bumpState();
        }
        if (!rooms.containsKey(code)) {
            GameStateView empty = new GameStateView();
            empty.setRoomCode(code);
            empty.setStatus(GameStatus.FINISHED);
            empty.setMessage("Room closed");
            empty.setPlayers(List.of());
            empty.setYourHand(List.of());
            empty.setEventLog(List.of());
            return empty;
        }
        return toView(room, playerId);
    }

    public synchronized GameStateView getStateForPlayer(String rawCode, String playerId) {
        Room room = requireRoom(normalizeCode(rawCode));
        return toView(room, playerId);
    }

    /**
     * All per-player views captured under one lock so every client sees the same
     * currentPlayerId and stateVersion (no torn turn broadcasts).
     */
    public synchronized List<AddressedState> addressedStates(String rawCode) {
        Room room = rooms.get(normalizeCode(rawCode));
        if (room == null) return List.of();
        List<AddressedState> out = new ArrayList<>();
        for (Player p : room.getPlayers()) {
            out.add(new AddressedState(p.getId(), toView(room, p.getId())));
        }
        return out;
    }

    /** Test-only access to the live room (same package). */
    Room roomForTest(String rawCode) {
        return rooms.get(normalizeCode(rawCode));
    }

    public synchronized GameStateView handleAction(String rawCode, GameAction action) {
        String code = normalizeCode(rawCode);
        Room room = requireRoom(code);
        room.touch();
        if (action.getPlayerId() == null || action.getType() == null) {
            return errorView(room, action.getPlayerId(), "Invalid action");
        }
        Player actor = room.findPlayer(action.getPlayerId());
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not in this room");
        }
        if (!room.consumeActionId(action.getActionId())) {
            log.info("duplicate action ignored room={} player={} type={} actionId={} currentPlayerId={}",
                    code, action.getPlayerId(), action.getType(), action.getActionId(), currentId(room));
            return toView(room, action.getPlayerId());
        }
        // Touch presence — any successful action implies connected
        actor.markConnected();

        try {
            switch (action.getType().toUpperCase(Locale.ROOT)) {
                case "START" -> startGame(room, actor);
                case "PLAY" -> playCard(room, actor, action.getCardId(), action.getChosenColor());
                case "DRAW" -> drawCard(room, actor);
                case "PASS" -> passAfterDraw(room, actor);
                case "CALL_UNO" -> callUno(room, actor);
                case "CHALLENGE_UNO" -> challengeUno(room, actor, action.getTargetPlayerId());
                case "REMATCH" -> rematch(room, actor);
                case "LEAVE" -> leaveRoom(room, actor);
                case "SYNC" -> { /* no-op; markConnected already done */ }
                default -> {
                    return errorView(room, action.getPlayerId(), "Unknown action: " + action.getType());
                }
            }
        } catch (GameException e) {
            room.bumpState();
            GameStateView view = toView(room, action.getPlayerId());
            view.setError(e.getMessage());
            return view;
        }

        if (!rooms.containsKey(code)) {
            GameStateView empty = new GameStateView();
            empty.setRoomCode(code);
            empty.setStatus(GameStatus.FINISHED);
            empty.setMessage("Left room");
            empty.setPlayers(List.of());
            empty.setYourHand(List.of());
            empty.setEventLog(List.of());
            return empty;
        }
        room.bumpState();
        log.info("state room={} version={} status={} currentPlayerId={} index={} dir={} players={}",
                code, room.getStateVersion(), room.getStatus(), currentId(room),
                room.getCurrentPlayerIndex(), room.getDirection(),
                room.getPlayers().stream().map(Player::getName).toList());
        return toView(room, action.getPlayerId());
    }

    /** @return true if state changed */
    public synchronized boolean markDisconnected(String rawCode, String playerId) {
        Room room = rooms.get(normalizeCode(rawCode));
        if (room == null) return false;
        Player p = room.findPlayer(playerId);
        if (p == null || !p.isConnected()) return false;
        p.markDisconnected(Instant.now());
        room.addEvent(p.getName() + " disconnected — seat held briefly");
        room.touch();
        room.bumpState();
        log.info("disconnect room={} playerId={} currentPlayerId={} index={} (turn unchanged)",
                room.getCode(), playerId, currentId(room), room.getCurrentPlayerIndex());
        return true;
    }

    public synchronized void markConnected(String rawCode, String playerId) {
        Room room = rooms.get(normalizeCode(rawCode));
        if (room == null) return;
        Player p = room.findPlayer(playerId);
        if (p != null) {
            boolean wasOffline = !p.isConnected();
            int indexBefore = room.getCurrentPlayerIndex();
            String turnBefore = currentId(room);
            p.markConnected();
            room.touch();
            if (wasOffline) {
                room.addEvent(p.getName() + " reconnected");
                maybeResumeFromPause(room);
                room.bumpState();
                log.info("reconnect room={} playerId={} turnBefore={} turnAfter={} index {} -> {} (not reset unless resume skipped offline)",
                        room.getCode(), playerId, turnBefore, currentId(room),
                        indexBefore, room.getCurrentPlayerIndex());
            }
        }
    }

    /**
     * Remove players whose disconnect grace elapsed. Returns room codes that changed.
     */
    public synchronized List<String> purgeTimedOutDisconnects() {
        Instant cutoff = Instant.now().minus(DISCONNECT_GRACE);
        Set<String> changed = new LinkedHashSet<>();
        for (Room room : List.copyOf(rooms.values())) {
            List<Player> timedOut = room.getPlayers().stream()
                    .filter(p -> !p.isConnected()
                            && p.getDisconnectedAt() != null
                            && p.getDisconnectedAt().isBefore(cutoff))
                    .toList();
            for (Player p : timedOut) {
                room.addEvent(p.getName() + " timed out — removing from room");
                leaveRoom(room, p);
                changed.add(room.getCode());
                if (!rooms.containsKey(room.getCode())) {
                    break;
                }
            }
        }
        return List.copyOf(changed);
    }

    public int purgeIdleRooms() {
        Instant cutoff = Instant.now().minus(IDLE_TIMEOUT);
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, Room> e : rooms.entrySet()) {
            if (e.getValue().getLastActivity().isBefore(cutoff)) {
                toRemove.add(e.getKey());
            }
        }
        toRemove.forEach(rooms::remove);
        return toRemove.size();
    }

    public Collection<Room> allRooms() {
        return rooms.values();
    }

    public boolean roomExists(String rawCode) {
        return rooms.containsKey(normalizeCode(rawCode));
    }

    // ---- game rules ----

    private void startGame(Room room, Player actor) {
        if (!actor.isHost()) {
            throw new GameException("Only the host can start the game");
        }
        if (room.getStatus() != GameStatus.LOBBY && room.getStatus() != GameStatus.FINISHED
                && room.getStatus() != GameStatus.PAUSED) {
            throw new GameException("Game already started");
        }
        long seated = room.getPlayers().size();
        if (seated < MIN_PLAYERS) {
            throw new GameException("Need at least " + MIN_PLAYERS + " players to start");
        }

        room.resetForRematch();
        room.setStatus(GameStatus.PLAYING);
        List<Card> deck = DeckFactory.createShuffledDeck();
        room.getDrawPile().addAll(deck);

        for (int i = 0; i < STARTING_HAND; i++) {
            for (Player p : room.getPlayers()) {
                p.getHand().add(drawFromPile(room));
            }
        }

        Card starter = drawFromPile(room);
        while (starter.getType() == CardType.WILD_DRAW_FOUR) {
            room.getDrawPile().add(0, starter);
            Collections.shuffle(room.getDrawPile(), ThreadLocalRandom.current());
            starter = drawFromPile(room);
        }
        room.getDiscardPile().add(starter);

        if (starter.isWild()) {
            room.setActiveColor(randomColor());
            room.addEvent("Top card is Wild — color set to " + room.getActiveColor());
        } else {
            room.setActiveColor(starter.getColor());
        }

        room.setCurrentPlayerIndex(0);
        room.setDirection(1);
        applyStarterEffects(room, starter);
        skipOfflineTurns(room);
        room.addEvent("Game started! " + safeCurrentName(room) + "'s turn");
    }

    private void applyStarterEffects(Room room, Card starter) {
        switch (starter.getType()) {
            case SKIP -> {
                room.addEvent("Starting Skip — " + room.getCurrentPlayer().getName() + " is skipped");
                advanceTurn(room);
            }
            case REVERSE -> {
                if (room.getPlayers().size() == 2) {
                    room.addEvent("Starting Reverse acts as Skip (2 players)");
                    advanceTurn(room);
                } else {
                    room.setDirection(-1);
                    room.addEvent("Starting Reverse — direction flipped");
                }
            }
            case DRAW_TWO -> {
                Player current = room.getCurrentPlayer();
                drawCards(room, current, 2);
                room.addEvent(current.getName() + " draws 2 from starting Draw Two");
                advanceTurn(room);
            }
            default -> { /* numbers and wild: no extra effect */ }
        }
    }

    private void playCard(Room room, Player actor, String cardId, CardColor chosenColor) {
        requirePlaying(room);
        requireTurn(room, actor);

        if (room.isMustDrawOrPlay() && cardId != null
                && !cardId.equals(room.getLastDrawnCardId())) {
            throw new GameException("After drawing, you may only play the drawn card or pass");
        }

        Card card = actor.getHand().stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElseThrow(() -> new GameException("Card not in your hand"));

        Card top = room.getTopDiscard();
        if (!card.matches(top, room.getActiveColor())) {
            throw new GameException("Card does not match the top card / active color");
        }

        if (card.isWild()) {
            if (chosenColor == null || chosenColor == CardColor.WILD) {
                throw new GameException("Choose a color for the wild card");
            }
        }

        actor.getHand().remove(card);
        room.getDiscardPile().add(card);
        room.setMustDrawOrPlay(false);
        room.setLastDrawnCardId(null);

        if (card.isWild()) {
            room.setActiveColor(chosenColor);
        } else {
            room.setActiveColor(card.getColor());
        }

        if (actor.getHandSize() == 1) {
            room.addEvent(actor.getName() + " played " + describe(card) + " — 1 card left!");
        } else if (actor.getHandSize() == 0) {
            room.setStatus(GameStatus.FINISHED);
            room.setWinnerId(actor.getId());
            room.setWinnerName(actor.getName());
            room.addEvent("🎉 " + actor.getName() + " wins!");
            return;
        } else {
            actor.setCalledUno(false);
            room.addEvent(actor.getName() + " played " + describe(card)
                    + (card.isWild() ? " → " + chosenColor : ""));
        }

        applyCardEffect(room, card);
        skipOfflineTurns(room);
    }

    private void applyCardEffect(Room room, Card card) {
        switch (card.getType()) {
            case SKIP -> {
                advanceTurn(room);
                Player skipped = room.getCurrentPlayer();
                if (skipped != null) {
                    room.addEvent(skipped.getName() + " was skipped");
                }
                advanceTurn(room);
            }
            case REVERSE -> {
                if (room.getPlayers().size() == 2) {
                    advanceTurn(room);
                    Player skipped = room.getCurrentPlayer();
                    if (skipped != null) {
                        room.addEvent("Reverse (2p) — " + skipped.getName() + " skipped");
                    }
                    advanceTurn(room);
                } else {
                    room.setDirection(room.getDirection() * -1);
                    room.addEvent("Direction reversed");
                    advanceTurn(room);
                }
            }
            case DRAW_TWO -> {
                advanceTurn(room);
                Player victim = room.getCurrentPlayer();
                if (victim != null) {
                    drawCards(room, victim, 2);
                    victim.setCalledUno(false);
                    room.addEvent(victim.getName() + " draws 2 and is skipped");
                }
                advanceTurn(room);
            }
            case WILD_DRAW_FOUR -> {
                advanceTurn(room);
                Player victim = room.getCurrentPlayer();
                if (victim != null) {
                    drawCards(room, victim, 4);
                    victim.setCalledUno(false);
                    room.addEvent(victim.getName() + " draws 4 and is skipped");
                }
                advanceTurn(room);
            }
            case WILD, NUMBER -> advanceTurn(room);
        }
    }

    private void drawCard(Room room, Player actor) {
        requirePlaying(room);
        requireTurn(room, actor);
        if (room.isMustDrawOrPlay()) {
            throw new GameException("You already drew — play the card or pass");
        }

        Card drawn = drawFromPile(room);
        actor.getHand().add(drawn);
        actor.setCalledUno(false);

        Card top = room.getTopDiscard();
        if (drawn.matches(top, room.getActiveColor())) {
            room.setMustDrawOrPlay(true);
            room.setLastDrawnCardId(drawn.getId());
            room.addEvent(actor.getName() + " drew a card (can play it or pass)");
        } else {
            room.addEvent(actor.getName() + " drew a card");
            room.setMustDrawOrPlay(false);
            room.setLastDrawnCardId(null);
            advanceTurn(room);
            skipOfflineTurns(room);
        }
    }

    private void passAfterDraw(Room room, Player actor) {
        requirePlaying(room);
        requireTurn(room, actor);
        if (!room.isMustDrawOrPlay()) {
            throw new GameException("Nothing to pass — draw or play a card");
        }
        room.setMustDrawOrPlay(false);
        room.setLastDrawnCardId(null);
        room.addEvent(actor.getName() + " passed");
        advanceTurn(room);
        skipOfflineTurns(room);
    }

    private void callUno(Room room, Player actor) {
        requirePlaying(room);
        if (actor.getHandSize() != 1 && actor.getHandSize() != 2) {
            throw new GameException("You can only call UNO with 1 or 2 cards");
        }
        actor.setCalledUno(true);
        room.addEvent(actor.getName() + " yelled UNO!");
    }

    private void challengeUno(Room room, Player actor, String targetPlayerId) {
        requirePlaying(room);
        if (targetPlayerId == null) {
            throw new GameException("Pick a player to challenge");
        }
        Player target = room.findPlayer(targetPlayerId);
        if (target == null) {
            throw new GameException("Player not found");
        }
        if (target.getId().equals(actor.getId())) {
            throw new GameException("Cannot challenge yourself");
        }
        if (target.getHandSize() != 1) {
            throw new GameException(target.getName() + " does not have exactly 1 card");
        }
        if (target.isCalledUno()) {
            throw new GameException(target.getName() + " already called UNO");
        }
        drawCards(room, target, 2);
        target.setCalledUno(false);
        room.addEvent(actor.getName() + " caught " + target.getName()
                + " without UNO! +2 cards");
    }

    private void rematch(Room room, Player actor) {
        if (!actor.isHost()) {
            throw new GameException("Only the host can start a rematch");
        }
        if (room.getStatus() != GameStatus.FINISHED && room.getStatus() != GameStatus.PLAYING
                && room.getStatus() != GameStatus.PAUSED) {
            throw new GameException("Cannot rematch right now");
        }
        if (room.getPlayers().size() < MIN_PLAYERS) {
            throw new GameException("Need at least " + MIN_PLAYERS + " players");
        }
        startGame(room, actor);
    }

    private void leaveRoom(Room room, Player actor) {
        int leaveIdx = room.getPlayers().indexOf(actor);
        if (leaveIdx < 0) return;

        boolean playing = room.getStatus() == GameStatus.PLAYING || room.getStatus() == GameStatus.PAUSED;
        boolean wasCurrent = playing && leaveIdx == room.getCurrentPlayerIndex();
        int oldIndex = room.getCurrentPlayerIndex();
        int direction = room.getDirection();

        // Return cards to draw pile so the deck stays playable
        if (!actor.getHand().isEmpty()) {
            room.getDrawPile().addAll(actor.getHand());
            actor.getHand().clear();
            Collections.shuffle(room.getDrawPile(), ThreadLocalRandom.current());
        }

        room.getPlayers().remove(leaveIdx);
        room.addEvent(actor.getName() + " left");

        if (room.getPlayers().isEmpty()) {
            rooms.remove(room.getCode());
            return;
        }

        if (actor.isHost()) {
            actor.setHost(false);
            Player newHost = room.getPlayers().get(0);
            newHost.setHost(true);
            room.setCreatedBy(newHost.getId());
            room.addEvent(newHost.getName() + " is now the host");
        }

        if (room.getStatus() == GameStatus.LOBBY) {
            room.bumpState();
            return;
        }

        if (room.getStatus() == GameStatus.FINISHED) {
            // Keep finished; just fix index bounds
            if (room.getCurrentPlayerIndex() >= room.getPlayers().size()) {
                room.setCurrentPlayerIndex(Math.max(0, room.getPlayers().size() - 1));
            }
            room.bumpState();
            return;
        }

        // PLAYING or PAUSED
        if (room.getPlayers().size() < MIN_PLAYERS) {
            room.setStatus(GameStatus.PAUSED);
            room.setMustDrawOrPlay(false);
            room.setLastDrawnCardId(null);
            if (room.getCurrentPlayerIndex() >= room.getPlayers().size()) {
                room.setCurrentPlayerIndex(0);
            }
            room.addEvent("Paused — need at least " + MIN_PLAYERS + " players. Reconnect or start a new room.");
            room.bumpState();
            return;
        }

        room.setStatus(GameStatus.PLAYING);
        fixTurnAfterLeave(room, leaveIdx, oldIndex, wasCurrent, direction);
        skipOfflineTurns(room);
        room.addEvent("Now " + safeCurrentName(room) + "'s turn");
        log.info("leave room={} removed={} currentPlayerId={} index={} dir={}",
                room.getCode(), actor.getId(), currentId(room),
                room.getCurrentPlayerIndex(), room.getDirection());
    }

    private void fixTurnAfterLeave(Room room, int leaveIdx, int oldIndex,
                                   boolean wasCurrent, int direction) {
        int n = room.getPlayers().size();
        if (n == 0) return;

        if (wasCurrent) {
            // After removal, the next player in direction lands at:
            // +1 → index leaveIdx (old leaveIdx+1 shifted down)
            // -1 → index leaveIdx-1
            int next;
            if (direction >= 0) {
                next = leaveIdx % n;
            } else {
                next = leaveIdx - 1;
                if (next < 0) next = n - 1;
            }
            room.setCurrentPlayerIndex(next);
            room.setMustDrawOrPlay(false);
            room.setLastDrawnCardId(null);
        } else if (leaveIdx < oldIndex) {
            room.setCurrentPlayerIndex(oldIndex - 1);
        } else if (room.getCurrentPlayerIndex() >= n) {
            room.setCurrentPlayerIndex(0);
        }
    }

    private void maybeResumeFromPause(Room room) {
        if (room.getStatus() == GameStatus.PAUSED && room.getPlayers().size() >= MIN_PLAYERS) {
            room.setStatus(GameStatus.PLAYING);
            skipOfflineTurns(room);
            room.addEvent("Resumed! " + safeCurrentName(room) + "'s turn");
        }
    }

    /** If the seated current player is offline, move to the next eligible connected player. */
    private void skipOfflineTurns(Room room) {
        if (room.getStatus() != GameStatus.PLAYING) return;
        Player cur = room.getCurrentPlayer();
        if (cur == null || cur.isConnected()) return;
        room.addEvent("Skipping offline player " + cur.getName());
        log.info("skip offline current room={} playerId={} index={}",
                room.getCode(), cur.getId(), room.getCurrentPlayerIndex());
        advanceTurn(room);
    }

    // ---- helpers ----

    /**
     * Move exactly one step in the current direction, then continue only over
     * disconnected seats. Connected players are never jumped. Order is a pure
     * function of seat index + direction (no randomness).
     */
    private void advanceTurn(Room room) {
        int n = room.getPlayers().size();
        if (n == 0) return;
        int dir = room.getDirection() < 0 ? -1 : 1;
        if (room.getDirection() != dir) {
            room.setDirection(dir);
        }
        int from = Math.floorMod(room.getCurrentPlayerIndex(), n);
        int idx = from;
        String fromId = room.getPlayers().get(from).getId();
        for (int step = 0; step < n; step++) {
            idx = Math.floorMod(idx + dir, n);
            Player candidate = room.getPlayers().get(idx);
            if (candidate.isConnected() || step == n - 1) {
                room.setCurrentPlayerIndex(idx);
                room.setMustDrawOrPlay(false);
                room.setLastDrawnCardId(null);
                log.info("TURN room={} fromIndex={} fromId={} toIndex={} toId={} toName={} dir={} step={} connected={}",
                        room.getCode(), from, fromId, idx, candidate.getId(), candidate.getName(),
                        dir, step + 1, candidate.isConnected());
                return;
            }
            room.addEvent("Skipping offline player " + candidate.getName());
            log.info("TURN skip-offline room={} index={} playerId={} name={}",
                    room.getCode(), idx, candidate.getId(), candidate.getName());
        }
    }

    private String currentId(Room room) {
        Player p = room.getCurrentPlayer();
        return p == null ? null : p.getId();
    }

    private Card drawFromPile(Room room) {
        if (room.getDrawPile().isEmpty()) {
            reshuffleDiscard(room);
        }
        if (room.getDrawPile().isEmpty()) {
            throw new GameException("No cards left to draw");
        }
        return room.getDrawPile().remove(room.getDrawPile().size() - 1);
    }

    private void drawCards(Room room, Player player, int count) {
        for (int i = 0; i < count; i++) {
            player.getHand().add(drawFromPile(room));
        }
    }

    private void reshuffleDiscard(Room room) {
        if (room.getDiscardPile().size() <= 1) return;
        Card top = room.getDiscardPile().remove(room.getDiscardPile().size() - 1);
        List<Card> rest = new ArrayList<>(room.getDiscardPile());
        room.getDiscardPile().clear();
        room.getDiscardPile().add(top);
        Collections.shuffle(rest, ThreadLocalRandom.current());
        room.getDrawPile().addAll(rest);
        room.addEvent("Discard pile reshuffled into draw pile");
    }

    private void requirePlaying(Room room) {
        if (room.getStatus() == GameStatus.PAUSED) {
            throw new GameException("Game paused — waiting for players to reconnect");
        }
        if (room.getStatus() != GameStatus.PLAYING) {
            throw new GameException("Game is not in progress");
        }
    }

    private void requireTurn(Room room, Player actor) {
        Player current = room.getCurrentPlayer();
        if (current == null || !current.getId().equals(actor.getId())) {
            throw new GameException("It's not your turn");
        }
    }

    private Room requireRoom(String code) {
        Room room = rooms.get(code);
        if (room == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Room not found — check the code or it may have expired");
        }
        return room;
    }

    public static String normalizeCode(String raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Room code required");
        }
        String code = raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        if (code.length() != 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Room code must be 6 characters");
        }
        return code;
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 50; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int i = 0; i < 6; i++) {
                sb.append(CODE_CHARS.charAt(rng.nextInt(CODE_CHARS.length())));
            }
            String code = sb.toString();
            if (!rooms.containsKey(code)) return code;
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate room code");
    }

    private String sanitizeName(String name) {
        String cleaned = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (cleaned.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Display name required");
        }
        if (cleaned.length() > 20) {
            cleaned = cleaned.substring(0, 20);
        }
        return cleaned;
    }

    private CardColor randomColor() {
        CardColor[] colors = {CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE};
        return colors[ThreadLocalRandom.current().nextInt(colors.length)];
    }

    private String describe(Card card) {
        if (card.getType() == CardType.NUMBER) {
            return card.getColor() + " " + card.getNumber();
        }
        if (card.isWild()) {
            return card.getType().name().replace('_', ' ');
        }
        return card.getColor() + " " + card.getType().name().replace('_', ' ');
    }

    private String safeCurrentName(Room room) {
        Player p = room.getCurrentPlayer();
        return p != null ? p.getName() : "someone";
    }

    public GameStateView toView(Room room, String viewerId) {
        GameStateView view = new GameStateView();
        view.setRoomCode(room.getCode());
        view.setStatus(room.getStatus());
        view.setTopCard(room.getTopDiscard());
        view.setActiveColor(room.getActiveColor());
        view.setDirection(room.getDirection());
        view.setDrawPileCount(room.getDrawPile().size());
        view.setWinnerId(room.getWinnerId());
        view.setWinnerName(room.getWinnerName());
        view.setEventLog(new ArrayList<>(room.getEventLog()));
        view.setYourPlayerId(viewerId);
        view.setMustDrawOrPlay(room.isMustDrawOrPlay()
                && room.getCurrentPlayer() != null
                && room.getCurrentPlayer().getId().equals(viewerId));
        view.setLastDrawnCardId(
                view.isMustDrawOrPlay() ? room.getLastDrawnCardId() : null);

        if (room.getStatus() == GameStatus.PAUSED) {
            view.setMessage("Game paused — waiting for players to reconnect (need "
                    + MIN_PLAYERS + "+)");
        }

        Player current = room.getCurrentPlayer();
        boolean showTurn = current != null && (room.getStatus() == GameStatus.PLAYING
                || room.getStatus() == GameStatus.PAUSED);
        String currentId = showTurn ? current.getId() : null;
        view.setCurrentPlayerId(currentId);
        view.setCurrentPlayerName(showTurn ? current.getName() : null);
        view.setStateVersion(room.getStateVersion());

        List<PlayerView> players = new ArrayList<>();
        for (Player p : room.getPlayers()) {
            boolean isCurrent = currentId != null && currentId.equals(p.getId());
            players.add(new PlayerView(
                    p.getId(), p.getName(), p.getHandSize(), p.isConnected(),
                    p.isCalledUno(), p.isHost(), isCurrent));
        }
        view.setPlayers(players);

        Player you = viewerId == null ? null : room.findPlayer(viewerId);
        if (you != null) {
            view.setYourHand(new ArrayList<>(you.getHand()));
            view.setYourTurn(currentId != null && currentId.equals(viewerId)
                    && room.getStatus() == GameStatus.PLAYING);
        } else {
            view.setYourHand(List.of());
            view.setYourTurn(false);
        }
        return view;
    }

    private GameStateView errorView(Room room, String playerId, String error) {
        GameStateView view = toView(room, playerId);
        view.setError(error);
        return view;
    }

    public record JoinResult(String playerId, String roomCode, String displayName,
                             boolean host, GameStateView gameState) {}

    public record AddressedState(String playerId, GameStateView view) {}

    private static class GameException extends RuntimeException {
        GameException(String message) {
            super(message);
        }
    }
}
