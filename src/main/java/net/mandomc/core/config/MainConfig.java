package net.mandomc.core.config;

import org.bukkit.plugin.Plugin;

/**
 * Typed wrapper for {@code config.yml}.
 *
 * Provides structured access to the database connection settings that
 * are shared across the storage layer. The plugin's main config file is
 * managed by Paper (via {@link org.bukkit.plugin.java.JavaPlugin#getConfig()});
 * this class delegates to it after reloads.
 */
public class MainConfig extends BaseConfig {

    /**
     * Creates the main config wrapper.
     *
     * @param plugin the owning plugin
     */
    public MainConfig(Plugin plugin) {
        super(plugin, "config.yml");
        // The main config is managed by Paper — sync our view with it
        this.config = plugin.getConfig();
    }

    @Override
    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    /** Database host name or IP. */
    public String getDatabaseHost() {
        return getString("database.host", "localhost");
    }

    /** Database port. */
    public int getDatabasePort() {
        return getInt("database.port", 3306);
    }

    /** Database schema/name. */
    public String getDatabaseName() {
        return getString("database.name", "mandomc");
    }

    /** Database username. */
    public String getDatabaseUser() {
        return getString("database.username", "user");
    }

    /** Database password. */
    public String getDatabasePassword() {
        return getString("database.password", "");
    }

    /** HikariCP maximum pool size. */
    public int getPoolSize() {
        return getInt("database.pool-size", 10);
    }
}
