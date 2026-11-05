package net.mandomc.gameplay.bounty.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.gameplay.bounty.model.Bounty;
import net.mandomc.gameplay.bounty.BountyStorage;
import net.mandomc.server.shop.gui.ShopGUI;

/**
 * GUI displaying all active bounties.
 *
 * Players can view bounty details, remove their own bounty for a refund,
 * or view the last known location of a target.
 */
public class BountyGUI extends InventoryGUI {

    private static final String BOUNTY_TITLE = ShopGUI.SHOP_TITLE.substring(0, ShopGUI.SHOP_TITLE.length() - 1) + "Ĳ";
    private static final int SLOT_INFO = 49;
    private static final int SLOT_PREVIOUS = 48;
    private static final int SLOT_NEXT = 50;
    private static final int MODEL_NEXT = 1;
    private static final int MODEL_PREVIOUS = 2;
    private static final int MODEL_BLANK = 5;

    private final GUIManager guiManager;
    private final int page;

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    /**
     * Creates the bounty GUI.
     *
     * @param guiManager the GUI manager for reopening this GUI after actions
     */
    public BountyGUI(GUIManager guiManager) {
        this(guiManager, 0);
    }

    /**
     * Creates the bounty GUI at a specific page.
     *
     * @param guiManager the GUI manager
     * @param page zero-based page index
     */
    public BountyGUI(GUIManager guiManager, int page) {
        this.guiManager = guiManager;
        this.page = Math.max(0, page);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, color(BOUNTY_TITLE));
    }

    @Override
    public void decorate(Player player) {
        List<Bounty> sorted = BountyStorage.getAll().stream()
                .sorted(Comparator.comparingDouble(Bounty::getTotal).reversed())
                .collect(Collectors.toList());

        int pageSize = CONTENT_SLOTS.length;
        int maxPage = (sorted.size() - 1) / pageSize;
        int currentPage = Math.min(page, maxPage);

        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, sorted.size());

        int slotIndex = 0;
        for (int i = start; i < end; i++) {
            int slot = CONTENT_SLOTS[slotIndex++];
            addButton(slot, createBountyButton(sorted.get(i), currentPage));
        }

        addButton(SLOT_INFO, new InventoryButton()
                .creator(p -> createInfoItem())
                .consumer(event -> {}));

        if (currentPage > 0) {
            addButton(SLOT_PREVIOUS, new InventoryButton()
                    .creator(p -> createNavItem("&9Previous Page", MODEL_PREVIOUS))
                    .consumer(event -> guiManager.openGUI(new BountyGUI(guiManager, currentPage - 1), player)));
        }

        if (end < sorted.size()) {
            addButton(SLOT_NEXT, new InventoryButton()
                    .creator(p -> createNavItem("&9Next Page", MODEL_NEXT))
                    .consumer(event -> guiManager.openGUI(new BountyGUI(guiManager, currentPage + 1), player)));
        }

        super.decorate(player);
    }

    /**
     * Creates a clickable button for a bounty entry.
     *
     * If the viewer placed this bounty, clicking removes it and refunds.
     * Otherwise, clicking shows the target's last known location.
     *
     * @param bounty the bounty to display
     * @return the button
     */
    private InventoryButton createBountyButton(Bounty bounty, int currentPage) {
        return new InventoryButton()
                .creator(player -> buildBountyItem(bounty))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    Location loc = bounty.getLastKnownLocation();

                    if (loc == null) {
                        clicker.sendMessage(LangManager.get("bounties.no-location"));
                        return;
                    }

                    clicker.sendMessage(LangManager.get("bounties.last-location",
                            "%x%", String.valueOf(loc.getBlockX()),
                            "%y%", String.valueOf(loc.getBlockY()),
                            "%z%", String.valueOf(loc.getBlockZ())));

                    clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                });
    }

    /**
     * Builds the display item for a bounty entry.
     *
     * @param bounty the bounty to represent
     * @return the player head item with bounty lore
     */
    private ItemStack buildBountyItem(Bounty bounty) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(bounty.getTarget());

        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return item;

        meta.setOwningPlayer(target);
        meta.setDisplayName(color("&c" + target.getName()));

        List<String> lore = new ArrayList<>();
        lore.add(color("&7Total Bounty: &a$" + EconomyModule.format(bounty.getTotal())));
        lore.add(color("&7Contributors: &f" + bounty.getEntries().size()));
        lore.add("");

        Location loc = bounty.getLastKnownLocation();

        if (loc != null) {
            lore.add(color("&7Last Seen:"));
            lore.add(color("&f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            lore.add(color("&7World: &f" + loc.getWorld().getName()));
            lore.add(color("&7Time: &f" + formatTimeAgo(bounty.getLastSeen())));
        } else {
            lore.add(color("&cNo tracking data"));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Creates a generic status/navigation item.
     *
     * @param material icon material
     * @param name display name
     * @param loreLine single lore line
     * @return item stack
     */
    private ItemStack createStatusItem(Material material, String name, String loreLine) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            List<String> lore = new ArrayList<>();
            lore.add(color(loreLine));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(MODEL_BLANK);
            meta.setDisplayName(color("&eBounty Info"));
            List<String> lore = new ArrayList<>();
            lore.add(color("&7Use &f/bounty place <player>"));
            lore.add(color("&7to place a bounty target."));
            lore.add(color("&7Click a head to view last known location."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNavItem(String name, int modelData) {
        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(modelData);
            meta.setDisplayName(color(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Formats a last-seen timestamp as a human-readable ago string.
     *
     * @param lastSeen the epoch millis timestamp
     * @return formatted string such as "5m ago" or "2h ago"
     */
    private String formatTimeAgo(long lastSeen) {
        if (lastSeen == 0) return "Unknown";

        long diff = System.currentTimeMillis() - lastSeen;
        long minutes = diff / 60000;

        if (minutes < 60) return minutes + "m ago";

        long hours = minutes / 60;
        return hours + "h ago";
    }

    /**
     * Translates color codes.
     *
     * @param text the text with color codes
     * @return the colorized string
     */
    private String color(String text) {
        return LangManager.colorize(text);
    }

}
