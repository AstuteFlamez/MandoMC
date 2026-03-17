package com.bilal.mandomc.features.small_features.fuel.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import com.bilal.mandomc.MandoMC;
import com.bilal.mandomc.features.small_features.fuel.FuelManager;

public class VehicleFuelTransferManager {

    private static final Map<UUID, BukkitTask> transfers = new HashMap<>();

    public static boolean isTransferring(Player player) {
        return transfers.containsKey(player.getUniqueId());
    }

    public static void startTransfer(Player player, ItemStack canister, ItemStack vehicleTank) {

        if (transfers.containsKey(player.getUniqueId())) return;

        cancelTransfer(player);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                MandoMC.getInstance(),
                () -> transferTick(player, canister, vehicleTank),
                0L,
                4L
        );

        transfers.put(player.getUniqueId(), task);
    }

    public static void stopTransfer(Player player) {

        BukkitTask task = transfers.remove(player.getUniqueId());

        if (task != null) {
            task.cancel();
            player.sendActionBar(ChatColor.GRAY + "⛽ Fuel transfer halted");
        }
    }

    private static void cancelTransfer(Player player) {

        BukkitTask task = transfers.remove(player.getUniqueId());

        if (task != null) {
            task.cancel();
        }
    }

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

        /* -------------------------
           Depositing (Canister → Vehicle)
        ------------------------- */

        if (mode.equals("depositing")) {

            if (canFuel <= 0) {
                player.sendActionBar(
                        ChatColor.RED + "⚠ Canister empty "
                        + ChatColor.GRAY + "(" + canFuel + "/" + canMax + ")"
                );
                cancelTransfer(player);
                return;
            }

            if (vehicleFuel >= vehicleMax) {
                player.sendActionBar(
                        ChatColor.RED + "⚠ Vehicle fuel tank full "
                        + ChatColor.GRAY + "(" + vehicleFuel + "/" + vehicleMax + ")"
                );
                cancelTransfer(player);
                return;
            }

            FuelManager.updateFuel(canister, canFuel - 1);
            FuelManager.updateFuel(vehicleTank, vehicleFuel + 1);

            int newCanFuel = FuelManager.getCurrentFuel(canister);
            int newVehicleFuel = FuelManager.getCurrentFuel(vehicleTank);

            player.sendActionBar(
                    ChatColor.GOLD + "⛽ Fueling Vehicle "
                    + ChatColor.YELLOW + newCanFuel + "/" + canMax
                    + ChatColor.DARK_GRAY + " → "
                    + ChatColor.AQUA + "Vehicle "
                    + ChatColor.YELLOW + newVehicleFuel + "/" + vehicleMax
            );

            return;
        }

        /* -------------------------
           Refueling (Vehicle → Canister)
        ------------------------- */

        if (vehicleFuel <= 0) {
            player.sendActionBar(
                    ChatColor.RED + "⚠ Vehicle fuel tank empty "
                    + ChatColor.GRAY + "(" + vehicleFuel + "/" + vehicleMax + ")"
            );
            cancelTransfer(player);
            return;
        }

        if (canFuel >= canMax) {
            player.sendActionBar(
                    ChatColor.RED + "⚠ Canister full "
                    + ChatColor.GRAY + "(" + canFuel + "/" + canMax + ")"
            );
            cancelTransfer(player);
            return;
        }

        FuelManager.updateFuel(canister, canFuel + 1);
        FuelManager.updateFuel(vehicleTank, vehicleFuel - 1);

        int newCanFuel = FuelManager.getCurrentFuel(canister);
        int newVehicleFuel = FuelManager.getCurrentFuel(vehicleTank);

        player.sendActionBar(
                ChatColor.AQUA + "⛽ Draining Vehicle "
                + ChatColor.YELLOW + newCanFuel + "/" + canMax
                + ChatColor.DARK_GRAY + " ← "
                + ChatColor.GOLD + "Vehicle "
                + ChatColor.YELLOW + newVehicleFuel + "/" + vehicleMax
        );
    }
}