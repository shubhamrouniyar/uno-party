package com.unoparty.dto;

import com.unoparty.model.CardColor;

public class GameAction {
    private String playerId;
    private String type; // START, PLAY, DRAW, PASS, CALL_UNO, CHALLENGE_UNO, REMATCH, LEAVE
    private String cardId;
    private CardColor chosenColor;
    private String targetPlayerId; // for UNO challenge
    /** Client idempotency key. Duplicate deliveries are ignored. */
    private String actionId;

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public CardColor getChosenColor() {
        return chosenColor;
    }

    public void setChosenColor(CardColor chosenColor) {
        this.chosenColor = chosenColor;
    }

    public String getTargetPlayerId() {
        return targetPlayerId;
    }

    public void setTargetPlayerId(String targetPlayerId) {
        this.targetPlayerId = targetPlayerId;
    }

    public String getActionId() {
        return actionId;
    }

    public void setActionId(String actionId) {
        this.actionId = actionId;
    }
}
