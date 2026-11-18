package net.mandomc.gameplay.jawa;

import java.io.InputStreamReader;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class JawaPurchaseTest {
    @Test public void singleAndStackPricesPreserveProportionalCosts() {
        var offer=new JawaSettings.Offer("sapling","minecraft:oak_sapling",4,4000);
        assertEquals(1000,JawaPurchase.price(offer,1));
        assertEquals(64000,JawaPurchase.price(offer,64));
        assertEquals(2,JawaPurchase.unitPrice(new JawaSettings.Offer("rounded","minecraft:dirt",8,9)));
        assertThrows(IllegalArgumentException.class,()->JawaPurchase.price(offer,0));
        assertThrows(IllegalArgumentException.class,()->JawaPurchase.price(offer,65));
    }
    @Test public void replacementShopHasTheRequestedOrderAndHighPrices() {
        var config=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/mobs/jawas.yml")));
        var definitions=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/items/definitions/spawners.yml")));
        for(String id:definitions.getConfigurationSection("items").getKeys(false))
            net.mandomc.gameplay.items.ItemDefinition.parse(id,definitions.getConfigurationSection("items."+id),"spawners.yml");
        var shop=JawaSettings.read(config).category("minerals");
        assertEquals("Spawners",shop.name());
        assertEquals(List.of("massiff_spawner","dewback_spawner","bantha_spawner","jawa_spawner"),shop.offers().stream().map(JawaSettings.Offer::item).toList());
        assertTrue(shop.offers().stream().allMatch(o->o.amount()==1 && o.price()>=10000000));
        assertEquals(List.of(10000000L,15000000L,20000000L,30000000L),shop.offers().stream().map(JawaSettings.Offer::price).toList());
    }
}
