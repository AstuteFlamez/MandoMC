package com.astuteflamez.mandomc.core.guis;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for all inventory-based GUIs.
 *
 * Handles button registration, rendering, and click routing.
 * Subclasses define the inventory layout and populate buttons.
 */
public abstract class InventoryGUI implements InventoryHandler {

    /**
     * Backing inventory for this GUI.
     */
    private final Inventory inventory;

    /**
     * Mapping of slot indices to buttons.
     */
    private final Map<Integer, InventoryButton> buttonMap = new HashMap<>();

    /**
     * Creates a new GUI and initializes its inventory.
     */
    public InventoryGUI() {
        this.inventory = this.createInventory();
    }

    /**
     * Gets the inventory backing this GUI.
     *
     * @return the inventory
     */
    public Inventory getInventory() {
        return this.inventory;
    }

    /**
     * Adds a button to a specific slot.
     *
     * @param slot the inventory slot
     * @param button the button to place
     */
    public void addButton(int slot, InventoryButton button) {
        this.buttonMap.put(slot, button);
    }

    /**
     * Renders all buttons for the given player.
     *
     * @param player the player viewing the GUI
     */
    public void decorate(Player player) {
        this.buttonMap.forEach((slot, button) -> {
            ItemStack icon = button.getIconCreator().apply(player);
            this.inventory.setItem(slot, icon);
        });
    }

    /**
     * Handles click events and routes them to the correct button.
     * Cancels the event to prevent item movement.
     *
     * @param event the inventory click event
     */
    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);

        int slot = event.getSlot();
        InventoryButton button = this.buttonMap.get(slot);

        if (button != null) {
            button.getEventConsumer().accept(event);
        }
    }

    /**
     * Called when the inventory is opened.
     * Triggers rendering of the GUI for the player.
     *
     * @param event the inventory open event
     */
    @Override
    public void onOpen(InventoryOpenEvent event) {
        this.decorate((Player) event.getPlayer());
    }

    /**
     * Called when the inventory is closed.
     * Can be overridden for cleanup logic.
     *
     * @param event the inventory close event
     */
    @Override
    public void onClose(InventoryCloseEvent event) {
    }

    /**
     * Creates the inventory instance for this GUI.
     *
     * @return the created inventory
     */
    protected abstract Inventory createInventory();

}