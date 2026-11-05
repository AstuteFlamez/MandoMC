package net.mandomc.gameplay.vehicle.gui;

import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.gameplay.vehicle.config.VehicleConfigResolver;
import net.mandomc.gameplay.vehicle.manager.VehicleSkinManager;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleSkinOption;
import net.mandomc.server.shop.gui.ShopGUI;
import net.mandomc.server.items.ItemRegistry;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Dedicated GUI to select vehicle skins.
 */
public class VehicleSkinGUI extends InventoryGUI {

    private static final int GUI_SIZE = 9;
    private static final int[] SKIN_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7};
    private static final int BACK_SLOT = 8;
    private static final int BLANK_MODEL_DATA = 5;
    private static final String SKIN_TITLE = ShopGUI.SHOP_TITLE.substring(0, ShopGUI.SHOP_TITLE.length() - 1) + "ķ";

    private final Vehicle vehicle;
    private final Player viewer;
    private final GUIManager guiManager;

    public VehicleSkinGUI(Vehicle vehicle, Player viewer, GUIManager guiManager) {
        this.vehicle = vehicle;
        this.viewer = viewer;
        this.guiManager = guiManager;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, GUI_SIZE, LangManager.colorize(SKIN_TITLE));
    }

    @Override
    public void decorate(Player player) {
        addSkinOptions();
        addBackButton();
        super.decorate(player);
    }

    private void addSkinOptions() {
        Map<String, VehicleSkinOption> options = VehicleConfigResolver.getSkinOptions(vehicle.getVehicleData().getItem());
        if (options.isEmpty()) return;

        int index = 0;
        for (VehicleSkinOption option : options.values()) {
            if (index >= SKIN_SLOTS.length) break;
            int slot = SKIN_SLOTS[index++];
            addButton(slot, buildSkinButton(option));
        }
    }

    private InventoryButton buildSkinButton(VehicleSkinOption option) {
        return new InventoryButton()
                .creator(p -> buildSkinItem(option))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    if (!clicker.getUniqueId().equals(vehicle.getOwnerUUID())) {
                        clicker.sendMessage(LangManager.get("vehicles.owner-only-action"));
                        return;
                    }
                    if (!VehicleSkinManager.playerHasSkinPermission(clicker, option)) {
                        clicker.sendMessage(LangManager.get("vehicles.skin.no-permission", "%skin%", option.id()));
                        return;
                    }
                    if (hasOtherRiders()) {
                        clicker.sendMessage(LangManager.get("vehicles.skin.cannot-change-occupied"));
                        return;
                    }

                    boolean applied = VehicleSkinManager.applySkinToVehicle(vehicle, option);
                    if (!applied) {
                        clicker.sendMessage(LangManager.get("vehicles.skin.invalid"));
                        return;
                    }

                    clicker.sendMessage(LangManager.get("vehicles.skin.changed", "%skin%", option.id()));
                    guiManager.openGUI(new VehicleSkinGUI(vehicle, clicker, guiManager), clicker);
                });
    }

    private ItemStack buildSkinItem(VehicleSkinOption option) {
        ItemStack base = ItemRegistry.get(vehicle.getItemId());
        if (base == null) {
            base = vehicle.getVehicleData().getItem();
        }
        if (base == null) {
            base = new ItemStack(Material.MINECART);
        }

        ItemStack item = VehicleSkinManager.applySkinToItem(base, option);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Skin: " + ChatColor.LIGHT_PURPLE + option.id());
        lore.add("");
        boolean selected = option.id().equalsIgnoreCase(vehicle.getSelectedSkinId());
        if (selected) {
            lore.add(ChatColor.GREEN + "Selected");
        } else {
            lore.add(ChatColor.YELLOW + "Click to select");
        }

        if (option.hasPermissionNode()) {
            boolean hasAccess = viewer.hasPermission(option.permission());
            lore.add(hasAccess
                    ? ChatColor.GRAY + "Permission: " + ChatColor.GREEN + "Granted"
                    : ChatColor.GRAY + "Permission: " + ChatColor.RED + "Missing");
        } else {
            lore.add(ChatColor.GRAY + "Permission: " + ChatColor.GREEN + "None required");
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void addBackButton() {
        addButton(BACK_SLOT, new InventoryButton()
                .creator(p -> {
                    ItemStack blank = new ItemStack(Material.FLINT);
                    ItemMeta meta = blank.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ChatColor.RED + "Back");
                        meta.setCustomModelData(BLANK_MODEL_DATA);
                        blank.setItemMeta(meta);
                    }
                    return blank;
                })
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    guiManager.openGUI(new VehicleInteractGUI(vehicle, clicker, guiManager), clicker);
                }));
    }

    private boolean hasOtherRiders() {
        UUID owner = vehicle.getOwnerUUID();
        return vehicle.getOccupants().keySet().stream().anyMatch(uuid -> !uuid.equals(owner));
    }
}
