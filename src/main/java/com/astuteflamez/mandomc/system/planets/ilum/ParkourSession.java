package com.astuteflamez.mandomc.system.planets.ilum;

import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public class ParkourSession {

    private final UUID playerId;

    private ParkourState state;

    private ItemStack[] savedInventory;
    private ItemStack[] savedArmor;

    private GameMode savedGamemode;

    private Location returnLocation;
    private Location startLocation;
    private Location checkpoint;
    private Location lastCheckpointPlate;

    private long startTime;
    private int lastCheckpoint;

    public ParkourSession(UUID playerId) {
        this.playerId = playerId;
        this.state = ParkourState.ACTIVE;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public ParkourState getState() {
        return state;
    }

    public void setState(ParkourState state) {
        this.state = state;
    }

    public ItemStack[] getSavedInventory() {
        return savedInventory;
    }

    public void setSavedInventory(ItemStack[] savedInventory) {
        this.savedInventory = savedInventory;
    }

    public ItemStack[] getSavedArmor() {
        return savedArmor;
    }

    public void setSavedArmor(ItemStack[] savedArmor) {
        this.savedArmor = savedArmor;
    }

    public GameMode getSavedGamemode() {
        return savedGamemode;
    }

    public void setSavedGamemode(GameMode savedGamemode) {
        this.savedGamemode = savedGamemode;
    }

    public Location getReturnLocation() {
        return returnLocation;
    }

    public void setReturnLocation(Location returnLocation) {
        this.returnLocation = returnLocation;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(Location startLocation) {
        this.startLocation = startLocation;
    }

    public Location getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Location checkpoint) {
        this.checkpoint = checkpoint;
    }

    public Location getLastCheckpointPlate() {
        return lastCheckpointPlate;
    }

    public void setLastCheckpointPlate(Location lastCheckpointPlate) {
        this.lastCheckpointPlate = lastCheckpointPlate;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public int getLastCheckpoint() {
        return lastCheckpoint;
    }

    public void setLastCheckpoint(int lastCheckpoint) {
        this.lastCheckpoint = lastCheckpoint;
    }
}