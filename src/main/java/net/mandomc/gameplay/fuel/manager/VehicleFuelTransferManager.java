package net.mandomc.gameplay.fuel.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import net.mandomc.MandoMC;
import net.mandomc.gameplay.fuel.FuelManager;
import net.mandomc.core.LangManager;

/**
 * Manages ticking fuel transfers between a rhydonium canister and a vehicle's fuel tank.
 *
 * Supports both depositing (canister to vehicle) and extracting (vehicle to canister) modes.
 * Each player may only have one active transfer at a time, enforced via a UUID-keyed task map.
 */
public class VehicleFuelTransferManager {

    private static final Map<UUID, BukkitTask> transfers = new HashMap<>();

    /**
     * Returns true if the given player currently has an active vehicle fuel transfer.
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
     * @param player      the player performing the transfer
     * @param canister    the canister item in the player's hand
     * @param vehicleTank the vehicle's tank item stack
     */
    public static void startTransfer(Player player, ItemStack canister, ItemStack vehicleTank) {
        if (transfers.containsKey(player.getUniqueId())) return;

        cancelTransfer(player);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                MandoMC.getInstance(),
                () -> transferTick(player, canister, vehicleTank),
                0L,
                2L
        );

        transfers.put(player.getUniqueId(), task);
    }

    /**
     * Stops the transfer for the given player and notifies them via action bar.
     *
     * @param player the player whose transfer to stop
     */
    public static void stopTransfer(Player player) {
        BukkitTask task = transfers.remove(player.getUniqueId());

        if (task != null) {
            task.cancel();
            player.sendActionBar(LangManager.get("fuel.transfer-halted"));
        }
    }

    /**
     * Cancels the transfer for the given player silently.
     *
     * @param player the player whose transfer to cancel
     */
    private static void cancelTransfer(Player player) {
        BukkitTask task = transfers.remove(player.getUniqueId());

        if (task != null) {
            task.cancel();
        }
    }

    /**
     * Executes one tick of a vehicle fuel transfer.
     *
     * Validates state (online, sneaking, items not null) before transferring one unit of fuel.
     * Sends an action bar status message to the player.
     *
     * @param player      the player performing the transfer
     * @param canister    the canister item stack (modified in place)
     * @param vehicleTank the vehicle tank item stack (modified in place)
     */
    private static void transferTick(Player player, ItemStack canister, ItemStack vehicleTank) {
        if (!player.isOnline()) {
            cancelTransfer(player);
            return;
        }

        if (!player.isSneaking()) {
            stopTransfer(player);
            return;
        }

        if (canister == null || vehicleTank == null) {
            cancelTransfer(player);
            return;
        }

        String mode = CanisterManager.getMode(canister);

        int vehicleFuel = FuelManager.getCurrentFuel(vehicleTank);
        int vehicleMax = FuelManager.getMaxFuel(vehicleTank);

        int canFuel = FuelManager.getCurrentFuel(canister);
        int canMax = FuelManager.getMaxFuel(canister);

        if (mode.equals("depositing")) {
            if (canFuel <= 0) {
                player.sendActionBar(
                        LangManager.get("fuel.vehicle.canister-empty",
                                "%current%", String.valueOf(canFuel),
                                "%max%", String.valueOf(canMax))
                );
                cancelTransfer(player);
                return;
            }

            if (vehicleFuel >= vehicleMax) {
                player.sendActionBar(
                        LangManager.get("fuel.vehicle.tank-full",
                                "%current%", String.valueOf(vehicleFuel),
                                "%max%", String.valueOf(vehicleMax))
                );
                cancelTransfer(player);
                return;
            }

            FuelManager.updateFuel(canister, canFuel - 1);
            FuelManager.updateFuel(vehicleTank, vehicleFuel + 1);

            int newCanFuel = FuelManager.getCurrentFuel(canister);
            int newVehicleFuel = FuelManager.getCurrentFuel(vehicleTank);

            player.sendActionBar(
                    LangManager.get("fuel.vehicle.fueling",
                            "%can%", String.valueOf(newCanFuel),
                            "%can-max%", String.valueOf(canMax),
                            "%vehicle%", String.valueOf(newVehicleFuel),
                            "%vehicle-max%", String.valueOf(vehicleMax))
            );

            return;
        }

        if (vehicleFuel <= 0) {
            player.sendActionBar(
                    LangManager.get("fuel.vehicle.tank-empty",
                            "%current%", String.valueOf(vehicleFuel),
                            "%max%", String.valueOf(vehicleMax))
            );
            cancelTransfer(player);
            return;
        }

        if (canFuel >= canMax) {
            player.sendActionBar(
                    LangManager.get("fuel.vehicle.canister-full",
                            "%current%", String.valueOf(canFuel),
                            "%max%", String.valueOf(canMax))
            );
            cancelTransfer(player);
            return;
        }

        FuelManager.updateFuel(canister, canFuel + 1);
        FuelManager.updateFuel(vehicleTank, vehicleFuel - 1);

        int newCanFuel = FuelManager.getCurrentFuel(canister);
        int newVehicleFuel = FuelManager.getCurrentFuel(vehicleTank);

        player.sendActionBar(
                LangManager.get("fuel.vehicle.draining",
                        "%can%", String.valueOf(newCanFuel),
                        "%can-max%", String.valueOf(canMax),
                        "%vehicle%", String.valueOf(newVehicleFuel),
                        "%vehicle-max%", String.valueOf(vehicleMax))
        );
    }
}
