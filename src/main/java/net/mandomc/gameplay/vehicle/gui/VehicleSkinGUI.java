package net.mandomc.gameplay.vehicle.gui;

import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.gameplay.vehicle.config.VehicleConfigResolver;
import net.mandomc.gameplay.vehicle.manager.VehicleSkinManager;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleSkinOption;
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

    private static final int GUI_SIZE = 27;
    private static final Material FILLER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    private static final int[] SKIN_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int BACK_SLOT = 22;

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
        return Bukkit.createInventory(null, GUI_SIZE, LangManager.colorize("&5Vehicle Skins"));
    }

    @Override
    public void decorate(Player player) {
        fill();
        addSkinOptions();
        addBackButton();
        super.decorate(player);
    }

    private void fill() {
        ItemStack filler = new ItemStack(FILLER_MATERIAL);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < GUI_SIZE; i++) {
            addButton(i, new InventoryButton()
                    .creator(p -> filler)
                    .consumer(event -> {}));
        }
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
                    ItemStack arrow = new ItemStack(Material.ARROW);
                    ItemMeta meta = arrow.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(ChatColor.YELLOW + "Back");
                        arrow.setItemMeta(meta);
                    }
                    return arrow;
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
