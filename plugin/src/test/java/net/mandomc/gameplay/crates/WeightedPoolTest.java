package net.mandomc.gameplay.crates;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeightedPoolTest {
    @Test public void everyTicketMatchesItsPublishedWeight() {
        var pool=new WeightedPool<>(List.of(new WeightedPool.Entry<>("blue",60),new WeightedPool.Entry<>("green",60),
                new WeightedPool.Entry<>("yellow",40),new WeightedPool.Entry<>("purple",30),new WeightedPool.Entry<>("orange",7),
                new WeightedPool.Entry<>("red",2),new WeightedPool.Entry<>("white",1)));
        var counts=new java.util.HashMap<String,Integer>();
        for(int ticket=0;ticket<pool.total();ticket++)counts.merge(pool.choose(ticket),1,Integer::sum);
        for(var entry:pool.entries())assertEquals(entry.weight(),counts.get(entry.value()).intValue());
        assertEquals(200,pool.total());assertEquals("blue",pool.choose(0));assertEquals("white",pool.choose(199));
    }
    @Test public void invalidPoolsAndTicketsFailClosed() {
        assertThrows(IllegalArgumentException.class,()->new WeightedPool<String>(List.of()));
        assertThrows(IllegalArgumentException.class,()->new WeightedPool<>(List.of(new WeightedPool.Entry<>("x",0))));
        var pool=new WeightedPool<>(List.of(new WeightedPool.Entry<>("x",1)));
        assertThrows(IllegalArgumentException.class,()->pool.choose(-1));
        assertThrows(IllegalArgumentException.class,()->pool.choose(1));
        assertThrows(ArithmeticException.class,()->new WeightedPool<>(java.util.Collections.nCopies(3000,new WeightedPool.Entry<>("x",1_000_000))));
    }
    @Test public void bundledKyberCrateHasOnlyExistingCrystalIdentities() {
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/crates/crates.yml"),java.nio.charset.StandardCharsets.UTF_8));
        var crate=CrateDefinition.read("kyber",config.getConfigurationSection("crates.kyber"));
        assertFalse(crate.virtual());assertEquals("ilum_kyber_crate_key",crate.keyItem());assertEquals(200,crate.rewards().total());
        assertEquals(7,crate.rewards().entries().size());
        for(var entry:crate.rewards().entries())assertTrue(entry.value().item().endsWith("_kyber"));
    }
}
