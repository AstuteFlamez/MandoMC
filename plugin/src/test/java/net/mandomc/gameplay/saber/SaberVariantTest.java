package net.mandomc.gameplay.saber;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.Test;
import static org.junit.Assert.*;

public class SaberVariantTest {
    private YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/sabers.yml"), StandardCharsets.UTF_8));
    }

    @Test public void everyNamedSaberHasMatchingItemAndDistinctPresentation() {
        var settings = SaberSettings.read(config());
        var items = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/items/sabers.yml"), StandardCharsets.UTF_8));
        assertEquals(78, settings.variants().size());
        assertEquals(items.getConfigurationSection("items").getKeys(false), settings.variants().keySet());
        settings.variants().forEach((id, variant) -> {
            assertEquals(items.getString("items." + id + ".item-model"), variant.model());
            assertTrue(items.getString("items." + id + ".name").contains("<bold:false>"));
        });
        assertEquals("legendary", items.getString("items.vader.rarity"));
        assertEquals("common", items.getString("items.blue_training_lightsaber.rarity"));
        assertEquals(1, settings.variants().get("vader").halfLength(), 0);
        assertTrue(settings.variants().get("darth_maul_apprentice_lightsaber").halfLength() > 2);
        assertTrue(settings.variants().get("yoda_lightsaber").halfLength() < 1);
    }

    @Test public void oldConfigurationRetainsVaderAndMalformedBoundsFailClearly() {
        var config = config();
        config.set("variants", null);
        assertEquals(java.util.Set.of("vader"), SaberSettings.read(config).variants().keySet());
        for (Object invalid : List.of(List.of(1, 2), List.of(0, "bad", 0), List.of(0, Double.NaN, 0))) {
            config = config();
            config.set("variants.vader.blade-center", invalid);
            try { SaberSettings.read(config); fail("accepted invalid bounds"); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("variants.vader")); }
        }
    }

    @Test public void variantColliderAndRenderUseTheSameCenterAndLengthThroughRotation() {
        var variants = SaberSettings.read(config()).variants();
        variants.values().forEach(variant -> {
            var rotation = new Quaternionf().rotateX(.8f).rotateY(.6f).rotateZ(1.2f);
            var pose = variant.pose(new SaberBlade.Pose(new Vector(2, 3, 4), rotation, 1));
            assertEquals(2 * variant.halfLength(), pose.base().distance(pose.tip()), 1e-5);
            var transform = pose.transformation(variant.center());
            var transformedCenter = transform.getLeftRotation().transform(variant.center())
                    .add(transform.getTranslation());
            assertEquals(0, transformedCenter.length(), 1e-5);
            var destination = variant.pose(new SaberBlade.Pose(new Vector(3, 3, 4), rotation, 1));
            assertEquals(variant.halfLength(), pose.interpolate(destination, .5).halfLength(), 0);
        });
    }
}
