package net.mandomc.gameplay.clans;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class OreTerrainOverlayTest {
    @Test public void appliesExactCellsWithoutChangingOriginalsAndKeepsStaffExclusions() throws Exception {
        var file=Files.createTempFile("coal-overlay-",".yml");
        try {
            Files.writeString(file,"schema-version: 1\nchanges:\n- 'minecraft:hoth|-15,1,-14|minecraft:stone|minecraft:coal_ore'\n");
            var overlay=new OreTerrainOverlay(file);var blocks=new String[512];Arrays.fill(blocks,"minecraft:stone");
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
                assertThrows(IllegalArgumentException.class,()->new OreTerrainOverlay(file));
            }
            String row="minecraft:hoth|1,1,1|minecraft:stone|minecraft:coal_ore";
            Files.writeString(file,"schema-version: 1\nchanges:\n- '"+row+"'\n- '"+row+"'\n");
            assertThrows(IllegalArgumentException.class,()->new OreTerrainOverlay(file));
        } finally {Files.deleteIfExists(file);}
    }

    @Test public void oreBalanceAppliesTheNativeRedstoneBlockState() throws Exception {
        var file = Files.createTempFile("ore-overlay-", ".yml");
        try {
            Files.writeString(file, """
                    schema-version: 2
                    changes:
                    - 'minecraft:mandalore_review|0,0,0|minecraft:deepslate|minecraft:deepslate_redstone_ore[lit=false]'
                    """);
            var target = new OreTerrainOverlay(file);
            var chunk = new ClanState.Chunk("minecraft:mandalore_review", 0, 0);
            var blocks = new String[] {"minecraft:deepslate"};
            target.apply(chunk, 0, blocks, new BitSet());
            assertArrayEquals(new String[] {"minecraft:deepslate_redstone_ore[lit=false]"}, blocks);
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
