package net.mandomc.gameplay.vehicle.gui;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.core.LangManager;
import net.mandomc.gameplay.vehicle.manager.SeatManager;
import net.mandomc.gameplay.vehicle.manager.VehicleManager;
import net.mandomc.gameplay.vehicle.model.SeatConfig;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI displayed when a player right-clicks a deployed vehicle.
 *
 * Lets the viewer choose an available seat to occupy, or (if they are the
 * vehicle owner) pick the vehicle up. Non-owners see a disabled pick-up
 * button. Occupied seats show the name of the current rider and cannot
 * be clicked.
 *
 * Layout (27-slot default):
 *  - All unused slots are filled with gray glass panes.
 *  - Each seat defined in the vehicle YAML occupies its configured slot.
 *  - The pick-up button is always placed at slot (guiSize - 4).
 *  - The skin button is always placed at slot (guiSize - 6).
 */
public class VehicleInteractGUI extends InventoryGUI {

    private static final Material FILLER_MATERIAL  = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material PICKUP_MATERIAL = Material.BARRIER;

    private final Vehicle vehicle;
    private final Player viewer;
    private final GUIManager guiManager;

    /**
     * Creates the vehicle interaction GUI.
     *
     * @param vehicle    the vehicle the player interacted with
     * @param viewer     the player who opened the GUI
     * @param guiManager the GUI manager used to handle events
     */
    public VehicleInteractGUI(Vehicle vehicle, Player viewer, GUIManager guiManager) {
        this.vehicle    = vehicle;
        this.viewer     = viewer;
        this.guiManager = guiManager;
    }

    @Override
    protected Inventory createInventory() {
        VehicleData data  = vehicle.getVehicleData();
        String rawTitle   = data.getDisplayName();
        String title      = LangManager.colorize(rawTitle.isBlank() ? "&fVehicle" : rawTitle);
        int size          = data.getGuiSize();

        return Bukkit.createInventory(null, size, title);
    }

    @Override
    public void decorate(Player player) {
        fillWithGlass();
        addSeatButtons();
        addSkinButton();
        addPickupButton();
        super.decorate(player);
    }

    // -------------------------------------------------------------------------
    // Building blocks
    // -------------------------------------------------------------------------

    /** Fills every slot with a silent gray glass pane. */
    private void fillWithGlass() {
        ItemStack filler = new ItemStack(FILLER_MATERIAL);
        ItemMeta meta    = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }

        int size = vehicle.getVehicleData().getGuiSize();
        for (int i = 0; i < size; i++) {
            addButton(i, new InventoryButton()
                    .creator(p -> filler)
                    .consumer(event -> {}));
        }
    }

    /** Adds one button per seat defined in the vehicle config. */
    private void addSeatButtons() {
        for (SeatConfig seat : vehicle.getSeats()) {
            addButton(seat.slot(), buildSeatButton(seat));
        }
    }

    /** Builds an InventoryButton for the given seat. */
    private InventoryButton buildSeatButton(SeatConfig seat) {
        return new InventoryButton()
                .creator(p -> buildSeatItem(seat))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();

                    if (vehicle.isOccupied(seat.slot())) {
                        UUID occupantId = vehicle.getOccupantAt(seat.slot());
                        String name = occupantId != null
                                ? fetchPlayerName(occupantId)
                                : "Unknown";
                        clicker.sendMessage(LangManager.get("vehicles.seat-occupied", "%player%", name));
                        return;
                    }

                    clicker.closeInventory();
                    SeatManager.mountSeat(clicker, vehicle, seat);
                });
    }

    /** Creates the skull item displayed on a seat button. */
    private ItemStack buildSeatItem(SeatConfig seat) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta  = (SkullMeta) skull.getItemMeta();
        if (meta == null) return skull;

        applySkullTexture(meta, seat.skullUrl());

        String typeLabel = ChatColor.GOLD + "[" + formatType(seat.type().name()) + "] ";
        meta.setDisplayName(typeLabel + ChatColor.WHITE + seat.name());

        List<String> lore = new ArrayList<>();
        if (vehicle.isOccupied(seat.slot())) {
            UUID occupantId = vehicle.getOccupantAt(seat.slot());
            String name = occupantId != null ? fetchPlayerName(occupantId) : "Unknown";
            lore.add(ChatColor.GRAY + "Status: " + ChatColor.RED + "Occupied by " + name);
        } else {
            lore.add(ChatColor.GRAY + "Status: " + ChatColor.GREEN + "Available");
        }
        meta.setLore(lore);

        skull.setItemMeta(meta);
        return skull;
    }

    /** Applies a URL-based skin texture to a skull meta if the URL is valid. */
    private static void applySkullTexture(SkullMeta meta, String skullUrl) {
        if (skullUrl == null || skullUrl.isBlank() || skullUrl.contains("TODO")) return;
        try {
            URL url = new URI(skullUrl).toURL();
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            PlayerTextures textures = profile.getTextures();
            textures.setSkin(url);
            profile.setTextures(textures);
            meta.setOwnerProfile(profile);
        } catch (Exception ignored) {
            // Malformed URL — leave as default skull appearance
        }
    }

    /** Adds the pick-up button at the fixed slot (guiSize - 4). */
    private void addPickupButton() {
        int pickupSlot           = vehicle.getVehicleData().getGuiSize() - 4;
        boolean isOwner          = viewer.getUniqueId().equals(vehicle.getOwnerUUID());
        boolean hasOtherRiders   = hasNonOwnerRiders();

        if (!isOwner) {
            addButton(pickupSlot, new InventoryButton()
                    .creator(p -> buildDisabledPickupItem())
                    .consumer(event -> {}));
            return;
        }

        addButton(pickupSlot, new InventoryButton()
                .creator(p -> buildOwnerPickupItem(hasOtherRiders))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    clicker.closeInventory();
                    SeatManager.ejectAll(vehicle);
                    VehicleManager.pickupVehicle(clicker);
                }));
    }

    private ItemStack buildOwnerPickupItem(boolean hasOtherRiders) {
        ItemStack item = new ItemStack(PICKUP_MATERIAL);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.RED + "Pick Up Vehicle");
        List<String> lore = new ArrayList<>();
        if (hasOtherRiders) {
            lore.add(ChatColor.GRAY + "Will eject all passengers.");
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildDisabledPickupItem() {
        ItemStack item = new ItemStack(PICKUP_MATERIAL);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.GRAY + "You don't own this vehicle.");
        item.setItemMeta(meta);
        return item;
    }

    private void addSkinButton() {
        int skinSlot = vehicle.getVehicleData().getGuiSize() - 6;
        boolean isOwner = viewer.getUniqueId().equals(vehicle.getOwnerUUID());

        addButton(skinSlot, new InventoryButton()
                .creator(p -> buildSkinItem())
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    if (!isOwner) {
                        clicker.sendMessage(LangManager.get("vehicles.owner-only-action"));
                        return;
                    }
                    guiManager.openGUI(new VehicleSkinGUI(vehicle, clicker, guiManager), clicker);
                }));
    }

    private ItemStack buildSkinItem() {
        ItemStack item = vehicle.getVehicleData().getItem();
        if (item == null) {
            item = new ItemStack(Material.MINECART);
        } else {
            item = item.clone();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Click to change vehicle skin.");
            String activeSkin = vehicle.getSelectedSkinId();
            if (activeSkin != null && !activeSkin.isBlank()) {
                lore.add(ChatColor.DARK_GRAY + "Active: " + ChatColor.LIGHT_PURPLE + activeSkin);
            }
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns true if any occupant in the vehicle is not the owner. */
    private boolean hasNonOwnerRiders() {
        UUID ownerUUID = vehicle.getOwnerUUID();
        return vehicle.getOccupants().keySet().stream()
                .anyMatch(uuid -> !uuid.equals(ownerUUID));
    }

    /** Returns the name for the given UUID, using online or offline player data. */
    private static String fetchPlayerName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        var offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    /** Capitalises first letter and lowercases the rest of a seat type name. */
    private static String formatType(String raw) {
        if (raw.isEmpty()) return raw;
        return raw.charAt(0) + raw.substring(1).toLowerCase();
    }
}
