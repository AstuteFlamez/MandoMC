package net.mandomc.gameplay.tutorial;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class TutorialTextTest {
    private YamlConfiguration load(String resource) throws Exception {
        try (var reader = new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream(resource)), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
    private void compact(String key, String markup) {
        var text = MiniMessage.miniMessage().deserialize(markup,
                Placeholder.unparsed("kills", "3"), Placeholder.unparsed("seconds", "300"),
                Placeholder.unparsed("total", "300"), Placeholder.unparsed("ammo", "Tibanna Gas Cells"));
        String line = PlainTextComponentSerializer.plainText().serialize(text);
        // Deliberately conservative standard-font budget, including space for bold glyphs.
        int budget = line.codePoints().map(c -> c < 128 ? 7 : 10).sum();
        assertFalse(key + " must be one line", line.contains("\n"));
        assertTrue(key + " exceeds compact action-bar budget (" + budget + "): " + line, budget <= 240);
    }
    @Test public void trainingShipHasItsActualWeaponAmmo() throws Exception {
        var tutorial = load("/tutorial/tutorial.yml"); var weapons = load("/vehicles/weapons.yml");
        assertEquals("xwing", tutorial.getString("items.vehicle"));
        assertEquals(weapons.getString("vehicles." + tutorial.getString("items.vehicle") + ".ammo"), tutorial.getString("items.vehicle-ammo"));
    }
    @Test public void allTutorialActionBarsFitWithLargestConfiguredCounters() throws Exception {
        var tutorial = load("/tutorial/tutorial.yml");
        var otherSurfaces = Set.of("players-only", "bossbar", "event-bar", "loading-bar", "event-next",
                "event-finish", "start-naboo", "naboo-title", "naboo-subtitle", "map-intro", "map-walk", "recovery");
        for (String key : tutorial.getConfigurationSection("messages").getKeys(false))
            if (!otherSurfaces.contains(key)) compact("messages." + key, tutorial.getString("messages." + key));
        for (String key : tutorial.getConfigurationSection("steps").getKeys(false))
            compact("steps." + key, tutorial.getString("steps." + key));
        for (int page = 0; page < 3; page++) compact("events." + page, tutorial.getString("events." + page + ".action"));
        var messages = load("/language/messages.yml");
        for (String key : Set.of("starter-kit.received", "vehicles.weapon-ammo", "vehicles.weapon-recharging"))
            compact(key, messages.getString(key));
    }
}
