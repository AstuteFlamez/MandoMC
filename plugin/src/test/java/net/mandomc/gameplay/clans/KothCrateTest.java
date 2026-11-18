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
        assertEquals(120,crate.rewards().total());assertEquals(15,crate.rewards().entries().size());
        assertEquals("se14c",crate.rewards().choose(0).item());
        assertTrue(crate.rewards().entries().stream().anyMatch(entry->entry.value().item().equals("black_kyber")));
        var items=new java.util.HashSet<String>();for(int i=0;i<crate.rewards().total();i++)items.add(crate.rewards().choose(i).item());
        assertEquals(java.util.Set.of("se14c","a280","ee3","jawa_ionization_blaster","cycler_rifle","relby_v10","boba_jetpack","speederbike","uwing","xwing","tiefighter","black_kyber","endurance_book","recovery_book","mending_book"),items);
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
        var config=config();config.set("crates.koth.rewards",List.of(java.util.Map.of("item","beskar","amount",65,"weight",1)));
        assertThrows(IllegalArgumentException.class,()->CrateDefinition.read("koth",config.getConfigurationSection("crates.koth")));
    }
}
