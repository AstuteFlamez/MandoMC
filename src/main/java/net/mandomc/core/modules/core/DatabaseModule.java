package net.mandomc.core.modules.core;

import net.mandomc.MandoMC;
import net.mandomc.core.config.MainConfig;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;
import net.mandomc.core.storage.DatabaseService;

/**
 * Initialises the HikariCP database connection pool and registers
 * {@link DatabaseService} in the {@link ServiceRegistry}.
 *
 * Must be enabled after {@link ConfigModule} so that {@link MainConfig}
 * is already registered and database credentials are available.
 */
public class DatabaseModule implements Module {

    private final MandoMC plugin;
    private DatabaseService databaseService;

    /**
     * Creates the database module.
     *
     * @param plugin the plugin instance
     */
    public DatabaseModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates the connection pool from {@link MainConfig} and registers
     * {@link DatabaseService} in the registry.
     *
     * If database credentials are not configured (host is blank or "localhost"
     * without explicit configuration), the pool creation is skipped and a
     * warning is logged. Modules that require the database must guard their
     * initialisation accordingly.
     */
    @Override
    public void enable(ServiceRegistry registry) {
        MainConfig config = registry.get(MainConfig.class);

        String host = config.getDatabaseHost();
        if (host == null || host.isBlank()) {
            plugin.getLogger().warning("[Database] No database host configured — SQL features disabled.");
            return;
        }

        try {
            databaseService = new DatabaseService(config, plugin.getLogger());
            registry.register(DatabaseService.class, databaseService);
        } catch (Exception e) {
            plugin.getLogger().severe("[Database] Failed to initialise pool: " + e.getMessage());
            plugin.getLogger().warning("[Database] SQL features will be unavailable.");
        }
    }

    /**
     * Closes the connection pool gracefully.
     */
    @Override
    public void disable() {
        if (databaseService != null) {
            databaseService.close();
            databaseService = null;
        }
    }
}
