package net.mandomc.mechanics.bounties;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Persists and retrieves bounty data via a JSON file.
 *
 * Stores all active bounties keyed by target UUID.
 * Supports load and save operations from the plugin data folder.
 */
public class BountyStorage {

    private static final Map<UUID, Bounty> bounties = new HashMap<>();
    private static File file;

    /**
     * Initializes the storage file location.
     *
     * Creates the bounty directory and file if they do not exist.
     *
     * @param dataFolder the plugin data folder
     */
    public static void setup(File dataFolder) {
        File dir = new File(dataFolder, "bounties");
        if (!dir.exists()) dir.mkdirs();

        file = new File(dir, "bounty.json");

        if (!file.exists()) {
            try {
                file.createNewFile();
                save();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Loads all bounty data from the JSON file.
     */
    public static void load() {
        try {
            String json = new String(Files.readAllBytes(file.toPath()));
            if (json.trim().isEmpty()) return;

            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            bounties.clear();

            for (String key : obj.keySet()) {
                UUID target = UUID.fromString(key);
                JsonObject bountyObj = obj.getAsJsonObject(key);

                Bounty bounty = new Bounty(target);
                JsonObject entries = bountyObj.getAsJsonObject("entries");

                for (String placerKey : entries.keySet()) {
                    UUID placer = UUID.fromString(placerKey);
                    double amount = entries.get(placerKey).getAsDouble();
                    bounty.addEntry(placer, amount);
                }

                readTrackingSnapshot(bountyObj, bounty);

                bounties.put(target, bounty);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves all bounty data to the JSON file.
     */
    public static void save() {
        try {
            JsonObject root = new JsonObject();

            for (Bounty bounty : bounties.values()) {
                JsonObject obj = new JsonObject();
                JsonObject entries = new JsonObject();

                for (BountyEntry entry : bounty.getEntries().values()) {
                    entries.addProperty(entry.getPlacer().toString(), entry.getAmount());
                }

                obj.add("entries", entries);
                writeTrackingSnapshot(obj, bounty);
                root.add(bounty.getTarget().toString(), obj);
            }

            Files.write(file.toPath(),
                    new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the bounty for the given target UUID, or null if none exists.
     *
     * @param target the target UUID
     * @return the bounty, or null
     */
    public static Bounty get(UUID target) {
        return bounties.get(target);
    }

    /**
     * Returns the bounty for the given target, creating one if it does not exist.
     *
     * @param target the target UUID
     * @return the existing or new bounty
     */
    public static Bounty getOrCreate(UUID target) {
        return bounties.computeIfAbsent(target, Bounty::new);
    }

    /**
     * Removes the bounty for the given target UUID.
     *
     * @param target the target UUID
     */
    public static void remove(UUID target) {
        bounties.remove(target);
    }

    /**
     * Returns all active bounties.
     *
     * @return collection of all bounties
     */
    public static Collection<Bounty> getAll() {
        return bounties.values();
    }

    /**
     * Returns true if the given player has placed a bounty on any target.
     *
     * @param placer the placer UUID to check
     * @return true if an entry exists
     */
    public static boolean hasPlaced(UUID placer) {
        return bounties.values().stream().anyMatch(b -> b.hasEntry(placer));
    }

    /**
     * Returns the UUID of the target on whom the given player has placed a bounty.
     *
     * @param placer the placer UUID
     * @return the target UUID, or null if no entry exists
     */
    public static UUID getPlacedTarget(UUID placer) {
        return bounties.values().stream()
                .filter(b -> b.hasEntry(placer))
                .map(Bounty::getTarget)
                .findFirst()
                .orElse(null);
    }

    private static void writeTrackingSnapshot(JsonObject obj, Bounty bounty) {
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

    private static void readTrackingSnapshot(JsonObject bountyObj, Bounty bounty) {
        JsonElement trackingElement = bountyObj.get("tracking");
        if (trackingElement != null && trackingElement.isJsonObject()) {
            JsonObject tracking = trackingElement.getAsJsonObject();

            World world = null;
            if (tracking.has("world")) {
                world = Bukkit.getWorld(tracking.get("world").getAsString());
            }

            if (world != null && tracking.has("x") && tracking.has("y") && tracking.has("z")) {
                float yaw = tracking.has("yaw") ? tracking.get("yaw").getAsFloat() : 0f;
                float pitch = tracking.has("pitch") ? tracking.get("pitch").getAsFloat() : 0f;
                Location location = new Location(
                        world,
                        tracking.get("x").getAsDouble(),
                        tracking.get("y").getAsDouble(),
                        tracking.get("z").getAsDouble(),
                        yaw,
                        pitch
                );
                bounty.setLastKnownLocation(location);
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
