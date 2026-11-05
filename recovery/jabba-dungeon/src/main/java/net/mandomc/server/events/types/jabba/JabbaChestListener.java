package net.mandomc.server.events.types.jabba;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import net.mandomc.server.events.EventManager;
import net.mandomc.server.events.model.GameEvent;
import net.mandomc.core.LangManager;
import net.mandomc.server.items.ItemRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Listens for players right-clicking chests during the Jabba's Dungeon event.
 *
 * When the Jabba event is active in the Tatooine world, chests give weighted-random
 * loot from the JABBA item tag pool and are removed after looting.
 */
public class JabbaChestListener implements Listener {

    private final Random random = new Random();
    private final EventManager eventManager;

    public JabbaChestListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {

        Player player = event.getPlayer();

        if (!isJabbaActive(player)) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }

        if (block.getType() != Material.CHEST) {
            return;
        }

        event.setCancelled(true);

        Location loc = block.getLocation();

        loc.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                loc.clone().add(0.5, 1, 0.5),
                50,
                0.3, 0.3, 0.3,
                0.1
        );

        loc.getWorld().playSound(
                loc,
                Sound.ENTITY_PLAYER_LEVELUP,
                1f,
                1.2f
        );

        List<String> jabbaItems = ItemRegistry.getItemIds().stream()
                .filter(id -> ItemRegistry.hasTag(id, "JABBA"))
                .toList();

        if (jabbaItems.isEmpty()) return;

        int amount = 1 + random.nextInt(3);

        for (int i = 0; i < amount; i++) {

            String id = pickWeighted(jabbaItems);
            ItemStack item = ItemRegistry.get(id);

            if (item != null) {
                player.getInventory().addItem(item);
            }
        }

        player.sendMessage(LangManager.get("jabba.looted-stash"));

        block.setType(Material.AIR);
    }

    private boolean isJabbaActive(Player player) {

        GameEvent active = eventManager.getActiveEvent();

        if (active == null) {
            return false;
        }

        if (!active.getId().equalsIgnoreCase("jabba")) {
            return false;
        }

        boolean worldMatch = player.getWorld().getName()
                .equalsIgnoreCase("Tatooine");

        return worldMatch;
    }

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
}