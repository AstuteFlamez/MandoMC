package net.mandomc.gameplay.dungeon;
import java.io.*;import org.bukkit.configuration.file.YamlConfiguration;import org.junit.Test;import static org.junit.Assert.*;
public class JabbaSettingsTest {
 private YamlConfiguration config(){return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/events/jabba.yml")));}
 @Test public void legacyLayoutAndPoolComplete(){var c=JabbaSettings.read(config());assertEquals(9,c.rooms().size());assertEquals(22,c.rooms().stream().mapToInt(r->r.chests().size()).sum());assertEquals(51,c.rooms().stream().mapToInt(r->r.spawns().size()*r.count()).sum());assertEquals("Bossk",c.rooms().get(3).mob());assertEquals("BobaFett",c.rooms().get(6).mob());assertEquals("Rancor",c.rooms().get(8).mob());assertEquals(7,c.loot().size());}
 @Test public void rejectsSoftlocksAndAmbiguousLayouts(){var c=config();c.set("rooms.1.spawns",java.util.List.of());assertThrows(IllegalArgumentException.class,()->JabbaSettings.read(c));var d=config();d.set("rooms.2.gate",15);assertThrows(IllegalArgumentException.class,()->JabbaSettings.read(d));var e=config();e.set("loot.wiring_coil",0);assertThrows(IllegalArgumentException.class,()->JabbaSettings.read(e));}
 @Test public void roomsDoNotRequireLootToProgress(){var c=config();c.set("rooms.1.chests",java.util.List.of());assertEquals(9,JabbaSettings.read(c).rooms().size());}
}
