package com.astuteflamez.mandomc.system.planets.ilum.listeners;

import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.system.planets.ilum.managers.ParkourManager;

import org.bukkit.entity.Player;

public class ParkourItemListener implements Listener {

    private final ParkourManager parkourManager;
    private final NamespacedKey key;

    public ParkourItemListener(ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
        this.key = new NamespacedKey(MandoMC.getInstance(), "parkour_item");
    }

    @EventHandler
    public void onItemUse(PlayerInteractEvent event) {

        // Prevent offhand triggering
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();

        // Only care about right clicks
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();

        // Player must be in parkour
        if (!parkourManager.hasSession(player)) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String type = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);

        if (type == null) return;

        event.setCancelled(true);

        switch (type) {

            case "leave":
                parkourManager.exitParkour(player);
                break;

            case "checkpoint":
                parkourManager.teleportCheckpoint(player);
                break;

            case "restart":
                parkourManager.restart(player);
                break;
        }
    }
}