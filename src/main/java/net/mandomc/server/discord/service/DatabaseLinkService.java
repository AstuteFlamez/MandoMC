package net.mandomc.server.discord.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

import net.mandomc.core.storage.DatabaseService;

/**
 * SQL-backed implementation of {@link LinkService}.
 */
public final class DatabaseLinkService implements LinkService {

    private static final String LINKED_QUERY =
            "SELECT 1 FROM links WHERE minecraft_uuid = ? AND linked = TRUE LIMIT 1";
    private static final String INSERT_QUERY =
            "INSERT INTO links (minecraft_uuid, link_code) VALUES (?, ?)";
    private static final String POLL_QUERY =
            "SELECT linked FROM links WHERE minecraft_uuid = ? ORDER BY created_at DESC LIMIT 1";

    private final DatabaseService databaseService;
    private final Logger logger;

    public DatabaseLinkService(DatabaseService databaseService, Logger logger) {
        this.databaseService = databaseService;
        this.logger = logger;
    }

    @Override
    public boolean isAvailable() {
        return databaseService != null && databaseService.isRunning();
    }

    @Override
    public boolean isAlreadyLinked(UUID playerUuid) {
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(LINKED_QUERY)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            logger.warning("[Link] Failed to query linked state: " + e.getMessage());
            return false;
        }
    }

    @Override
    public String createPendingLink(UUID playerUuid) {
        String code = generateCode();
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_QUERY)) {
            ps.setString(1, playerUuid.toString());
            ps.setString(2, code);
            ps.executeUpdate();
            return code;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create link code", e);
        }
    }

    @Override
    public boolean isLinked(UUID playerUuid) {
        try (Connection conn = databaseService.getConnection();
             PreparedStatement ps = conn.prepareStatement(POLL_QUERY)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("linked");
            }
        } catch (SQLException e) {
            logger.warning("[Link] Failed to poll link state: " + e.getMessage());
            return false;
        }
    }

    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            int index = ThreadLocalRandom.current().nextInt(chars.length());
            code.append(chars.charAt(index));
        }
        return code.toString();
    }
}
