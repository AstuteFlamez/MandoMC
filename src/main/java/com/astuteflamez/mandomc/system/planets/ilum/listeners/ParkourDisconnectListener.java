package com.astuteflamez.mandomc.system.planets.ilum.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.astuteflamez.mandomc.system.planets.ilum.managers.ParkourManager;

import org.bukkit.entity.Player;

public class ParkourDisconnectListener implements Listener {

    private final ParkourManager parkourManager;

    public ParkourDisconnectListener(ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        Player player = event.getPlayer();

        if (!parkourManager.hasSession(player)) return;

        parkourManager.exitParkour(player);
    }
}