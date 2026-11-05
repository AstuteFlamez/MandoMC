package net.mandomc.gameplay.bounty.storage;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mandomc.core.storage.JsonRepository;
import net.mandomc.gameplay.bounty.model.Bounty;
import net.mandomc.gameplay.bounty.model.BountyEntry;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * JSON-backed repository for {@link Bounty} entities.
 *
 * Replaces the static {@link net.mandomc.gameplay.bounty.BountyStorage}:
 * all data is held in an in-memory map protected by a read/write lock, and
 * serialised to {@code bounties/bounties.json} on demand.
 */
public class BountyRepository extends JsonRepository<Bounty, UUID> {

    /**
     * Creates the bounty repository.
     *
     * @param plugin the plugin owning the data folder
     */
    public BountyRepository(Plugin plugin) {
        super(plugin, "bounties/bounties.json");
    }

    // -----------------------------------------------------------------------
    // JsonRepository template methods
    // -----------------------------------------------------------------------

    @Override
    protected void populate(String json, Map<UUID, Bounty> target) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        for (String key : root.keySet()) {
            UUID targetUuid = UUID.fromString(key);
            JsonObject bountyObj = root.getAsJsonObject(key);

            Bounty bounty = new Bounty(targetUuid);

            JsonObject entries = bountyObj.getAsJsonObject("entries");
            if (entries != null) {
                for (String placerKey : entries.keySet()) {
                    UUID placer = UUID.fromString(placerKey);
                    double amount = entries.get(placerKey).getAsDouble();
                    bounty.addEntry(placer, amount);
                }
            }

            readTracking(bountyObj, bounty);
            target.put(targetUuid, bounty);
        }
    }

    @Override
    protected String serialize(Map<UUID, Bounty> data) {
        JsonObject root = new JsonObject();

        for (Bounty bounty : data.values()) {
            JsonObject obj = new JsonObject();

            JsonObject entries = new JsonObject();
            for (BountyEntry entry : bounty.getEntries().values()) {
                entries.addProperty(entry.getPlacer().toString(), entry.getAmount());
            }
            obj.add("entries", entries);

            writeTracking(obj, bounty);
            root.add(bounty.getTarget().toString(), obj);
        }

        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    @Override
    protected UUID idOf(Bounty entity) {
        return entity.getTarget();
    }

    // -----------------------------------------------------------------------
    // Domain-specific helpers
    // -----------------------------------------------------------------------

    /**
     * Returns the bounty for {@code target}, creating an empty one if none exists.
     *
     * @param target the target player UUID
     * @return existing or newly created Bounty
     */
    public Bounty getOrCreate(UUID target) {
        return getOrCreate(target, Bounty::new);
    }

    /**
     * Returns true if the given placer has an active bounty entry on any target.
     *
     * @param placer the player who placed the bounty
     * @return true if a bounty entry exists
     */
    public boolean hasPlacedBounty(UUID placer) {
        Collection<Bounty> all = findAll();
        for (Bounty bounty : all) {
            if (bounty.hasEntry(placer)) return true;
        }
        return false;
    }

    /**
     * Returns the target UUID that has a bounty entry from {@code placer},
     * or {@code null} if none exists.
     *
     * @param placer the bounty placer
     * @return the target UUID or {@code null}
     */
    public UUID getPlacedTarget(UUID placer) {
        for (Bounty bounty : findAll()) {
            if (bounty.hasEntry(placer)) return bounty.getTarget();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Tracking snapshot helpers (ported from BountyStorage)
    // -----------------------------------------------------------------------

    private static void writeTracking(JsonObject obj, Bounty bounty) {
        Location loc = bounty.getLastKnownLocation();
        if (loc == null || loc.getWorld() == null) {
            if (bounty.getLastSeen() > 0) {
                obj.addProperty("lastSeen", bounty.getLastSeen());
            }
            return;
        }

        JsonObject tracking = new JsonObject();
        tracking.addProperty("world", loc.getWorld().getName());
        tracking.addProperty("x", loc.getX());
        tracking.addProperty("y", loc.getY());
        tracking.addProperty("z", loc.getZ());
        tracking.addProperty("yaw", loc.getYaw());
        tracking.addProperty("pitch", loc.getPitch());
        tracking.addProperty("lastSeen", bounty.getLastSeen());
        obj.add("tracking", tracking);
    }

    private static void readTracking(JsonObject bountyObj, Bounty bounty) {
        JsonElement trackingElement = bountyObj.get("tracking");
        if (trackingElement != null && trackingElement.isJsonObject()) {
            JsonObject tracking = trackingElement.getAsJsonObject();

            World world = null;
            if (tracking.has("world")) {
                world = Bukkit.getWorld(tracking.get("world").getAsString());
            }

            if (world != null && tracking.has("x") && tracking.has("y") && tracking.has("z")) {
                float yaw   = tracking.has("yaw")   ? tracking.get("yaw").getAsFloat()   : 0f;
                float pitch = tracking.has("pitch") ? tracking.get("pitch").getAsFloat() : 0f;
                bounty.setLastKnownLocation(new Location(
                        world,
                        tracking.get("x").getAsDouble(),
                        tracking.get("y").getAsDouble(),
                        tracking.get("z").getAsDouble(),
                        yaw, pitch
                ));
            }

            if (tracking.has("lastSeen")) {
                bounty.setLastSeen(tracking.get("lastSeen").getAsLong());
            }
            return;
        }

        if (bountyObj.has("lastSeen")) {
            bounty.setLastSeen(bountyObj.get("lastSeen").getAsLong());
        }
    }
}
