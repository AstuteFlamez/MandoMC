package net.mandomc.mechanics.gambling.lottery;

import com.google.gson.*;
import net.mandomc.MandoMC;

import java.io.*;
import java.util.*;

/**
 * Handles persistence of lottery data.
 *
 * Stores and loads the current pot and player tickets
 * to a JSON file located in the gambling folder.
 */
public class LotteryStorage {

    private static File file;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Initializes the storage system.
     *
     * Creates the gambling folder and lottery.json file if they do not exist.
     */
    public static void setup() {

        File folder = new File(MandoMC.getInstance().getDataFolder(), "gambling");

        if (!folder.exists() && !folder.mkdirs()) {
            System.err.println("[LotteryStorage] Failed to create gambling folder.");
            return;
        }

        file = new File(folder, "lottery.json");

        if (!file.exists()) {
            createDefaultFile();
        }
    }

    /**
     * Saves the current lottery state to disk.
     *
     * Includes total pot and all player tickets.
     */
    public static void save() {

        if (file == null) {
            System.err.println("[LotteryStorage] File not initialized. Did you call setup()?");
            return;
        }

        try (Writer writer = new FileWriter(file)) {

            Map<String, Object> data = new HashMap<>();
            data.put("pot", LotteryManager.getPot());
            data.put("tickets", LotteryManager.getAllTickets());

            GSON.toJson(data, writer);

        } catch (IOException e) {
            System.err.println("[LotteryStorage] Failed to save lottery data.");
            e.printStackTrace();
        }
    }

    /**
     * Loads lottery data from disk.
     *
     * Restores pot and ticket distribution.
     */
    public static void load() {

        if (file == null || !file.exists()) {
            return;
        }

        try (Reader reader = new FileReader(file)) {

            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            if (json == null) return;

            double pot = json.has("pot") ? json.get("pot").getAsDouble() : 0;

            Map<UUID, Integer> tickets = new HashMap<>();

            if (json.has("tickets")) {
                JsonObject ticketObject = json.getAsJsonObject("tickets");

                for (String key : ticketObject.keySet()) {
                    try {
                        UUID uuid = UUID.fromString(key);
                        int amount = ticketObject.get(key).getAsInt();
                        tickets.put(uuid, amount);
                    } catch (Exception ignored) {
                        // Ignore invalid UUIDs or malformed entries
                    }
                }
            }

            LotteryManager.loadData(pot, tickets);

        } catch (IOException e) {
            System.err.println("[LotteryStorage] Failed to load lottery data.");
            e.printStackTrace();
        }
    }

    /**
     * Creates a default lottery file with empty data.
     */
    private static void createDefaultFile() {
        try {
            if (file.createNewFile()) {
                save();
            }
        } catch (IOException e) {
            System.err.println("[LotteryStorage] Failed to create lottery.json.");
            e.printStackTrace();
        }
    }
}