package net.mandomc.gameplay.ilum;

import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class ParkourRunTest {
    @Test public void orderedCheckpointsCannotBeSkippedOrFarmed() {
        var alice=new ParkourRun(UUID.randomUUID(),0,true);
        var bob=new ParkourRun(UUID.randomUUID(),0,true);
        assertEquals(alice,alice.reach(4));assertFalse(alice.canFinish());
        for(int cp=1;cp<=4;cp++){
            alice=alice.reach(cp);assertEquals(cp,alice.checkpoint());assertEquals(alice,alice.reach(cp));
            assertEquals(0,bob.checkpoint());
        }
        assertTrue(alice.canFinish());assertFalse(bob.canFinish());
    }
    @Test public void practiceModeKeepsProgressButCannotBeRanked() {
        var run=new ParkourRun(UUID.randomUUID(),4,true).practice();
        assertTrue(run.canFinish());assertFalse(run.ranked());
        assertThrows(IllegalArgumentException.class,()->new ParkourRun(UUID.randomUUID(),5,true));
    }
    @Test public void twentyFourHourRewardBoundaryAndClockRollbackAreSafe() {
        long day=86_400_000L,last=1_000_000_000L;
        assertTrue(ParkourRun.rewardReady(0,last,day));
        assertFalse(ParkourRun.rewardReady(last,last,day));
        assertFalse(ParkourRun.rewardReady(last,last+day-1,day));
        assertTrue(ParkourRun.rewardReady(last,last+day,day));
        assertFalse(ParkourRun.rewardReady(last,last-1,day));
    }
    @Test public void timeFormattingKeepsSubsecondRecords() {
        assertEquals("0:35.378",ParkourService.format(35378));
        assertEquals("60:00.001",ParkourService.format(3_600_001));
    }
}
