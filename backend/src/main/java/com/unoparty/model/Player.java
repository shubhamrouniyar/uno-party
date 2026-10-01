package com.unoparty.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

public class Player {
    private final String id;
    private final String name;
    private final List<Card> hand = new ArrayList<>();
    private boolean connected = true;
    private boolean calledUno = false;
    private boolean host = false;

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    @JsonIgnore
    public List<Card> getHand() {
        return hand;
    }

    public int getHandSize() {
        return hand.size();
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public boolean isCalledUno() {
        return calledUno;
    }

    public void setCalledUno(boolean calledUno) {
        this.calledUno = calledUno;
    }

    public boolean isHost() {
        return host;
    }

    public void setHost(boolean host) {
        this.host = host;
    }
}
