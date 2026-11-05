package net.mandomc.server.discord.storage;

import net.mandomc.core.storage.DatabaseService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * SQL-backed repository for Discord account link codes.
 *
 * A link code is a short random string generated in-game that the player
 * enters in the Discord bot to link their Minecraft account. Codes expire
 * and are deleted after use.
 *
 * Requires {@link DatabaseService} to be initialised and registered in the
 * {@link net.mandomc.core.services.ServiceRegistry}.
 */
public class DiscordLinkRepository {

    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS discord_link_codes ("
            + "  code        VARCHAR(16)  NOT NULL PRIMARY KEY,"
            + "  player_uuid CHAR(36)     NOT NULL,"
            + "  created_at  BIGINT       NOT NULL"
            + ")";

    private static final String INSERT =
            "INSERT INTO discord_link_codes (code, player_uuid, created_at) VALUES (?, ?, ?)";

    private static final String FIND_BY_CODE =
            "SELECT player_uuid FROM discord_link_codes WHERE code = ?";

    private static final String DELETE_BY_CODE =
            "DELETE FROM discord_link_codes WHERE code = ?";

    private static final String DELETE_EXPIRED =
            "DELETE FROM discord_link_codes WHERE created_at < ?";

    private final DatabaseService db;
    private final Logger logger;

    /**
     * Creates the repository and ensures the backing table exists.
     *
     * @param db     the database connection pool
     * @param logger plugin logger for error reporting
     */
    public DiscordLinkRepository(DatabaseService db, Logger logger) {
        this.db     = db;
        this.logger = logger;
        createTable();
    }

    /**
     * Saves a link code for the given player.
     *
     * @param code       the generated link code
     * @param playerUuid the player's unique identifier
     */
    public void save(String code, UUID playerUuid) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT)) {
            ps.setString(1, code);
            ps.setString(2, playerUuid.toString());
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[DiscordLinkRepository] Failed to save code: " + e.getMessage());
        }
    }

    /**
     * Looks up the player UUID associated with {@code code}, or empty if not found.
     *
     * @param code the link code
     * @return the player UUID if the code exists, or empty
     */
    public Optional<UUID> findByCode(String code) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(FIND_BY_CODE)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(UUID.fromString(rs.getString("player_uuid")));
                }
            }
        } catch (SQLException e) {
            logger.severe("[DiscordLinkRepository] Failed to find code: " + e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Deletes a link code after it has been consumed.
     *
     * @param code the link code to remove
     */
    public void deleteByCode(String code) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_CODE)) {
            ps.setString(1, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[DiscordLinkRepository] Failed to delete code: " + e.getMessage());
        }
    }

    /**
     * Deletes all codes older than {@code maxAgeMillis} milliseconds.
     * Should be called on module enable to clear stale codes.
     *
     * @param maxAgeMillis maximum age in milliseconds
     */
    public void deleteExpired(long maxAgeMillis) {
        long cutoff = System.currentTimeMillis() - maxAgeMillis;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_EXPIRED)) {
            ps.setLong(1, cutoff);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                logger.info("[DiscordLinkRepository] Purged " + deleted + " expired link code(s).");
            }
        } catch (SQLException e) {
            logger.severe("[DiscordLinkRepository] Failed to purge expired codes: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private void createTable() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(CREATE_TABLE)) {
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.severe("[DiscordLinkRepository] Failed to create table: " + e.getMessage());
        }
    }
}
