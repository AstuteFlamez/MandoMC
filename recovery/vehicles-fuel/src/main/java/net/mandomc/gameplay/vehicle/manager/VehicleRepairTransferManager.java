package net.mandomc.gameplay.vehicle.manager;

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

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.mandomc.server.items.ItemUtils;

/**
 * Manages the vehicle repair transfer process.
 *
 * When a player crouches and holds a wrench near a vehicle, a repeating
 * task gradually restores vehicle health until the vehicle is fully repaired,
 * the player stops crouching, or the wrench breaks.
 */
public class VehicleRepairTransferManager {

    private static final Map<UUID, Integer> activeRepairs = new HashMap<>();

    private static final double REPAIR_PER_TICK = 5;

    /**
     * Returns whether the player is currently in a repair transfer.
     *
     * @param player the player to check
     * @return true if the player has an active repair task
     */
    public static boolean isTransferring(Player player) {
        return activeRepairs.containsKey(player.getUniqueId());
    }

    /**
     * Starts a repeating repair task for the given player.
     *
     * The task runs every 4 ticks and continues until the vehicle is
     * fully repaired, the player changes conditions, or the wrench breaks.
     *
     * @param player the player performing the repair
     * @param wrench the wrench item being used
     * @param vehicleItem the vehicle item being repaired
     */
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

    /**
     * Executes one repair tick.
     *
     * Validates player conditions, applies repair, degrades wrench durability,
     * plays effects, and sends action bar status.
     */
    private static void repairTick(Player player, ItemStack wrench, ItemStack vehicleItem) {
        UUID uuid = player.getUniqueId();

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
            player.sendActionBar(LangManager.get("vehicles.fully-repaired"));
            return;
        }

        double newHealth = Math.min(current + REPAIR_PER_TICK, max);
        VehicleHealthManager.setHealth(vehicleItem, newHealth, player);

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

        Location loc = player.getLocation();

        player.getWorld().spawnParticle(Particle.CRIT, loc, 6, 0.3, 0.3, 0.3, 0.1);
        player.getWorld().playSound(loc, Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);

        player.sendActionBar(
                "§a🔧 Repairing Vehicle: §f" + (int) newHealth + "/" + (int) max
        );
    }

    /**
     * Cancels the active repair task for the given player.
     *
     * @param player the player whose repair task should be stopped
     */
    private static void stopTransfer(Player player) {
        Integer task = activeRepairs.remove(player.getUniqueId());
        if (task != null) {
            Bukkit.getScheduler().cancelTask(task);
        }
    }
}
