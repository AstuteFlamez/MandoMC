package net.mandomc.gameplay.clans;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CoalTerrainOverlayTest {
    @Test public void appliesExactCellsWithoutChangingOriginalsAndKeepsStaffExclusions() throws Exception {
        var file=Files.createTempFile("coal-overlay-",".yml");
        try {
            Files.writeString(file,"schema-version: 1\nchanges:\n- 'minecraft:hoth|-15,1,-14|minecraft:stone|minecraft:coal_ore'\n");
            var overlay=new CoalTerrainOverlay(file);var blocks=new String[512];Arrays.fill(blocks,"minecraft:stone");
            var chunk=new ClanState.Chunk("minecraft:hoth",-1,-1);int index=256+2*16+1;
            var staff=new BitSet();staff.set(index);overlay.apply(chunk,0,blocks,staff);assertEquals("minecraft:stone",blocks[index]);
            staff.clear();overlay.apply(chunk,0,blocks,staff);assertEquals("minecraft:coal_ore",blocks[index]);
            overlay.apply(chunk,0,blocks,staff);assertEquals(1,Arrays.stream(blocks).filter("minecraft:coal_ore"::equals).count());
            blocks[index]="minecraft:barrel";assertThrows(IllegalStateException.class,()->overlay.apply(chunk,0,blocks,staff));
        } finally {Files.deleteIfExists(file);}
    }
    @Test public void rejectsNonCoalTargetsAndDuplicateCoordinates() throws Exception {
        var file=Files.createTempFile("coal-overlay-",".yml");
        try {
            for(String row:List.of("minecraft:hoth|1,1,1|minecraft:stone|minecraft:diamond_ore","minecraft:spawnworld|1,1,1|minecraft:stone|minecraft:coal_ore")) {
                Files.writeString(file,"schema-version: 1\nchanges:\n- '"+row+"'\n");
                assertThrows(IllegalArgumentException.class,()->new CoalTerrainOverlay(file));
            }
            String row="minecraft:hoth|1,1,1|minecraft:stone|minecraft:coal_ore";
            Files.writeString(file,"schema-version: 1\nchanges:\n- '"+row+"'\n- '"+row+"'\n");
            assertThrows(IllegalArgumentException.class,()->new CoalTerrainOverlay(file));
        } finally {Files.deleteIfExists(file);}
    }
}
