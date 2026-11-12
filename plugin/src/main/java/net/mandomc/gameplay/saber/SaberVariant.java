package net.mandomc.gameplay.saber;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.joml.Vector3f;

/** Numeric fixed-context blade bounds derived from the pinned upstream models; no artwork. */
public record SaberVariant(String model, float centerX, float centerY, float centerZ, double halfLength) {
    public Vector3f center() { return new Vector3f(centerX, centerY, centerZ); }

    public SaberBlade.Pose pose(SaberBlade.Pose motion) {
        return new SaberBlade.Pose(motion.center(), motion.rotation(), halfLength);
    }

    public static Map<String, SaberVariant> read(ConfigurationSection config, String legacyId, String legacyModel) {
        var variants = new LinkedHashMap<String, SaberVariant>();
        variants.put(legacyId, new SaberVariant(legacyModel, .00625f, 1.34375f, -.09375f, 1));
        var section = config.getConfigurationSection("variants");
        if (section == null) return Map.copyOf(variants);
        for (String id : section.getKeys(false)) {
            String path = "variants." + id;
            String model = section.getString(id + ".item-model", "");
            var center = section.getList(id + ".blade-center");
            double halfLength = section.getDouble(id + ".blade-half-length", Double.NaN);
            if (!id.matches("[a-z0-9_]+") || !model.contains(":") || NamespacedKey.fromString(model) == null
                    || center == null || center.size() != 3 || center.stream().anyMatch(v -> !(v instanceof Number n)
                    || !Double.isFinite(n.doubleValue()) || Math.abs(n.doubleValue()) > 8)
                    || !Double.isFinite(halfLength) || halfLength < .1 || halfLength > 4) {
                throw new IllegalArgumentException("sabers.yml: invalid " + path + " model or blade bounds");
            }
            variants.put(id, new SaberVariant(model, ((Number) center.get(0)).floatValue(),
                    ((Number) center.get(1)).floatValue(), ((Number) center.get(2)).floatValue(), halfLength));
        }
        return Map.copyOf(variants);
    }
}
