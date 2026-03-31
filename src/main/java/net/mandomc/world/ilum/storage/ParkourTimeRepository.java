package net.mandomc.world.ilum.storage;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mandomc.core.storage.JsonRepository;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * JSON-backed repository for parkour personal-best records.
 *
 * Reads and writes {@code parkour_times.json} in the plugin data folder using
 * the same structure as the legacy {@link net.mandomc.world.ilum.manager.ParkourTimeManager}
 * so existing data is preserved without migration.
 *
 * JSON structure:
 * <pre>
 * {
 *   "players": [
 *     {"uuid":"...", "name":"...", "best_time": 42.3},
 *     ...
 *   ]
 * }
 * </pre>
 */
public class ParkourTimeRepository extends JsonRepository<ParkourTime, UUID> {

    /**
     * Creates the parkour time repository.
     *
     * @param plugin the plugin owning the data folder
     */
    public ParkourTimeRepository(Plugin plugin) {
        super(plugin, "parkour_times.json");
    }

    // -----------------------------------------------------------------------
    // Domain convenience methods
    // -----------------------------------------------------------------------

    /**
     * Returns the best time for the given player, or {@link Optional#empty()} if none.
     *
     * @param playerUuid the player UUID
     * @return the best time record, if any
     */
    public Optional<ParkourTime> getBestTime(UUID playerUuid) {
        return findById(playerUuid);
    }

    /**
     * Returns the top {@code limit} times ordered by fastest first.
     *
     * @param limit maximum number of records to return
     * @return sorted list, may be shorter than {@code limit}
     */
    public List<ParkourTime> getTopTimes(int limit) {
        List<ParkourTime> sorted = new ArrayList<>(findAll());
        sorted.sort(Comparator.comparingDouble(ParkourTime::getBestTime));
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    /**
     * Saves a new personal best only if {@code time} is strictly faster than
     * the player's current record (or no record exists yet).
     *
     * @param playerUuid the player UUID
     * @param playerName the player's current display name
     * @param time       the completion time in seconds
     * @return true if the record was updated, false if current best stands
     */
    public boolean updateIfFaster(UUID playerUuid, String playerName, double time) {
        Optional<ParkourTime> existing = findById(playerUuid);
        if (existing.isEmpty() || time < existing.get().getBestTime()) {
            save(new ParkourTime(playerUuid, playerName, time));
            return true;
        }
        // Always sync the display name even when time isn't beaten
        existing.ifPresent(pt -> {
            if (!pt.getPlayerName().equals(playerName)) {
                save(pt.withName(playerName));
            }
        });
        return false;
    }

    // -----------------------------------------------------------------------
    // JsonRepository template methods
    // -----------------------------------------------------------------------

    @Override
    protected void populate(String json, Map<UUID, ParkourTime> target) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has("players")) return;

        JsonArray players = root.getAsJsonArray("players");
        for (JsonElement element : players) {
            JsonObject obj = element.getAsJsonObject();
            try {
                UUID uuid    = UUID.fromString(obj.get("uuid").getAsString());
                String name  = obj.has("name") ? obj.get("name").getAsString() : "Unknown";
                double time  = obj.get("best_time").getAsDouble();
                target.put(uuid, new ParkourTime(uuid, name, time));
            } catch (Exception ignored) {
                // skip malformed entries
            }
        }
    }

    @Override
    protected String serialize(Map<UUID, ParkourTime> data) {
        JsonArray players = new JsonArray();
        for (ParkourTime pt : data.values()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("uuid",      pt.getPlayerUuid().toString());
            obj.addProperty("name",      pt.getPlayerName());
            obj.addProperty("best_time", pt.getBestTime());
            players.add(obj);
        }

        JsonObject root = new JsonObject();
        root.add("players", players);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    @Override
    protected UUID idOf(ParkourTime entity) {
        return entity.getPlayerUuid();
    }

    /**
     * Returns all stored records without defensive copy (for read-only iteration).
     * Prefer {@link #findAll()} unless ordering is needed.
     */
    public Collection<ParkourTime> getAll() {
        return findAll();
    }
}
