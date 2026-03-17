package com.astuteflamez.mandomc.features.events.guis;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.astuteflamez.mandomc.features.events.EventDefinition;
import com.astuteflamez.mandomc.features.events.EventManager;
import com.astuteflamez.mandomc.features.events.GameEvent;

import java.util.ArrayList;

public class EventMenu {

    private final EventManager manager;

    public EventMenu(EventManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        String title = manager.color(manager.getConfig().getString("gui.title", "&8Server Events"));
        int size = manager.getConfig().getInt("gui.size", 27);

        Inventory inv = Bukkit.createInventory(null, size, title);

        Material filler = Material.matchMaterial(manager.getConfig().getString("gui.filler", "GRAY_STAINED_GLASS_PANE"));
        if (filler == null) filler = Material.GRAY_STAINED_GLASS_PANE;

        ItemStack fillerItem = new ItemStack(filler);
        ItemMeta fillerMeta = fillerItem.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            fillerItem.setItemMeta(fillerMeta);
        }

        for (int i = 0; i < size; i++) {
            inv.setItem(i, fillerItem);
        }

        inv.setItem(11, buildCurrentEventItem());
        inv.setItem(13, buildNextEventItem());
        inv.setItem(15, buildChanceItem());

        player.openInventory(inv);
    }

    private ItemStack buildCurrentEventItem() {
        GameEvent active = manager.getActiveEvent();

        Material mat = active == null ? Material.BARRIER : Material.EMERALD_BLOCK;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(active == null ? "§cNo Active Event" : "§aCurrent Event");
            ArrayList<String> lore = new ArrayList<>();

            if (active == null) {
                lore.add("§7There is no event running.");
            } else {
                lore.add("§7" + active.getDisplayName());
                lore.add("§7State: §f" + manager.getState());
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack buildNextEventItem() {
        GameEvent queued = manager.getQueuedEvent();

        Material mat = queued == null ? Material.CLOCK : Material.NETHER_STAR;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6Next Event");
            ArrayList<String> lore = new ArrayList<>();

            lore.add("§7Queued: §f" + (queued == null ? "Not selected" : queued.getDisplayName()));
            lore.add("§7Starts in: §f" + formatDuration(manager.getSecondsUntilNextHour()));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack buildChanceItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§bEvent Chances");
            ArrayList<String> lore = new ArrayList<>();

            manager.getCurrentChances().forEach((id, pct) -> {
                EventDefinition def = manager.getDefinition(id);
                String name = def == null ? id : def.getDisplayName();
                lore.add("§7" + name + " §f- " + String.format("%.1f", pct) + "%");
            });

            if (lore.isEmpty()) {
                lore.add("§7No eligible events.");
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private String formatDuration(long totalSeconds) {
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;

        if (minutes <= 0) {
            return seconds + " Sec";
        }

        if (seconds <= 0) {
            return minutes + " Min";
        }

        return minutes + " Min " + seconds + " Sec";
    }
}