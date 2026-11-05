package net.mandomc.server.events.types.jabba;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.mandomc.server.events.EventManager;
import net.mandomc.server.events.model.GameEvent;

/**
 * Listens for players picking up or using dungeon keys to advance through rooms.
 */
public class DoorListener implements Listener {

    private final NamespacedKey KEY_ID;
    private final EventManager eventManager;

    public DoorListener(MandoMC plugin, EventManager eventManager) {
        this.KEY_ID = new NamespacedKey(plugin, "key_id");
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent event) {

        ItemStack item = event.getItem().getItemStack();
        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer keyId = pdc.get(KEY_ID, PersistentDataType.INTEGER);

        if (keyId == null) return;

        Player player = event.getPlayer();

        event.setCancelled(true);
        event.getItem().remove();

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "opendoor " + keyId);

        GameEvent active = eventManager.getActiveEvent();
        if (active instanceof JabbaDungeonEvent dungeon) {
            dungeon.advanceRoom();
        }

        player.sendMessage(LangManager.get("jabba.door-unlocked"));
    }

    @EventHandler
    public void onUseKey(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer keyId = pdc.get(KEY_ID, PersistentDataType.INTEGER);

        if (keyId == null) return;

        // consume
        if (item.getAmount() > 1) {
            item.setAmount(item.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "opendoor " + keyId);

        GameEvent active = eventManager.getActiveEvent();
        if (active instanceof JabbaDungeonEvent dungeon) {
            dungeon.advanceRoom();
        }

        player.sendMessage(LangManager.get("jabba.door-unlocked"));
    }
}