package net.mandomc.gameplay.credits;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SupplyLedgerTest {
    @Test public void purchaseDebitsAndRetainsOneClaimAcrossRestart() throws Exception {
        var path=Files.createTempDirectory("jawa").resolve("credits.yml");var ledger=new CreditLedger(path);
        var owner=UUID.randomUUID();var tx=UUID.randomUUID();ledger.adjust(owner,"give",10000);
        String bytes=Base64.getEncoder().encodeToString(new byte[]{1,2,3});
        ledger.supply(tx,owner,bytes,4000,true);
        var restarted=new CreditLedger(path);
        assertEquals(6000,restarted.balance(owner));assertEquals(bytes,restarted.auctions().claims().get(tx).item());
        assertThrows(IllegalArgumentException.class,()->restarted.supply(tx,owner,bytes,4000,true));
        assertEquals(6000,restarted.balance(owner));
        restarted.completeAuctionClaims(owner,Set.of(tx));
        assertThrows(IllegalArgumentException.class,()->restarted.supply(tx,owner,bytes,4000,true));
    }
    @Test public void saleReceiptIsDurableAndCannotBeCreditedTwice() throws Exception {
        var path=Files.createTempDirectory("jawa").resolve("credits.yml");var ledger=new CreditLedger(path);
        var owner=UUID.randomUUID();var tx=UUID.randomUUID();ledger.supply(tx,owner,null,1200,false);
        var restarted=new CreditLedger(path);assertEquals(1200,restarted.balance(owner));
        assertTrue(restarted.auctions().accepted().contains(tx));assertFalse(restarted.auctions().claims().containsKey(tx));
        assertThrows(IllegalArgumentException.class,()->restarted.supply(tx,owner,null,1200,false));
    }
    @Test public void insufficientFundsOverflowAndSaveFailureKeepMoneyAndReceiptsUnchanged() throws Exception {
        var path=Files.createTempDirectory("jawa").resolve("credits.yml");var ledger=new CreditLedger(path);
        var owner=UUID.randomUUID();var tx=UUID.randomUUID();String bytes="AQID";
        assertThrows(IllegalArgumentException.class,()->ledger.supply(tx,owner,bytes,1,true));
        ledger.adjust(owner,"set",CreditLedger.MAX);
        assertThrows(IllegalArgumentException.class,()->ledger.supply(tx,owner,null,1,false));
        ledger.adjust(owner,"set",100);
        Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("block"),"blocked");
        assertThrows(Exception.class,()->ledger.supply(tx,owner,bytes,50,true));
        assertEquals(100,ledger.balance(owner));assertFalse(ledger.auctions().accepted().contains(tx));
        assertFalse(ledger.auctions().claims().containsKey(tx));
    }
}
