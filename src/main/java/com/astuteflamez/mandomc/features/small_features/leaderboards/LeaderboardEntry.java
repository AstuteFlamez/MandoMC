package com.astuteflamez.mandomc.features.small_features.leaderboards;

import java.util.UUID;

public class LeaderboardEntry {

    private final UUID uuid;
    private final String name;
    private final double value;

    public LeaderboardEntry(UUID uuid, String name, double value) {
        this.uuid = uuid;
        this.name = name;
        this.value = value;
    }

    public UUID getUUID() { return uuid; }
    public String getName() { return name; }
    public double getValue() { return value; }
}