package net.mandomc.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.mandomc.core.config.MainConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Manages a HikariCP connection pool for MySQL access.
 *
 * Created by {@link net.mandomc.core.modules.core.DatabaseModule} and
 * registered in the {@link net.mandomc.core.services.ServiceRegistry} so
 * SQL-backed repositories can obtain connections via constructor injection.
 */
public class DatabaseService {

    private final HikariDataSource dataSource;
    private final Logger logger;

    /**
     * Initialises the HikariCP pool from the provided config.
     *
     * @param config the main plugin configuration
     * @param logger the plugin logger for startup messages
     * @throws IllegalStateException if the pool cannot be created
     */
    public DatabaseService(MainConfig config, Logger logger) {
        this.logger = logger;

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(
                "jdbc:mysql://" + config.getDatabaseHost()
                + ":" + config.getDatabasePort()
                + "/" + config.getDatabaseName()
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"
        );
        hikariConfig.setUsername(config.getDatabaseUser());
        hikariConfig.setPassword(config.getDatabasePassword());
        hikariConfig.setMaximumPoolSize(config.getPoolSize());
        hikariConfig.setConnectionTimeout(10_000);
        hikariConfig.setInitializationFailTimeout(10_000);
        hikariConfig.setPoolName("MandoMC-Pool");

        this.dataSource = new HikariDataSource(hikariConfig);
        logger.info("[Database] Pool initialised (max=" + config.getPoolSize() + ").");
    }

    /**
     * Borrows a connection from the pool.
     *
     * @return a JDBC {@link Connection}; the caller is responsible for closing it
     * @throws SQLException if a connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Closes the connection pool. Called by {@link net.mandomc.core.modules.core.DatabaseModule#disable()}.
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("[Database] Pool closed.");
        }
    }

    /**
     * Returns true if the pool is open and accepting connections.
     *
     * @return true if running
     */
    public boolean isRunning() {
        return dataSource != null && !dataSource.isClosed();
    }
}
