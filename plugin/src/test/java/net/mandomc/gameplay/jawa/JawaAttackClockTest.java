package net.mandomc.gameplay.jawa;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class JawaAttackClockTest {
    @Test public void groupMembersGetIndependentFirstDelaysAndBoundedSubsequentIntervals(){
        var clock=new JawaAttackClock(new JawaAttackClock.Settings(6,36,60,80),new Random(16));var target=UUID.randomUUID();var times=new HashMap<UUID,List<Long>>();
        for(int i=0;i<4;i++)times.put(UUID.randomUUID(),new ArrayList<>());
        for(long tick=0;tick<400;tick++)for(var row:times.entrySet())if(clock.ready(row.getKey(),target,tick))row.getValue().add(tick);
        var first=new HashSet<Long>();var intervals=new HashSet<Long>();
        for(var shots:times.values()){assertTrue(shots.getFirst()>=6&&shots.getFirst()<=36);first.add(shots.getFirst());for(int i=1;i<shots.size();i++){long delta=shots.get(i)-shots.get(i-1);assertTrue(delta>=60&&delta<=80);intervals.add(delta);}}
        assertTrue(first.size()>1);assertTrue(intervals.size()>1);
    }
    @Test public void disengagementRequiresAnotherInitialDelay(){
        var clock=new JawaAttackClock(new JawaAttackClock.Settings(6,6,60,60),new Random(1));var jawa=UUID.randomUUID();var target=UUID.randomUUID();
        assertFalse(clock.ready(jawa,target,0));assertTrue(clock.ready(jawa,target,6));clock.disengage(jawa);assertFalse(clock.ready(jawa,target,500));assertTrue(clock.ready(jawa,target,506));
    }
}
