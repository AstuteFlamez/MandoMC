package com.astuteflamez.mandomc.features.persistentevents;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.features.items.ItemRegistry;

import java.util.*;

public class TatooinePotListener implements Listener {

    private final Random random = new Random();

    private final List<Location> allLocations = new ArrayList<>();
    private final Set<Location> activePots = new HashSet<>();

    private final int MAX_ACTIVE = 10;

    public TatooinePotListener() {
        loadLocations();
    }

    // =========================
    // INIT
    // =========================

    public void enable() {
        spawnInitialPots();
    }

    public void disable() {
        for (Location loc : activePots) {
            loc.getBlock().setType(Material.AIR);
        }
        activePots.clear();
    }

    private void spawnInitialPots() {
        Collections.shuffle(allLocations);

        for (int i = 0; i < Math.min(MAX_ACTIVE, allLocations.size()); i++) {
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

        Bukkit.getScheduler().runTaskLater(MandoMC.getInstance(), () -> {

            List<Location> available = new ArrayList<>(allLocations);
            available.removeAll(activePots);

            // 🔥 filter for distance (far away)
            available.removeIf(loc -> loc.distanceSquared(oldLoc) < 400); 
            // 400 = 20 blocks (20^2)

            // fallback if nothing far enough
            if (available.isEmpty()) {
                available = new ArrayList<>(allLocations);
                available.removeAll(activePots);
            }

            if (available.isEmpty()) return;

            Location newLoc = available.get(random.nextInt(available.size()));
            spawnPot(newLoc);

        }, 200L); // ⏱ 10 seconds
    }

    // =========================
    // CLICK LOGIC
    // =========================

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

        // ✨ Effects
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

        // 🎁 Loot
        List<String> items = ItemRegistry.getItemIds().stream()
                .filter(id -> ItemRegistry.hasTag(id, "TATOOINEPOTS"))
                .toList();

        if (!items.isEmpty()) {
            int amount = 1 + random.nextInt(2);

            for (int i = 0; i < amount; i++) {
                String id = pickWeighted(items);
                ItemStack item = ItemRegistry.get(id);

                if (item != null) {
                    player.getInventory().addItem(item);
                }
            }
        }

        player.sendMessage("§6You rummage through the pot...");

        // 💥 Remove + respawn
        block.setType(Material.AIR);
        respawnPotDelayed(loc);
    }

    // =========================
    // WEIGHTED LOOT
    // =========================

    private String pickWeighted(List<String> items) {

        Map<String, Double> weights = new HashMap<>();

        for (String id : items) {

            String rarity = ItemRegistry.getRarity(id);

            double weight = switch (rarity.toLowerCase()) {
                case "common" -> 60;
                case "uncommon" -> 25;
                case "rare" -> 10;
                case "epic" -> 4;
                case "legendary" -> 1;
                case "mythic" -> 0.2;
                default -> 1;
            };

            weights.put(id, weight);
        }

        double total = weights.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        double roll = Math.random() * total;

        double cumulative = 0;

        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }

        return items.get(0);
    }

    // =========================
    // HARDCODED LOCATIONS
    // =========================

    private void loadLocations() {

        World world = Bukkit.getWorld("Tatooine");
        if (world == null) return;

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

        // (trimmed for readability — continue same pattern)
    }
}