package net.mandomc.server.events.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.core.LangManager;
import net.mandomc.server.events.model.EventDefinition;
import net.mandomc.server.events.EventManager;
import net.mandomc.server.events.model.GameEvent;
import net.mandomc.server.events.model.EventState;
import net.mandomc.server.shop.gui.ShopGUI;

import java.util.ArrayList;

public class EventMenu {

    private static final int BLANK_MODEL_DATA = 5;

    private final EventManager manager;

    public EventMenu(EventManager manager) {
        this.manager = manager;
    }

    public void open(Player player) {
        String title = manager.color(eventMenuTitle(manager.getActiveEvent() != null));
        int size = manager.getConfig().getInt("gui.size", 27);

        Inventory inv = Bukkit.createInventory(null, size, title);

        ItemStack fillerItem = new ItemStack(Material.FLINT);
        ItemMeta fillerMeta = fillerItem.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setCustomModelData(BLANK_MODEL_DATA);
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

    public static String eventMenuTitle(boolean hasActiveEvent) {
        String base = ShopGUI.SHOP_TITLE;
        return base.substring(0, base.length() - 1) + (hasActiveEvent ? "ĵ" : "Ĵ");
    }

    private ItemStack buildCurrentEventItem() {
        GameEvent active = manager.getActiveEvent();

        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setCustomModelData(BLANK_MODEL_DATA);
            meta.setDisplayName(active == null
                    ? LangManager.get("events.menu.current.none-title")
                    : LangManager.get("events.menu.current.title"));
            ArrayList<String> lore = new ArrayList<>();

            if (active == null) {
                lore.add(LangManager.get("events.menu.current.none-lore"));
            } else {
                lore.add("§7" + active.getDisplayName());
                lore.add(LangManager.get("events.menu.current.state", "%state%", formatState(manager.getState())));
            }

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack buildNextEventItem() {
        GameEvent queued = manager.getQueuedEvent();

        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setCustomModelData(BLANK_MODEL_DATA);
            meta.setDisplayName(LangManager.get("events.menu.next.title"));
            ArrayList<String> lore = new ArrayList<>();

            lore.add(LangManager.get(
                    "events.menu.next.queued",
                    "%event%",
                    queued == null ? LangManager.get("events.menu.next.not-selected") : queued.getDisplayName()
            ));
            lore.add(LangManager.get("events.menu.next.starts-in", "%time%", formatDuration(manager.getSecondsUntilNextHour())));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack buildChanceItem() {
        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setCustomModelData(BLANK_MODEL_DATA);
            meta.setDisplayName(LangManager.get("events.menu.chances.title"));
            ArrayList<String> lore = new ArrayList<>();

            manager.getCurrentChances().forEach((id, pct) -> {
                EventDefinition def = manager.getDefinition(id);
                String name = def == null ? id : def.getDisplayName();
                lore.add(LangManager.get(
                        "events.menu.chances.entry",
                        "%event%", name,
                        "%chance%", String.format("%.1f", pct)
                ));
            });

            if (lore.isEmpty()) {
                lore.add(LangManager.get("events.menu.chances.empty"));
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

    private String formatState(EventState state) {
        if (state == null) {
            return LangManager.get("events.menu.state.idle");
        }
        return switch (state) {
            case IDLE -> LangManager.get("events.menu.state.idle");
            case STARTING_SOON -> LangManager.get("events.menu.state.starting-soon");
            case RUNNING -> LangManager.get("events.menu.state.running");
            case ENDING_SOON -> LangManager.get("events.menu.state.ending-soon");
        };
    }
}