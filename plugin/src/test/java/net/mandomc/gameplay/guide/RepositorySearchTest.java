package net.mandomc.gameplay.guide;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RepositorySearchTest {
    @Test public void broaderGameplayPassagesAreRelevantBoundedAndRemainServerOnly() throws Exception {
        var files = Map.of(
                "docs/features/vehicles-and-explosions.md", "Status: deployed\n\nRepair a damaged vehicle by crouching nearby with a wrench. The vehicle must be landed, empty and out of combat.",
                "docs/features/native-utilities.md", "Status: deployed\n\nHomes: Players may set three named homes. Use /sethome to save a home and /home to return.",
                "docs/features/progression-crafting.md", "Status: deployed\n\nFortune does not multiply custom ore drops. Each ore makes one uniform roll of 1 to 5 scraps.",
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
}
