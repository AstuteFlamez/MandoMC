package com.astuteflamez.mandomc.core.guis;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages active GUI inventories and routes inventory events
 * to their corresponding handlers.
 *
 * Tracks open GUI inventories and ensures all click, open,
 * and close events are delegated to the correct handler.
 */
public class GUIManager {

    /**
     * Map of active inventories to their handlers.
     */
    private final Map<Inventory, InventoryHandler> activeInventories = new HashMap<>();

    /**
     * Opens a GUI for a player and registers it for event handling.
     *
     * @param gui the GUI to open
     * @param player the player viewing the GUI
     */
    public void openGUI(InventoryGUI gui, Player player) {
        this.registerHandledInventory(gui.getInventory(), gui);
        player.openInventory(gui.getInventory());
    }

    /**
     * Registers an inventory with its corresponding handler.
     *
     * @param inventory the inventory to track
     * @param handler the handler responsible for this inventory
     */
    public void registerHandledInventory(Inventory inventory, InventoryHandler handler) {
        this.activeInventories.put(inventory, handler);
    }

    /**
     * Unregisters an inventory and removes its handler.
     *
     * @param inventory the inventory to remove
     */
    public void unregisterInventory(Inventory inventory) {
        this.activeInventories.remove(inventory);
    }

    /**
     * Handles inventory click events and delegates to the appropriate handler.
     *
     * @param event the inventory click event
     */
    public void handleClick(InventoryClickEvent event) {
        InventoryHandler handler = this.activeInventories.get(event.getInventory());
        if (handler != null) {
            handler.onClick(event);
        }
    }

    /**
     * Handles inventory open events and delegates to the appropriate handler.
     *
     * @param event the inventory open event
     */
    public void handleOpen(InventoryOpenEvent event) {
        InventoryHandler handler = this.activeInventories.get(event.getInventory());
        if (handler != null) {
            handler.onOpen(event);
        }
    }

    /**
     * Handles inventory close events, delegates to the handler,
     * and unregisters the inventory.
     *
     * @param event the inventory close event
     */
    public void handleClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHandler handler = this.activeInventories.get(inventory);
        if (handler != null) {
            handler.onClose(event);
            this.unregisterInventory(inventory);
        }
    }

}