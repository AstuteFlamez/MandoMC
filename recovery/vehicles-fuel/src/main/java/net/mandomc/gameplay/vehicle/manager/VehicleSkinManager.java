package net.mandomc.gameplay.vehicle.manager;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.gameplay.vehicle.config.VehicleConfigResolver;
import net.mandomc.gameplay.vehicle.model.SeatConfig;
import net.mandomc.gameplay.vehicle.model.SeatType;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.gameplay.vehicle.model.VehicleSkinOption;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Handles skin selection persistence and runtime model swapping.
 */
public final class VehicleSkinManager {

    private static final NamespacedKey SKIN_ID_KEY =
            new NamespacedKey(MandoMC.getInstance(), "vehicle_skin_id");

    private VehicleSkinManager() {
    }

    public static String getSelectedSkinId(ItemStack item) {
        if (item == null) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "";
        return meta.getPersistentDataContainer().getOrDefault(SKIN_ID_KEY, PersistentDataType.STRING, "");
    }

    public static void setSelectedSkinId(ItemStack item, String skinId) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (skinId == null || skinId.isBlank()) {
            pdc.remove(SKIN_ID_KEY);
        } else {
            pdc.set(SKIN_ID_KEY, PersistentDataType.STRING, skinId);
        }
        item.setItemMeta(meta);
    }

    public static VehicleSkinOption resolveActiveSkin(ItemStack item) {
        String selectedSkinId = getSelectedSkinId(item);
        return VehicleConfigResolver.resolveSkinOption(item, selectedSkinId);
    }

    public static ItemStack applySkinToItem(ItemStack baseItem, VehicleSkinOption skin) {
        if (baseItem == null) return null;
        ItemStack item = baseItem.clone();
        if (skin == null) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (skin.itemName() != null && !skin.itemName().isBlank()) {
            meta.setDisplayName(LangManager.colorize(skin.itemName()));
        }
        if (skin.customModelData() >= 0) {
            meta.setCustomModelData(skin.customModelData());
        }

        item.setItemMeta(meta);
        setSelectedSkinId(item, skin.id());
        return item;
    }

    public static boolean playerHasSkinPermission(org.bukkit.entity.Player player, VehicleSkinOption skin) {
        if (skin == null || !skin.hasPermissionNode()) return true;
        return player.hasPermission(skin.permission());
    }

    /**
     * Applies the selected skin to the active deployed vehicle.
     * Returns false if model swap failed.
     */
    public static boolean applySkinToVehicle(Vehicle vehicle, VehicleSkinOption skin) {
        if (vehicle == null || skin == null) return false;
        VehicleData data = vehicle.getVehicleData();
        if (data == null || data.getEntity() == null) return false;

        // Build the target model first; if unavailable, keep current model untouched.
        ActiveModel activeModel = ModelEngineAPI.createActiveModel(skin.modelKey());
        if (activeModel == null) return false;

        Map<UUID, Integer> occupantSnapshot = new HashMap<>(vehicle.getOccupants());
        clearOccupants(vehicle, occupantSnapshot);

        ModeledEntity oldModeled = data.getModeledEntity();
        if (oldModeled != null) {
            oldModeled.destroy();
        }

        ModeledEntity remodeled = ModelEngineAPI.createModeledEntity(data.getEntity());
        activeModel.setScale(data.getScale());
        activeModel.setHitboxScale(data.getScale());
        remodeled.addModel(activeModel, true);
        remodeled.getBase().setMaxStepHeight(1.0);

        data.setModeledEntity(remodeled);
        data.setActiveModel(activeModel);
        data.setModelKey(skin.modelKey());

        ItemStack updatedItem = applySkinToItem(data.getItem(), skin);
        data.setItem(updatedItem);
        vehicle.setSelectedSkinId(skin.id());
        restoreOccupants(vehicle, occupantSnapshot);
        return true;
    }

    private static void clearOccupants(Vehicle vehicle, Map<UUID, Integer> occupantSnapshot) {
        for (UUID riderId : occupantSnapshot.keySet()) {
            vehicle.vacate(riderId);
            VehicleModule.unregisterOccupant(riderId);
        }
    }

    private static void restoreOccupants(Vehicle vehicle, Map<UUID, Integer> occupantSnapshot) {
        if (occupantSnapshot.isEmpty()) return;

        List<Map.Entry<UUID, Integer>> ordered = new ArrayList<>(occupantSnapshot.entrySet());
        ordered.sort(Comparator.comparingInt(entry -> {
            SeatConfig seat = vehicle.getSeatAt(entry.getValue());
            return seat != null && seat.type() == SeatType.DRIVER ? 0 : 1;
        }));

        for (Map.Entry<UUID, Integer> entry : ordered) {
            SeatConfig seat = vehicle.getSeatAt(entry.getValue());
            if (seat == null) continue;

            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;

            SeatManager.remountSeat(player, vehicle, seat);
        }
    }
}
