package net.mandomc.server.discord.service;

import java.util.UUID;

/**
 * Fallback used when the database service is unavailable.
 */
public final class UnavailableLinkService implements LinkService {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public boolean isAlreadyLinked(UUID playerUuid) {
        return false;
    }

    @Override
    public String createPendingLink(UUID playerUuid) {
        throw new IllegalStateException("Link service unavailable");
    }

    @Override
    public boolean isLinked(UUID playerUuid) {
        return false;
    }
}
