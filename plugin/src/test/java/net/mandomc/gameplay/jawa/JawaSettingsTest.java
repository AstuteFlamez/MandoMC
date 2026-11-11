package net.mandomc.gameplay.jawa;
import java.io.InputStreamReader;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;
public class JawaSettingsTest {
    private YamlConfiguration config(){return YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/mobs/jawas.yml"))));}
    @Test public void stockStaysStablePerJawaAndDifferentJawaCanCarryDifferentSupplies(){
        var settings=JawaSettings.read(config());assertEquals(settings.stock(7),settings.stock(7));
        assertEquals(6,new HashSet<>(settings.stock(7)).size());assertNotEquals(settings.stock(7),settings.stock(8));
    }
    @Test public void invalidPopulationAndPricesFailBeforeSpawning(){
        for(String path:List.of("spawning.group-max","spawning.world-cap","spawning.min-distance","shop.offers-per-jawa")){
            var config=config();config.set(path,0);assertThrows(IllegalArgumentException.class,()->JawaSettings.read(config));
        }
        var config=config();config.set("shop.supplies.oak.price","-1");assertThrows(IllegalArgumentException.class,()->JawaSettings.read(config));
    }
    @Test public void grayBuybackCannotProfitFromTheSameBrownBundle(){
        var settings=JawaSettings.read(config());
        for(var sell:settings.supplies())for(var buy:settings.purchases())if(sell.item().equals(buy.item()))
            assertTrue(sell.id(),sell.price()*buy.amount()>buy.price()*sell.amount());
    }
}
