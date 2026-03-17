package com.astuteflamez.mandomc.features.events.types.jabba_dungeon;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.astuteflamez.mandomc.MandoMC;

import java.util.HashMap;
import java.util.Map;

public class DoorListener implements Listener {

    private final Map<String, Integer> doors = new HashMap<>();
    private final NamespacedKey KEY_ID;

    public DoorListener(MandoMC plugin) {

        this.KEY_ID = new NamespacedKey(plugin, "key_id");

        // world:x:y:z -> door ID

        doors.put(key("JabbasPalace", -157, -48, 76), 1);
        doors.put(key("JabbasPalace", -143, -45, 101), 5);
        doors.put(key("JabbasPalace", -133, -43, 91), 6);
        doors.put(key("JabbasPalace", -155, -45, 101), 13);

        doors.put(key("JabbasPalace", -132, -49, 120), 4);
        doors.put(key("JabbasPalace", -96, -41, 118), 7);
        doors.put(key("JabbasPalace", -82, -41, 118), 8);
        doors.put(key("JabbasPalace", -94, -41, 102), 11);
    }

    @EventHandler
    public void onDoorInteract(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        if (event.getClickedBlock().getType() != Material.DISPENSER) return;

        Location loc = event.getClickedBlock().getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        String lookup = key(world.getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

        if (!doors.containsKey(lookup)) return;

        int doorId = doors.get(lookup);

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Integer keyId = pdc.get(KEY_ID, PersistentDataType.INTEGER);

        if (keyId == null) return;

        // key must match door
        if (keyId != doorId) {
            player.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cThis key doesn't fit this door.");
            return;
        }

        // prevent dispenser GUI
        event.setCancelled(true);

        // consume key
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        Bukkit.dispatchCommand(
                Bukkit.getConsoleSender(),
                "opendoor " + doorId
        );
    }

    private String key(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }
}