package net.mandomc.gameplay.mobs;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class JabbaDropsSettingsTest {
    private YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/mobs/drops/jabba.yml"))));
    }

    @Test public void preservesLegacyIndependentRollsAndBounds() {
        var settings = JabbaDropsSettings.read(config());
        var random = new Random(36);
        for (String name : settings.mobs().keySet()) {
            var rule = settings.mobs().get(name);
            int weapons = 0, ammo = 0;
            for (int i = 0; i < 10000; i++) {
                var drops = rule.roll(random);
                assertTrue(drops.size() <= 1 + rule.ammoRolls() + rule.salvage().maximum());
                long parts=drops.stream().filter(rule.salvage().pool()::contains).count();
                assertTrue(parts>=rule.salvage().minimum() && parts<=rule.salvage().maximum());
                assertTrue(drops.stream().filter(rule.weapon()::equals).count() <= 1);
                weapons += drops.stream().filter(rule.weapon()::equals).count();
                ammo += drops.stream().filter(rule.ammo()::equals).count();
            }
            assertEquals(10000 * rule.weaponChance(), weapons, 150);
            assertEquals(10000 * rule.ammoRolls() * rule.ammoChance(), ammo, 300);
        }
    }

    @Test public void zeroAndFullChancesAreExact() {
        assertEquals(List.of(), new JabbaDropsSettings.Drop("axe", 0, "cell", 0, 3).roll(new Random(1)));
        assertEquals(List.of("axe", "cell", "cell", "cell"),
                new JabbaDropsSettings.Drop("axe", 1, "cell", 1, 3).roll(new Random(1)));
    }

    @Test public void salvagePoolAndCountsAreValidated() {
        for(Object invalid:List.of(0,9,"two",1.5)) {
            var config=config();config.set("drops.JawaBrown.salvage.maximum",invalid);
            assertThrows(IllegalArgumentException.class,()->JabbaDropsSettings.read(config));
        }
        var config=config();config.set("drops.JawaBrown.salvage.pool",List.of());
        assertThrows(IllegalArgumentException.class,()->JabbaDropsSettings.read(config));
    }
    @Test public void rejectsInvalidConfigurationBeforeRegisteringDrops() {
        for (Object value : List.of(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            var config = config(); config.set("drops.QuarrenGuard.weapon-chance", value);
            assertThrows(IllegalArgumentException.class, () -> JabbaDropsSettings.read(config));
        }
        for (Object value : List.of(-1, 65, 1.5, "three")) {
            var config = config(); config.set("drops.QuarrenGuard.ammo-rolls", value);
            assertThrows(IllegalArgumentException.class, () -> JabbaDropsSettings.read(config));
        }
        var config = config(); config.set("drops.QuarrenGuard.ammo", "");
        assertThrows(IllegalArgumentException.class, () -> JabbaDropsSettings.read(config));
        config.set("drops.QuarrenGuard", null);
        assertThrows(IllegalArgumentException.class, () -> JabbaDropsSettings.read(config));
    }
}
