package net.mandomc.core.guis;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

/**
 * Central listener for all GUI-related inventory events.
 *
 * This class delegates Bukkit inventory events to the GUIManager,
 * which is responsible for handling GUI logic such as click actions,
 * open initialization, and cleanup on close.
 */
public class GUIListener implements Listener {

    /**
     * Manager responsible for handling GUI interactions and state.
     */
    private final GUIManager guiManager;

    /**
     * Creates a new GUIListener.
     *
     * @param guiManager the GUI manager used to handle inventory events
     */
    public GUIListener(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    /**
     * Handles inventory click events and delegates them to the GUI manager.
     *
     * @param event the inventory click event
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        this.guiManager.handleClick(event);
    }

    /**
     * Handles inventory open events and delegates them to the GUI manager.
     *
     * @param event the inventory open event
     */
    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        this.guiManager.handleOpen(event);
    }

    /**
     * Handles inventory close events and delegates them to the GUI manager.
     *
     * @param event the inventory close event
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        this.guiManager.handleClose(event);
    }

}