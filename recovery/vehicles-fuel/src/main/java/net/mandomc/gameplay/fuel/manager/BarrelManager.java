package net.mandomc.gameplay.fuel.manager;

import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import net.mandomc.MandoMC;
import net.mandomc.core.integration.OptionalPluginSupport;

import net.mandomc.gameplay.fuel.FuelManager;

/**
 * Manages visual updates for placed rhydonium barrel armor stands.
 *
 * Handles custom model data selection based on fuel level for both
 * inventory items and armor stand models. Also creates and updates
 * the fuel level hologram displayed above barrels.
 */
public class BarrelManager {

    public static final String BARREL_TAG = "rhydonium_barrel";
    private static final String HOLOGRAM_ID_PREFIX = "fuel_barrel_";
    private static final String ANCHOR_X = "fuel_barrel_anchor_x";
    private static final String ANCHOR_Y = "fuel_barrel_anchor_y";
    private static final String ANCHOR_Z = "fuel_barrel_anchor_z";

    /**
     * Updates the custom model data of an inventory barrel item based on fuel percentage.
     *
     * CMD values 6-10 correspond to 0%, 25%, 50%, 75%, and 100% fill levels.
     *
     * @param item the barrel item stack to update
     * @return the updated item
     */
    public static ItemStack updateItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int fuel = FuelManager.getCurrentFuel(item);
        int max = FuelManager.getMaxFuel(item);

        if (max <= 0) return item;

        double percent = (fuel * 100.0) / max;

        int cmd;

        if (percent >= 100) cmd = 10;
        else if (percent >= 75) cmd = 9;
        else if (percent >= 50) cmd = 8;
        else if (percent >= 25) cmd = 7;
        else cmd = 6;

        meta.setCustomModelData(cmd);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Updates the custom model data of a barrel armor stand model based on fuel percentage.
     *
     * CMD values 11-15 correspond to stages 0-4 (0%, 25%, 50%, 75%, 100% fill levels).
     *
     * @param item the barrel model item stack to update
     * @return the updated item
     */
    public static ItemStack updateModel(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int fuel = FuelManager.getCurrentFuel(item);
        int max = FuelManager.getMaxFuel(item);

        if (max <= 0) return item;

        double percent = (fuel * 100.0) / max;

        int stage;

        if (percent >= 100) stage = 4;
        else if (percent >= 75) stage = 3;
        else if (percent >= 50) stage = 2;
        else if (percent >= 25) stage = 1;
        else stage = 0;

        int cmd = 11 + stage;

        meta.setCustomModelData(cmd);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Applies a +5 custom model data offset to a barrel item for use when placed.
     *
     * @param item the item to offset
     * @return the modified item
     */
    public static ItemStack applyPlacementOffset(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return item;

        int cmd = meta.getCustomModelData();
        meta.setCustomModelData(cmd + 5);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Records the placed block used as this barrel's collision anchor.
     */
    public static void setAnchorBlock(ArmorStand barrel, Block block) {
        if (barrel == null || block == null) return;

        PersistentDataContainer data = barrel.getPersistentDataContainer();
        data.set(key(ANCHOR_X), PersistentDataType.INTEGER, block.getX());
        data.set(key(ANCHOR_Y), PersistentDataType.INTEGER, block.getY());
        data.set(key(ANCHOR_Z), PersistentDataType.INTEGER, block.getZ());
    }

    /**
     * Returns the barrel's collision anchor block if recorded.
     */
    public static Block getAnchorBlock(ArmorStand barrel) {
        if (barrel == null) return null;

        PersistentDataContainer data = barrel.getPersistentDataContainer();
        Integer x = data.get(key(ANCHOR_X), PersistentDataType.INTEGER);
        Integer y = data.get(key(ANCHOR_Y), PersistentDataType.INTEGER);
        Integer z = data.get(key(ANCHOR_Z), PersistentDataType.INTEGER);

        if (x == null || y == null || z == null) return null;
        World world = barrel.getWorld();
        return world.getBlockAt(x, y, z);
    }

    /**
     * Places a barrier at the anchor block for collision and stores its location.
     */
    public static boolean placeBarrier(ArmorStand barrel, Block block) {
        if (barrel == null || block == null) return false;
        Block upper = block.getRelative(0, 1, 0);
        if (block.getType() != Material.AIR) return false;
        if (upper.getType() != Material.AIR) return false;

        block.setType(Material.BARRIER, false);
        upper.setType(Material.BARRIER, false);
        setAnchorBlock(barrel, block);
        return true;
    }

    /**
     * Removes the barrier at the barrel's anchor location, if present.
     */
    public static void removeBarrier(ArmorStand barrel) {
        Block anchor = getAnchorBlock(barrel);
        if (anchor == null) return;
        Block upper = anchor.getRelative(0, 1, 0);

        if (anchor.getType() == Material.BARRIER) {
            anchor.setType(Material.AIR, false);
        }
        if (upper.getType() == Material.BARRIER) {
            upper.setType(Material.AIR, false);
        }
    }

    /**
     * Resolves a placed barrel stand for a given barrier block.
     */
    public static ArmorStand findBarrelAt(Block block) {
        if (block == null) return null;

        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        for (var entity : block.getWorld().getNearbyEntities(center, 0.8, 1.8, 0.8)) {
            if (!(entity instanceof ArmorStand stand)) continue;
            if (!stand.getScoreboardTags().contains(BARREL_TAG)) continue;

            Block anchor = getAnchorBlock(stand);
            if (anchor != null && anchor.getX() == block.getX() && anchor.getZ() == block.getZ()) {
                int baseY = anchor.getY();
                int clickedY = block.getY();
                if (clickedY == baseY || clickedY == baseY + 1) {
                    return stand;
                }
            }
        }

        return null;
    }

    /**
     * Creates (or refreshes) the FancyHolograms display for this barrel.
     */
    public static void createHologram(ArmorStand barrel) {
        updateHologram(barrel);
    }

    /**
     * Updates the FancyHolograms display for this barrel.
     */
    public static void updateHologram(ArmorStand barrel) {
        HologramManager manager = getHologramManager();
        if (manager == null || barrel == null || !barrel.isValid()) {
            return;
        }

        Location location = barrel.getLocation().clone().add(0, 2.00, 0);
        List<String> lines = List.of(buildFuelBar(barrel));
        String hologramId = hologramId(barrel);

        manager.getHologram(hologramId).ifPresentOrElse(existing -> {
            if (existing.getData() instanceof TextHologramData textData) {
                textData.setLocation(location);
                textData.setText(lines);
                textData.setVisibilityDistance(5);
                return;
            }
            manager.removeHologram(existing);
            createHologramData(manager, hologramId, location, lines);
        }, () -> createHologramData(manager, hologramId, location, lines));
    }

    /**
     * Removes this barrel's FancyHolograms display, if present.
     */
    public static void removeHologram(ArmorStand barrel) {
        HologramManager manager = getHologramManager();
        if (manager == null || barrel == null) return;
        manager.getHologram(hologramId(barrel)).ifPresent(manager::removeHologram);
    }

    /**
     * Rebuilds all barrel holograms from currently loaded barrel entities.
     */
    public static void refreshAllHolograms() {
        if (!OptionalPluginSupport.hasFancyHolograms()) return;

        for (World world : MandoMC.getInstance().getServer().getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.getScoreboardTags().contains(BARREL_TAG)) {
                    updateHologram(stand);
                }
            }
        }
    }

    /**
     * Removes all barrel holograms during module disable/reload.
     */
    public static void removeAllHolograms() {
        if (!OptionalPluginSupport.hasFancyHolograms()) return;

        for (World world : MandoMC.getInstance().getServer().getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                if (stand.getScoreboardTags().contains(BARREL_TAG)) {
                    removeHologram(stand);
                }
            }
        }
    }

    private static void createHologramData(HologramManager manager, String id, Location location, List<String> lines) {
        TextHologramData data = new TextHologramData(id, location);
        data.setText(lines);
        data.setBillboard(Display.Billboard.CENTER);
        data.setTextShadow(true);
        data.setVisibilityDistance(5);
        data.setBackground(Color.fromARGB(0, 0, 0, 0));

        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);
    }

    private static String buildFuelBar(ArmorStand barrel) {
        ItemStack item = barrel.getEquipment().getHelmet();
        if (item == null) return ChatColor.DARK_GRAY + "■■■■■■■■■■";

        int fuel = FuelManager.getCurrentFuel(item);
        int max = FuelManager.getMaxFuel(item);
        if (max <= 0) return ChatColor.DARK_GRAY + "■■■■■■■■■■";

        double percent = (double) fuel / max;
        int filled = (int) Math.round(percent * 10);
        filled = Math.max(0, Math.min(10, filled));

        ChatColor color;
        if (percent >= 0.75) color = ChatColor.GREEN;
        else if (percent >= 0.40) color = ChatColor.YELLOW;
        else if (percent > 0) color = ChatColor.GOLD;
        else color = ChatColor.RED;

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            if (i < filled) bar.append(color).append("■");
            else bar.append(ChatColor.DARK_GRAY).append("■");
        }
        return bar.toString();
    }

    private static String hologramId(ArmorStand barrel) {
        return HOLOGRAM_ID_PREFIX + barrel.getUniqueId().toString().replace("-", "");
    }

    private static NamespacedKey key(String suffix) {
        return new NamespacedKey(MandoMC.getInstance(), suffix);
    }

    private static HologramManager getHologramManager() {
        if (!OptionalPluginSupport.hasFancyHolograms()) {
            return null;
        }
        try {
            return FancyHologramsPlugin.get().getHologramManager();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
