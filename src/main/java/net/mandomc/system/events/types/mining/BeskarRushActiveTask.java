package net.mandomc.system.events.types.mining;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ticking task for the Beskar Rush event.
 *
 * Manages active ore spawns, tracks remaining ore count,
 * and notifies the event when ores are mined.
 */
public class BeskarRushActiveTask extends BukkitRunnable {

    private final BeskarRushEvent event;

    private final Map<Location, BeskarOre> ores = new HashMap<>();

    private int remaining;

    public BeskarRushActiveTask(BeskarRushEvent event) {
        this.event = event;

        spawnInitialOres();
    }

    private void spawnInitialOres() {

        List<Location> points = new ArrayList<>(event.getSpawnPoints());

        Collections.shuffle(points);

        int amount = Math.min(event.getStartSpawn(), points.size());

        remaining = amount;

        for (int i = 0; i < amount; i++) {

            Location loc = points.get(i);

            BeskarOre ore = new BeskarOre(event, loc);

            ores.put(loc, ore);

            ore.spawn();
        }

        event.updateBossBar(remaining);
    }

    @Override
    public void run() {
        // reserved for future particles
    }

    public BeskarOre getOre(Location loc) {
        return ores.get(loc);
    }

    public void oreMined(BeskarOre ore) {

        remaining--;

        event.updateBossBar(remaining);
    }

    public void restoreAll() {

        for (BeskarOre ore : ores.values()) {
            ore.restore();
        }

        ores.clear();
    }

    public Collection<BeskarOre> getOres() {
        return ores.values();
    }
}