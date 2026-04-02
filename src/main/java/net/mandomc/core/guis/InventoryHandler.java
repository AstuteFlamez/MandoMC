package net.mandomc.core.guis;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

/**
 * Defines handlers for inventory GUI events.
 *
 * Implementations respond to click, open, and close events
 * for a managed inventory.
 */
public interface InventoryHandler {

    /**
     * Called when an inventory click occurs.
     *
     * @param event the inventory click event
     */
    void onClick(InventoryClickEvent event);

    /**
     * Called when an inventory is opened.
     *
     * @param event the inventory open event
     */
    void onOpen(InventoryOpenEvent event);

    /**
     * Called when an inventory drag occurs.
     *
     * @param event the inventory drag event
     */
    default void onDrag(InventoryDragEvent event) {
    }

    /**
     * Called when an inventory is closed.
     *
     * @param event the inventory close event
     */
    void onClose(InventoryCloseEvent event);

}