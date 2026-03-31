package net.mandomc.gameplay.warp.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.gameplay.warp.config.WarpConfig;
import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for displaying and selecting available warps.
 *
 * Builds a menu from the warp configuration and allows players
 * to teleport by clicking on warp icons.
 */
public class WarpsGUI extends InventoryGUI {

    private final GUIManager guiManager;
    private final WarpConfig warpConfig;

    /**
     * Creates a new warps GUI.
     *
     * @param guiManager the GUI manager
     * @param warpConfig typed warp configuration
     */
    public WarpsGUI(GUIManager guiManager, WarpConfig warpConfig) {
        this.guiManager = guiManager;
        this.warpConfig = warpConfig;
    }

    /**
     * Creates the backing inventory for the GUI.
     */
    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(
                null,
                6 * 9,
                color("&4&lMandoMC Warps")
        );
    }

    /**
     * Populates the GUI with warp buttons.
     *
     * @param player the player viewing the GUI
     */
    @Override
    public void decorate(Player player) {

        ConfigurationSection warps = getWarpSection();
        if (warps == null) return;

        for (String warpName : warps.getKeys(false)) {

            ConfigurationSection warp = warps.getConfigurationSection(warpName);
            if (warp == null) continue;

            int slot = warp.getInt("slot", -1);
            if (slot == -1) continue;

            ItemStack icon = buildWarpItem(warp);

            this.addButton(slot, createWarpButton(icon, warpName));
        }

        super.decorate(player);
    }

    /**
     * Builds the display item for a warp.
     */
    private ItemStack buildWarpItem(ConfigurationSection warp) {

        String name = warp.getString("name", "&7Unknown Warp");
        List<String> description = warp.getStringList("description");

        Material material = Material.matchMaterial(
                warp.getString("material", "ENDER_PEARL")
        );
        if (material == null) material = Material.ENDER_PEARL;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.setDisplayName(color(name));

        int customModelData = warp.getInt("custommodeldata", 1);
        meta.setCustomModelData(customModelData);

        List<String> lore = new ArrayList<>();
        for (String line : description) {
            lore.add(color(line));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Creates a clickable button for a warp.
     */
    private InventoryButton createWarpButton(ItemStack icon, String warpName) {

        return new InventoryButton()
                .creator(player -> icon)
                .consumer(event -> {

                    Player player = (Player) event.getWhoClicked();

                    Location location = getWarpLocation(warpName, player);
                    if (location == null) {
                        player.sendMessage(LangManager.get("warps.world-offline"));
                        player.closeInventory();
                        return;
                    }

                    player.teleport(location);
                    player.playSound(location, Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.3f);
                    player.closeInventory();
                });
    }

    /**
     * Builds a warp location from config.
     */
    private Location getWarpLocation(String warpName, Player player) {

        ConfigurationSection warp = warpConfig.getWarpSection(warpName);
        if (warp == null) return null;

        String worldName = warp.getString("world", player.getWorld().getName());
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = warp.getDouble("x");
        double y = warp.getDouble("y");
        double z = warp.getDouble("z");
        float yaw = (float) warp.getDouble("yaw");
        float pitch = (float) warp.getDouble("pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Gets the warp configuration section.
     */
    private ConfigurationSection getWarpSection() {
        return warpConfig.getSection("warps");
    }

    /**
     * Applies color formatting.
     */
    private String color(String text) {
        return LangManager.colorize(text);
    }
}