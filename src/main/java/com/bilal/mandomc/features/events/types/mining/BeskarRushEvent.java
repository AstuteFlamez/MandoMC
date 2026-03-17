package com.bilal.mandomc.features.events.types.mining;

import com.bilal.mandomc.MandoMC;
import com.bilal.mandomc.features.events.AbstractGameEvent;
import com.bilal.mandomc.features.events.EventDefinition;
import com.bilal.mandomc.features.events.EventManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class BeskarRushEvent extends AbstractGameEvent {

    private final EventDefinition definition;

    private BeskarRushActiveTask task;

    private final List<Location> spawnPoints = new ArrayList<>();

    private BossBar bossBar;

    private int dropMin;
    private int dropMax;

    public BeskarRushEvent(EventDefinition definition) {
        super(definition.getId(), definition.getDisplayName());
        this.definition = definition;
    }

    @Override
    protected void onStart(EventManager manager) {

        loadSpawnPoints();

        dropMin = intSetting("drops.min", 1);
        dropMax = intSetting("drops.max", 1);

        bossBar = Bukkit.createBossBar(
                "§6Beskar Remaining",
                BarColor.YELLOW,
                BarStyle.SOLID
        );

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().getName().equalsIgnoreCase(worldSetting())) {
                bossBar.addPlayer(player);
            }
        }

        task = new BeskarRushActiveTask(this);
        task.runTaskTimer(MandoMC.getInstance(), 0L, 20L);

        Bukkit.broadcastMessage("§6§lBESKAR RUSH §7has begun!");
    }

    @Override
    protected void onEnd(EventManager manager) {

        if (task != null) {
            task.restoreAll();
            task.cancel();
            task = null;
        }

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        Bukkit.broadcastMessage("§6§lBESKAR RUSH §7has ended.");
    }

    private void loadSpawnPoints() {

        spawnPoints.clear();

        Object value = definition.getSetting("spawn-points");

        if (!(value instanceof List<?> list)) return;

        for (Object obj : list) {

            String[] split = String.valueOf(obj).split(",");

            if (split.length != 4) continue;

            spawnPoints.add(new Location(
                    Bukkit.getWorld(split[0]),
                    Double.parseDouble(split[1]),
                    Double.parseDouble(split[2]),
                    Double.parseDouble(split[3])
            ));
        }
    }

    public void updateBossBar(int remaining) {

        if (bossBar == null) return;

        double progress = Math.max(0.0,
                Math.min(1.0, (double) remaining / getStartSpawn()));

        bossBar.setProgress(progress);
        bossBar.setTitle("§6Beskar Remaining: §e" + remaining);
    }

    public List<Location> getSpawnPoints() {
        return spawnPoints;
    }

    public int getStartSpawn() {

        Object value = definition.getSetting("ores.start-spawn");

        if (value instanceof Number number) return number.intValue();

        return 10;
    }

    public int getDropMin() {
        return dropMin;
    }

    public int getDropMax() {
        return dropMax;
    }

    public BeskarRushActiveTask getActiveTask() {
        return task;
    }

    private int intSetting(String key, int def) {

        Object value = definition.getSetting(key);

        if (value instanceof Number number) {
            return number.intValue();
        }

        return def;
    }

    private String worldSetting() {

        Object value = definition.getSetting("world");

        if (value != null) return String.valueOf(value);

        return "world";
    }
}