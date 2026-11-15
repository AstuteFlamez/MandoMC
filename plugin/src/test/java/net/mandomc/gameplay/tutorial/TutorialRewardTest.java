package net.mandomc.gameplay.tutorial;

import java.nio.file.*;
import java.util.UUID;
import net.mandomc.gameplay.credits.CreditLedger;
import org.junit.Test;
import static org.junit.Assert.*;

public class TutorialRewardTest {
    @Test public void rewardAndReceiptSurviveReloadAndUnrelatedPayments() throws Exception {
        var file = Files.createTempDirectory("tutorial-ledger").resolve("credits.yml");
        var player = UUID.randomUUID(); var other = UUID.randomUUID();
        var ledger = new CreditLedger(file); ledger.initializeAccount(player, true, 250000);
        assertTrue(ledger.rewardTutorial(player, 500000)); assertEquals(750000, ledger.balance(player));
        ledger.transfer(player, other, 10000); ledger.adjust(player, "take", 1000);
        var reloaded = new CreditLedger(file); assertTrue(reloaded.tutorialRewarded(player));
        assertFalse(reloaded.rewardTutorial(player, 500000)); assertEquals(739000, reloaded.balance(player));
        assertEquals(10000, reloaded.balance(other));
    }
    @Test public void failedCommitNeverConsumesRewardReceipt() throws Exception {
        var parent = Files.createTempDirectory("tutorial-ledger-failure"); var file = parent.resolve("credits.yml");
        var ledger = new CreditLedger(file); var player = UUID.randomUUID();
        Files.createDirectory(file); Files.writeString(file.resolve("obstruction"), "kept");
        try { ledger.rewardTutorial(player, 500000); fail("write should fail"); } catch (java.io.IOException expected) { }
        assertFalse(ledger.tutorialRewarded(player)); assertEquals(0, ledger.balance(player));
        Files.delete(file.resolve("obstruction")); Files.delete(file);
        assertTrue(ledger.rewardTutorial(player, 500000));
    }
    @Test public void limitFailureCanBeRetriedWithoutPartialCredit() throws Exception {
        var ledger = new CreditLedger(Files.createTempDirectory("tutorial-ledger-limit").resolve("credits.yml"));
        var player = UUID.randomUUID(); ledger.adjust(player, "set", CreditLedger.MAX);
        try { ledger.rewardTutorial(player, 500000); fail("limit should fail"); } catch (IllegalArgumentException expected) { }
        assertFalse(ledger.tutorialRewarded(player)); assertEquals(CreditLedger.MAX, ledger.balance(player));
        ledger.adjust(player, "take", 500000); assertTrue(ledger.rewardTutorial(player, 500000));
        assertEquals(CreditLedger.MAX, ledger.balance(player));
    }
}
