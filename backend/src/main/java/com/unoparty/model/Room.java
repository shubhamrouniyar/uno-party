package com.unoparty.model;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class Room {
    private final String code;
    private final List<Player> players = new CopyOnWriteArrayList<>();
    private GameStatus status = GameStatus.LOBBY;
    private final List<Card> drawPile = new ArrayList<>();
    private final List<Card> discardPile = new ArrayList<>();
    private CardColor activeColor;
    private int currentPlayerIndex = 0;
    private int direction = 1; // 1 = clockwise, -1 = counter
    private String winnerId;
    private String winnerName;
    private final List<String> eventLog = new ArrayList<>();
    private int pendingDraw = 0; // stacked draw amount waiting
    private boolean mustDrawOrPlay = false; // after drawing one card, can play it or pass
    private String lastDrawnCardId; // card just drawn that may be playable
    private Instant lastActivity = Instant.now();
    private String createdBy;
    private long stateVersion = 0;
    private final ArrayDeque<String> actionOrder = new ArrayDeque<>();
    private final Set<String> actionIds = new HashSet<>();

    public Room(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public List<Card> getDrawPile() {
        return drawPile;
    }

    public List<Card> getDiscardPile() {
        return discardPile;
    }

    public CardColor getActiveColor() {
        return activeColor;
    }

    public void setActiveColor(CardColor activeColor) {
        this.activeColor = activeColor;
    }

    public int getCurrentPlayerIndex() {
        return currentPlayerIndex;
    }

    public void setCurrentPlayerIndex(int currentPlayerIndex) {
        this.currentPlayerIndex = currentPlayerIndex;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }

    public String getWinnerName() {
        return winnerName;
    }

    public void setWinnerName(String winnerName) {
        this.winnerName = winnerName;
    }

    public List<String> getEventLog() {
        return eventLog;
    }

    public void addEvent(String event) {
        eventLog.add(event);
        if (eventLog.size() > 30) {
            eventLog.remove(0);
        }
    }

    public int getPendingDraw() {
        return pendingDraw;
    }

    public void setPendingDraw(int pendingDraw) {
        this.pendingDraw = pendingDraw;
    }

    public boolean isMustDrawOrPlay() {
        return mustDrawOrPlay;
    }

    public void setMustDrawOrPlay(boolean mustDrawOrPlay) {
        this.mustDrawOrPlay = mustDrawOrPlay;
    }

    public String getLastDrawnCardId() {
        return lastDrawnCardId;
    }

    public void setLastDrawnCardId(String lastDrawnCardId) {
        this.lastDrawnCardId = lastDrawnCardId;
    }

    public Instant getLastActivity() {
        return lastActivity;
    }

    public void touch() {
        this.lastActivity = Instant.now();
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getStateVersion() {
        return stateVersion;
    }

    public long bumpState() {
        stateVersion += 1;
        return stateVersion;
    }

    /**
     * @return true if this action id should be processed. Blank ids are always processed.
     *         Repeats of a seen id are duplicates and must be ignored.
     */
    public boolean consumeActionId(String actionId) {
        if (actionId == null || actionId.isBlank()) {
            return true;
        }
        if (!actionIds.add(actionId)) {
            return false;
        }
        actionOrder.addLast(actionId);
        while (actionOrder.size() > 200) {
            actionIds.remove(actionOrder.removeFirst());
        }
        return true;
    }

    public Player getCurrentPlayer() {
        if (players.isEmpty() || currentPlayerIndex < 0 || currentPlayerIndex >= players.size()) {
            return null;
        }
        return players.get(currentPlayerIndex);
    }

    public Player findPlayer(String playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst().orElse(null);
    }

    public Card getTopDiscard() {
        if (discardPile.isEmpty()) {
            return null;
        }
        return discardPile.get(discardPile.size() - 1);
    }

    public void resetForRematch() {
        drawPile.clear();
        discardPile.clear();
        activeColor = null;
        currentPlayerIndex = 0;
        direction = 1;
        winnerId = null;
        winnerName = null;
        pendingDraw = 0;
        mustDrawOrPlay = false;
        lastDrawnCardId = null;
        eventLog.clear();
        for (Player p : players) {
            p.getHand().clear();
            p.setCalledUno(false);
        }
        status = GameStatus.LOBBY;
    }
}
