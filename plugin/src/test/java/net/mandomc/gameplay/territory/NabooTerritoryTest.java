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
        assertEquals(100,atlas.rows().stream().flatMapToInt(String::chars).filter(c->c=='W').count());
        for(int z=188;z<=231;z++) for(int x=-864;x<=-831;x++) {
            char zone=atlas.zone(x,z);boolean safe=zone=='S';boolean wild=zone=='.';
            for(int y:new int[]{-64,0,59,319}) for(int offset:new int[]{0,15}) {
                assertEquals(safe?SAFEZONE:wild?WILDERNESS:WARZONE,policy.zone(world,x*16+offset,y,z*16+offset));
                assertEquals(wild,policy.edits(world,x*16+offset,y,z*16+offset));
                assertEquals(!safe,policy.combat(world,x*16+offset,y,z*16+offset));
                assertEquals(!safe,policy.spawning(world,x*16+offset,y,z*16+offset));
            }
            assertEquals(wild,policy.wildernessChunk(world,x,z));
        }
        assertEquals(SAFEZONE,policy.zone(world,-13342,59,3209));
        assertEquals(SAFEZONE,policy.zone(world,-13480,200,3080));
        assertEquals(WILDERNESS,policy.zone(world,-13551,101,3359));
        assertEquals(WILDERNESS,policy.zone(world,-13400,59,3250));
        assertEquals(WARZONE,policy.zone(world,-13720,80,3280));
        assertEquals(WARZONE,policy.zone(world,-13760,-64,3200));
        assertEquals(WILDERNESS,policy.zone(world,-13600,80,3280));
        assertFalse(policy.interactions(world,-13720,80,3280));
        assertFalse(policy.interactions(world,-13342,59,3209));
        assertTrue(policy.interactions(world,-13600,80,3280));
        assertTrue(policy.interactions("minecraft:hoth",0,70,0));
        assertFalse(policy.wholeWorldSafe(world));
    }
}
