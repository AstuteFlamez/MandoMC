package net.mandomc.gameplay.clans;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.mandomc.gameplay.crates.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class KothCrateTest {
    private YamlConfiguration config(){return YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/crates/crates.yml"),StandardCharsets.UTF_8));}
    @Test public void kothUsesTheSharedCrateDefinitionAndSavedVirtualKeys() {
        var crate=CrateDefinition.read("koth",config().getConfigurationSection("crates.koth"));
        assertTrue(crate.virtual());assertEquals("koth_crate_key",crate.keyItem());
        assertEquals(100,crate.rewards().total());assertEquals(5,crate.rewards().entries().size());
        assertEquals("durasteel_sheet",crate.rewards().choose(0).item());
        assertEquals("beskar_sheet",crate.rewards().choose(99).item());
    }
    @Test public void weightedRewardsUseExactBoundariesAndRejectInvalidTickets() {
        var pool=new WeightedPool<>(List.of(new WeightedPool.Entry<>("common",95),new WeightedPool.Entry<>("rare",5)));
        for(int i=0;i<100;i++)assertEquals(i<95?"common":"rare",pool.choose(i));
        assertThrows(IllegalArgumentException.class,()->pool.choose(-1));
        assertThrows(IllegalArgumentException.class,()->pool.choose(100));
    }
    @Test public void invalidWeightsAndUnboundedItemAmountsFailValidation() {
        assertThrows(IllegalArgumentException.class,()->new WeightedPool<>(List.of()));
        assertThrows(IllegalArgumentException.class,()->new WeightedPool<>(List.of(new WeightedPool.Entry<>("bad",0))));
        var config=config();config.set("crates.koth.rewards",List.of(java.util.Map.of("item","beskar_sheet","amount",65,"weight",1)));
        assertThrows(IllegalArgumentException.class,()->CrateDefinition.read("koth",config.getConfigurationSection("crates.koth")));
    }
}
