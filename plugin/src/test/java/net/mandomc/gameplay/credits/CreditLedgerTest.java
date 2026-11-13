package net.mandomc.gameplay.credits;

import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CreditLedgerTest {
    @Test public void firstAccountGetsStartingCreditsOnceAcrossLoginAndRestart() throws Exception {
        var file = Files.createTempDirectory("credits").resolve("data.yml");
        var ledger = new CreditLedger(file); var id = UUID.randomUUID();
        assertTrue(ledger.initializeAccount(id, true, 250000));
        assertEquals(250000, ledger.balance(id));
        assertFalse(ledger.initializeAccount(id, true, 250000));
        ledger.adjust(id, "take", 250000);
        var restarted = new CreditLedger(file);
        assertFalse(restarted.initializeAccount(id, false, 250000));
        assertFalse(restarted.initializeAccount(id, true, 250000));
        assertEquals(0, restarted.balance(id));
    }
    @Test public void returningPlayersAndExistingAccountsAreNeverReset() throws Exception {
        var file = Files.createTempDirectory("credits").resolve("data.yml");
        var ledger = new CreditLedger(file); var returning = UUID.randomUUID(); var funded = UUID.randomUUID();
        ledger.adjust(funded, "set", 73125);
        assertFalse(ledger.initializeAccount(funded, true, 250000));
        assertEquals(73125, ledger.balance(funded));
        assertFalse(ledger.initializeAccount(returning, false, 250000));
        var restarted = new CreditLedger(file);
        assertFalse(restarted.initializeAccount(returning, true, 250000));
        assertEquals(0, restarted.balance(returning));
        assertEquals(73125, restarted.balance(funded));
    }
    @Test public void concurrentFirstLoginsCannotGrantTwice() throws Exception {
        var ledger = new CreditLedger(Files.createTempDirectory("credits").resolve("data.yml"));
        var id = UUID.randomUUID();
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> ledger.initializeAccount(id, true, 250000));
            var b = pool.submit(() -> ledger.initializeAccount(id, true, 250000));
            assertNotEquals(a.get(), b.get());
        }
        assertEquals(250000, ledger.balance(id));
    }
    @Test public void failedInitialSaveCanRetryWithoutCreditingMemory() throws Exception {
        var dir = Files.createTempDirectory("credits"); var file = dir.resolve("data.yml");
        var ledger = new CreditLedger(file); var id = UUID.randomUUID();
        Files.createDirectory(file); Files.writeString(file.resolve("block"), "occupied");
        assertThrows(Exception.class, () -> ledger.initializeAccount(id, true, 250000));
        assertEquals(0, ledger.balance(id));
        Files.delete(file.resolve("block")); Files.delete(file);
        assertTrue(ledger.initializeAccount(id, true, 250000));
        assertEquals(250000, new CreditLedger(file).balance(id));
    }
    @Test public void transferSurvivesRestartWithoutCreatingMoney() throws Exception {
        var file = Files.createTempDirectory("credits").resolve("credits-data.yml");
        var ledger = new CreditLedger(file); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        assertEquals(0, ledger.balance(a)); ledger.adjust(a, "give", 10000); ledger.transfer(a, b, 2575);
        var reloaded = new CreditLedger(file);
        assertEquals(7425, reloaded.balance(a)); assertEquals(2575, reloaded.balance(b));
        assertEquals(a, reloaded.top(1).getFirst().getKey());
    }
    @Test public void invalidPaymentsAndOverflowLeaveBothAccountsUnchanged() throws Exception {
        var ledger = new CreditLedger(Files.createTempDirectory("credits").resolve("data.yml"));
        var a = UUID.randomUUID(); var b = UUID.randomUUID(); ledger.adjust(a, "set", 100);
        assertThrows(IllegalArgumentException.class, () -> ledger.transfer(a, b, 101));
        assertThrows(IllegalArgumentException.class, () -> ledger.transfer(a, a, 1));
        assertThrows(IllegalArgumentException.class, () -> ledger.transfer(a, b, 0));
        assertThrows(IllegalArgumentException.class, () -> ledger.transfer(a, b, -1));
        ledger.adjust(b, "set", CreditLedger.MAX);
        assertThrows(IllegalArgumentException.class, () -> ledger.transfer(a, b, 1));
        assertEquals(100, ledger.balance(a)); assertEquals(CreditLedger.MAX, ledger.balance(b));
    }
    @Test public void rejectedPersistenceDoesNotChangeMemory() throws Exception {
        var dir = Files.createTempDirectory("credits"); var file = dir.resolve("data.yml"); var ledger = new CreditLedger(file);
        var a = UUID.randomUUID(); ledger.adjust(a, "set", 500);
        Files.delete(file); Files.createDirectory(file); Files.writeString(file.resolve("block"), "occupied");
        assertThrows(Exception.class, () -> ledger.adjust(a, "give", 100));
        assertEquals(500, ledger.balance(a));
    }
    @Test public void decimalInputIsStrictAndExact() {
        assertEquals(1, CreditLedger.amount("0.01")); assertEquals(12345, CreditLedger.amount("123.45"));
        assertEquals("12,345.67", CreditLedger.format(1234567));
        for (String invalid : new String[] {"NaN", "-1", "1e3", "1.001", "Infinity", " 1", "99999999999999.99"})
            assertThrows(invalid, IllegalArgumentException.class, () -> CreditLedger.amount(invalid));
    }
    @Test public void concurrentTransfersCannotDoubleSpend() throws Exception {
        var ledger = new CreditLedger(Files.createTempDirectory("credits").resolve("data.yml"));
        var a = UUID.randomUUID(); var b = UUID.randomUUID(); var c = UUID.randomUUID(); ledger.adjust(a, "set", 100);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { try { ledger.transfer(a,b,100); return true; } catch (Exception error) { return false; } });
            var two = pool.submit(() -> { try { ledger.transfer(a,c,100); return true; } catch (Exception error) { return false; } });
            assertNotEquals(one.get(), two.get());
        }
        assertEquals(0, ledger.balance(a)); assertEquals(100, ledger.balance(b) + ledger.balance(c));
    }
    @Test public void invalidSavedDataFailsInsteadOfZeroingAccounts() throws Exception {
        var file = Files.createTempFile("credits", ".yml");
        Files.writeString(file, "schema-version: 1\nbalances:\n  '" + UUID.randomUUID() + "': -1\n");
        assertThrows(IllegalArgumentException.class, () -> new CreditLedger(file));
    }
}
