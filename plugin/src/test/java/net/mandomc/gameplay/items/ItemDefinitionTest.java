package net.mandomc.gameplay.items;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class ItemDefinitionTest {
    private YamlConfiguration config() throws Exception {
        var config = new YamlConfiguration();
        config.loadFromString("""
                material: LEATHER_HELMET
                name: '<yellow>Desert Robes Hood'
                rarity: uncommon
                category: armor
                lore: ['<gray>Original description']
                custom-model-data: 1
                item-model: mandomc:armor/robes_helmet
                equipment-model: mandomc:desert_robes
                """);
        return config;
    }

    @Test public void retainsIdentityAndIndependentImmutableDescription() throws Exception {
        var config = config();
        var result = ItemDefinition.parse("robes_helmet", config, "robes.yml");
        config.set("lore", java.util.List.of("changed"));
        assertEquals("<gray>Original description", result.lore().getFirst());
        assertThrows(UnsupportedOperationException.class, () -> result.lore().add("changed"));
        assertEquals("mandomc:desert_robes", result.equipmentModel().toString());
    }

    @Test public void rejectsBrokenDefinitionInsteadOfCreatingFallbackStone() throws Exception {
        for (var key : java.util.List.of("material", "item-model", "equipment-model", "custom-model-data", "name", "lore")) {
            var config = config();
            config.set(key, key.equals("material") ? "NOT_A_MATERIAL" : key.equals("name") ? "" : "invalid");
            var error = assertThrows(IllegalArgumentException.class,
                    () -> ItemDefinition.parse("robes_helmet", config, "robes.yml"));
            assertTrue(error.getMessage().contains("robes.yml"));
        }
    }

    @Test public void rejectsUnsupportedKeysAndUnsafeIds() throws Exception {
        var config = config();
        config.set("recipe", java.util.List.of("AAA"));
        assertThrows(IllegalArgumentException.class, () -> ItemDefinition.parse("robes_helmet", config, "robes.yml"));
        for (String id : java.util.List.of("Robes", "../robes", "robes.helmet", "")) {
            assertThrows(IllegalArgumentException.class, () -> ItemDefinition.parse(id, config(), "robes.yml"));
        }
    }

    @Test public void pageBoundariesNeverProduceAnEmptyExtraPage() {
        assertEquals(1, ItemBrowser.pageCount(0));
        assertEquals(1, ItemBrowser.pageCount(45));
        assertEquals(2, ItemBrowser.pageCount(46));
        assertEquals(2, ItemBrowser.pageCount(90));
        assertEquals(3, ItemBrowser.pageCount(91));
        assertEquals(0, ItemBrowser.clampPage(-1, 100));
        assertEquals(1, ItemBrowser.clampPage(10, 46));
        assertEquals(0, ItemBrowser.clampPage(5, 0));
    }
}
