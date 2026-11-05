package net.mandomc.gameplay.vehicle.weapon;

import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

/**
 * Resolves a ModelEngine bone's world-space pivot point as a Bukkit {@link Location}.
 * Falls back to the backing entity's location when the bone is not found.
 */
public final class BonePositionResolver {

    private BonePositionResolver() {}

    /**
     * @param model    the active model containing the bone
     * @param entity   the backing entity (fallback location source)
     * @param boneName the bone id as defined in the model
     * @return the bone's world location, or the entity's location if the bone is missing
     */
    public static Location resolve(ActiveModel model, LivingEntity entity, String boneName) {
        if (model == null || boneName == null || boneName.isBlank()) {
            return entity.getLocation();
        }

        ModelBone bone = model.getBone(boneName).orElse(null);
        if (bone == null) {
            return entity.getLocation();
        }

        Location boneLoc = bone.getLocation();
        return boneLoc != null ? boneLoc : entity.getLocation();
    }
}
