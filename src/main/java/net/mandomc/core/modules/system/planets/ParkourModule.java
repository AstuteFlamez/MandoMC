package net.mandomc.core.modules.system.planets;

import org.bukkit.Bukkit;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.system.planets.ilum.ParkourTimerDisplay;
import net.mandomc.system.planets.ilum.listeners.ParkourCheckpointListener;
import net.mandomc.system.planets.ilum.listeners.ParkourDisconnectListener;
import net.mandomc.system.planets.ilum.listeners.ParkourItemListener;
import net.mandomc.system.planets.ilum.listeners.ParkourProtectionListener;
import net.mandomc.system.planets.ilum.listeners.ParkourRespawnListener;
import net.mandomc.system.planets.ilum.listeners.ParkourWorldListener;
import net.mandomc.system.planets.ilum.managers.CheckpointManager;
import net.mandomc.system.planets.ilum.managers.ParkourLeaderboardManager;
import net.mandomc.system.planets.ilum.managers.ParkourManager;
import net.mandomc.system.planets.ilum.managers.ParkourTimeManager;

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