package mandomc;

import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {

    private static Main instance;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config.yml if it doesn't exist
        saveDefaultConfig();

        getLogger().info("MandoMC has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MandoMC has been disabled.");
    }

    public static Main getInstance() {
        return instance;
    }
}
