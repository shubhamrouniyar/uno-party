package com.unoparty.model;

public enum GameStatus {
    LOBBY,
    PLAYING,
    /** Fewer than 2 seated players mid-game — waiting for reconnect or new lobby. */
    PAUSED,
    FINISHED
}
