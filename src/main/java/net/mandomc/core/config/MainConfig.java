package net.mandomc.core.config;

import org.bukkit.plugin.Plugin;
import java.util.List;

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

    /** World where vehicle deployment is restricted. */
    public String getVehicleSpawnRestrictionWorld() {
        return getString("vehicle.spawn-restriction.world", "world");
    }

    /** Friendly world label shown in denial messages. */
    public String getVehicleSpawnRestrictionDisplayWorld() {
        return getString("vehicle.spawn-restriction.display-world", "Earth");
    }

    /** First X coordinate corner for spawn restriction bounds. */
    public int getVehicleSpawnRestrictionX1() {
        return getInt("vehicle.spawn-restriction.bounds.x1", 703);
    }

    /** First Z coordinate corner for spawn restriction bounds. */
    public int getVehicleSpawnRestrictionZ1() {
        return getInt("vehicle.spawn-restriction.bounds.z1", -176);
    }

    /** Second X coordinate corner for spawn restriction bounds. */
    public int getVehicleSpawnRestrictionX2() {
        return getInt("vehicle.spawn-restriction.bounds.x2", 672);
    }

    /** Second Z coordinate corner for spawn restriction bounds. */
    public int getVehicleSpawnRestrictionZ2() {
        return getInt("vehicle.spawn-restriction.bounds.z2", -145);
    }

    /** Tatooine pot world name. */
    public String getTatooinePotWorld() {
        return getString("tatooine.pots.world", "Tatooine");
    }

    /** Maximum number of active decorated pots. */
    public int getTatooineMaxActivePots() {
        return Math.max(1, getInt("tatooine.pots.max-active", 10));
    }

    /** Respawn delay in ticks after a pot is opened. */
    public long getTatooineRespawnDelayTicks() {
        return Math.max(20L, getInt("tatooine.pots.respawn-delay-ticks", 200));
    }

    /** Minimum distance in blocks from old location when selecting a new pot location. */
    public int getTatooineMinRespawnDistanceBlocks() {
        return Math.max(0, getInt("tatooine.pots.min-distance-blocks", 20));
    }

    /** Optional list of configured pot locations in format "x,y,z". */
    public List<String> getTatooinePotLocationStrings() {
        return getStringList("tatooine.pots.locations");
    }

    /** Whether gameplay abilities should be enabled. */
    public boolean isAbilitiesEnabled() {
        return getBoolean("abilities.enabled", false);
    }
}
