package com.unoparty.service;

import com.unoparty.dto.GameAction;
import com.unoparty.dto.GameStateView;
import com.unoparty.dto.PlayerView;
import com.unoparty.model.*;
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

    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 6;
    private static final int STARTING_HAND = 7;
    private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(45);
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();

    public synchronized JoinResult createRoom(String displayName) {
        String code = generateUniqueCode();
        Room room = new Room(code);
        String playerId = UUID.randomUUID().toString();
        Player host = new Player(playerId, sanitizeName(displayName));
        host.setHost(true);
        room.getPlayers().add(host);
        room.setCreatedBy(playerId);
        room.touch();
        rooms.put(code, room);
        room.addEvent(host.getName() + " created the room");
        return new JoinResult(playerId, code, host.getName(), true, toView(room, playerId));
    }

    public synchronized JoinResult joinRoom(String code, String displayName) {
        Room room = requireRoom(code.toUpperCase());
        if (room.getStatus() != GameStatus.LOBBY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Game already in progress");
        }
        if (room.getPlayers().size() >= MAX_PLAYERS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is full (max " + MAX_PLAYERS + ")");
        }
        String name = sanitizeName(displayName);
        boolean nameTaken = room.getPlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(name));
        if (nameTaken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Display name already taken in this room");
        }
        String playerId = UUID.randomUUID().toString();
        Player player = new Player(playerId, name);
        room.getPlayers().add(player);
        room.touch();
        room.addEvent(name + " joined the party");
        return new JoinResult(playerId, room.getCode(), name, false, toView(room, playerId));
    }

    public GameStateView getStateForPlayer(String code, String playerId) {
        Room room = requireRoom(code.toUpperCase());
        return toView(room, playerId);
    }

    public synchronized GameStateView handleAction(String code, GameAction action) {
        Room room = requireRoom(code.toUpperCase());
        room.touch();
        if (action.getPlayerId() == null || action.getType() == null) {
            return errorView(room, action.getPlayerId(), "Invalid action");
        }
        Player actor = room.findPlayer(action.getPlayerId());
        if (actor == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not in this room");
        }

        try {
            switch (action.getType().toUpperCase()) {
                case "START" -> startGame(room, actor);
                case "PLAY" -> playCard(room, actor, action.getCardId(), action.getChosenColor());
                case "DRAW" -> drawCard(room, actor);
                case "PASS" -> passAfterDraw(room, actor);
                case "CALL_UNO" -> callUno(room, actor);
                case "CHALLENGE_UNO" -> challengeUno(room, actor, action.getTargetPlayerId());
                case "REMATCH" -> rematch(room, actor);
                case "LEAVE" -> leaveRoom(room, actor);
                default -> {
                    return errorView(room, action.getPlayerId(), "Unknown action: " + action.getType());
                }
            }
        } catch (GameException e) {
            GameStateView view = toView(room, action.getPlayerId());
            view.setError(e.getMessage());
            return view;
        }

        return toView(room, action.getPlayerId());
    }

    public synchronized void markDisconnected(String code, String playerId) {
        Room room = rooms.get(code.toUpperCase());
        if (room == null) return;
        Player p = room.findPlayer(playerId);
        if (p != null) {
            p.setConnected(false);
            room.addEvent(p.getName() + " disconnected");
            room.touch();
        }
    }

    public synchronized void markConnected(String code, String playerId) {
        Room room = rooms.get(code.toUpperCase());
        if (room == null) return;
        Player p = room.findPlayer(playerId);
        if (p != null) {
            p.setConnected(true);
            room.touch();
        }
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

    // ---- game rules ----

    private void startGame(Room room, Player actor) {
        if (!actor.isHost()) {
            throw new GameException("Only the host can start the game");
        }
        if (room.getStatus() != GameStatus.LOBBY && room.getStatus() != GameStatus.FINISHED) {
            throw new GameException("Game already started");
        }
        if (room.getPlayers().size() < MIN_PLAYERS) {
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

        // Apply starter action effects for Skip/Reverse/Draw Two
        applyStarterEffects(room, starter);

        room.addEvent("Game started! " + room.getCurrentPlayer().getName() + "'s turn");
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

        // UNO tracking: if hand size becomes 1, they should call UNO
        if (actor.getHandSize() == 1) {
            // calledUno must be true — if they already called before playing, keep it
            // If they didn't call yet, leave calledUno false so others can challenge
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
    }

    private void applyCardEffect(Room room, Card card) {
        switch (card.getType()) {
            case SKIP -> {
                advanceTurn(room);
                Player skipped = room.getCurrentPlayer();
                room.addEvent(skipped.getName() + " was skipped");
                advanceTurn(room);
            }
            case REVERSE -> {
                if (room.getPlayers().size() == 2) {
                    // Reverse with 2 players = skip opponent
                    advanceTurn(room);
                    Player skipped = room.getCurrentPlayer();
                    room.addEvent("Reverse (2p) — " + skipped.getName() + " skipped");
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
                drawCards(room, victim, 2);
                victim.setCalledUno(false);
                room.addEvent(victim.getName() + " draws 2 and is skipped");
                advanceTurn(room);
            }
            case WILD_DRAW_FOUR -> {
                advanceTurn(room);
                Player victim = room.getCurrentPlayer();
                drawCards(room, victim, 4);
                victim.setCalledUno(false);
                room.addEvent(victim.getName() + " draws 4 and is skipped");
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

        // If player has any playable card, still allow draw (house rule: optional draw)
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
    }

    private void callUno(Room room, Player actor) {
        requirePlaying(room);
        if (actor.getHandSize() != 1 && actor.getHandSize() != 2) {
            // Allow calling UNO when about to play down to 1, or when at 1
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
        if (room.getStatus() != GameStatus.FINISHED && room.getStatus() != GameStatus.PLAYING) {
            throw new GameException("Cannot rematch right now");
        }
        if (room.getPlayers().size() < MIN_PLAYERS) {
            throw new GameException("Need at least " + MIN_PLAYERS + " players");
        }
        startGame(room, actor);
    }

    private void leaveRoom(Room room, Player actor) {
        room.getPlayers().remove(actor);
        room.addEvent(actor.getName() + " left");
        if (room.getPlayers().isEmpty()) {
            rooms.remove(room.getCode());
            return;
        }
        if (actor.isHost()) {
            Player newHost = room.getPlayers().get(0);
            newHost.setHost(true);
            room.setCreatedBy(newHost.getId());
            room.addEvent(newHost.getName() + " is now the host");
        }
        if (room.getStatus() == GameStatus.PLAYING) {
            if (room.getPlayers().size() < MIN_PLAYERS) {
                room.setStatus(GameStatus.FINISHED);
                room.addEvent("Not enough players — game ended");
            } else {
                // Fix current index
                int idx = room.getCurrentPlayerIndex();
                if (idx >= room.getPlayers().size()) {
                    room.setCurrentPlayerIndex(0);
                }
            }
        }
    }

    // ---- helpers ----

    private void advanceTurn(Room room) {
        int n = room.getPlayers().size();
        if (n == 0) return;
        int next = (room.getCurrentPlayerIndex() + room.getDirection()) % n;
        if (next < 0) next += n;
        room.setCurrentPlayerIndex(next);
        room.setMustDrawOrPlay(false);
        room.setLastDrawnCardId(null);
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found");
        }
        return room;
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

        Player current = room.getCurrentPlayer();
        List<PlayerView> players = new ArrayList<>();
        for (Player p : room.getPlayers()) {
            boolean isCurrent = current != null && current.getId().equals(p.getId())
                    && room.getStatus() == GameStatus.PLAYING;
            players.add(new PlayerView(
                    p.getId(), p.getName(), p.getHandSize(), p.isConnected(),
                    p.isCalledUno(), p.isHost(), isCurrent));
        }
        view.setPlayers(players);

        Player you = room.findPlayer(viewerId);
        if (you != null) {
            view.setYourHand(new ArrayList<>(you.getHand()));
            view.setYourTurn(current != null && current.getId().equals(viewerId)
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

    private static class GameException extends RuntimeException {
        GameException(String message) {
            super(message);
        }
    }
}
