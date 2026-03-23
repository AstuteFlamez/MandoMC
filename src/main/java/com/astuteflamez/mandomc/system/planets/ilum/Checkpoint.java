package com.astuteflamez.mandomc.system.planets.ilum;

import org.bukkit.Location;

public class Checkpoint {

    private final int number;
    private final Location location;

    public Checkpoint(int number, Location location) {
        this.number = number;
        this.location = location;
    }

    public int getNumber() {
        return number;
    }

    public Location getLocation() {
        return location;
    }
}