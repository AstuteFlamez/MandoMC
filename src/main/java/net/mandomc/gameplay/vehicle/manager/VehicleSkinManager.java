package net.mandomc.gameplay.vehicle.manager;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.mandomc.gameplay.vehicle.config.VehicleConfigResolver;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.gameplay.vehicle.model.VehicleSkinOption;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

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

        ModeledEntity oldModeled = data.getModeledEntity();
        if (oldModeled != null) {
            oldModeled.destroy();
        }

        ModeledEntity remodeled = ModelEngineAPI.createModeledEntity(data.getEntity());
        ActiveModel activeModel = ModelEngineAPI.createActiveModel(skin.modelKey());
        if (activeModel == null) return false;

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
        return true;
    }
}
