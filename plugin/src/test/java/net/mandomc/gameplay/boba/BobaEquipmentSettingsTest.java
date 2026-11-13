package net.mandomc.gameplay.boba;

import java.io.InputStreamReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class BobaEquipmentSettingsTest {
    private YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/equipment/boba.yml")));
    }
    @Test public void playerDefaultsRetainLegacyReachAndPacketSpeed() {
        var s=BobaEquipmentSettings.read(config());assertEquals(9,s.range(),0);assertEquals(.5,s.projectileSpeed(),0);
        assertEquals(20,s.burstTicks());assertEquals(60,s.cooldownTicks());
    }
    @Test public void rejectsUnsafeAndNonfiniteSettings() {
        for(String key:new String[]{"jetpack.speed","jetpack.steering","jetpack.launch","flamethrower.range","flamethrower.damage"}) {
            for(double value:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,1000}) {
                var c=config();c.set(key,value);assertThrows(IllegalArgumentException.class,()->BobaEquipmentSettings.read(c));
            }
        }
        var c=config();c.set("flamethrower.burst-ticks",1.5);
        assertThrows(IllegalArgumentException.class,()->BobaEquipmentSettings.read(c));
    }
}
