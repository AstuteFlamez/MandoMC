package net.mandomc.gameplay.auction;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.mandomc.gameplay.credits.CreditLedger;
import org.junit.Test;
import static org.junit.Assert.*;

public class AuctionLedgerTest {
    private AuctionBook.Listing row(UUID seller,long price,long expires) {
        return new AuctionBook.Listing(UUID.randomUUID(),seller,"Seller",Base64.getEncoder().encodeToString(new byte[]{1,2,3}),price,expires);
    }
    @Test public void purchasePersistsBalancesAndEscrowTogetherAndCannotRepeat() throws Exception {
        var file=Files.createTempDirectory("market").resolve("credits.yml");var ledger=new CreditLedger(file);
        var seller=UUID.randomUUID();var buyer=UUID.randomUUID();ledger.adjust(buyer,"set",10000);
        var row=row(seller,2575,10000);ledger.listAuction(row,8,100);ledger.buyAuction(buyer,row.id(),2575,200);
        var saved=new CreditLedger(file);assertEquals(7425,saved.balance(buyer));assertEquals(2575,saved.balance(seller));
        assertTrue(saved.auctions().listings().isEmpty());assertEquals(buyer,saved.auctions().claims().get(row.id()).owner());
        assertThrows(IllegalArgumentException.class,()->ledger.buyAuction(buyer,row.id(),2575,200));
        saved.completeAuctionClaims(buyer,Set.of(row.id()));var restarted=new CreditLedger(file);
        assertTrue(restarted.auctions().claims().isEmpty());assertTrue(restarted.auctions().accepted().contains(row.id()));
    }
    @Test public void cancellationExpiryAndListingLimitsPreserveItems() throws Exception {
        var ledger=new CreditLedger(Files.createTempDirectory("market").resolve("data.yml"));var seller=UUID.randomUUID();
        var first=row(seller,100,1000);ledger.listAuction(first,1,100);
        assertThrows(IllegalArgumentException.class,()->ledger.listAuction(row(seller,100,1000),1,100));
        assertThrows(IllegalArgumentException.class,()->ledger.cancelAuction(UUID.randomUUID(),first.id()));
        ledger.expireAuctions(1000);assertTrue(ledger.auctions().listings().isEmpty());
        assertEquals(seller,ledger.auctions().claims().get(first.id()).owner());
        var second=row(seller,200,10000);ledger.listAuction(second,1,1100);ledger.cancelAuction(seller,second.id());
        assertEquals(2,ledger.auctions().claims().size());
        ledger.completeAuctionClaims(UUID.randomUUID(),Set.of(first.id()));assertEquals(2,ledger.auctions().claims().size());
    }
    @Test public void rejectedMoneyAndFailedDiskWriteLeaveWholeSaleUnchanged() throws Exception {
        var dir=Files.createTempDirectory("market");var file=dir.resolve("data.yml");var ledger=new CreditLedger(file);
        var seller=UUID.randomUUID();var buyer=UUID.randomUUID();var row=row(seller,100,10000);ledger.listAuction(row,8,100);
        assertThrows(IllegalArgumentException.class,()->ledger.buyAuction(buyer,row.id(),100,200));
        ledger.adjust(buyer,"set",1000);ledger.adjust(seller,"set",CreditLedger.MAX);
        assertThrows(IllegalArgumentException.class,()->ledger.buyAuction(buyer,row.id(),100,200));ledger.adjust(seller,"set",0);
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("block"),"occupied");
        assertThrows(Exception.class,()->ledger.buyAuction(buyer,row.id(),100,200));
        assertEquals(1000,ledger.balance(buyer));assertEquals(0,ledger.balance(seller));assertTrue(ledger.auctions().listings().containsKey(row.id()));assertTrue(ledger.auctions().claims().isEmpty());
    }
    @Test public void competingBuyersPayOnlyOnce() throws Exception {
        var ledger=new CreditLedger(Files.createTempDirectory("market").resolve("data.yml"));var seller=UUID.randomUUID();var a=UUID.randomUUID();var b=UUID.randomUUID();
        ledger.adjust(a,"set",500);ledger.adjust(b,"set",500);var row=row(seller,500,10000);ledger.listAuction(row,8,100);
        try(var pool=Executors.newFixedThreadPool(2)){
            var one=pool.submit(()->{try{ledger.buyAuction(a,row.id(),500,200);return true;}catch(IllegalArgumentException error){return false;}});
            var two=pool.submit(()->{try{ledger.buyAuction(b,row.id(),500,200);return true;}catch(IllegalArgumentException error){return false;}});
            assertNotEquals(one.get(),two.get());
        }
        assertEquals(500,ledger.balance(a)+ledger.balance(b));assertEquals(500,ledger.balance(seller));assertEquals(1,ledger.auctions().claims().size());
    }
}
