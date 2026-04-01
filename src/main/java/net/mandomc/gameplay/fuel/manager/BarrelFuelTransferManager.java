package net.mandomc.gameplay.fuel.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import net.mandomc.MandoMC;
import net.mandomc.gameplay.fuel.FuelManager;
import net.mandomc.core.LangManager;

/**
 * Manages ticking fuel transfers between a rhydonium canister and a placed barrel.
 *
 * Supports both depositing (canister to barrel) and extracting (barrel to canister) modes.
 * Each player may only have one active transfer at a time, enforced via a UUID-keyed task map.
 */
public class BarrelFuelTransferManager {

    private static final Map<UUID, BukkitTask> transfers = new HashMap<>();
    private static final Map<UUID, Integer> displayTickCounters = new HashMap<>();
    private static final int DISPLAY_UPDATE_INTERVAL_TICKS = 5;

    /**
     * Returns true if the given player currently has an active barrel fuel transfer.
     *
     * @param player the player to check
     * @return true if transferring
     */
    public static boolean isTransferring(Player player) {
        return transfers.containsKey(player.getUniqueId());
    }

    /**
     * Starts a repeating fuel transfer task for the given player.
     *
     * Cancels any existing transfer before starting. Runs every 2 ticks.
     *
     * @param player   the player performing the transfer
     * @param canister the canister item in the player's hand
     * @param barrel   the barrel armor stand being targeted
     */
    public static void startTransfer(Player player, ItemStack canister, ArmorStand barrel) {
        if (transfers.containsKey(player.getUniqueId())) return;

        cancelTransfer(player);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                MandoMC.getInstance(),
                () -> transferTick(player, canister, barrel),
                0L,
                2L
        );

        transfers.put(player.getUniqueId(), task);
        displayTickCounters.put(player.getUniqueId(), 0);
    }

    /**
     * Stops the transfer for the given player and notifies them via action bar.
     *
     * @param player the player whose transfer to stop
     */
    public static void stopTransfer(Player player) {
        displayTickCounters.remove(player.getUniqueId());
        BukkitTask task = transfers.remove(player.getUniqueId());

        if (task != null) {
            task.cancel();
            player.sendActionBar(LangManager.get("fuel.transfer-halted"));
        }
    }

    /**
     * Stops all active barrel transfer tasks silently.
     */
    public static void stopAll() {
        for (BukkitTask task : transfers.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        transfers.clear();
        displayTickCounters.clear();
    }

    /**
     * Cancels the transfer for the given player silently.
     *
     * @param player the player whose transfer to cancel
     */
    private static void cancelTransfer(Player player) {
        displayTickCounters.remove(player.getUniqueId());
        BukkitTask task = transfers.remove(player.getUniqueId());

        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Executes one tick of a barrel fuel transfer.
     *
     * Validates state (online, sneaking, items not null) before transferring one unit of fuel.
     * Updates barrel model visuals and hologram after each tick.
     * Sends an action bar status message to the player.
     *
     * @param player   the player performing the transfer
     * @param canister the canister item stack (modified in place)
     * @param barrel   the target barrel armor stand
     */
    private static void transferTick(Player player, ItemStack canister, ArmorStand barrel) {
        if (!player.isOnline()) {
            cancelTransfer(player);
            return;
        }

        if (!player.isSneaking()) {
            stopTransfer(player);
            return;
        }

        if (canister == null || barrel == null || !barrel.isValid()) {
            cancelTransfer(player);
            return;
        }

        ItemStack barrelItem = barrel.getEquipment().getHelmet();
        if (barrelItem == null) {
            cancelTransfer(player);
            return;
        }

        String mode = CanisterManager.getMode(canister);
        UUID playerId = player.getUniqueId();
        boolean refreshDisplay = shouldRefreshDisplay(playerId);

        int barrelFuel = FuelManager.getCurrentFuel(barrelItem);
        int barrelMax = FuelManager.getMaxFuel(barrelItem);

        int canFuel = FuelManager.getCurrentFuel(canister);
        int canMax = FuelManager.getMaxFuel(canister);

        if (mode.equals("depositing")) {
            if (canFuel <= 0) {
                player.sendActionBar(
                        LangManager.get("fuel.barrel.canister-empty",
                                "%current%", String.valueOf(canFuel),
                                "%max%", String.valueOf(canMax))
                );
                cancelTransfer(player);
                return;
            }

            if (barrelFuel >= barrelMax) {
                player.sendActionBar(
                        LangManager.get("fuel.barrel.barrel-full",
                                "%current%", String.valueOf(barrelFuel),
                                "%max%", String.valueOf(barrelMax))
                );
                cancelTransfer(player);
                return;
            }

            FuelManager.updateFuel(canister, canFuel - 1, refreshDisplay);
            FuelManager.updateFuel(barrelItem, barrelFuel + 1, refreshDisplay);

            if (refreshDisplay) {
                BarrelManager.updateModel(barrelItem);
                player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.45f, 1.15f);
            }
            barrel.getEquipment().setHelmet(barrelItem);

            if (refreshDisplay) {
                BarrelManager.updateHologram(barrel);
            }

            int newCanFuel = FuelManager.getCurrentFuel(canister);
            int newBarrelFuel = FuelManager.getCurrentFuel(barrelItem);

            player.sendActionBar(
                    LangManager.get("fuel.barrel.depositing",
                            "%can%", String.valueOf(newCanFuel),
                            "%can-max%", String.valueOf(canMax),
                            "%barrel%", String.valueOf(newBarrelFuel),
                            "%barrel-max%", String.valueOf(barrelMax))
            );

            return;
        }

        if (barrelFuel <= 0) {
            player.sendActionBar(
                    LangManager.get("fuel.barrel.barrel-empty",
                            "%current%", String.valueOf(barrelFuel),
                            "%max%", String.valueOf(barrelMax))
            );
            cancelTransfer(player);
            return;
        }

        if (canFuel >= canMax) {
            player.sendActionBar(
                    LangManager.get("fuel.barrel.canister-full",
                            "%current%", String.valueOf(canFuel),
                            "%max%", String.valueOf(canMax))
            );
            cancelTransfer(player);
            return;
        }

        FuelManager.updateFuel(canister, canFuel + 1, refreshDisplay);
        FuelManager.updateFuel(barrelItem, barrelFuel - 1, refreshDisplay);

        if (refreshDisplay) {
            BarrelManager.updateModel(barrelItem);
            player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.45f, 1.15f);
        }
        barrel.getEquipment().setHelmet(barrelItem);

        if (refreshDisplay) {
            BarrelManager.updateHologram(barrel);
        }

        int newCanFuel = FuelManager.getCurrentFuel(canister);
        int newBarrelFuel = FuelManager.getCurrentFuel(barrelItem);

        player.sendActionBar(
                LangManager.get("fuel.barrel.refueling",
                        "%can%", String.valueOf(newCanFuel),
                        "%can-max%", String.valueOf(canMax),
                        "%barrel%", String.valueOf(newBarrelFuel),
                        "%barrel-max%", String.valueOf(barrelMax))
        );
    }

    private static boolean shouldRefreshDisplay(UUID playerId) {
        int next = displayTickCounters.getOrDefault(playerId, 0) + 1;
        displayTickCounters.put(playerId, next);
        return next % DISPLAY_UPDATE_INTERVAL_TICKS == 0;
    }
}
