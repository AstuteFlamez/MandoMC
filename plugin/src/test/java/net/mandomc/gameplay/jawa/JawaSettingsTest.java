package net.mandomc.gameplay.jawa;
import java.io.InputStreamReader;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;
public class JawaSettingsTest {
    private YamlConfiguration config(){return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/mobs/jawas.yml"))));}
    @Test public void eachJawaGetsOneCompleteStableCategory(){
        var settings=JawaSettings.read(config());var random=new Random(17);var seen=new HashSet<String>();
        for(int i=0;i<1000;i++) {
            long seed=random.nextLong();var category=settings.category(seed);seen.add(category.id());
            assertEquals(category.offers(),settings.stock(seed));assertEquals(settings.stock(seed),settings.stock(seed));
            assertEquals(category.offers().size(),new HashSet<>(settings.stock(seed)).size());
        }
        assertEquals(settings.categories().size(),seen.size());
        var all=settings.categories().stream().flatMap(c->c.offers().stream()).toList();
        assertEquals(settings.supplies().size(),all.size());assertEquals(new HashSet<>(settings.supplies()),new HashSet<>(all));
    }
    @Test public void incompleteUnknownAndDuplicateCategoryAssignmentsAreRejected(){
        for(List<String> ids:List.of(List.<String>of(),List.of("not_an_offer"),List.of("oak","oak"),List.of("wheat_seeds"))){
            var config=config();config.set("shop.categories.saplings.items",ids);assertThrows(IllegalArgumentException.class,()->JawaSettings.read(config));
        }
        var config=config();config.set("shop.categories.end",null);assertThrows(IllegalArgumentException.class,()->JawaSettings.read(config));
    }
    @Test public void invalidPopulationAndPricesFailBeforeSpawning(){
        for(String path:List.of("spawning.group-max","spawning.world-cap","spawning.min-distance")){
            var config=config();config.set(path,0);assertThrows(IllegalArgumentException.class,()->JawaSettings.read(config));
        }
        var config=config();config.set("shop.supplies.oak.price","-1");assertThrows(IllegalArgumentException.class,()->JawaSettings.read(config));
    }
    @Test public void grayBuysCanonicalMetalsHideAndPartsInsteadOfReplacedVanillaMaterials(){
        var settings=JawaSettings.read(config());var items=settings.purchases().stream().map(JawaSettings.Offer::item).toList();
        assertTrue(items.containsAll(List.of("bantha_hide","agrinium","durasteel","wiring_coil")));
        for(String item:List.of("minecraft:iron_ore","minecraft:iron_ingot","minecraft:diamond","minecraft:leather"))assertFalse(items.contains(item));
    }
    @Test public void grayBuybackCannotProfitFromTheSameBrownBundle(){
        var settings=JawaSettings.read(config());
        for(var sell:settings.supplies())for(var buy:settings.purchases())if(sell.item().equals(buy.item()))
            assertTrue(sell.id(),sell.price()*buy.amount()>buy.price()*sell.amount());
    }
}
