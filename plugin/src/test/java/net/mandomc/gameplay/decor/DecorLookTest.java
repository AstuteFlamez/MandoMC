package net.mandomc.gameplay.decor;

import org.junit.Test;
import static org.junit.Assert.*;

public class DecorLookTest {
    @Test public void nearbyHeadTurnsPromptlyWithoutRotatingBody(){
        var first=DecorLook.step(0,0,0,30,10,true);
        assertEquals(0,first.body(),0);assertEquals(30,first.head(),0);assertEquals(10,first.pitch(),0);
    }
    @Test public void behindTargetSettlesQuicklyWithBoundedNeckAndBody(){
        var pose=new DecorLook.Pose(0,0,0);
        for(int tick=0;tick<20;tick++){
            var next=DecorLook.step(pose.body(),pose.head(),pose.pitch(),179,90,true);
            assertTrue(Math.abs(DecorLook.wrap(next.body()-pose.body()))<=12.001);
            assertTrue(Math.abs(DecorLook.wrap(next.head()-next.body()))<=60.001);
            assertTrue(Math.abs(next.pitch())<=35.001);pose=next;
        }
        assertEquals(179,pose.head(),.001);assertEquals(134,pose.body(),.001);
    }
    @Test public void wrapsAlongShortestArcAndReturnsExactlyToIdle(){
        var pose=DecorLook.step(179,179,0,-179,-90,true);
        assertEquals(179,pose.body(),0);assertEquals(-179,pose.head(),0);assertEquals(-15,pose.pitch(),0);
        for(int tick=0;tick<20;tick++)pose=DecorLook.step(pose.body(),pose.head(),pose.pitch(),12,2,false);
        assertEquals(12,pose.body(),.001);assertEquals(12,pose.head(),.001);assertEquals(2,pose.pitch(),0);
    }
}
