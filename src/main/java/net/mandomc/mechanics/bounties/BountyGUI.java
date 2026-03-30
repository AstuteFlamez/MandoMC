package net.mandomc.mechanics.bounties;

import java.util.ArrayList;
import java.util.List;

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

/**
 * GUI displaying all active bounties.
 *
 * Players can view bounty details, remove their own bounty for a refund,
 * or view the last known location of a target.
 */
public class BountyGUI extends InventoryGUI {

    private final GUIManager guiManager;

    /**
     * Creates the bounty GUI.
     *
     * @param guiManager the GUI manager for reopening this GUI after actions
     */
    public BountyGUI(GUIManager guiManager) {
        this.guiManager = guiManager;
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

        int slot = 0;
        for (Bounty bounty : BountyStorage.getAll()) {
            addButton(slot++, createBountyButton(bounty, player));
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
     * @param viewer the player viewing the GUI
     * @return the button
     */
    private InventoryButton createBountyButton(Bounty bounty, Player viewer) {
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

                        clicker.sendMessage(LangManager.get("bounties.refunded"));
                        clicker.playSound(clicker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
                        guiManager.openGUI(new BountyGUI(guiManager), clicker);
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

        lore.add("");
        lore.add(color("&eClick to track"));
        lore.add(color("&cClick to remove your bounty"));

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Fills all GUI slots with a filler item from config.
     */
    private void fillBackground() {
        ConfigurationSection section = BountyConfig.get().getConfigurationSection("bounty.filler");

        Material mat = Material.matchMaterial(section.getString("material", "BLACK_STAINED_GLASS_PANE"));
        ItemStack filler = new ItemStack(mat == null ? Material.BLACK_STAINED_GLASS_PANE : mat);

        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(section.getString("name", " ")));
            meta.setLore(section.getStringList("lore"));
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < getInventory().getSize(); i++) {
            addButton(i, new InventoryButton()
                    .creator(player -> filler)
                    .consumer(event -> {}));
        }
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
