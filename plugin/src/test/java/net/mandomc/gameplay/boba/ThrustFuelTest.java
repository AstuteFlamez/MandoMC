package net.mandomc.gameplay.boba;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;
public class ThrustFuelTest {
    @Test public void tenCoalBuyOneSecondAcrossRapidToggles() {
        var coal=new AtomicInteger(10);var fuel=new ThrustFuel(0);
        java.util.function.BooleanSupplier buy=()->coal.getAndUpdate(v->Math.max(0,v-1))>0;
        for(int i=0;i<100;i++){assertTrue(fuel.start(buy));assertTrue(fuel.spend(10_000_000,buy));}
        assertEquals(0,coal.get());assertEquals(0,fuel.credit());assertFalse(fuel.start(buy));
    }
    @Test public void firstCoalBuysPointOneSecondAndPartialIntervalSurvivesPause() {
        var fuel=new ThrustFuel(0);var bought=new AtomicInteger();
        assertTrue(fuel.start(()->{bought.incrementAndGet();return true;}));
        assertEquals(100_000_000,fuel.credit());assertTrue(fuel.spend(20_000_000,()->false));
        assertTrue(fuel.start(()->{throw new AssertionError("Partial interval must not buy more fuel");}));
        assertEquals(.58,fuel.seconds(5),.000001);assertEquals(1,bought.get());
    }
    @Test public void stalledTickPaysEveryPointOneSecondWithoutOverdraw() {
        var coal=new AtomicInteger(26);var fuel=new ThrustFuel(0);
        java.util.function.BooleanSupplier buy=()->coal.getAndUpdate(v->Math.max(0,v-1))>0;
        assertTrue(fuel.start(buy));assertTrue(fuel.spend(2_550_000_000L,buy));
        assertEquals(50_000_000,fuel.credit());assertEquals(0,coal.get());
        assertFalse(fuel.spend(60_000_000,buy));assertEquals(0,fuel.credit());
    }
    @Test public void fiveSecondWarningNowMeansFiftyCoalAndRearmsAboveIt() {
        var fuel=new ThrustFuel(0);assertFalse(fuel.warning(51));assertTrue(fuel.warning(50));
        assertFalse(fuel.warning(40));assertFalse(fuel.warning(51));assertTrue(fuel.warning(50));
        var partial=new ThrustFuel(50_000_000);assertFalse(partial.warning(50));
        assertTrue(partial.spend(50_000_000,()->false));assertTrue(partial.warning(50));
        assertFalse(new ThrustFuel(0).warning(0));
    }
    @Test public void alreadyPurchasedOldIntervalIsHonoredDuringRateChange() {
        var fuel=new ThrustFuel(800_000_000);assertTrue(fuel.start(()->false));
        assertTrue(fuel.spend(700_000_000,()->false));assertEquals(.1,fuel.seconds(0),0);
    }
    @Test public void persistentCreditResumesWithoutFreeRounding() {
        var fuel=new ThrustFuel(1234);assertTrue(fuel.start(()->false));
        var resumed=new ThrustFuel(fuel.credit());assertTrue(resumed.spend(1234,()->false));assertFalse(resumed.start(()->false));
    }
    @Test public void levelTwoPaysHalfTheCoalForTheSamePoweredTime() {
        for(int level:new int[]{1,2}) {
            var coal=new AtomicInteger(10);var fuel=new ThrustFuel(0,level);
            java.util.function.BooleanSupplier buy=()->coal.getAndUpdate(v->Math.max(0,v-1))>0;
            assertTrue(fuel.start(buy));assertTrue(fuel.spend(1_000_000_000L,buy));
            assertEquals(level==1?0:5,coal.get());assertEquals(0,fuel.credit());
        }
    }
    @Test public void levelChangePreservesPaidNanosecondsAndFiveSecondWarning() {
        var fuel=new ThrustFuel(70_000_000,1);fuel.setLevel(2);
        assertEquals(70_000_000,fuel.credit());assertEquals(5.07,fuel.seconds(25),.000001);
        assertFalse(fuel.warning(25));assertTrue(fuel.warning(24));
        assertTrue(fuel.spend(70_000_000,()->false));assertTrue(fuel.start(()->true));
        assertEquals(200_000_000,fuel.credit());fuel.setLevel(1);assertEquals(200_000_000,fuel.credit());
    }
}
