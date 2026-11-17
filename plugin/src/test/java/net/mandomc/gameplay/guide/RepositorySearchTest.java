package net.mandomc.gameplay.guide;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepositorySearchTest {
    @Test public void broaderGameplayPassagesAreRelevantBoundedAndRemainServerOnly() throws Exception {
        var files = Map.of(
                "docs/features/vehicles-and-explosions.md", "Status: deployed\n\nRepair a damaged vehicle by crouching nearby with a wrench. The vehicle must be landed, empty and out of combat.",
                "docs/features/native-utilities.md", "Status: deployed\n\nHomes: Players may set three named homes. Use /sethome to save a home and /home to return.",
                "docs/features/progression-crafting.md", "Status: deployed\n\nFortune does not multiply custom ore drops. Each eligible ore drops one scrap.",
                "docs/player-experience.md", "In normal survival, death drops the player's carried gear. When you die your items drop.");
        var manifest = RepositorySnapshotTest.manifest("a".repeat(40), true, files);
        var snapshot = RepositorySnapshot.archive(manifest, RepositorySnapshotTest.zip(files));
        var search = new RepositorySearch(snapshot, "Use /tutorial or /recipes for written guidance.");
        assertTrue(search.find("How do I repair a damaged speederbike?").getFirst().facts().contains("wrench"));
        assertTrue(search.find("How many homes can I set?").getFirst().facts().contains("three"));
        assertTrue(search.find("How does Fortune affect custom ore drops?").getFirst().facts().contains("does not multiply"));
        assertTrue(search.find("What happens to my gear when I die?").getFirst().facts().contains("death"));
        assertTrue(search.find("What is the capital of France?").isEmpty());
        assertTrue(search.find("Write python code").isEmpty());
        assertTrue(search.find("Minecraft server").isEmpty());
        for (var note : search.find("vehicle repair damaged speeder bike")) {
            assertTrue(note.facts().length() < 1500);assertTrue(note.fallback().contains("/tutorial"));
            assertTrue(note.id().contains(manifest.commit()));
        }
    }
    @Test public void relevantDetailsOutrankIncidentalMentionsAndDuplicateOverrides() throws Exception {
        String tutorial = "Make a home with your clan. Tutorial mining and crafting practice.";
        var files = Map.of(
                "server/config/common/mandomc/admin/utilities.yml", "schema-version: 1\nhomes-limit: 3\nrequest-expiry-seconds: 60",
                "docs/features/native-utilities.md", "Players can save three named homes. /sethome saves a home. /home returns to a home. /homes lists homes. The default limit is three homes.",
                "server/config/common/mandomc/tutorial/tutorial.yml", tutorial,
                "plugin/src/main/resources/tutorial/tutorial.yml", tutorial);
        var manifest = RepositorySnapshotTest.manifest("a".repeat(40), true, files);
        var search = new RepositorySearch(RepositorySnapshot.archive(manifest, RepositorySnapshotTest.zip(files)), "FAQ");
        var notes = search.find("How many homes can I set?");
        assertTrue(notes.stream().anyMatch(n -> n.facts().contains("homes limit: 3")));
        assertEquals(3, notes.size());
    }
    @Test public void historicalRolloutsAndFormattingDoNotBecomePlayerFacts() throws Exception {
        var files = Map.of("docs/features/progression-crafting.md", "## Player experience\nFortune adds no extra scraps; each eligible ore drops one scrap.\n\n## Historical rollout\nFortune ore drops used to roll 1 to 5 scraps.",
                "server/config/common/mandomc/language/handbook.yml", "<gray>Death drops carried gear.<newline><yellow>Use /tutorial.");
        var manifest = RepositorySnapshotTest.manifest("a".repeat(40), true, files);
        var search = new RepositorySearch(RepositorySnapshot.archive(manifest, RepositorySnapshotTest.zip(files)), "FAQ");
        assertTrue(search.find("Fortune ore scraps").stream().noneMatch(n -> n.facts().contains("1 to 5")));
        var death = search.find("death gear").getFirst().facts();
        assertTrue(death.contains("Death drops carried gear"));assertFalse(death.contains("<gray>"));
    }
}
