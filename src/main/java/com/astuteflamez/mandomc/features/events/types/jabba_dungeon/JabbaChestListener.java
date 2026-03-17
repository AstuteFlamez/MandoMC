package com.astuteflamez.mandomc.features.events.types.jabba_dungeon;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.features.items.ItemRegistry;

import java.util.*;

public class JabbaChestListener implements Listener {

    private final Random random = new Random();

    @EventHandler
    public void onClick(PlayerInteractEvent event) {

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CHEST) return;

        // 🔥 Prevent opening chest GUI
        event.setCancelled(true);

        Player player = event.getPlayer();
        Location loc = block.getLocation();

        // 🔥 Particle effect
        loc.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                loc.clone().add(0.5, 1, 0.5),
                50,
                0.3, 0.3, 0.3,
                0.1
        );

        // 🔥 Sound
        loc.getWorld().playSound(
                loc,
                Sound.ENTITY_PLAYER_LEVELUP,
                1f,
                1.2f
        );

        // 🔥 Get JABBA items
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

        player.sendMessage("§6You looted a stash!");

        // 🔥 Remove chest
        block.setType(Material.AIR);
    }

    // 🔥 Weighted rarity picker
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