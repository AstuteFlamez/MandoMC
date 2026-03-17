package com.astuteflamez.mandomc.features.parkour.listeners;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import com.astuteflamez.mandomc.features.parkour.configs.ParkourConfig;
import com.astuteflamez.mandomc.features.parkour.managers.ParkourManager;

import org.bukkit.entity.Player;

public class ParkourWorldListener implements Listener {

    private final ParkourManager parkourManager;

    public ParkourWorldListener(ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();

        String parkourWorldName = ParkourConfig.get().getString("parkour.world");

        if (parkourWorldName == null) return;

        World parkourWorld = Bukkit.getWorld(parkourWorldName);

        if (parkourWorld == null) return;

        // Player entered the parkour world
        if (player.getWorld().equals(parkourWorld)) {

            Location startLocation = getStartLocation(parkourWorld);

            parkourManager.enterParkour(player, startLocation);
        }
    }

    private Location getStartLocation(World world) {

        double x = ParkourConfig.get().getDouble("parkour.start.x");
        double y = ParkourConfig.get().getDouble("parkour.start.y");
        double z = ParkourConfig.get().getDouble("parkour.start.z");

        float yaw = (float) ParkourConfig.get().getDouble("parkour.start.yaw");
        float pitch = (float) ParkourConfig.get().getDouble("parkour.start.pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }
}