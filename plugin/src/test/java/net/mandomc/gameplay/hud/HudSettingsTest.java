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
    @Test public void combatReplacesStatsDeathsKillsAndBalance() {
        var settings = HudSettings.read(config());
        assertEquals(settings.lines().size(), settings.combatLines().size());
        assertTrue(settings.combatLines().get(7).contains("ᴄᴏᴍʙᴀᴛ"));
        assertTrue(settings.combatLines().get(8).contains("ᴅᴏ ɴᴏᴛ ʟᴏɢ ᴏᴜᴛ"));
        assertEquals("<combat-or-kills>", settings.combatLines().get(9));
        assertTrue(settings.combatLines().get(10).contains("ᴅᴏ ɴᴏᴛ ʟᴏɢ ᴏᴜᴛ"));
        var legacy = config(); legacy.set("combat-lines", null);
        assertEquals(settings.combatLines(), HudSettings.read(legacy).combatLines());
    }
    @Test public void combinedKillsDeathsAndTerritoryFitBothLayouts() {
        var settings = HudSettings.read(config());
        assertTrue(settings.kills().contains("ᴋɪʟʟѕ/ᴅᴇᴀᴛʜѕ"));
        assertTrue(settings.kills().contains("<kills><gray>/<#75CDA9><deaths>"));
        assertEquals(1, settings.lines().stream().filter(line -> line.contains("<territory>")).count());
        assertEquals(1, settings.combatLines().stream().filter(line -> line.contains("<territory>")).count());
        assertFalse(settings.lines().stream().anyMatch(line -> line.contains("<deaths>")));
        assertEquals(14, settings.combatLines().size());
    }
    @Test public void bothLayoutsKeepOneStaticGrayPlayAddress() {
        var settings=HudSettings.read(config());var footer=settings.animations().get("serverinfo");
        assertEquals(1,footer.frames().size());
        assertEquals("play.mandomc.net",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(footer.at(0)));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.GRAY,footer.at(0).color());
        assertEquals(footer.at(0),footer.at(9999999));
        assertTrue(settings.lines().contains("<serverinfo>"));assertTrue(settings.combatLines().contains("<serverinfo>"));
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
