package com.unoparty.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;
import java.util.UUID;

public class Card {
    private final String id;
    private final CardColor color;
    private final CardType type;
    private final Integer number; // 0-9 for NUMBER, null otherwise

    public Card(CardColor color, CardType type, Integer number) {
        this.id = UUID.randomUUID().toString();
        this.color = color;
        this.type = type;
        this.number = number;
    }

    public String getId() {
        return id;
    }

    public CardColor getColor() {
        return color;
    }

    public CardType getType() {
        return type;
    }

    public Integer getNumber() {
        return number;
    }

    @JsonIgnore
    public boolean isWild() {
        return type == CardType.WILD || type == CardType.WILD_DRAW_FOUR;
    }

    @JsonIgnore
    public boolean isAction() {
        return type == CardType.SKIP || type == CardType.REVERSE || type == CardType.DRAW_TWO;
    }

    public boolean matches(Card top, CardColor activeColor) {
        if (isWild()) {
            return true;
        }
        CardColor effective = activeColor != null ? activeColor : top.getColor();
        if (color == effective) {
            return true;
        }
        if (type == CardType.NUMBER && top.getType() == CardType.NUMBER
                && Objects.equals(number, top.getNumber())) {
            return true;
        }
        return type != CardType.NUMBER && type == top.getType();
    }

    @Override
    public String toString() {
        if (type == CardType.NUMBER) {
            return color + " " + number;
        }
        if (isWild()) {
            return type.name();
        }
        return color + " " + type;
    }
}
