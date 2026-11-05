package net.mandomc.gameplay.bounty;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mandomc.gameplay.bounty.model.Bounty;
import net.mandomc.gameplay.bounty.storage.BountyRepository;

import java.io.File;
import java.nio.file.Files;
import java.util.Collection;
import java.util.UUID;

/**
 * Legacy static facade for bounty persistence APIs.
 *
 * All runtime operations delegate to {@link BountyRepository} so bounty state
 * has one source of truth. Legacy file support exists only for one-way startup
 * migration from older installs.
 */
public final class BountyStorage {

    private static final String LEGACY_FILE = "bounty.json";

    private static BountyRepository repository;
    private static File legacyFile;

    private BountyStorage() {
    }

    public static void setup(File dataFolder, BountyRepository repo) {
        repository = repo;
        File dir = new File(dataFolder, "bounties");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        legacyFile = new File(dir, LEGACY_FILE);
    }

    /**
     * @deprecated temporary compatibility for existing callsites.
     */
    @Deprecated
    public static void setup(File dataFolder) {
        setup(dataFolder, repository);
    }

    /**
     * Performs one-way migration from legacy bounties/bounty.json if present.
     */
    public static void load() {
        if (repository == null || legacyFile == null || !legacyFile.exists()) {
            return;
        }
        if (!repository.findAll().isEmpty()) {
            return;
        }

        try {
            String json = Files.readString(legacyFile.toPath());
            if (json.isBlank()) {
                return;
            }
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            for (String key : root.keySet()) {
                UUID targetId = UUID.fromString(key);
                JsonObject bountyObj = root.getAsJsonObject(key);
                Bounty bounty = new Bounty(targetId);

                JsonObject entries = bountyObj.getAsJsonObject("entries");
                if (entries != null) {
                    for (String placerKey : entries.keySet()) {
                        bounty.addEntry(UUID.fromString(placerKey), entries.get(placerKey).getAsDouble());
                    }
                }

                if (bountyObj.has("lastSeen")) {
                    bounty.setLastSeen(bountyObj.get("lastSeen").getAsLong());
                }
                repository.save(bounty);
            }
            repository.flush();
        } catch (Exception ignored) {
            // Keep server boot resilient; malformed legacy data should not crash enable.
        }
    }

    public static void save() {
        if (repository != null) {
            repository.flush();
        }
    }

    public static Bounty get(UUID target) {
        return repository == null ? null : repository.findById(target).orElse(null);
    }

    public static Bounty getOrCreate(UUID target) {
        if (repository == null) {
            throw new IllegalStateException("Bounty repository not configured");
        }
        return repository.getOrCreate(target);
    }

    public static void remove(UUID target) {
        if (repository != null) {
            repository.delete(target);
        }
    }

    public static Collection<Bounty> getAll() {
        return repository == null ? java.util.List.of() : repository.findAll();
    }

    public static boolean hasPlaced(UUID placer) {
        return repository != null && repository.hasPlacedBounty(placer);
    }

    public static UUID getPlacedTarget(UUID placer) {
        return repository == null ? null : repository.getPlacedTarget(placer);
    }
}
