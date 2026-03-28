package net.mandomc.mechanics.bounties;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.core.modules.core.EconomyModule;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

/**
 * GUI displaying all active bounties.
 *
 * Players can:
 * - View bounty info
 * - Remove their own bounty
 * - Track targets
 */
public class BountyGUI extends InventoryGUI {

    private final GUIManager guiManager;

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
     * Creates a clickable bounty entry.
     */
    private InventoryButton createBountyButton(Bounty bounty, Player viewer) {

        return new InventoryButton()
                .creator(p -> buildBountyItem(bounty))
                .consumer(e -> {

                    Player clicker = (Player) e.getWhoClicked();

                    // ✅ If player owns this bounty → remove
                    if (bounty.hasEntry(clicker.getUniqueId())) {

                        BountyEntry entry = bounty.getEntries().get(clicker.getUniqueId());

                        EconomyModule.deposit(clicker, entry.getAmount());

                        bounty.removeEntry(clicker.getUniqueId());

                        if (bounty.isEmpty()) {
                            BountyStorage.remove(bounty.getTarget());
                        }

                        clicker.sendMessage(prefix("&aBounty removed and refunded."));
                        clicker.playSound(clicker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

                        guiManager.openGUI(new BountyGUI(guiManager), clicker);
                        return;
                    }

                    // 🔍 Otherwise → tracking info
                    Location loc = bounty.getLastKnownLocation();

                    if (loc == null) {
                        clicker.sendMessage(prefix("&cNo location data available."));
                        return;
                    }

                    clicker.sendMessage(prefix("&eLast known location: "
                            + loc.getBlockX() + ", "
                            + loc.getBlockY() + ", "
                            + loc.getBlockZ()));

                    clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                });
    }

    /**
     * Builds the bounty display item.
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
     * Fills GUI with background.
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
                    .creator(p -> filler)
                    .consumer(e -> {}));
        }
    }

    /**
     * Formats time since last seen.
     */
    private String formatTimeAgo(long lastSeen) {

        if (lastSeen == 0) return "Unknown";

        long diff = System.currentTimeMillis() - lastSeen;

        long minutes = diff / 60000;
        if (minutes < 60) return minutes + "m ago";

        long hours = minutes / 60;
        return hours + "h ago";
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String prefix(String msg) {
        return color("&c&lBounties &8» " + msg);
    }
}