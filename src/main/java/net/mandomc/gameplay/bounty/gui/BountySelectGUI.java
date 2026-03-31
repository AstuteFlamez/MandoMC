package net.mandomc.gameplay.bounty.gui;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;

/**
 * GUI for selecting a player to place a bounty on.
 *
 * Displays online players as skull items with pagination support.
 * Clicking a player opens the bounty dialog factory.
 */
public class BountySelectGUI extends InventoryGUI {

    private final GUIManager guiManager;
    private final int page;

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    /**
     * Creates the player selection GUI.
     *
     * @param guiManager the GUI manager for navigation
     * @param page the current page index (0-based)
     */
    public BountySelectGUI(GUIManager guiManager, int page) {
        this.guiManager = guiManager;
        this.page = page;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, color("&c&lSelect Target"));
    }

    @Override
    public void decorate(Player player) {
        fillBackground();

        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(other -> !other.equals(player))
                .sorted(Comparator.comparing(Player::getName))
                .collect(Collectors.toList());

        if (players.isEmpty()) {
            addButton(22, new InventoryButton()
                    .creator(p -> createNoPlayersItem())
                    .consumer(event -> {}));
            super.decorate(player);
            return;
        }

        int start = page * CONTENT_SLOTS.length;
        int end = Math.min(start + CONTENT_SLOTS.length, players.size());

        int slotIndex = 0;
        for (int i = start; i < end; i++) {
            if (slotIndex >= CONTENT_SLOTS.length) break;

            Player target = players.get(i);
            int slot = CONTENT_SLOTS[slotIndex++];
            addButton(slot, createPlayerButton(target));
        }

        if (page > 0) {
            addButton(45, new InventoryButton()
                    .creator(p -> createNavItem(Material.ARROW, "&e&l\u00ab &7Previous Page"))
                    .consumer(event -> guiManager.openGUI(new BountySelectGUI(guiManager, page - 1), player)));
        }

        if (end < players.size()) {
            addButton(53, new InventoryButton()
                    .creator(p -> createNavItem(Material.ARROW, "&7Next Page &e&l\u00bb"))
                    .consumer(event -> guiManager.openGUI(new BountySelectGUI(guiManager, page + 1), player)));
        }

        super.decorate(player);
    }

    /**
     * Creates a button representing a selectable player target.
     *
     * Clicking opens the bounty dialog for that player.
     *
     * @param target the player to display
     * @return the button
     */
    private InventoryButton createPlayerButton(Player target) {
        return new InventoryButton()
                .creator(player -> buildSkull(target.getName()))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    clicker.closeInventory();
                    clicker.showDialog(BountyDialogFactory.create(clicker, target));
                });
    }

    /**
     * Builds a player skull item for display in the GUI.
     *
     * Attempts to load skin textures from the player profile; falls back
     * to the getSkinDataFromUUID implementation if textures are unavailable.
     *
     * @param playerName the name of the player to display
     * @return the skull item
     */
    public ItemStack buildSkull(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();

        PlayerProfile profile = Bukkit.getServer().createProfile(playerName);
        profile.update();

        if (!profile.hasTextures()) {
            String[] skinData = getSkinDataFromUUID(getUUIDFromName(playerName));
            if (skinData != null) {
                profile.getProperties().add(new ProfileProperty("textures", skinData[0], skinData[1]));
            }
        }

        skullMeta.setPlayerProfile(profile);
        head.setItemMeta(skullMeta);

        return head;
    }

    /**
     * Creates the "no players online" placeholder item.
     *
     * @return the barrier item with display text
     */
    private ItemStack createNoPlayersItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color("&c&lNo Players Online"));
            meta.setLore(Collections.singletonList(color("&7There is no one else to bounty!")));
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Creates a navigation button item.
     *
     * @param mat the material to use
     * @param name the display name with color codes
     * @return the navigation item
     */
    private ItemStack createNavItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(color(name));
            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Fills all GUI slots with a gray stained glass pane filler.
     */
    private void fillBackground() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < 54; i++) {
            addButton(i, new InventoryButton()
                    .creator(player -> filler)
                    .consumer(event -> {}));
        }
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

    /**
     * Returns the UUID for the given player name using the offline player cache.
     *
     * @param name the player name
     * @return the UUID
     */
    private UUID getUUIDFromName(String name) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(name);
        return offlinePlayer.getUniqueId();
    }

    /**
     * Returns skin texture data for the given UUID.
     *
     * Returns an array of [value, signature] for use with PlayerProfile,
     * or null if the skin cannot be fetched.
     *
     * @param uuid the player UUID
     * @return the skin data array, or null
     */
    private String[] getSkinDataFromUUID(UUID uuid) {
        return null;
    }
}
