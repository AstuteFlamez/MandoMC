package com.astuteflamez.mandomc.features.small_features.leaderboards;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

public class Leaderboard {

    private final String id;
    private final String placeholder;
    private final String permission;
    private final Location location;

    private final List<TextDisplay> displays = new ArrayList<>();

    public Leaderboard(String id, String placeholder, String permission, Location location) {
        this.id = id;
        this.placeholder = placeholder;
        this.permission = permission;
        this.location = location;
    }

    public String getId() { return id; }
    public String getPlaceholder() { return placeholder; }
    public String getPermission() { return permission; }
    public Location getLocation() { return location; }
    public List<TextDisplay> getDisplays() { return displays; }
}