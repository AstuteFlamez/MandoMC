package net.mandomc.gameplay.tutorial;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class HandbookDefinitionTest {
    private YamlConfiguration config(){return YamlConfiguration.loadConfiguration(new InputStreamReader(
            getClass().getResourceAsStream("/language/handbook.yml"),StandardCharsets.UTF_8));}
    @Test public void approvedThirteenTopicsStayOnePageEachInTheConfirmedOrder() {
        var book=HandbookDefinition.read(config());
        assertEquals(13,book.pages().size());
        assertEquals(List.of("travel","combat_weapons","armor_repairs","mining_materials","crafting","credits_auctions",
                "clans_claims","raids_warpoints","koth","jabba","vehicles","jawas","ilum_crates"),book.pages().stream().map(HandbookDefinition.Page::id).toList());
        for(var page:book.pages()) {
            int lines=BookPageLayout.wrap(page.heading(),true).size()+1+BookPageLayout.wrap(page.body(),false).size();
            assertTrue(page.id()+" overflows: "+lines,lines<=14);
            for(var line:BookPageLayout.wrap(page.body(),false))assertTrue(BookPageLayout.width(line,false)<=114);
        }
    }
    @Test public void combinedCombatPageIncludesAllFourRequestedSubjects() {
        var body=HandbookDefinition.read(config()).pages().get(1).body();
        for(var text:List.of("Death","combat","Blaster","Saber"))assertTrue(body.contains(text));
        assertFalse(body.contains("Q ")); // Action names respect custom keybindings.
    }
    @Test public void longCommandsWrapAtWordsWithoutBeingTruncated() {
        var lines=BookPageLayout.wrap("/ah sell <price> [amount]",false);
        assertEquals("/ah sell <price> [amount]",String.join(" ",lines));
        lines.forEach(line->assertTrue(BookPageLayout.width(line,false)<=114));
    }
    @Test public void oversizedPagesAndUnbreakableWordsAreRejectedInsteadOfSilentlyCutOff() {
        assertThrows(IllegalArgumentException.class,()->BookPageLayout.validate("Title","one\n".repeat(15)));
        assertThrows(IllegalArgumentException.class,()->BookPageLayout.wrap("W".repeat(40),false));
    }
    @Test public void versionAndTopicCountAreValidatedBeforeCreatingAnyBook() {
        var count=config();count.set("page-count",12);
        assertThrows(IllegalArgumentException.class,()->HandbookDefinition.read(count));
        var version=config();version.set("schema-version",2);
        assertThrows(IllegalArgumentException.class,()->HandbookDefinition.read(version));
    }
}
