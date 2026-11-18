package net.mandomc.integrations.discord;

import java.nio.file.*;
import java.util.*;
import net.mandomc.gameplay.ranks.RankService;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordReceiptTest {
    @Test public void existingRankFilesNeedNoReceiptMigration() throws Exception {
        var file = Files.createTempFile("discord-receipt", ".yml");
        try {
            Files.writeString(file, "schema-version: 1\nusers: {}\n");
            assertTrue(RankService.readReceipts(file).isEmpty());
        } finally { Files.delete(file); }
    }
    @Test public void brokerReceiptsSurvivePersistenceAndRejectMalformedVersions() throws Exception {
        var file = Files.createTempFile("discord-receipt", ".yml"); UUID id = UUID.randomUUID();
        try {
            Files.writeString(file, "schema-version: 1\nusers: {}\ndiscord-sync:\n  '" + id + "':\n    revision: 27\n    signature: 'mapped-roles'\n");
            assertEquals(new RankService.DiscordReceipt(27, "mapped-roles"), RankService.readReceipts(file).get(id));
            Files.writeString(file, Files.readString(file).replace("revision: 27", "revision: -1"));
            assertThrows(IllegalArgumentException.class, () -> RankService.readReceipts(file));
        } finally { Files.delete(file); }
    }
}
