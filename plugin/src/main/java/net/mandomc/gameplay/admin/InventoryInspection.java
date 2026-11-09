package net.mandomc.gameplay.admin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.mandomc.commands.CommandMessages;
import net.mandomc.gameplay.ui.MenuItems;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Live 41-slot inspection. Left-click swaps authoritative slots; unsupported transfer modes are cancelled. */
public final class InventoryInspection implements Listener, AutoCloseable {
    private record View(Player viewer, Player target, Inventory inventory, boolean ender) { }
    private final Map<UUID, View> views = new HashMap<>();
    private final BukkitTask refresh;
    public InventoryInspection(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        refresh = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 1, 1);
    }
    public void open(Player viewer, Player target, boolean ender, CommandMessages messages) {
        var inventory = Bukkit.createInventory(null, ender ? 36 : 54,
                messages.text(ender ? "ender-title" : "inventory-title", target.getName()));
        var view = new View(viewer, target, inventory, ender);
        MenuItems.fill(inventory);
        copy(view);
        viewer.openInventory(inventory);
        views.put(viewer.getUniqueId(), view);
        messages.send(viewer, "inventory-help");
    }
    private Inventory source(View view) { return view.ender() ? view.target().getEnderChest() : view.target().getInventory(); }
    private void copy(View view) {
        var source = source(view);
        for (int slot = 0; slot < source.getSize(); slot++) {
            var item = source.getItem(slot);
            view.inventory().setItem(slot, item == null ? null : item.clone());
        }
    }
    private boolean valid(View view) {
        return view.target().isOnline() && !view.target().isDead() && !view.viewer().isDead()
                && view.viewer().hasPermission("mandomc.admin." + (view.ender() ? "enderchest" : "invsee"))
                && (view.target().equals(view.viewer()) || view.viewer().hasPermission("mandomc.admin.others"));
    }
    private void refresh() {
        for (var view : List.copyOf(views.values())) {
            if (!valid(view)) view.viewer().closeInventory(); else copy(view);
        }
    }
    @EventHandler(ignoreCancelled = true) public void click(InventoryClickEvent event) {
        var view = views.get(event.getWhoClicked().getUniqueId());
        if (view == null || event.getView().getTopInventory() != view.inventory()) return;
        event.setCancelled(true);
        if (!valid(view)) { view.viewer().closeInventory(); return; }
        if (event.getRawSlot() >= view.inventory().getSize() && (event.isLeftClick() || event.isRightClick())
                && !event.isShiftClick()) { event.setCancelled(false); return; }
        if (event.getClick() != org.bukkit.event.inventory.ClickType.LEFT
                || event.getRawSlot() < 0 || event.getRawSlot() >= view.inventory().getSize()) return;
        var source = source(view);
        int slot = event.getRawSlot();
        if (slot >= source.getSize()) return;
        var cursor = view.viewer().getItemOnCursor();
        var held = source.getItem(slot);
        source.setItem(slot, cursor.isEmpty() ? null : cursor.clone());
        view.viewer().setItemOnCursor(held == null ? new ItemStack(Material.AIR) : held.clone());
        copy(view);
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (views.containsKey(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent event) { views.remove(event.getPlayer().getUniqueId()); }
    private void closeTarget(Player player) {
        for (var view : List.copyOf(views.values())) {
            if (view.target().equals(player) || view.viewer().equals(player)) view.viewer().closeInventory();
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event) { closeTarget(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) { closeTarget(event.getEntity()); }
    @Override public void close() {
        refresh.cancel();
        for (var view : List.copyOf(views.values())) view.viewer().closeInventory();
        views.clear(); HandlerList.unregisterAll(this);
    }
}
