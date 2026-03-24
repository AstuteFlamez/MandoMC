package com.astuteflamez.mandomc.modules.system.planets;

import org.bukkit.Bukkit;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.core.module.Module;
import com.astuteflamez.mandomc.system.planets.ilum.*;
import com.astuteflamez.mandomc.system.planets.ilum.listeners.*;
import com.astuteflamez.mandomc.system.planets.ilum.managers.*;

public class ParkourModule implements Module {

    public static ParkourManager PARKOUR_MANAGER;

    private final MandoMC plugin;
    private ParkourLeaderboardManager leaderboardManager;

    public ParkourModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {

        ParkourTimeManager timeManager = new ParkourTimeManager(plugin);
        leaderboardManager = new ParkourLeaderboardManager(plugin, timeManager);

        PARKOUR_MANAGER = new ParkourManager(timeManager, leaderboardManager);

        leaderboardManager.updateLeaderboards();
        leaderboardManager.startAutoUpdate();

        new ParkourTimerDisplay(plugin, PARKOUR_MANAGER, timeManager).start();

        CheckpointManager checkpointManager = new CheckpointManager(plugin);
        checkpointManager.loadCheckpoints();

        Bukkit.getPluginManager().registerEvents(new ParkourWorldListener(PARKOUR_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new ParkourItemListener(PARKOUR_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new ParkourCheckpointListener(PARKOUR_MANAGER, checkpointManager), plugin);
        Bukkit.getPluginManager().registerEvents(new ParkourProtectionListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new ParkourDisconnectListener(PARKOUR_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new ParkourRespawnListener(PARKOUR_MANAGER), plugin);
    }

    @Override
    public void disable() {
        if (leaderboardManager != null) {
            leaderboardManager.removeAllDisplays();
        }
    }
}