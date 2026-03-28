package net.mandomc.mechanics.bounties;

import org.bukkit.Location;

import java.util.*;

public class Bounty {

    private UUID target;

    private Map<UUID, BountyEntry> entries = new HashMap<>();

    private Location lastKnownLocation;
    private long lastSeen;

    public Bounty(UUID target) {
        this.target = target;
    }

    public UUID getTarget() {
        return target;
    }

    public Map<UUID, BountyEntry> getEntries() {
        return entries;
    }

    public void addEntry(UUID placer, double amount) {
        entries.put(placer, new BountyEntry(placer, amount));
    }

    public void removeEntry(UUID placer) {
        entries.remove(placer);
    }

    public boolean hasEntry(UUID placer) {
        return entries.containsKey(placer);
    }

    public double getTotal() {
        return entries.values().stream().mapToDouble(BountyEntry::getAmount).sum();
    }

    public Location getLastKnownLocation() {
        return lastKnownLocation;
    }

    public void setLastKnownLocation(Location loc) {
        this.lastKnownLocation = loc;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}