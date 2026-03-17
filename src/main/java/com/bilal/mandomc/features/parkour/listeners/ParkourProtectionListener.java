package com.bilal.mandomc.features.parkour.listeners;

import java.util.List;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;

import com.bilal.mandomc.features.parkour.configs.ParkourConfig;

import org.bukkit.entity.Player;

public class ParkourProtectionListener implements Listener {

    private boolean isParkourWorld(Player player) {

        String worldName = ParkourConfig.get().getString("parkour.world");

        if (worldName == null) return false;

        return player.getWorld().getName().equalsIgnoreCase(worldName);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {

        if (isParkourWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {

        if (isParkourWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {

        if (isParkourWorld(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {

        if (!(event.getEntity() instanceof Player player)) return;

        if (isParkourWorld(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (isParkourWorld(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {

        Player player = event.getPlayer();

        if (!isParkourWorld(player)) return;

        // Allow OPs to run commands
        if (player.isOp()) return;

        // Allow players with bypass permission
        if (player.hasPermission("mmc.parkour.bypass-command-blocks")) return;

        List<String> allowed = ParkourConfig.get().getStringList("parkour.settings.allow-commands");

        String command = event.getMessage().split(" ")[0].replace("/", "").toLowerCase();

        if (!allowed.contains(command)) {

            player.sendMessage("§3§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Commands are disabled in parkour.");

            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFireDamage(EntityDamageEvent event) {

        if (!(event.getEntity() instanceof Player player)) return;

        if (!isParkourWorld(player)) return;

        switch (event.getCause()) {

            case LAVA:
            case FIRE:
            case FIRE_TICK:
                event.setCancelled(true);
                player.setFireTicks(0);
                break;

            default:
                break;
        }
    }

    @EventHandler
    public void onHungerChange(FoodLevelChangeEvent event) {

        if (!(event.getEntity() instanceof Player player)) return;

        if (!isParkourWorld(player)) return;

        event.setCancelled(true);
    }
}