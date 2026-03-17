package com.bilal.mandomc.features.events.listeners;

import com.bilal.mandomc.features.events.EventManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class EventMenuListener implements Listener {

    private final EventManager manager;

    public EventMenuListener(EventManager manager) {
        this.manager = manager;
    }

    private boolean isEventMenu(String title) {
        String menuTitle = manager.color(manager.getConfig().getString("gui.title", "&8Server Events"));
        return title != null && title.equals(menuTitle);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView() == null || event.getView().getTitle() == null) return;
        if (!isEventMenu(event.getView().getTitle())) return;

        event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView() == null || event.getView().getTitle() == null) return;
        if (!isEventMenu(event.getView().getTitle())) return;

        event.setCancelled(true);
    }
}