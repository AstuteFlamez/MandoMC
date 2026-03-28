package net.mandomc.mechanics.bounties;

import com.google.gson.*;

import java.io.*;
import java.nio.file.Files;
import java.util.*;

public class BountyStorage {

    private static final Map<UUID, Bounty> bounties = new HashMap<>();
    private static File file;

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

    public static void load() {
        try {
            String json = new String(Files.readAllBytes(file.toPath()));
            if (json.isEmpty()) return;

            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

            for (String key : obj.keySet()) {
                UUID target = UUID.fromString(key);
                JsonObject bountyObj = obj.getAsJsonObject(key);

                Bounty bounty = new Bounty(target);

                JsonObject entries = bountyObj.getAsJsonObject("entries");

                for (String p : entries.keySet()) {
                    UUID placer = UUID.fromString(p);
                    double amount = entries.get(p).getAsDouble();

                    bounty.addEntry(placer, amount);
                }

                bounties.put(target, bounty);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

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

                root.add(bounty.getTarget().toString(), obj);
            }

            Files.write(file.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Bounty get(UUID target) {
        return bounties.get(target);
    }

    public static Bounty getOrCreate(UUID target) {
        return bounties.computeIfAbsent(target, Bounty::new);
    }

    public static void remove(UUID target) {
        bounties.remove(target);
    }

    public static Collection<Bounty> getAll() {
        return bounties.values();
    }

    public static boolean hasPlaced(UUID placer) {
        return bounties.values().stream().anyMatch(b -> b.hasEntry(placer));
    }

    public static UUID getPlacedTarget(UUID placer) {
        return bounties.values().stream()
                .filter(b -> b.hasEntry(placer))
                .map(Bounty::getTarget)
                .findFirst().orElse(null);
    }
}