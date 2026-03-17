package com.astuteflamez.mandomc.features.small_features.fuel.listeners;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.features.items.ItemUtils;
import com.astuteflamez.mandomc.features.small_features.fuel.managers.BarrelFuelTransferManager;

public class BarrelCanisterInteractListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {

        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand stand)) return;

        if (!stand.getScoreboardTags().contains("rhydonium_barrel")) return;

        Player player = event.getPlayer();

        if (!player.isSneaking()) return;

        /* Prevent multiple transfers */

        if (BarrelFuelTransferManager.isTransferring(player)) return;

        ItemStack canister = player.getInventory().getItemInMainHand();

        if (canister == null) return;
        if (!ItemUtils.hasTag(canister, "FUEL")) return;
        if (!ItemUtils.isItem(canister, "rhydonium_canister")) return;

        ItemStack barrelItem = stand.getEquipment().getHelmet();
        if (barrelItem == null) return;

        event.setCancelled(true);

        BarrelFuelTransferManager.startTransfer(player, canister, stand);
    }
}