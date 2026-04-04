package net.mandomc.server.events.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import net.mandomc.server.events.EventManager;
import net.mandomc.server.events.gui.EventMenu;

public class EventMenuListener implements Listener {

    private final EventManager manager;

    public EventMenuListener(EventManager manager) {
        this.manager = manager;
    }

    private boolean isEventMenu(String title) {
        if (title == null) {
            return false;
        }
        String idleTitle = manager.color(EventMenu.eventMenuTitle(false));
        String activeTitle = manager.color(EventMenu.eventMenuTitle(true));
        return title.equals(idleTitle) || title.equals(activeTitle);
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