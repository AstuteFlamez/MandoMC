package net.mandomc.gameplay.territory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.mandomc.gameplay.territory.TerritoryPolicy.Zone.*;

public class NabooTerritoryTest {
    @Test public void wholeIslandIsSafeAtEveryHeightAndFormerArrivalIsWilderness() {
        var config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/territory/maps.yml"), StandardCharsets.UTF_8));
        var policy = new TerritoryPolicy(config);
        String world = "minecraft:naboo";
        var atlas = policy.map(world,-834,200);
        assertEquals(100,atlas.rows().stream().flatMapToInt(String::chars).filter(c->c=='S').count());
        assertFalse(atlas.rows().stream().anyMatch(row->row.contains("W")));
        for(int z=188;z<=231;z++) for(int x=-864;x<=-831;x++) {
            boolean safe=atlas.zone(x,z)=='S';
            for(int y:new int[]{-64,0,59,319}) for(int offset:new int[]{0,15}) {
                assertEquals(safe?SAFEZONE:WILDERNESS,policy.zone(world,x*16+offset,y,z*16+offset));
                assertEquals(!safe,policy.edits(world,x*16+offset,y,z*16+offset));
                assertEquals(!safe,policy.combat(world,x*16+offset,y,z*16+offset));
                assertEquals(!safe,policy.spawning(world,x*16+offset,y,z*16+offset));
            }
            assertEquals(!safe,policy.wildernessChunk(world,x,z));
        }
        assertEquals(SAFEZONE,policy.zone(world,-13342,59,3209));
        assertEquals(SAFEZONE,policy.zone(world,-13480,200,3080));
        assertEquals(WILDERNESS,policy.zone(world,-13551,101,3359));
        assertEquals(WILDERNESS,policy.zone(world,-13400,59,3250));
        assertFalse(policy.wholeWorldSafe(world));
    }
}
