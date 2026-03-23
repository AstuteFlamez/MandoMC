package com.astuteflamez.mandomc.mechanics.fuel.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.mechanics.fuel.FuelManager;

public class BarrelFuelTransferManager {

    private static final Map<UUID, BukkitTask> transfers = new HashMap<>();

    public static boolean isTransferring(Player player) {
        return transfers.containsKey(player.getUniqueId());
    }

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

    private static void transferTick(Player player, ItemStack canister, ArmorStand barrel) {

        if (!player.isOnline()) {
            cancelTransfer(player);
            return;
        }

        if (!player.isSneaking()) {
            stopTransfer(player);
            return;
        }

        if (canister == null || barrel == null) {
            cancelTransfer(player);
            return;
        }

        ItemStack barrelItem = barrel.getEquipment().getHelmet();
        if (barrelItem == null) {
            cancelTransfer(player);
            return;
        }

        String mode = CanisterManager.getMode(canister);

        int barrelFuel = FuelManager.getCurrentFuel(barrelItem);
        int barrelMax = FuelManager.getMaxFuel(barrelItem);

        int canFuel = FuelManager.getCurrentFuel(canister);
        int canMax = FuelManager.getMaxFuel(canister);

        /* -------------------------
           Depositing (Canister → Barrel)
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

            if (barrelFuel >= barrelMax) {
                player.sendActionBar(
                        ChatColor.RED + "⚠ Barrel full "
                        + ChatColor.GRAY + "(" + barrelFuel + "/" + barrelMax + ")"
                );
                cancelTransfer(player);
                return;
            }

            FuelManager.updateFuel(canister, canFuel - 1);
            FuelManager.updateFuel(barrelItem, barrelFuel + 1);

            /* Update barrel visuals */

            BarrelManager.updateModel(barrelItem);
            barrel.getEquipment().setHelmet(barrelItem);

            ArmorStand holo = BarrelManager.getHologram(barrel);
            if (holo != null) {
                BarrelManager.updateHologram(barrel, holo);
            }

            int newCanFuel = FuelManager.getCurrentFuel(canister);
            int newBarrelFuel = FuelManager.getCurrentFuel(barrelItem);

            player.sendActionBar(
                    ChatColor.GOLD + "⛽ Depositing "
                    + ChatColor.YELLOW + newCanFuel + "/" + canMax
                    + ChatColor.DARK_GRAY + " → "
                    + ChatColor.AQUA + "Barrel "
                    + ChatColor.YELLOW + newBarrelFuel + "/" + barrelMax
            );

            return;
        }

        /* -------------------------
           Refueling (Barrel → Canister)
        ------------------------- */

        if (barrelFuel <= 0) {
            player.sendActionBar(
                    ChatColor.RED + "⚠ Barrel empty "
                    + ChatColor.GRAY + "(" + barrelFuel + "/" + barrelMax + ")"
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
        FuelManager.updateFuel(barrelItem, barrelFuel - 1);

        /* Update barrel visuals */

        BarrelManager.updateModel(barrelItem);
        barrel.getEquipment().setHelmet(barrelItem);

        ArmorStand holo = BarrelManager.getHologram(barrel);
        if (holo != null) {
            BarrelManager.updateHologram(barrel, holo);
        }

        int newCanFuel = FuelManager.getCurrentFuel(canister);
        int newBarrelFuel = FuelManager.getCurrentFuel(barrelItem);

        player.sendActionBar(
                ChatColor.AQUA + "⛽ Refueling "
                + ChatColor.YELLOW + newCanFuel + "/" + canMax
                + ChatColor.DARK_GRAY + " ← "
                + ChatColor.GOLD + "Barrel "
                + ChatColor.YELLOW + newBarrelFuel + "/" + barrelMax
        );
    }
}