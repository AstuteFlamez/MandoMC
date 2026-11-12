package net.mandomc.gameplay.items;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyFacilityLoreTest {
    private Component c(String text) { return MiniMessage.miniMessage().deserialize(text); }

    @Test public void removesSplitLegacyStatementAndOnlyItsTrailingSeparator() {
        var badge = c("<white>badge"); var description = c("<gray>Portable storage.");
        var lore = List.of(badge, Component.empty(), description, Component.empty(),
                c("<gold><italic>Extracted from a secured facility on"), c("<gold><bold>Tatooine"));
        assertEquals(List.of(badge, Component.empty(), description), LegacyFacilityLore.clean(lore));
    }
    @Test public void recognizesCapitalizationPunctuationAndSingleLineVariants() {
        for (String statement : List.of("EXTRACTED FROM A SECURE FACILITY ON TATOOINE.",
                "Extracted from a secured facility on Tatooine!", "Extracted from the secure facility on Tatooine")) {
            assertEquals(List.of(c("Description")), LegacyFacilityLore.clean(List.of(c("Description"), c(statement))));
        }
    }
    @Test public void preservesCustomLoreAndUnrelatedTatooineLines() {
        var a = c("<aqua>Player's note"); var planet = c("<red>Tatooine");
        var lore = List.of(a, Component.empty(), c("Extracted from a secure facility on"), c("Tatooine"),
                Component.empty(), planet, c("Keep this custom source description."));
        assertEquals(List.of(a, Component.empty(), planet, c("Keep this custom source description.")), LegacyFacilityLore.clean(lore));
        var unrelated = List.of(c("Recovered from Tatooine"), planet);
        assertSame(unrelated, LegacyFacilityLore.clean(unrelated));
    }
    @Test public void recognizesOnlyTheFourOwnedCatalogIdentitiesAndIsIdempotent() {
        assertFalse(LegacyFacilityLore.affected(null)); assertFalse(LegacyFacilityLore.affected("third_party"));
        for (String id : List.of("basic_power_cell", "durable_mid_grade_power_core", "batteries", "switch"))
            assertTrue(LegacyFacilityLore.affected(id));
        var clean = LegacyFacilityLore.clean(List.of(c("Description"), c("Extracted from a secure facility on Tatooine")));
        assertSame(clean, LegacyFacilityLore.clean(clean));
        assertNull(LegacyFacilityLore.clean(null));
    }
}
