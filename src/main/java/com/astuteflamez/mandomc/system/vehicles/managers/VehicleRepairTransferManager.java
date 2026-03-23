package com.astuteflamez.mandomc.system.vehicles.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.system.items.ItemUtils;

public class VehicleRepairTransferManager {

    private static final Map<UUID, Integer> activeRepairs = new HashMap<>();

    private static final double REPAIR_PER_TICK = 5;

    public static boolean isTransferring(Player player) {
        return activeRepairs.containsKey(player.getUniqueId());
    }

    public static void startTransfer(Player player, ItemStack wrench, ItemStack vehicleItem) {

        UUID uuid = player.getUniqueId();

        int task = Bukkit.getScheduler().runTaskTimer(
                MandoMC.getInstance(),
                () -> repairTick(player, wrench, vehicleItem),
                0,
                4
        ).getTaskId();

        activeRepairs.put(uuid, task);
    }

    private static void repairTick(Player player, ItemStack wrench, ItemStack vehicleItem) {

        UUID uuid = player.getUniqueId();

        /* ---------------------------
        Stop if player conditions change
        --------------------------- */

        if (!player.isOnline()
                || !player.isSneaking()
                || player.getInventory().getItemInMainHand() == null
                || !ItemUtils.isItem(player.getInventory().getItemInMainHand(), "wrench")) {

            stopTransfer(player);
            return;
        }

        double current = VehicleHealthManager.getCurrentHealth(vehicleItem);
        double max = VehicleHealthManager.getMaxHealth(vehicleItem);

        if (current >= max) {
            stopTransfer(player);
            player.sendActionBar("§aVehicle fully repaired.");
            return;
        }

        double newHealth = Math.min(current + REPAIR_PER_TICK, max);

        VehicleHealthManager.setHealth(vehicleItem, newHealth, player);

        /* durability */

        if (wrench.getItemMeta() instanceof Damageable damageable) {

            damageable.setDamage(damageable.getDamage() + 1);
            wrench.setItemMeta((org.bukkit.inventory.meta.ItemMeta) damageable);

            if (damageable.getDamage() >= wrench.getType().getMaxDurability()) {

                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);

                stopTransfer(player);
                return;
            }
        }

        /* effects */

        Location loc = player.getLocation();

        player.getWorld().spawnParticle(
                Particle.CRIT,
                loc,
                6,
                0.3,
                0.3,
                0.3,
                0.1
        );

        player.getWorld().playSound(
                loc,
                Sound.BLOCK_ANVIL_USE,
                0.6f,
                1.2f
        );

        player.sendActionBar(
                "§a🔧 Repairing Vehicle: §f" + (int)newHealth + "/" + (int)max
        );
    }

    private static void stopTransfer(Player player) {

        UUID uuid = player.getUniqueId();

        Integer task = activeRepairs.remove(uuid);

        if (task != null) {
            Bukkit.getScheduler().cancelTask(task);
        }
    }
}