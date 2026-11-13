package net.mandomc.gameplay.territory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.mandomc.gameplay.territory.TerritoryPolicy.Zone.*;

public class NabooTerritoryTest {
    @Test public void onlyArrivalChunkIsSafeAtEveryHeight() {
        var config = YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/territory/maps.yml"), StandardCharsets.UTF_8));
        var policy = new TerritoryPolicy(config);
        String world = "minecraft:naboo";
        for (int x = -848; x <= -846; x++) for (int z = 208; z <= 210; z++) {
            boolean safe = x == -847 && z == 209;
            for (int y : new int[] {-64, 0, 263, 319}) {
                for (int offset : new int[] {0, 15}) {
                    assertEquals(safe ? SAFEZONE : WILDERNESS,
                            policy.zone(world, x * 16 + offset, y, z * 16 + offset));
                    assertEquals(!safe, policy.edits(world, x * 16 + offset, y, z * 16 + offset));
                    assertEquals(!safe, policy.combat(world, x * 16 + offset, y, z * 16 + offset));
                    assertEquals(!safe, policy.spawning(world, x * 16 + offset, y, z * 16 + offset));
                }
            }
            assertEquals(!safe, policy.wildernessChunk(world, x, z));
        }
        assertEquals(WILDERNESS, policy.zone(world, -1, 70, -1));
        assertEquals(WILDERNESS, policy.zone(world, 5000, 70, 5000));
        var map = policy.map(world, -847, 209);
        assertEquals(1, map.rows().stream().flatMapToInt(String::chars).filter(c -> c == 'S').count());
        assertFalse(map.rows().stream().anyMatch(row -> row.contains("W")));
        assertFalse(policy.wholeWorldSafe(world));
    }
}
