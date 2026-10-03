package com.unoparty.dto;

import com.unoparty.model.Card;
import com.unoparty.model.CardColor;
import com.unoparty.model.GameStatus;

import java.util.List;

public class GameStateView {
    private String roomCode;
    private GameStatus status;
    private List<PlayerView> players;
    private Card topCard;
    private CardColor activeColor;
    private int direction;
    private int drawPileCount;
    private List<Card> yourHand;
    private String yourPlayerId;
    private boolean yourTurn;
    private boolean mustDrawOrPlay;
    private String lastDrawnCardId;
    private String winnerId;
    private String winnerName;
    private List<String> eventLog;
    private String error;
    private String message;
    /** Authoritative seat. Clients must render this, never guess the next player. */
    private String currentPlayerId;
    private String currentPlayerName;
    /** Monotonic room revision. Clients drop older revisions. */
    private long stateVersion;

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public GameStatus getStatus() { return status; }
    public void setStatus(GameStatus status) { this.status = status; }

    public List<PlayerView> getPlayers() { return players; }
    public void setPlayers(List<PlayerView> players) { this.players = players; }

    public Card getTopCard() { return topCard; }
    public void setTopCard(Card topCard) { this.topCard = topCard; }

    public CardColor getActiveColor() { return activeColor; }
    public void setActiveColor(CardColor activeColor) { this.activeColor = activeColor; }

    public int getDirection() { return direction; }
    public void setDirection(int direction) { this.direction = direction; }

    public int getDrawPileCount() { return drawPileCount; }
    public void setDrawPileCount(int drawPileCount) { this.drawPileCount = drawPileCount; }

    public List<Card> getYourHand() { return yourHand; }
    public void setYourHand(List<Card> yourHand) { this.yourHand = yourHand; }

    public String getYourPlayerId() { return yourPlayerId; }
    public void setYourPlayerId(String yourPlayerId) { this.yourPlayerId = yourPlayerId; }

    public boolean isYourTurn() { return yourTurn; }
    public void setYourTurn(boolean yourTurn) { this.yourTurn = yourTurn; }

    public boolean isMustDrawOrPlay() { return mustDrawOrPlay; }
    public void setMustDrawOrPlay(boolean mustDrawOrPlay) { this.mustDrawOrPlay = mustDrawOrPlay; }

    public String getLastDrawnCardId() { return lastDrawnCardId; }
    public void setLastDrawnCardId(String lastDrawnCardId) { this.lastDrawnCardId = lastDrawnCardId; }

    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }

    public String getWinnerName() { return winnerName; }
    public void setWinnerName(String winnerName) { this.winnerName = winnerName; }

    public List<String> getEventLog() { return eventLog; }
    public void setEventLog(List<String> eventLog) { this.eventLog = eventLog; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getCurrentPlayerId() { return currentPlayerId; }
    public void setCurrentPlayerId(String currentPlayerId) { this.currentPlayerId = currentPlayerId; }

    public String getCurrentPlayerName() { return currentPlayerName; }
    public void setCurrentPlayerName(String currentPlayerName) { this.currentPlayerName = currentPlayerName; }

    public long getStateVersion() { return stateVersion; }
    public void setStateVersion(long stateVersion) { this.stateVersion = stateVersion; }
}
