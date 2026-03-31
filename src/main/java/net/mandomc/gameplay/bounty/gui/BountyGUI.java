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
import org.bukkit.configuration.ConfigurationSection;
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
import net.mandomc.gameplay.bounty.config.BountyConfig;
import net.mandomc.gameplay.bounty.BountyStorage;
import net.mandomc.gameplay.bounty.BountyShowcaseManager;
import net.mandomc.gameplay.bounty.model.BountyEntry;

/**
 * GUI displaying all active bounties.
 *
 * Players can view bounty details, remove their own bounty for a refund,
 * or view the last known location of a target.
 */
public class BountyGUI extends InventoryGUI {

    private final GUIManager guiManager;
    private final int page;

    private static final int[] CONTENT_SLOTS = {
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
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
        ConfigurationSection cfg = BountyConfig.get().getConfigurationSection("bounty.gui");

        if (cfg == null) {
            return Bukkit.createInventory(null, 54, "Bounties");
        }

        return Bukkit.createInventory(
                null,
                cfg.getInt("size", 54),
                color(cfg.getString("title", "&c&lBounties"))
        );
    }

    @Override
    public void decorate(Player player) {
        fillBackground();

        List<Bounty> sorted = BountyStorage.getAll().stream()
                .sorted(Comparator.comparingDouble(Bounty::getTotal).reversed())
                .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            addButton(22, new InventoryButton()
                    .creator(p -> createStatusItem(Material.BARRIER, "&cNo Active Bounties", "&7There are no active bounty targets."))
                    .consumer(event -> {}));
            super.decorate(player);
            return;
        }

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

        addButton(49, new InventoryButton()
                .creator(p -> createStatusItem(Material.PAPER,
                        "&ePage " + (currentPage + 1) + "&7/&e" + (maxPage + 1),
                        "&7Showing " + (start + 1) + "-&f" + end + "&7 of &f" + sorted.size()))
                .consumer(event -> {}));

        if (currentPage > 0) {
            addButton(45, new InventoryButton()
                    .creator(p -> createStatusItem(Material.ARROW, "&e&l<< Previous", "&7Go to page " + currentPage))
                    .consumer(event -> guiManager.openGUI(new BountyGUI(guiManager, currentPage - 1), player)));
        }

        if (end < sorted.size()) {
            addButton(53, new InventoryButton()
                    .creator(p -> createStatusItem(Material.ARROW, "&e&lNext >>", "&7Go to page " + (currentPage + 2)))
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

                    if (bounty.hasEntry(clicker.getUniqueId())) {
                        BountyEntry entry = bounty.getEntries().get(clicker.getUniqueId());
                        EconomyModule.deposit(clicker, entry.getAmount());
                        bounty.removeEntry(clicker.getUniqueId());

                        if (bounty.isEmpty()) {
                            BountyStorage.remove(bounty.getTarget());
                        }

                        BountyStorage.save();
                        BountyShowcaseManager.update();

                        clicker.sendMessage(LangManager.get("bounties.refunded"));
                        clicker.playSound(clicker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                        guiManager.openGUI(new BountyGUI(guiManager, currentPage), clicker);
                        return;
                    }

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
     * Fills all GUI slots with a filler item from config.
     */
    private void fillBackground() {
        ConfigurationSection section = BountyConfig.get().getConfigurationSection("bounty.filler");

        String materialName = section == null ? "BLACK_STAINED_GLASS_PANE" : section.getString("material", "BLACK_STAINED_GLASS_PANE");
        Material mat = Material.matchMaterial(materialName);
        ItemStack filler = new ItemStack(mat == null ? Material.BLACK_STAINED_GLASS_PANE : mat);

        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            String name = section == null ? " " : section.getString("name", " ");
            meta.setDisplayName(color(name));
            meta.setLore(section == null ? new ArrayList<>() : section.getStringList("lore"));
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < getInventory().getSize(); i++) {
            addButton(i, new InventoryButton()
                    .creator(player -> filler)
                    .consumer(event -> {}));
        }
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
        return ChatColor.translateAlternateColorCodes('&', text);
    }

}
