package com.astuteflamez.mandomc.system.planets.ilum.managers;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.*;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ParkourTimeManager {

    private final Map<UUID, PlayerTime> bestTimes = new HashMap<>();

    private final File file;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ParkourTimeManager(Plugin plugin) {

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        file = new File(plugin.getDataFolder(), "parkour_times.json");

        load();
    }

    public void load() {

        try {

            if (!file.exists()) {
                file.createNewFile();
                save();
                return;
            }

            try (FileReader reader = new FileReader(file)) {

                PlayerTimeData data = gson.fromJson(reader, PlayerTimeData.class);

                if (data != null && data.players != null) {

                    for (PlayerTime time : data.players) {
                        bestTimes.put(UUID.fromString(time.uuid), time);
                    }

                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {

        try {

            PlayerTimeData data = new PlayerTimeData();

            data.players = new ArrayList<>(bestTimes.values());

            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(data, writer);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void updateTime(Player player, double time) {

        UUID uuid = player.getUniqueId();

        PlayerTime existing = bestTimes.get(uuid);

        if (existing == null || time < existing.best_time) {

            PlayerTime newTime = new PlayerTime();

            newTime.uuid = uuid.toString();
            newTime.name = player.getName();
            newTime.best_time = time;

            bestTimes.put(uuid, newTime);

            save();

        } else {
            existing.name = player.getName();
        }
    }

    public List<PlayerTime> getTop(int limit) {

        List<PlayerTime> list = new ArrayList<>(bestTimes.values());

        list.sort(Comparator.comparingDouble(t -> t.best_time));

        if (list.size() > limit) {
            return new ArrayList<>(list.subList(0, limit));
        }

        return list;
    }

    public Double getBestTime(UUID uuid) {

        PlayerTime time = bestTimes.get(uuid);

        if (time == null) return null;

        return time.best_time;
    }

    public static class PlayerTime {

        public String uuid;
        public String name;
        public double best_time;

    }

    public static class PlayerTimeData {

        public List<PlayerTime> players;

    }

}