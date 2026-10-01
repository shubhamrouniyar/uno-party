package com.unoparty.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PlayerView {
    private String id;
    private String name;
    private int handSize;
    private boolean connected;
    private boolean calledUno;
    private boolean host;
    private boolean current;

    public PlayerView(String id, String name, int handSize, boolean connected, boolean calledUno,
                      boolean host, boolean current) {
        this.id = id;
        this.name = name;
        this.handSize = handSize;
        this.connected = connected;
        this.calledUno = calledUno;
        this.host = host;
        this.current = current;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getHandSize() { return handSize; }
    public boolean isConnected() { return connected; }
    public boolean isCalledUno() { return calledUno; }
    public boolean isHost() { return host; }

    @JsonProperty("current")
    public boolean isCurrent() { return current; }
}
