package net.mandomc.mechanics.bounties;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import java.util.*;
import java.util.stream.Collectors;

/**
 * GUI for selecting a player to place a bounty on.
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
                .filter(p -> !p.equals(player))
                .sorted(Comparator.comparing(Player::getName))
                .collect(Collectors.toList());

        if (players.isEmpty()) {
            addButton(22, new InventoryButton()
                    .creator(p -> createNoPlayersItem())
                    .consumer(e -> {}));
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
                    .creator(p -> createNavItem(Material.ARROW, "&e&l« &7Previous Page"))
                    .consumer(e -> guiManager.openGUI(new BountySelectGUI(guiManager, page - 1), player)));
        }

        if (end < players.size()) {
            addButton(53, new InventoryButton()
                    .creator(p -> createNavItem(Material.ARROW, "&7Next Page &e&l»"))
                    .consumer(e -> guiManager.openGUI(new BountySelectGUI(guiManager, page + 1), player)));
        }

        super.decorate(player);
    }

    /**
     * Player button
     */
    private InventoryButton createPlayerButton(Player target) {
        return new InventoryButton()
                .creator(p -> buildSkull(target.getName()))
                .consumer(e -> {
                    Player clicker = (Player) e.getWhoClicked();
                    clicker.closeInventory();
                    clicker.showDialog(BountyDialogFactory.create(clicker, target));
                });
    }

    /**
     * YOUR SKULL METHOD (fixed + improved)
     */
    public ItemStack buildSkull(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();

        PlayerProfile profile = null;
        profile = Bukkit.getServer().createProfile(playerName);
        profile.update();
        if(!profile.hasTextures()) {
            String[] skinData = getSkinDataFromUUID(getUUIDFromName(playerName));
            profile.getProperties().add(new ProfileProperty("textures", skinData[0], skinData[1]));
        }
        if(profile != null) {
            skullMeta.setPlayerProfile(profile);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    /**
     * No players item
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
     * Navigation item
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
     * Background filler
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
                    .creator(p -> filler)
                    .consumer(e -> {}));
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // =========================
    // 🔧 REQUIRED HELPERS (you MUST implement these)
    // =========================

    private UUID getUUIDFromName(String name) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(name);
        return p.getUniqueId();
    }

    private String[] getSkinDataFromUUID(UUID uuid) {
        // You already had this in your pastebin system
        // return [value, signature]

        // Placeholder — replace with your actual implementation
        return null;
    }
}