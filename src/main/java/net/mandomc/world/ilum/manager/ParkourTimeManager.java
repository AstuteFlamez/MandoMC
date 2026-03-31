package net.mandomc.world.ilum.manager;

import net.mandomc.world.ilum.storage.ParkourTime;
import net.mandomc.world.ilum.storage.ParkourTimeRepository;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Coordinates parkour personal-best record logic.
 *
 * Delegates all persistence to {@link ParkourTimeRepository}. The
 * {@link PlayerTime} inner class is retained for backward compatibility
 * with {@link ParkourLeaderboardManager}.
 */
public class ParkourTimeManager {

    private final ParkourTimeRepository repository;

    /**
     * Creates the manager backed by the provided repository.
     *
     * @param repository the parkour time repository (already loaded)
     */
    public ParkourTimeManager(ParkourTimeRepository repository) {
        this.repository = repository;
    }

    /**
     * Records a new personal best if {@code time} is faster than any existing record.
     *
     * @param player the player who completed the course
     * @param time   completion time in seconds
     */
    public void updateTime(Player player, double time) {
        boolean updated = repository.updateIfFaster(player.getUniqueId(), player.getName(), time);
        if (updated) {
            repository.flushSoon(40L);
        }
    }

    /**
     * Returns the top {@code limit} times ordered fastest first,
     * converted to the legacy {@link PlayerTime} format.
     *
     * @param limit maximum number of entries to return
     * @return sorted list of player times
     */
    public List<PlayerTime> getTop(int limit) {
        return repository.getTopTimes(limit).stream()
                .map(ParkourTimeManager::toPlayerTime)
                .collect(Collectors.toList());
    }

    /**
     * Returns the best time for the given player, or {@code null} if none recorded.
     *
     * @param uuid the player UUID
     * @return best time in seconds, or {@code null}
     */
    public Double getBestTime(UUID uuid) {
        return repository.findById(uuid)
                .map(ParkourTime::getBestTime)
                .orElse(null);
    }

    // -----------------------------------------------------------------------
    // Internal conversion
    // -----------------------------------------------------------------------

    private static PlayerTime toPlayerTime(ParkourTime pt) {
        PlayerTime t = new PlayerTime();
        t.uuid      = pt.getPlayerUuid().toString();
        t.name      = pt.getPlayerName();
        t.best_time = pt.getBestTime();
        return t;
    }

    // -----------------------------------------------------------------------
    // Legacy inner class (kept for ParkourLeaderboardManager compatibility)
    // -----------------------------------------------------------------------

    public static class PlayerTime {
        public String uuid;
        public String name;
        public double best_time;
    }
}
