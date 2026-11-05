package net.mandomc.world.tatooine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import net.mandomc.core.LangManager;
import net.mandomc.core.config.MainConfig;

import net.mandomc.MandoMC;
import net.mandomc.server.items.ItemRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.bukkit.scheduler.BukkitTask;

/**
 * Handles the Tatooine decorated-pot loot system.
 *
 * A fixed pool of pot locations is maintained on the map. A limited number
 * are active (visible) at any time. When a player right-clicks a pot, it
 * breaks, drops weighted loot, and respawns at a distant location after a delay.
 */
public class TatooinePotListener implements Listener {

    private final Random random = new Random();
    private final MainConfig mainConfig;

    private final List<Location> allLocations = new ArrayList<>();
    private final Set<Location> activePots = new HashSet<>();
    private final List<String> lootItemIds = new ArrayList<>();
    private final List<Double> cumulativeLootWeights = new ArrayList<>();
    private double totalLootWeight;
    private final Set<BukkitTask> pendingRespawns = new HashSet<>();

    /**
     * Constructs the listener and loads all pot locations.
     */
    public TatooinePotListener(MainConfig mainConfig) {
        this.mainConfig = mainConfig;
        loadLocations();
        rebuildLootPool();
    }

    /**
     * Spawns the initial set of active pots.
     */
    public void enable() {
        spawnInitialPots();
    }

    /**
     * Removes all active pot blocks and clears the tracking set.
     */
    public void disable() {
        for (BukkitTask task : new HashSet<>(pendingRespawns)) {
            task.cancel();
        }
        pendingRespawns.clear();
        for (Location loc : activePots) {
            loc.getBlock().setType(Material.AIR);
        }
        activePots.clear();
    }

    private void spawnInitialPots() {
        Collections.shuffle(allLocations);

        int maxActive = mainConfig.getTatooineMaxActivePots();
        for (int i = 0; i < Math.min(maxActive, allLocations.size()); i++) {
            spawnPot(allLocations.get(i));
        }
    }

    private void spawnPot(Location loc) {
        Block block = loc.getBlock();
        block.setType(Material.DECORATED_POT);
        activePots.add(loc);
    }

    private void respawnPotDelayed(Location oldLoc) {

        activePots.remove(oldLoc);

        final BukkitTask[] scheduled = new BukkitTask[1];
        BukkitTask task = Bukkit.getScheduler().runTaskLater(MandoMC.getInstance(), () -> {
            pendingRespawns.remove(scheduled[0]);
            if (!MandoMC.getInstance().isEnabled()) {
                return;
            }

            List<Location> available = new ArrayList<>(allLocations);
            available.removeAll(activePots);

            int minDistance = mainConfig.getTatooineMinRespawnDistanceBlocks();
            int minDistanceSquared = minDistance * minDistance;
            available.removeIf(loc -> loc.distanceSquared(oldLoc) < minDistanceSquared);

            // fallback if nothing far enough
            if (available.isEmpty()) {
                available = new ArrayList<>(allLocations);
                available.removeAll(activePots);
            }

            if (available.isEmpty()) return;

            Location newLoc = available.get(random.nextInt(available.size()));
            spawnPot(newLoc);

        }, mainConfig.getTatooineRespawnDelayTicks());
        scheduled[0] = task;
        pendingRespawns.add(task);
    }

    /**
     * Handles a player right-clicking a decorated pot.
     *
     * Cancels the event, plays effects, gives loot, removes the block,
     * and schedules a respawn at a new location.
     *
     * @param event the player interact event
     */
    @EventHandler
    public void onClick(PlayerInteractEvent event) {

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (block.getType() != Material.DECORATED_POT) return;

        Location loc = block.getLocation();

        if (!activePots.contains(loc)) return;

        event.setCancelled(true);

        Player player = event.getPlayer();

        // effects
        loc.getWorld().spawnParticle(
                Particle.CLOUD,
                loc.clone().add(0.5, 1, 0.5),
                30,
                0.3, 0.3, 0.3,
                0.05
        );

        loc.getWorld().playSound(
                loc,
                Sound.BLOCK_DECORATED_POT_BREAK,
                1f,
                1f
        );

        // loot
        if (!lootItemIds.isEmpty()) {
            int amount = 1 + random.nextInt(2);

            for (int i = 0; i < amount; i++) {
                String id = pickWeighted();
                if (id == null) {
                    continue;
                }
                ItemStack item = ItemRegistry.get(id);

                if (item != null) {
                    player.getInventory().addItem(item);
                }
            }
        }

        player.sendMessage(LangManager.get("tatooine.pot-rummage"));

        block.setType(Material.AIR);
        respawnPotDelayed(loc);
    }

    /**
     * Picks a random item ID from the list using rarity-based weights.
     *
     * @param items the list of candidate item IDs
     * @return the selected item ID
     */
    private String pickWeighted() {
        if (lootItemIds.isEmpty() || totalLootWeight <= 0D) {
            return null;
        }

        double roll = random.nextDouble(totalLootWeight);
        for (int i = 0; i < cumulativeLootWeights.size(); i++) {
            if (roll <= cumulativeLootWeights.get(i)) {
                return lootItemIds.get(i);
            }
        }

        return lootItemIds.get(lootItemIds.size() - 1);
    }

    private void rebuildLootPool() {
        lootItemIds.clear();
        cumulativeLootWeights.clear();
        totalLootWeight = 0D;

        for (String id : ItemRegistry.getItemIds()) {
            if (!ItemRegistry.hasTag(id, "TATOOINEPOTS")) {
                continue;
            }
            lootItemIds.add(id);
            totalLootWeight += rarityWeight(ItemRegistry.getRarity(id));
            cumulativeLootWeights.add(totalLootWeight);
        }
    }

    private double rarityWeight(String rarity) {
        if (rarity == null) {
            return 1D;
        }
        return switch (rarity.toLowerCase()) {
            case "common" -> 60D;
            case "uncommon" -> 25D;
            case "rare" -> 10D;
            case "epic" -> 4D;
            case "legendary" -> 1D;
            case "mythic" -> 0.2D;
            default -> 1D;
        };
    }

    private void loadLocations() {
        allLocations.clear();

        World world = Bukkit.getWorld(mainConfig.getTatooinePotWorld());
        if (world == null) return;

        List<String> configured = mainConfig.getTatooinePotLocationStrings();
        for (String encoded : configured) {
            Location parsed = parseLocation(encoded, world);
            if (parsed != null) {
                allLocations.add(parsed);
            } else {
                MandoMC.getInstance().getLogger().warning("[Tatooine] Invalid pot location entry: " + encoded);
            }
        }

        if (!allLocations.isEmpty()) {
            return;
        }

        allLocations.add(new Location(world, 1649, 20, 1598));
        allLocations.add(new Location(world, 1648, 20, 1598));
        allLocations.add(new Location(world, 1647, 20, 1598));

        allLocations.add(new Location(world, 1647, 16, 1626));
        allLocations.add(new Location(world, 1647, 16, 1627));
        allLocations.add(new Location(world, 1653, 16, 1627));

        allLocations.add(new Location(world, 1646, 20, 1629));
        allLocations.add(new Location(world, 1647, 16, 1629));
        allLocations.add(new Location(world, 1648, 16, 1629));
        allLocations.add(new Location(world, 1652, 19, 1629));
        allLocations.add(new Location(world, 1654, 17, 1629));

        allLocations.add(new Location(world, 1650, 16, 1636));
        allLocations.add(new Location(world, 1650, 19, 1636));
        allLocations.add(new Location(world, 1648, 20, 1636));
        allLocations.add(new Location(world, 1647, 17, 1636));

        allLocations.add(new Location(world, 1639, 16, 1666));
        allLocations.add(new Location(world, 1636, 16, 1668));
        allLocations.add(new Location(world, 1638, 16, 1668));
        allLocations.add(new Location(world, 1638, 16, 1669));
        allLocations.add(new Location(world, 1637, 17, 1671));
        allLocations.add(new Location(world, 1637, 17, 1672));
        allLocations.add(new Location(world, 1636, 16, 1672));
        allLocations.add(new Location(world, 1635, 16, 1672));
        allLocations.add(new Location(world, 1635, 16, 1673));
        allLocations.add(new Location(world, 1635, 17, 1674));
        allLocations.add(new Location(world, 1636, 17, 1674));

        allLocations.add(new Location(world, 1629, 16, 1660));

        allLocations.add(new Location(world, 1634, 18, 1691));
        allLocations.add(new Location(world, 1633, 18, 1691));
        allLocations.add(new Location(world, 1632, 18, 1691));

        allLocations.add(new Location(world, 1623, 17, 1689));
        allLocations.add(new Location(world, 1620, 17, 1685));
        allLocations.add(new Location(world, 1619, 17, 1685));
        allLocations.add(new Location(world, 1618, 17, 1685));
        allLocations.add(new Location(world, 1618, 17, 1684));
        allLocations.add(new Location(world, 1619, 17, 1684));
        allLocations.add(new Location(world, 1620, 17, 1684));
        allLocations.add(new Location(world, 1620, 17, 1683));
        allLocations.add(new Location(world, 1619, 17, 1683));
        allLocations.add(new Location(world, 1618, 17, 1683));

        allLocations.add(new Location(world, 1626, 20, 1677));

        allLocations.add(new Location(world, 1619, 18, 1670));
        allLocations.add(new Location(world, 1620, 18, 1670));
        allLocations.add(new Location(world, 1621, 18, 1670));

        allLocations.add(new Location(world, 1633, 17, 1671));

        allLocations.add(new Location(world, 1675, 22, 1688));
        allLocations.add(new Location(world, 1677, 21, 1687));
        allLocations.add(new Location(world, 1677, 22, 1681));
        allLocations.add(new Location(world, 1676, 22, 1680));

        // (trimmed for readability --- continue same pattern)
    }

    private Location parseLocation(String encoded, World world) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        String[] split = encoded.split(",");
        if (split.length != 3) {
            return null;
        }
        try {
            double x = Double.parseDouble(split[0].trim());
            double y = Double.parseDouble(split[1].trim());
            double z = Double.parseDouble(split[2].trim());
            return new Location(world, x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
