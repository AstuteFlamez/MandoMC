package net.mandomc.gameplay.guide;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class GameplayEncyclopediaTest {
    private GameplayEncyclopedia encyclopedia(Map<String,String> files) throws Exception {
        var manifest = RepositorySnapshotTest.manifest("a".repeat(40),true,files);
        return new GameplayEncyclopedia(RepositorySnapshot.archive(manifest,RepositorySnapshotTest.zip(files)),"FAQ");
    }

    @Test public void recordsPreserveNamesUnitsAndEffectiveOverrides() throws Exception {
        var index = encyclopedia(Map.of(
                "plugin/src/main/resources/vehicles/speederbike.yml","name: Speeder Bike\nhealth: 900\nspeed: 1",
                "server/config/common/mandomc/vehicles/speederbike.yml","name: Speeder Bike\nhealth: 50\nspeed: 1",
                "server/config/common/mythicmobs/sandy-mobs.yml","Massiff:\n  Health: 30\n  Damage: 0\nBantha:\n  Health: 50\n  Damage: 0",
                "server/config/common/mythicmobs/massiff-skills.yml","Massiff_Attack:\n  Skills:\n    - damage{a=6} @target",
                "server/config/common/mandomc/mobs/spawning.yml","world: minecraft:tatooine_review\nbiomes: [minecraft:desert]\nspawn:\n  minimum-player-distance: 32\n  maximum-player-distance: 64"));
        var massiff = index.find("How much HP does a massiff have?").getFirst().facts();
        assertTrue(massiff.contains("30"));assertTrue(massiff.contains("hit points"));
        assertFalse(massiff.contains("Health: 50"));
        assertTrue(index.find("Massiff attack damage").stream().anyMatch(n -> n.facts().contains("damage{a=6}")));
        String vehicle = index.find("speeder bike health").getFirst().facts();
        assertTrue(vehicle.contains("50"));assertFalse(vehicle.contains("900"));
        assertTrue(index.find("where do massiffs spawn").stream().anyMatch(n -> n.facts().contains("Tatooine") && n.facts().contains("32")));
        assertTrue(index.find("massifs health").getFirst().facts().contains("30"));
        assertTrue(index.find("capital of France").isEmpty());
    }

    @Test public void cratePercentagesAreComputedFromTotalWeight() throws Exception {
        var index = encyclopedia(Map.of("server/config/common/mandomc/crates/crates.yml",
                "crates:\n  koth:\n    rewards:\n      - {item: black_kyber, amount: 1, weight: 1}\n      - {item: blue_kyber, amount: 1, weight: 9}"));
        var facts = index.find("black kyber drop chance").getFirst().facts();
        assertTrue(facts.contains("10.0000%"));assertFalse(facts.contains("1.0000%"));
    }

    @Test public void wholeBuildBundleIsCompleteAndCanGenerateRecordsWithoutNetwork() throws Exception {
        var manifest = RepositorySnapshot.manifest(getClass().getResourceAsStream("/guide-repository.json"));
        var snapshot = RepositorySnapshot.bundled(manifest,getClass().getResourceAsStream("/guide-public.json.gz"));
        assertEquals(manifest.sources().size(),snapshot.documents().size());
        var index = new GameplayEncyclopedia(snapshot,"FAQ");
        assertTrue(index.size() > 150);
        assertTrue(index.find("massiff health").getFirst().facts().contains("30"));
        assertTrue(index.find("E11 damage").getFirst().facts().contains("2.0"));
        assertTrue(index.find("What is the capital of France?").isEmpty());
        assertTrue(index.find("massiff health").stream().noneMatch(n -> n.facts().contains("<")));
    }
}
