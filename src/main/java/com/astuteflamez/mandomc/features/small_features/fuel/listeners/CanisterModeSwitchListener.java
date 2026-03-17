package com.astuteflamez.mandomc.features.small_features.fuel.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import com.astuteflamez.mandomc.features.items.ItemUtils;
import com.astuteflamez.mandomc.features.small_features.fuel.managers.CanisterManager;

public class CanisterModeSwitchListener implements Listener {

    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();

        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();

        if (item == null) return;

        /* Only apply to canisters */

        if (!ItemUtils.isItem(item, "rhydonium_canister")) return;

        CanisterManager.switchMode(item);
    }
}