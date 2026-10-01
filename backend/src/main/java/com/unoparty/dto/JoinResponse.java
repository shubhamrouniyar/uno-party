package com.unoparty.dto;

public class JoinResponse {
    private String playerId;
    private String roomCode;
    private String displayName;
    private boolean host;
    private GameStateView gameState;

    public JoinResponse(String playerId, String roomCode, String displayName, boolean host, GameStateView gameState) {
        this.playerId = playerId;
        this.roomCode = roomCode;
        this.displayName = displayName;
        this.host = host;
        this.gameState = gameState;
    }

    public String getPlayerId() {
        return playerId;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isHost() {
        return host;
    }

    public GameStateView getGameState() {
        return gameState;
    }
}
