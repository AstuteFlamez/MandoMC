package com.astuteflamez.mandomc.system.events.types.jabba_dungeon;

public class JabbaDungeonState {

    private int currentRoom = 1;
    private int kills = 0;
    private boolean keyDropped = false;

    public int getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(int room) {
        this.currentRoom = room;
    }

    public int getKills() {
        return kills;
    }

    public void incrementKills() {
        this.kills++;
    }

    public boolean isKeyDropped() {
        return keyDropped;
    }

    public void setKeyDropped(boolean keyDropped) {
        this.keyDropped = keyDropped;
    }

    public void resetRoom() {
        this.kills = 0;
        this.keyDropped = false;
    }
}