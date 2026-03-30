package net.mandomc.core.modules.mechanics;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.core.modules.core.GUIModule;
import net.mandomc.mechanics.bounties.BountyCommand;
import net.mandomc.mechanics.bounties.BountyConfig;
import net.mandomc.mechanics.bounties.BountyListener;
import net.mandomc.mechanics.bounties.BountyStorage;
import net.mandomc.mechanics.bounties.BountyTrackerTask;

/**
 * Manages the lifecycle of the bounty system.
 *
 * Initializes config, storage, the tracker task, the bounty listener,
 * and the /bounty command on enable. Persists data on disable.
 */
public class BountyModule implements Module {

    private final MandoMC plugin;

    /**
     * Creates the bounty module.
     *
     * @param plugin the plugin instance
     */
    public BountyModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Enables the bounty system.
     *
     * Sets up config, loads persisted bounties, starts the tracker task,
     * registers the bounty event listener, and registers the /bounty command.
     */
    @Override
    public void enable() {
        BountyConfig.setup(plugin);
        BountyStorage.setup(plugin.getDataFolder());
        BountyStorage.load();

        BountyTrackerTask.start();

        Bukkit.getPluginManager().registerEvents(new BountyListener(), plugin);

        PluginCommand cmd = plugin.getCommand("bounty");

        if (cmd != null) {
            BountyCommand bountyCommand = new BountyCommand(GUIModule.GUI_MANAGER);
            cmd.setExecutor(bountyCommand);
            cmd.setTabCompleter(bountyCommand);
        } else {
            plugin.getLogger().severe("Command 'bounty' not found in plugin.yml");
        }

        plugin.getLogger().info("Bounty module enabled.");
    }

    /**
     * Disables the bounty system and persists all bounty data.
     */
    @Override
    public void disable() {
        BountyStorage.save();
        plugin.getLogger().info("Bounty module disabled.");
    }
}
