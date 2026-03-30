package net.mandomc.system.events.types.jabba_dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;

import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
import io.lumine.mythic.core.mobs.DespawnMode;

import net.mandomc.system.events.AbstractGameEvent;
import net.mandomc.system.events.EventDefinition;
import net.mandomc.system.events.EventManager;

/**
 * The Jabba's Dungeon game event.
 *
 * Players advance through 9 rooms by defeating guards and bosses.
 * Each room spawns MythicMobs enemies; killing the last enemy drops a key
 * that unlocks the next door. Boss rooms trigger named boss spawns.
 * Random chests are placed at spawn time and removed on cleanup.
 */
public class JabbaDungeonEvent extends AbstractGameEvent {

    private final EventDefinition definition;
    private final Random random = new Random();

    private final Map<Integer, List<Location>> roomSpawns = new HashMap<>();
    private final Map<Integer, Location> bossSpawns = new HashMap<>();
    private final List<Location> placedChests = new ArrayList<>();
    private final Map<Integer, List<UUID>> aliveMobs = new HashMap<>();

    private JabbaDungeonState state;

    /**
     * Creates the Jabba dungeon event.
     *
     * @param definition the event definition loaded from config
     */
    public JabbaDungeonEvent(EventDefinition definition) {
        super(definition.getId(), definition.getDisplayName());
        this.definition = definition;
        setupSpawns();
    }

    @Override
    protected void onStart(EventManager manager) {
        Bukkit.broadcastMessage("\u00a7e\u00a7l\u1d0d\u1d00\u0274\u1d05\u1d0f\u1d0d\u1d04 \u00a7r\u00a78\u00bb \u00a77The dungeon has opened!");

        state = new JabbaDungeonState();
        state.setCurrentRoom(1);

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "opendoor 15");

        spawnChests();
        spawnRoom(1);
    }

    @Override
    protected void onEnd(EventManager manager) {
        Bukkit.broadcastMessage("\u00a77Dungeon closed.");
        cleanupDungeon();
    }

    /**
     * Randomly places chests at configured spawn-point locations.
     *
     * Each spawn point has a 50% chance of receiving a chest.
     * All chest locations are tracked for cleanup.
     */
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

    /**
     * Advances to the next dungeon room.
     *
     * Increments the room counter, resets room state, and spawns either
     * a boss (rooms 4, 7, 9) or a wave of guards.
     */
    public void advanceRoom() {
        int current = state.getCurrentRoom();
        current++;

        state.setCurrentRoom(current);
        state.resetRoom();

        Bukkit.broadcastMessage("\u00a77Entering Room " + current);

        if (current == 4 || current == 7 || current == 9) {
            spawnBoss(current);
        } else {
            spawnRoom(current);
        }
    }

    /**
     * Spawns a wave of random guards in the given room.
     *
     * Three guards are spawned at each spawn point for the room.
     * All spawned mob UUIDs are tracked for kill detection.
     *
     * @param room the room number to spawn guards in
     */
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
                        .spawnMob(mobType, loc, room);

                mob.setDespawnMode(DespawnMode.PERSISTENT);
                mobIds.add(mob.getEntity().getUniqueId());
            }
        }

        aliveMobs.put(room, mobIds);
    }

    /**
     * Spawns the boss for a given boss room.
     *
     * Room 4 spawns Bossk, room 7 spawns BobaFett, room 9 spawns Rancor.
     *
     * @param room the boss room number
     */
    private void spawnBoss(int room) {
        Location loc = bossSpawns.get(room);
        if (loc == null) return;

        switch (room) {
            case 4 -> MythicBukkit.inst().getMobManager().spawnMob("Bossk", loc);
            case 7 -> MythicBukkit.inst().getMobManager().spawnMob("BobaFett", loc);
            case 9 -> MythicBukkit.inst().getMobManager().spawnMob("Rancor", loc);
        }
    }

    /**
     * Removes a mob from the alive list for the given room.
     *
     * @param room the room the mob was in
     * @param uuid the UUID of the killed mob
     * @return true if this was the last mob in the room
     */
    public boolean removeMob(int room, UUID uuid) {
        List<UUID> list = aliveMobs.get(room);
        if (list == null) return false;

        list.remove(uuid);

        return list.isEmpty();
    }

    /**
     * Dispatches console commands to close all dungeon doors (IDs 15-23).
     */
    public void closeAllDoors() {
        int[] doors = {15, 16, 17, 18, 19, 20, 21, 22, 23};

        for (int id : doors) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "closedoor " + id);
        }
    }

    /**
     * Removes all placed chests, closes all doors, and despawns all dungeon mobs.
     */
    public void cleanupDungeon() {
        for (Location loc : placedChests) {
            loc.getBlock().setType(Material.AIR);
        }
        placedChests.clear();

        closeAllDoors();

        var mobManager = MythicBukkit.inst().getMobManager();

        Set<String> targets = Set.of(
            "Rancor",
            "BobaFett",
            "Bossk",
            "GamorreanGuard",
            "TwilekGuard",
            "QuarrenGuard"
        );

        for (ActiveMob am : mobManager.getActiveMobs()) {
            String type = am.getType().getInternalName();

            if (targets.stream().anyMatch(t -> t.equalsIgnoreCase(type))) {
                am.despawn();
            }
        }
    }

    /**
     * Configures hardcoded room and boss spawn locations.
     */
    private void setupSpawns() {
        roomSpawns.put(1, List.of(loc("Tatooine", 1701, 102, 2201)));

        roomSpawns.put(2, List.of(
            loc("Tatooine", 1672, 85, 2180),
            loc("Tatooine", 1686, 87, 2175),
            loc("Tatooine", 1692, 78, 2180)
        ));

        roomSpawns.put(3, List.of(
            loc("Tatooine", 1695, 73, 2195),
            loc("Tatooine", 1697, 76, 2206),
            loc("Tatooine", 1684, 80, 2208)
        ));

        roomSpawns.put(5, List.of(
            loc("Tatooine", 1662, 72, 2175),
            loc("Tatooine", 1665, 74, 2159),
            loc("Tatooine", 1662, 72, 2144)
        ));

        roomSpawns.put(6, List.of(
            loc("Tatooine", 1665, 95, 2151),
            loc("Tatooine", 1665, 95, 2163),
            loc("Tatooine", 1654, 99, 2157),
            loc("Tatooine", 1676, 99, 2157)
        ));

        roomSpawns.put(8, List.of(
            loc("Tatooine", 1680, 98, 2219),
            loc("Tatooine", 1686, 98, 2216)
        ));

        bossSpawns.put(4, loc("Tatooine", 1677, 80, 2223));
        bossSpawns.put(7, loc("Tatooine", 1665, 97, 2202));
        bossSpawns.put(9, loc("Tatooine", 1672, 73, 2197));
    }

    private Location loc(String world, int x, int y, int z) {
        return new Location(Bukkit.getWorld(world), x, y, z);
    }

    /**
     * Returns the current dungeon state.
     *
     * @return the dungeon state
     */
    public JabbaDungeonState getState() {
        return state;
    }
}
