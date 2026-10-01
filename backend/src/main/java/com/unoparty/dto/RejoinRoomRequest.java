package com.unoparty.dto;

import jakarta.validation.constraints.NotBlank;

public class RejoinRoomRequest {
    @NotBlank
    private String playerId;

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }
}
