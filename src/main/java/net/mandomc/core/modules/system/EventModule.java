package net.mandomc.core.modules.system;

import org.bukkit.Bukkit;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.system.events.EventManager;
import net.mandomc.system.events.EventScheduler;
import net.mandomc.system.events.listeners.EventMenuListener;
import net.mandomc.system.events.types.jabba_dungeon.DoorListener;
import net.mandomc.system.events.types.jabba_dungeon.JabbaChestListener;
import net.mandomc.system.events.types.jabba_dungeon.JabbaDungeonMobListener;
import net.mandomc.system.events.types.koth.KothChestListener;
import net.mandomc.system.events.types.koth.KothEvent;
import net.mandomc.system.events.types.mining.BeskarMiningListener;

/**
 * Manages the lifecycle of the game events system.
 *
 * Initializes the EventManager, starts the event scheduler, and registers
 * all event-related listeners on enable. Cleans up on disable.
 */
public class EventModule implements Module {

    /**
     * The shared EventManager instance used by all event subsystems.
     */
    public static EventManager EVENT_MANAGER;

    private final MandoMC plugin;
    private EventScheduler scheduler;

    /**
     * Creates the event module.
     *
     * @param plugin the plugin instance
     */
    public EventModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Enables the event system.
     *
     * Creates the EventManager, loads event definitions, starts the scheduler,
     * and registers all event listeners.
     */
    @Override
    public void enable() {
        EVENT_MANAGER = new EventManager(plugin);
        EVENT_MANAGER.load();

        scheduler = new EventScheduler(plugin, EVENT_MANAGER);
        scheduler.start();

        Bukkit.getPluginManager().registerEvents(new EventMenuListener(EVENT_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new KothChestListener(), plugin);
        Bukkit.getPluginManager().registerEvents(new BeskarMiningListener(EVENT_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new JabbaChestListener(EVENT_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new DoorListener(plugin, EVENT_MANAGER), plugin);
        Bukkit.getPluginManager().registerEvents(new JabbaDungeonMobListener(EVENT_MANAGER), plugin);
    }

    /**
     * Disables the event system.
     *
     * Stops the scheduler and clears any active KOTH reward chests.
     */
    @Override
    public void disable() {
        if (scheduler != null) scheduler.stop();
        KothEvent.clearRewardChest();
    }
}
