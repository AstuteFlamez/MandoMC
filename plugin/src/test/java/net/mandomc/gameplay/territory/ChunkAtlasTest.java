package net.mandomc.gameplay.territory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChunkAtlasTest {
    private ChunkAtlas atlas() {
        return ChunkAtlas.read(YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/territory/maps.yml"), StandardCharsets.UTF_8)));
    }
    @Test public void completeGridCoversNegativeAndPartialEdgeChunks() {
        var a = atlas();
        assertEquals(32, a.rows().size());
        assertEquals(1024, a.rows().stream().mapToInt(String::length).sum());
        assertTrue(a.contains(Math.floorDiv(-250, 16), Math.floorDiv(249, 16)));
        assertFalse(a.contains(-17, 0));
        assertFalse(a.contains(0, 16));
    }
    @Test public void authoredLandmarksHaveExpectedZones() {
        var a = atlas();
        for (int[] point : new int[][] {{-18,-192},{-27,-201},{-9,-197}}) {
            assertEquals("surface build", 'S', a.zone(Math.floorDiv(point[0],16),Math.floorDiv(point[1],16)));
        }
        for (int[] point : new int[][] {{87,-76},{-126,79},{141,137},{-73,-139},{-69,-188},{94,92},{-164,14}}) {
            assertNotEquals("Other build is not safezone", 'S', a.zone(Math.floorDiv(point[0],16),Math.floorDiv(point[1],16)));
        }
        assertEquals(16, a.rows().stream().mapToLong(row->row.chars().filter(c->c=='S').count()).sum());
        assertEquals('W', a.zone(0,0));
        assertEquals('W', a.zone(-2,-7));
        assertEquals('W', a.zone(1,7));
        assertEquals('.', a.zone(-16,-16));
        assertEquals('.', a.zone(15,15));
    }
    @Test public void entireSpawnTerraceAndGatewayAreSafeAtChunkEdges() {
        var a=atlas();
        for(int x=-42;x<=6;x++)for(int z=-211;z<=-173;z++)
            assertEquals("Spawn footprint "+x+","+z,'S',a.zone(Math.floorDiv(x,16),Math.floorDiv(z,16)));
        assertEquals('S',a.zone(0,-14));
        assertEquals('S',a.zone(0,-11));
        assertEquals('W',a.zone(-2,-10));
        assertEquals('.',a.zone(-5,-12));
    }
    @Test public void malformedRowsAndOversizedBoundsAreRejected() {
        for (List<String> rows : List.of(List.of("?"), List.of("SS"), List.<String>of())) {
            assertThrows(IllegalArgumentException.class, () -> new ChunkAtlas("minecraft:test", "Test",0,0,0,0,rows));
        }
        assertThrows(IllegalArgumentException.class, () -> new ChunkAtlas("minecraft:test","Test",0,0,1000,0,List.of(".")));
        assertThrows(IllegalArgumentException.class, () -> ChunkAtlas.read(new YamlConfiguration()));
    }
}
