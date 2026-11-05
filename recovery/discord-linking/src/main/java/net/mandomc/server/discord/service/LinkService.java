package net.mandomc.server.discord.service;

import java.util.UUID;

/**
 * Contract for linking Minecraft accounts to Discord accounts.
 */
public interface LinkService {

    /**
     * Returns true when the service can currently execute link requests.
     */
    boolean isAvailable();

    /**
     * Checks whether the given player is already linked.
     */
    boolean isAlreadyLinked(UUID playerUuid);

    /**
     * Creates a pending link entry and returns the generated code.
     */
    String createPendingLink(UUID playerUuid);

    /**
     * Returns true when the most recent link row for the player is marked linked.
     */
    boolean isLinked(UUID playerUuid);
}
