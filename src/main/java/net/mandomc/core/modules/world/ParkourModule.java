package net.mandomc.core.modules.world;

import net.mandomc.MandoMC;
import net.mandomc.core.lifecycle.ListenerRegistrar;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.world.ilum.ParkourTimerDisplay;
import net.mandomc.world.ilum.config.ParkourConfig;
import net.mandomc.world.ilum.listener.ParkourCheckpointListener;
import net.mandomc.world.ilum.listener.ParkourDisconnectListener;
import net.mandomc.world.ilum.listener.ParkourItemListener;
import net.mandomc.world.ilum.listener.ParkourProtectionListener;
import net.mandomc.world.ilum.listener.ParkourRespawnListener;
import net.mandomc.world.ilum.listener.ParkourWorldListener;
import net.mandomc.world.ilum.manager.CheckpointManager;
import net.mandomc.world.ilum.manager.ParkourLeaderboardManager;
import net.mandomc.world.ilum.manager.ParkourManager;
import net.mandomc.world.ilum.manager.ParkourTimeManager;
import net.mandomc.world.ilum.storage.ParkourTimeRepository;

/**
 * Manages the lifecycle of the Ilum parkour system.
 *
 * Creates and registers {@link ParkourTimeRepository} and
 * {@link ParkourManager} in the service registry, and tracks all
 * listeners for clean unregistration on disable.
 */
public class ParkourModule implements Module {

    private final MandoMC plugin;
    private ParkourLeaderboardManager leaderboardManager;
    private ParkourTimerDisplay timerDisplay;
    private ListenerRegistrar listenerRegistrar;
    private ParkourTimeRepository timeRepository;
    private ParkourManager parkourManager;
    private CheckpointManager checkpointManager;

    public ParkourModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable(ServiceRegistry registry) {
        listenerRegistrar = new ListenerRegistrar(plugin);

        // Repository — load persisted times, register for other modules
        timeRepository = new ParkourTimeRepository(plugin);
        timeRepository.load();
        registry.register(ParkourTimeRepository.class, timeRepository);

        // Manager delegates storage to repository
        ParkourTimeManager timeManager = new ParkourTimeManager(timeRepository);
        ParkourConfig parkourConfig = registry.get(ParkourConfig.class);

        leaderboardManager = new ParkourLeaderboardManager(plugin, timeManager, parkourConfig);

        parkourManager = new ParkourManager(timeManager, leaderboardManager);
        registry.register(ParkourManager.class, parkourManager);

        leaderboardManager.updateLeaderboards();
        leaderboardManager.startAutoUpdate();

        timerDisplay = new ParkourTimerDisplay(plugin, parkourManager, timeManager);
        timerDisplay.start();

        checkpointManager = new CheckpointManager(plugin);
        checkpointManager.loadCheckpoints();

        listenerRegistrar.register(new ParkourWorldListener(parkourManager));
        listenerRegistrar.register(new ParkourItemListener(parkourManager));
        listenerRegistrar.register(new ParkourCheckpointListener(parkourManager, checkpointManager));
        listenerRegistrar.register(new ParkourProtectionListener(parkourManager));
        listenerRegistrar.register(new ParkourDisconnectListener(parkourManager));
        listenerRegistrar.register(new ParkourRespawnListener(parkourManager));
    }

    @Override
    public void disable() {
        if (listenerRegistrar  != null) listenerRegistrar.unregisterAll();
        if (parkourManager    != null) parkourManager.shutdownSessions();
        if (timerDisplay       != null) timerDisplay.stop();
        if (leaderboardManager != null) leaderboardManager.stopAutoUpdate();
        if (leaderboardManager != null) leaderboardManager.removeAllDisplays();
        if (checkpointManager  != null) checkpointManager.clearDisplays();
        if (timeRepository     != null) timeRepository.flush();
    }
}
