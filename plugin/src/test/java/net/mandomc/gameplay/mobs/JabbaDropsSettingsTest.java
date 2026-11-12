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
                Objects.requireNonNull(getClass().getResourceAsStream("/jabba-mobs.yml"))));
    }

    @Test public void preservesLegacyIndependentRollsAndBounds() {
        var settings = JabbaDropsSettings.read(config());
        var random = new Random(36);
        for (String name : settings.mobs().keySet()) {
            var rule = settings.mobs().get(name);
            int weapons = 0, ammo = 0;
            for (int i = 0; i < 10000; i++) {
                var drops = rule.roll(random);
                assertTrue(drops.size() <= (name.equals("GamorreanGuard") ? 1 : 4) + rule.extras().size());
                assertTrue(drops.stream().filter(rule.weapon()::equals).count() <= 1);
                weapons += drops.stream().filter(rule.weapon()::equals).count();
                ammo += drops.stream().filter(rule.ammo()::equals).count();
            }
            assertEquals(10000 * rule.weaponChance(), weapons, 150);
            assertEquals(10000 * rule.ammoRolls() * rule.ammoChance(), ammo, 300);
        }
    }

    @Test public void equipmentRollsAreIndependentAndDoNotReplaceOriginalLoot() {
        var config=config();config.set("drops.BobaFett.equipment.boba_jetpack",1.0);
        config.set("drops.BobaFett.equipment.boba_flamethrower",1.0);
        var rule=JabbaDropsSettings.read(config).mobs().get("BobaFett");
        var random=new Random(2);
        for(int i=0;i<100;i++) {
            var drops=rule.roll(random);assertEquals(1,drops.stream().filter("boba_jetpack"::equals).count());
            assertEquals(1,drops.stream().filter("boba_flamethrower"::equals).count());
        }
        config.set("drops.BobaFett.equipment.boba_jetpack",0.0);
        assertFalse(JabbaDropsSettings.read(config).mobs().get("BobaFett").roll(random).contains("boba_jetpack"));
        config.set("drops.BobaFett.equipment.boba_jetpack",Double.NaN);
        assertThrows(IllegalArgumentException.class,()->JabbaDropsSettings.read(config));
    }

    @Test public void deployedJawaSalvageSurvivesEquipmentExtension() {
        var live = YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/mobs/drops/jabba.yml"))));
        var settings = JabbaDropsSettings.read(live);
        var jawa = settings.mobs().get("JawaBrown");
        assertEquals("jawa_ionization_blaster", jawa.weapon());
        var random = new Random(17);
        for (int i = 0; i < 1000; i++) {
            long parts = jawa.roll(random).stream().filter(jawa.salvage().pool()::contains).count();
            assertTrue(parts >= 1 && parts <= 2);
        }
        var original = new JabbaDropsSettings.Drop(jawa.weapon(), jawa.weaponChance(), jawa.ammo(),
                jawa.ammoChance(), jawa.ammoRolls(), jawa.salvage());
        assertEquals(original.roll(new Random(9)), jawa.roll(new Random(9)));
        live.set("drops.JawaBrown.salvage.maximum", 9);
        assertThrows(IllegalArgumentException.class, () -> JabbaDropsSettings.read(live));
    }

    @Test public void zeroAndFullChancesAreExact() {
        assertEquals(List.of(), new JabbaDropsSettings.Drop("axe", 0, "cell", 0, 3).roll(new Random(1)));
        assertEquals(List.of("axe", "cell", "cell", "cell"),
                new JabbaDropsSettings.Drop("axe", 1, "cell", 1, 3).roll(new Random(1)));
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
