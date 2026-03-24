package net.mandomc.mechanics.gambling.lottery;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class LotteryConfig {

    private final JavaPlugin plugin;

    private File file;
    private FileConfiguration config;

    public LotteryConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {

        File folder = new File(plugin.getDataFolder(), "gambling");
        if (!folder.exists()) {
            folder.mkdirs();
        }

        file = new File(folder, "lottery.yml");

        if (!file.exists()) {
            plugin.saveResource("gambling/lottery.yml", false);
        }

        config = YamlConfiguration.loadConfiguration(file);
    }

    public void reload() {
        load();
    }

    public FileConfiguration get() {
        return config;
    }

    public double getTicketPrice() {
        return config.getDouble("lottery.ticket.price");
    }

    public int getMaxTicketsPerPlayer() {
        return config.getInt("lottery.ticket.max-per-player");
    }

    public double getStartingPot() {
        return config.getDouble("lottery.pot.starting");
    }

    public boolean isCarryOverEnabled() {
        return config.getBoolean("lottery.pot.carry-over");
    }

    public double getWinnerPercentage() {
        return config.getDouble("lottery.rewards.winner-percentage");
    }

    public double getServerPercentage() {
        return config.getDouble("lottery.rewards.server-percentage");
    }

    public String getDrawDay() {
        return config.getString("lottery.draw.day");
    }

    public int getDrawHour() {
        return config.getInt("lottery.draw.hour");
    }

    public int getDrawMinute() {
        return config.getInt("lottery.draw.minute");
    }

    // =========================
    // 🧱 HOLOGRAM
    // =========================
    public boolean isHologramEnabled() {
        return config.getBoolean("lottery.hologram.enabled");
    }

    public String getHologramWorld() {
        return config.getString("lottery.hologram.location.world");
    }

    public double getHologramX() {
        return config.getDouble("lottery.hologram.location.x");
    }

    public double getHologramY() {
        return config.getDouble("lottery.hologram.location.y");
    }

    public double getHologramZ() {
        return config.getDouble("lottery.hologram.location.z");
    }

    public long getHologramUpdateInterval() {
        return config.getLong("lottery.hologram.update-interval");
    }

    public java.util.List<String> getHologramLines() {
        return config.getStringList("lottery.hologram.lines");
    }

    public String getMessage(String path) {
        return config.getString("lottery.messages." + path, "");
    }

    public boolean isEnabled() {
        return config.getBoolean("lottery.enabled");
    }
}