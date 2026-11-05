package net.mandomc.core.modules.gameplay;

import net.mandomc.MandoMC;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.lifecycle.ListenerRegistrar;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.gameplay.bounty.command.BountyCommand;
import net.mandomc.gameplay.bounty.listener.BountyListener;
import net.mandomc.gameplay.bounty.BountyShowcaseManager;
import net.mandomc.gameplay.bounty.task.BountyTrackerTask;
import net.mandomc.gameplay.bounty.config.BountyConfig;
import net.mandomc.gameplay.bounty.storage.BountyRepository;

/**
 * Manages the lifecycle of the bounty system.
 *
 * Creates and registers {@link BountyRepository} in the service registry,
 * wires it into the tracker task and listener, and persists data on disable.
 */
public class BountyModule implements Module {

    private final MandoMC plugin;
    private ListenerRegistrar listenerRegistrar;
    private BountyTrackerTask trackerTask;
    private BountyRepository repository;

    public BountyModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable(ServiceRegistry registry) {
        listenerRegistrar = new ListenerRegistrar(plugin);
        GUIManager guiManager = registry.get(GUIManager.class);

        // Typed config (Phase 3)
        BountyConfig config = registry.get(BountyConfig.class);

        // Repository — registers so Phase 10 BountyManager can inject it
        repository = new BountyRepository(plugin);
        repository.load();
        registry.register(BountyRepository.class, repository);

        // Legacy facade now delegates to repository-backed storage.
        net.mandomc.gameplay.bounty.BountyStorage.setup(plugin.getDataFolder(), repository);
        net.mandomc.gameplay.bounty.BountyStorage.load();

        // Tracker task and showcase
        trackerTask = new BountyTrackerTask(plugin, repository, config);
        trackerTask.start();
        BountyShowcaseManager.start(config);

        // Listener with injected repository
        listenerRegistrar.register(new BountyListener(repository));

        // Command
        var cmd = plugin.getCommand("bounty");
        if (cmd != null) {
            BountyCommand bountyCommand = new BountyCommand(guiManager);
            cmd.setExecutor(bountyCommand);
            cmd.setTabCompleter(bountyCommand);
        } else {
            plugin.getLogger().severe("Command 'bounty' not found in plugin.yml");
        }

        plugin.getLogger().info("Bounty module enabled.");
    }

    @Override
    public void disable() {
        if (listenerRegistrar != null) listenerRegistrar.unregisterAll();
        if (trackerTask       != null) trackerTask.stop();
        BountyShowcaseManager.stop();
        BountyShowcaseManager.remove();
        if (repository != null) repository.flush();
        net.mandomc.gameplay.bounty.BountyStorage.save();
        plugin.getLogger().info("Bounty module disabled.");
    }
}
