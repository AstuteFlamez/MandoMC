package net.mandomc.gameplay.credits;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordRewardTest {
    @Test public void retryRestartAndOtherEconomyWritesNeverRepeatReward() throws Exception {
        var path = Files.createTempDirectory("discord-reward").resolve("credits.yml");
        var id = UUID.randomUUID(); var ledger = new CreditLedger(path);
        assertFalse(ledger.rewardDiscord(id, 500000)); // Wait for first join; preserve starter credit policy.
        ledger.initializeAccount(id, true, 250000);
        assertTrue(ledger.rewardDiscord(id, 500000));
        assertFalse(ledger.rewardDiscord(id, 500000));
        ledger.adjust(id, "give", 100);
        var restarted = new CreditLedger(path);
        assertFalse(restarted.rewardDiscord(id, 500000));
        assertEquals(750100, restarted.balance(id));
    }
    @Test public void failedAtomicSaveDoesNotConsumeReward() throws Exception {
        var path = Files.createTempDirectory("discord-reward").resolve("credits.yml");
        var id = UUID.randomUUID(); var ledger = new CreditLedger(path);
        ledger.initializeAccount(id, false, 0);
        Files.delete(path); Files.createDirectory(path); Files.writeString(path.resolve("occupied"), "x");
        assertThrows(Exception.class, () -> ledger.rewardDiscord(id, 500000));
        assertEquals(0, ledger.balance(id));
        Files.delete(path.resolve("occupied")); Files.delete(path);
        assertTrue(ledger.rewardDiscord(id, 500000));
        assertEquals(500000, new CreditLedger(path).balance(id));
    }
}
