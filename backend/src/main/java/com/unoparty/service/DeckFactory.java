package com.unoparty.service;

import com.unoparty.model.Card;
import com.unoparty.model.CardColor;
import com.unoparty.model.CardType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class DeckFactory {

    private DeckFactory() {}

    public static List<Card> createShuffledDeck() {
        List<Card> deck = new ArrayList<>(108);
        CardColor[] colors = {CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE};

        for (CardColor color : colors) {
            deck.add(new Card(color, CardType.NUMBER, 0));
            for (int n = 1; n <= 9; n++) {
                deck.add(new Card(color, CardType.NUMBER, n));
                deck.add(new Card(color, CardType.NUMBER, n));
            }
            for (int i = 0; i < 2; i++) {
                deck.add(new Card(color, CardType.SKIP, null));
                deck.add(new Card(color, CardType.REVERSE, null));
                deck.add(new Card(color, CardType.DRAW_TWO, null));
            }
        }
        for (int i = 0; i < 4; i++) {
            deck.add(new Card(CardColor.WILD, CardType.WILD, null));
            deck.add(new Card(CardColor.WILD, CardType.WILD_DRAW_FOUR, null));
        }

        Collections.shuffle(deck, ThreadLocalRandom.current());
        return deck;
    }
}
