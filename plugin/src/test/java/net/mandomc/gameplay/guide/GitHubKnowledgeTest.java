package net.mandomc.gameplay.guide;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class GitHubKnowledgeTest {
    @Test public void warmCacheWorksOfflineAndAnotherVersionIsNeverUsedAsFallback() throws Exception {
        String a = "a".repeat(40), b = "b".repeat(40), doc = "docs/features/vehicles-and-explosions.md";
        var oldFiles = Map.of(doc, "Speeder bike health is 50. Repair with a wrench.");
        var newFiles = Map.of(doc, "Speeder bike health is 900 in the unreleased development version.");
        var folder = Files.createTempDirectory("public-knowledge-offline");
        var calls = new AtomicInteger();
        try {
            var old = RepositorySnapshotTest.manifest(a, true, oldFiles);
            RepositorySnapshot.archive(old, RepositorySnapshotTest.zip(oldFiles)).save(folder.resolve(a + ".json"));
            try (var knowledge = new GitHubKnowledge(
                    new ByteArrayInputStream(RepositorySnapshotTest.manifestBytes(a, true, oldFiles)), folder, true,
                    "FAQ", ignored -> { }, () -> { calls.incrementAndGet();throw new IOException("offline"); })) {
                knowledge.refresh();
                assertTrue(knowledge.passageCount() > 0);assertEquals(0, calls.get());
                assertTrue(knowledge.find("speeder bike health").getFirst().facts().contains("50"));
            }
            try (var next = new GitHubKnowledge(
                    new ByteArrayInputStream(RepositorySnapshotTest.manifestBytes(b, true, newFiles)), folder, true,
                    "FAQ", ignored -> { }, () -> { calls.incrementAndGet();throw new IOException("offline"); })) {
                next.refresh();assertEquals(0, next.passageCount());assertEquals(1, calls.get());
                assertTrue(next.find("speeder bike health").isEmpty());
            }
        } finally {
            try (var files = Files.list(folder)) { for (Path file : files.toList()) Files.delete(file); }
            Files.delete(folder);
        }
    }
    @Test public void modifiedBuildCannotFetchOrReadAReleasedCache() throws Exception {
        var files = Map.of("docs/features/native-utilities.md", "Players may set three homes.");
        var calls = new AtomicInteger();
        try (var knowledge = new GitHubKnowledge(
                new ByteArrayInputStream(RepositorySnapshotTest.manifestBytes("a".repeat(40), false, files)),
                Path.of("unused-test-cache"), true, "FAQ", ignored -> { },
                () -> { calls.incrementAndGet();throw new IOException("must not fetch"); })) {
            knowledge.refresh();assertEquals(0, calls.get());assertEquals(0, knowledge.passageCount());
        }
    }
}
