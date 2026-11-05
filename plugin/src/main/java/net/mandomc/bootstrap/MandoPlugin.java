package net.mandomc.bootstrap;

import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

/** Minimal bootstrap. Gameplay modules are introduced through separate feature branches. */
public final class MandoPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (getConfig().getInt("schema-version", -1) != 1) {
            getLogger().severe("Unsupported config.yml schema-version. Expected 1; plugin disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Objects.requireNonNull(getCommand("mandomc"), "Missing mandomc command in plugin.yml")
                .setExecutor((sender, command, label, arguments) -> {
                    sender.sendMessage(Component.text(
                            "MandoMC " + getPluginMeta().getVersion() + ": foundation enabled."));
                    return true;
                });
        getLogger().info("Foundation enabled. No gameplay modules are installed yet.");
    }
}
