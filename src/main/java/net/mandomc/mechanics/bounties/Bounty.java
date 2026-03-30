package net.mandomc.mechanics.bounties;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;

/**
 * Represents a bounty on a target player.
 *
 * Stores all contributor entries, tracks the total reward,
 * and records the target's last known location.
 */
public class Bounty {

    private final UUID target;
    private final Map<UUID, BountyEntry> entries = new HashMap<>();
    private Location lastKnownLocation;
    private long lastSeen;

    /**
     * Creates a new bounty for the given target.
     *
     * @param target the UUID of the target player
     */
    public Bounty(UUID target) {
        this.target = target;
    }

    /**
     * Returns the UUID of the bounty target.
     *
     * @return the target UUID
     */
    public UUID getTarget() {
        return target;
    }

    /**
     * Returns all bounty entries keyed by placer UUID.
     *
     * @return the entry map
     */
    public Map<UUID, BountyEntry> getEntries() {
        return entries;
    }

    /**
     * Adds or updates a bounty entry for the given placer.
     *
     * @param placer the UUID of the player placing the bounty
     * @param amount the bounty amount
     */
    public void addEntry(UUID placer, double amount) {
        entries.put(placer, new BountyEntry(placer, amount));
    }

    /**
     * Removes the bounty entry for the given placer.
     *
     * @param placer the UUID of the player who placed the entry
     */
    public void removeEntry(UUID placer) {
        entries.remove(placer);
    }

    /**
     * Returns true if the given player has placed a bounty on this target.
     *
     * @param placer the UUID to check
     * @return true if an entry exists for this placer
     */
    public boolean hasEntry(UUID placer) {
        return entries.containsKey(placer);
    }

    /**
     * Returns the sum of all bounty entries.
     *
     * @return the total bounty amount
     */
    public double getTotal() {
        return entries.values().stream().mapToDouble(BountyEntry::getAmount).sum();
    }

    /**
     * Returns the target's last known location, or null if not yet tracked.
     *
     * @return the last known location
     */
    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }

    /**
     * Sets the target's last known location.
     *
     * @param loc the location to record
     */
    public void setLastKnownLocation(Location loc) {
        this.lastKnownLocation = loc == null ? null : loc.clone();
    }

    /**
     * Returns the epoch millis when the target was last seen.
     *
     * @return the last seen timestamp
     */
    public long getLastSeen() {
        return lastSeen;
    }

    /**
     * Sets the epoch millis when the target was last seen.
     *
     * @param lastSeen the timestamp to record
     */
    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    /**
     * Updates the latest tracking snapshot for this bounty target.
     *
     * @param loc current player location
     * @param seenAt epoch millis timestamp
     */
    public void updateTracking(Location loc, long seenAt) {
        setLastKnownLocation(loc);
        setLastSeen(seenAt);
    }

    /**
     * Returns true if this bounty has no contributor entries.
     *
     * @return true if empty
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
