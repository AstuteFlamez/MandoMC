package net.mandomc.world.ilum.storage;

import java.util.UUID;

/**
 * Immutable record of a player's best parkour completion time.
 */
public final class ParkourTime {

    private final UUID playerUuid;
    private final String playerName;
    private final double bestTime;

    /**
     * Creates a ParkourTime record.
     *
     * @param playerUuid the player's unique identifier
     * @param playerName the player's display name at time of record
     * @param bestTime   best completion time in seconds
     */
    public ParkourTime(UUID playerUuid, String playerName, double bestTime) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.bestTime   = bestTime;
    }

    public UUID   getPlayerUuid() { return playerUuid; }
    public String getPlayerName() { return playerName; }
    public double getBestTime()   { return bestTime; }

    /**
     * Returns a new {@code ParkourTime} with the given display name
     * (used when a player reconnects and their name has changed).
     *
     * @param name the updated name
     * @return updated record
     */
    public ParkourTime withName(String name) {
        return new ParkourTime(playerUuid, name, bestTime);
    }
}
