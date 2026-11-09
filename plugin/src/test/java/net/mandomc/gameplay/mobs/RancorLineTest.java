package net.mandomc.gameplay.mobs;
import org.junit.Test;import static org.junit.Assert.*;
public class RancorLineTest {
 @Test public void onlyTheForwardStripAndCurrentWaveSectionHit(){assertTrue(RancorLine.hits(6,1,0,4,6));assertFalse(RancorLine.hits(6,3,0,4,6));assertFalse(RancorLine.hits(-1,0,0,0,2));assertFalse(RancorLine.hits(2,0,0,6,8));assertFalse(RancorLine.hits(10,0,0,6,8));}
 @Test public void upstairsAndOutOfWidthPlayersAreExcluded(){assertFalse(RancorLine.hits(6,0,8,4,6));assertTrue(RancorLine.hits(6,-1.7,2,4,6));assertFalse(RancorLine.hits(6,-1.9,2,4,6));}
}
