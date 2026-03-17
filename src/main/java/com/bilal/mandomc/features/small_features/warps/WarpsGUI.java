package com.bilal.mandomc.features.small_features.warps;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.bilal.mandomc.guis.GUIManager;
import com.bilal.mandomc.guis.InventoryButton;

import java.util.ArrayList;
import java.util.List;

public class WarpsGUI extends com.bilal.mandomc.guis.InventoryGUI {

    private final GUIManager guiManager;

    public WarpsGUI(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(
                null,
                6 * 9,
                ChatColor.translateAlternateColorCodes('&', "&4&lMandoMC Warps")
        );
    }

    @Override
    public void decorate(Player player) {

        ConfigurationSection warps = WarpConfig.get().getConfigurationSection("warps");
        if (warps == null) return;

        for (String warpName : warps.getKeys(false)) {

            String path = "warps." + warpName;

            int slot = WarpConfig.get().getInt(path + ".slot", -1);
            if (slot == -1) continue;

            String displayName = WarpConfig.get().getString(path + ".name", "&7Unknown Warp");
            List<String> loreLines = WarpConfig.get().getStringList(path + ".description");

            Material material = Material.matchMaterial(
                    WarpConfig.get().getString(path + ".material", "ENDER_PEARL")
            );

            if (material == null) material = Material.ENDER_PEARL;

            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {

                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

                int customModelData = WarpConfig.get().getInt(path + ".custommodeldata", 1);
                meta.setCustomModelData(customModelData);

                List<String> lore = new ArrayList<>();

                for (String line : loreLines) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }

                meta.setLore(lore);

                item.setItemMeta(meta);
            }

            this.addButton(slot, createWarpButton(item, warpName));
        }

        super.decorate(player);
    }

    private InventoryButton createWarpButton(ItemStack itemStack, String warpName) {

        return new InventoryButton()
                .creator(player -> itemStack)
                .consumer(event -> {

                    Player player = (Player) event.getWhoClicked();
                    String path = "warps." + warpName;

                    double x = WarpConfig.get().getDouble(path + ".x");
                    double y = WarpConfig.get().getDouble(path + ".y");
                    double z = WarpConfig.get().getDouble(path + ".z");

                    float yaw = (float) WarpConfig.get().getDouble(path + ".yaw");
                    float pitch = (float) WarpConfig.get().getDouble(path + ".pitch");

                    String worldName = WarpConfig.get().getString(
                            path + ".world",
                            player.getWorld().getName()
                    );

                    World world = Bukkit.getWorld(worldName);

                    if (world == null) {
                        player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7World '" + worldName + "' is not loaded!");
                        player.closeInventory();
                        return;
                    }

                    Location loc = new Location(world, x, y, z, yaw, pitch);

                    player.teleport(loc);

                    player.playSound(loc, Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.3f);

                    player.closeInventory();
                });
    }
}