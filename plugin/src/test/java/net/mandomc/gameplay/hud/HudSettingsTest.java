package net.mandomc.gameplay.hud;

import java.io.InputStreamReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class HudSettingsTest {
    private YamlConfiguration config() { return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/interface/hud.yml"))); }
    @Test public void recoveredLayoutFitsAndAnimationsKeepCadence() {
        var settings = HudSettings.read(config()); assertEquals(14, settings.lines().size());
        var logo = settings.animations().get("logo"); assertEquals(68, logo.frames().size());
        assertEquals(logo.frames().get(0), logo.at(0)); assertEquals(logo.frames().get(1), logo.at(50));
        assertEquals(logo.frames().get(0), logo.at(3400));
        assertEquals(288, settings.animations().get("bar").frames().size());
    }
    @Test public void timerAlwaysHasOneDecimal() {
        assertEquals("15.0", HudSettings.seconds(150)); assertEquals("0.1", HudSettings.seconds(1)); assertEquals("0.0", HudSettings.seconds(0));
    }
    @Test public void invalidAnimationAndMissingCombatLineFail() {
        var config = config(); config.set("animations.logo.interval-ms", 0);
        assertThrows(IllegalArgumentException.class, () -> HudSettings.read(config));
        config.set("animations.logo.interval-ms", 50); config.set("lines", java.util.List.of("hello"));
        assertThrows(IllegalArgumentException.class, () -> HudSettings.read(config));
    }
}
