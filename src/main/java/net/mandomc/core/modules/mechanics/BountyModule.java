package net.mandomc.core.modules.mechanics;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.core.modules.core.GUIModule;

import net.mandomc.mechanics.bounties.*;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;

/**
 * Handles lifecycle of the bounty system.
 */
public class BountyModule implements Module {

    private final MandoMC plugin;

    public BountyModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {

        // =========================
        // ⚙️ CONFIG + STORAGE
        // =========================
        BountyConfig.setup(plugin);
        BountyStorage.setup(plugin.getDataFolder());
        BountyStorage.load();

        // =========================
        // ⏱ TRACKER TASK
        // =========================
        BountyTrackerTask.start(); // ✅ FIXED

        // =========================
        // 🎧 LISTENERS
        // =========================
        Bukkit.getPluginManager().registerEvents(new BountyListener(), plugin);

        // =========================
        // 🧾 COMMANDS
        // =========================
        PluginCommand cmd = plugin.getCommand("bounty");

        if (cmd != null) {
            BountyCommand bountyCommand = new BountyCommand(GUIModule.GUI_MANAGER);

            cmd.setExecutor(bountyCommand);
            cmd.setTabCompleter(bountyCommand); // ✅ IMPORTANT
        } else {
            plugin.getLogger().severe("❌ Command 'bounty' not found in plugin.yml");
        }

        plugin.getLogger().info("✅ Bounty module enabled");
    }

    @Override
    public void disable() {
        BountyStorage.save();
        plugin.getLogger().info("❌ Bounty module disabled");
    }
}