package net.mandomc.gameplay.decor;

import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class DecorDefinitionTest {
    private List<DecorDefinition> definitions(){
        var config=YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(getClass().getResourceAsStream("/decor/placements.yml"),java.nio.charset.StandardCharsets.UTF_8));
        return config.getMapList("placements").stream().map(DecorDefinition::read).toList();
    }
    @Test public void requestedModesAndExactCapturedFacingAreRetained(){
        var values=definitions();assertEquals(12,values.size());assertEquals(12,values.stream().map(DecorDefinition::id).distinct().count());
        assertEquals(9,values.stream().filter(d->!d.id().startsWith("brown_jawa_")).count());
        var uwing=values.stream().filter(d->d.type().equals("u_wing")).findFirst().orElseThrow();
        assertTrue(uwing.openDoors());assertEquals("landing",uwing.mode());assertEquals(11.695954251385313,uwing.x(),0);assertEquals(18.749952f,uwing.yaw(),0);
        assertEquals(8,values.stream().filter(DecorDefinition::npc).count());
        for(var value:values)if(value.npc()){assertTrue(value.dialogue().size()>=3);assertTrue(value.voices().isEmpty());assertEquals(List.of("idle"),value.animations());}
    }
    @Test public void capturedCharactersAndShipsUseTheirOwnWorlds() {
        for (var d : definitions()) {
            assertEquals(d.npc() ? "minecraft:tatooine_review" : "minecraft:spawnworld", d.world());
            if (d.id().startsWith("brown_jawa_")) {
                assertEquals("jawa_brown", d.model());
                assertEquals(69, d.y(), 0);
                assertTrue(d.npc());
            }
        }
        var byId = definitions().stream().collect(java.util.stream.Collectors.toMap(DecorDefinition::id, d -> d));
        var quarren = byId.get("point6_quarren");
        var twilek = byId.get("point7_twilek");
        assertEquals(quarren.x(), twilek.x(), 0);
        assertEquals(quarren.z(), twilek.z(), 0);
        assertEquals(quarren.yaw(), twilek.yaw(), 0);
        assertEquals(-2.975476778477544, byId.get("point3_boba_fett").x(), 0);
    }

    @Test public void legacyForwardOffsetIsAppliedOnlyOnce() {
        var row = Map.of("id", "example", "name", "Guard", "type", "guard", "model", "guard",
                "mode", "stationary_idle", "scale", 1, "forward-offset-pixels", 3,
                "location", Map.of("world", "minecraft:spawnworld", "x", 10, "y", 93, "z", 8, "yaw", 90, "pitch", 0));
        var first = DecorDefinition.read(row);
        assertEquals(first, DecorDefinition.read(row));
        assertEquals(10 - 3.0 / 16, first.x(), 1e-12);
        assertEquals(8, first.z(), 1e-12);
    }
    @Test public void nonfiniteOrOutOfContractDataIsRejected(){
        var d=definitions().getFirst();
        assertThrows(IllegalArgumentException.class,()->copy(d,Double.NaN,1,List.of()));
        assertThrows(IllegalArgumentException.class,()->copy(d,1,-1,List.of()));
        assertThrows(IllegalArgumentException.class,()->copy(d,1,1,List.of("not a key")));
    }
    private DecorDefinition copy(DecorDefinition d,double x,double scale,List<String> sounds){return new DecorDefinition(d.id(),d.name(),d.type(),d.model(),scale,d.mode(),d.openDoors(),d.world(),x,d.y(),d.z(),d.yaw(),d.pitch(),d.animations(),sounds,d.dialogue());}
}
