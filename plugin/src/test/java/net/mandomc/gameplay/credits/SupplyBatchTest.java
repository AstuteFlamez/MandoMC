package net.mandomc.gameplay.credits;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class SupplyBatchTest {
    @Test public void wholeTrayPayoutAndEveryReceiptCommitTogether() throws Exception {
        var path=Files.createTempDirectory("jawa-tray").resolve("credits.yml");var ledger=new CreditLedger(path);
        var owner=UUID.randomUUID();var ids=Set.of(UUID.randomUUID(),UUID.randomUUID());ledger.sellSupplies(owner,ids,400);
        var loaded=new CreditLedger(path);assertEquals(400,loaded.balance(owner));assertTrue(loaded.auctions().accepted().containsAll(ids));
        assertThrows(IllegalArgumentException.class,()->loaded.sellSupplies(owner,ids,400));assertEquals(400,loaded.balance(owner));
        assertThrows(IllegalArgumentException.class,()->loaded.sellSupplies(owner,Set.of(ids.iterator().next(),UUID.randomUUID()),400));assertEquals(400,loaded.balance(owner));
    }
    @Test public void saveFailureCannotPayOrAcceptPartOfTray() throws Exception {
        var path=Files.createTempDirectory("jawa-tray").resolve("credits.yml");var ledger=new CreditLedger(path);var owner=UUID.randomUUID();ledger.adjust(owner,"give",100);
        Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("occupied"),"occupied");var ids=Set.of(UUID.randomUUID(),UUID.randomUUID());
        assertThrows(Exception.class,()->ledger.sellSupplies(owner,ids,400));assertEquals(100,ledger.balance(owner));assertTrue(Collections.disjoint(ids,ledger.auctions().accepted()));
    }
}
