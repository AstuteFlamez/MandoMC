package net.mandomc.gameplay.boba;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThrustFuelTest {
    @Test public void buysFirstSecondBeforeThrustAndRetainsFractionsAcrossRapidToggles() {
        var coal=new AtomicInteger(2);var fuel=new ThrustFuel(0);
        java.util.function.BooleanSupplier buy=()->coal.getAndUpdate(v->Math.max(0,v-1))>0;
        for(int i=0;i<100;i++) {
            assertTrue(fuel.start(buy));assertTrue(fuel.spend(10_000_000,buy));
        }
        assertEquals(1,coal.get());assertEquals(0,fuel.credit());
        assertTrue(fuel.start(buy));assertEquals(0,coal.get());
        assertTrue(fuel.spend(1_000_000_000,buy));assertFalse(fuel.start(buy));
    }
    @Test public void stalledTickPaysEveryElapsedIntervalAndCannotOverdraw() {
        var coal=new AtomicInteger(3);var fuel=new ThrustFuel(0);
        java.util.function.BooleanSupplier buy=()->coal.getAndUpdate(v->Math.max(0,v-1))>0;
        assertTrue(fuel.start(buy));assertTrue(fuel.spend(2_500_000_000L,buy));
        assertEquals(500_000_000,fuel.credit());assertEquals(.5,fuel.seconds(0),0);
        assertFalse(fuel.spend(600_000_000,buy));assertEquals(0,fuel.credit());
    }
    @Test public void warningsIncludeCreditAndRearmOnlyAboveThreshold() {
        var fuel=new ThrustFuel(500_000_000);
        assertFalse(fuel.warning(5));assertTrue(fuel.warning(4));assertFalse(fuel.warning(3));
        assertFalse(fuel.warning(5));assertTrue(fuel.warning(4));
        assertTrue(new ThrustFuel(0).warning(5));assertFalse(new ThrustFuel(0).warning(0));
    }
    @Test public void persistentCreditResumesWithoutFreeRounding() {
        var fuel=new ThrustFuel(1234);assertTrue(fuel.start(()->false));
        var resumed=new ThrustFuel(fuel.credit());assertTrue(resumed.spend(1234,()->false));
        assertFalse(resumed.start(()->false));
    }
}
