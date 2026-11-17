package net.mandomc.gameplay.jawa;
import java.io.InputStreamReader;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;
public class JawaAcquisitionNotesTest {
    @Test public void ordinarySuppliesPointToCurrentGrayShopCategories(){
        var config=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/items/recipes.yml")));
        for(String id:List.of("bantha_hide","minecraft:string","minecraft:paper","minecraft:blue_dye","minecraft:stick")){
            String note=config.getString("sources."+id);assertTrue(id,note.contains("Batuu"));assertFalse(id,note.toLowerCase().contains("brown jawa"));
        }
        assertTrue(config.getString("sources.minecraft:string").contains("General Supplies"));
        assertTrue(config.getString("sources.minecraft:paper").contains("Farming"));
        assertTrue(config.getString("sources.wiring_coil").contains("brown Jawa on Tatooine"));
    }
}
