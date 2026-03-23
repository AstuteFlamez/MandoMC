package com.astuteflamez.mandomc.features.events.types.jabba_dungeon;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import com.astuteflamez.mandomc.features.events.AbstractGameEvent;
import com.astuteflamez.mandomc.features.events.EventDefinition;
import com.astuteflamez.mandomc.features.events.EventManager;

import java.util.*;

public class JabbaDungeonEvent extends AbstractGameEvent {

    private final EventDefinition definition;
    private final Random random = new Random();

    private final Map<Integer, List<Location>> roomSpawns = new HashMap<>();
    private final Map<Integer, Location> bossSpawns = new HashMap<>();

    // 🔥 NEW
    private final List<Location> placedChests = new ArrayList<>();
    private final Map<Integer, List<UUID>> aliveMobs = new HashMap<>();

    private JabbaDungeonState state;

    public JabbaDungeonEvent(EventDefinition definition) {
        super(definition.getId(), definition.getDisplayName());
        this.definition = definition;
        setupSpawns();
    }

    @Override
    protected void onStart(EventManager manager) {

        Bukkit.broadcastMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7The dungeon has opened!");

        state = new JabbaDungeonState();
        state.setCurrentRoom(1);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "opendoor 15");

        spawnChests(); // 🔥 NEW
        spawnRoom(1);
    }

    @Override
    protected void onEnd(EventManager manager) {

        Bukkit.broadcastMessage("§7Dungeon closed.");

        // 🔥 REMOVE CHESTS
        cleanupDungeon();
    }

    // =========================
    // 🔥 CHEST SPAWNING
    // =========================
    private void spawnChests() {

        Object raw = definition.getSetting("spawn-points");
        if (!(raw instanceof List<?> list)) return;

        for (Object obj : list) {

            if (!(obj instanceof String s)) continue;

            if (random.nextDouble() > 0.5) continue;

            String[] split = s.split(",");
            if (split.length < 5) continue;

            World world = Bukkit.getWorld(split[0]);
            if (world == null) continue;

            Location loc = new Location(
                    world,
                    Integer.parseInt(split[1]),
                    Integer.parseInt(split[2]),
                    Integer.parseInt(split[3])
            );

            Block block = loc.getBlock();
            block.setType(Material.CHEST, false);

            BlockData data = block.getBlockData();

            if (data instanceof Directional dir) {
                try {
                    BlockFace face = BlockFace.valueOf(split[4].toUpperCase());
                    if (dir.getFaces().contains(face)) {
                        dir.setFacing(face);
                        block.setBlockData(dir, false);
                    }
                } catch (Exception ignored) {}
            }

            placedChests.add(loc);
        }
    }

    // =========================
    // 🔥 ROOM ADVANCE
    // =========================
    public void advanceRoom() {

        int current = state.getCurrentRoom();
        current++;

        state.setCurrentRoom(current);
        state.resetRoom();

        Bukkit.broadcastMessage("§7Entering Room " + current);

        if (current == 4 || current == 7 || current == 9) {
            spawnBoss(current);
        } else {
            spawnRoom(current);
        }
    }

    // =========================
    // 🔥 SPAWN ROOM
    // =========================
    private void spawnRoom(int room) {

        List<Location> spawns = roomSpawns.get(room);
        if (spawns == null) return;

        List<UUID> mobIds = new ArrayList<>();

        String[] guards = {
            "GamorreanGuard",
            "QuarrenGuard",
            "TwilekGuard"
        };

        for (Location loc : spawns) {

            for (int i = 0; i < 3; i++) {

                String mobType = guards[random.nextInt(guards.length)];

                ActiveMob mob = MythicBukkit.inst()
                        .getMobManager()
                        .spawnMob(mobType, loc);

                mobIds.add(mob.getEntity().getUniqueId());
            }
        }

        aliveMobs.put(room, mobIds); // 🔥 TRACK
    }

    // =========================
    // 🔥 SPAWN BOSSES
    // =========================
    private void spawnBoss(int room) {

        Location loc = bossSpawns.get(room);
        if (loc == null) return;

        switch (room) {
            case 4 -> MythicBukkit.inst().getMobManager().spawnMob("Bossk", loc);
            case 7 -> MythicBukkit.inst().getMobManager().spawnMob("BobaFett", loc);
            case 9 -> MythicBukkit.inst().getMobManager().spawnMob("Rancor", loc);
        }
    }

    // =========================
    // 🔥 REMOVE MOB (CALLED BY LISTENER)
    // =========================
    public boolean removeMob(int room, UUID uuid) {

        List<UUID> list = aliveMobs.get(room);
        if (list == null) return false;

        list.remove(uuid);

        return list.isEmpty(); // 🔥 LAST MOB?
    }

    public void closeAllDoors() {
        int[] doors = {15,16,17,18,19,20,21,22,23};

        for (int id : doors) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "closedoor " + id);
        }
    }

    private void setupSpawns() {

        roomSpawns.put(1, List.of(loc("Tatooine",1701,102,2201)));

        roomSpawns.put(2, List.of(
            loc("Tatooine",1672,85,2180),
            loc("Tatooine",1686,87,2175),
            loc("Tatooine",1692,78,2180)
        ));

        roomSpawns.put(3, List.of(
            loc("Tatooine",1695,73,2195),
            loc("Tatooine",1697,76,2206),
            loc("Tatooine",1684,80,2208)
        ));

        roomSpawns.put(5, List.of(
            loc("Tatooine",1662,72,2175),
            loc("Tatooine",1665,74,2159),
            loc("Tatooine",1662,72,2144)
        ));

        roomSpawns.put(6, List.of(
            loc("Tatooine",1665,95,2151),
            loc("Tatooine",1665,95,2163),
            loc("Tatooine",1654,99,2157),
            loc("Tatooine",1676,99,2157)
        ));

        roomSpawns.put(8, List.of(
            loc("Tatooine",1680,98,2219),
            loc("Tatooine",1686,98,2216)
        ));

        bossSpawns.put(4, loc("Tatooine",1677,80,2223));
        bossSpawns.put(7, loc("Tatooine",1665,97,2202));
        bossSpawns.put(9, loc("Tatooine",1672,73,2197));
    }

    // =========================
    // 🔥 CLEANUP (CHESTS + DOORS)
    // =========================
    public void cleanupDungeon() {

        // remove chests
        for (Location loc : placedChests) {
            loc.getBlock().setType(Material.AIR);
        }
        placedChests.clear();

        // close doors
        closeAllDoors();
    }

    private Location loc(String world, int x, int y, int z) {
        return new Location(Bukkit.getWorld(world), x, y, z);
    }

    public JabbaDungeonState getState() {
        return state;
    }
}