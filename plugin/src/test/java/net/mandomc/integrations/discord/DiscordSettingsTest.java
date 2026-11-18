package net.mandomc.integrations.discord;

import java.io.InputStreamReader;
import java.util.*;
import net.mandomc.gameplay.ranks.RankCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordSettingsTest {
    private RankCatalog catalog() {
        return new RankCatalog(YamlConfiguration.loadConfiguration(new InputStreamReader(
                Objects.requireNonNull(getClass().getClassLoader().getResourceAsStream("ranks/ranks.yml")))));
    }
    private YamlConfiguration config() {
        var config = new YamlConfiguration(); config.set("schema-version", 1);
        config.set("endpoint", "https://bot.example"); config.set("rank-roles.warrior", "123456789012345678");
        return config;
    }
    @Test public void readsExplicitMapping() {
        var settings = DiscordSettings.read(config(), catalog());
        assertEquals(Map.of("warrior", "123456789012345678"), settings.mapping());
        assertEquals(30, settings.intervalSeconds());
    }
    @Test public void refusesUnsafeOriginsAndInvalidMappings() {
        for (String url : List.of("http://bot.example", "https://secret@bot.example", "https://bot.example/other", "https://bot.example?token=secret")) {
            var config = config(); config.set("endpoint", url);
            assertThrows(IllegalArgumentException.class, () -> DiscordSettings.read(config, catalog()));
        }
        var duplicate = config(); duplicate.set("rank-roles.marshal", "123456789012345678");
        assertThrows(IllegalArgumentException.class, () -> DiscordSettings.read(duplicate, catalog()));
        var unknown = config(); unknown.set("rank-roles.fake", "333333333333333333");
        assertThrows(IllegalArgumentException.class, () -> DiscordSettings.read(unknown, catalog()));
        var defaults = config(); defaults.set("rank-roles.default", "333333333333333333");
        assertThrows(IllegalArgumentException.class, () -> DiscordSettings.read(defaults, catalog()));
    }
    @Test public void mergesOnlyManagedRanks() {
        var current = Set.of("builder", "warrior");
        assertEquals(Set.of("builder", "marshal"), DiscordSettings.merge(current, current, Set.of("marshal"), Set.of("warrior", "marshal")));
        assertEquals(Set.of("default"), DiscordSettings.merge(Set.of("warrior"), Set.of("warrior"), Set.of(), Set.of("warrior")));
    }
    @Test public void rejectsChangesMadeDuringNetworkRequestAndUnmappedGrants() {
        assertNull(DiscordSettings.merge(Set.of("builder"), Set.of("warrior"), Set.of(), Set.of("warrior")));
        assertThrows(IllegalArgumentException.class, () -> DiscordSettings.merge(Set.of("default"), Set.of("default"), Set.of("mandostudios"), Set.of("warrior")));
    }
}
