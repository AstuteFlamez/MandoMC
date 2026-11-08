package net.mandomc.gameplay.saber;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuardStateTest {
    private SaberSettings settings() {
        return new SaberSettings("vader","mandomc:sabers/vader","mandomc:sabers/vader_hilt",8,40,12,1,1.5,.45,100,20,.5,30,80,80,4);
    }
    @Test public void repeatedFireOverheatsAndCannotCoolDuringLockout() {
        var g=new GuardState();var s=settings();
        for(int i=0;i<4;i++)assertFalse(g.hit(i,s));
        assertTrue(g.hit(4,s));assertTrue(g.disabled(83));assertFalse(g.disabled(84));
        g.cool(83,s);assertEquals(100,g.heat(),0);g.cool(84,s);assertEquals(0,g.heat(),0);
    }
    @Test public void cooldownWaitsAfterLastHitAndNeverGoesNegative() {
        var g=new GuardState();var s=settings();g.hit(10,s);g.cool(39,s);assertEquals(20,g.heat(),0);
        for(int i=40;i<200;i++)g.cool(i,s);assertEquals(0,g.heat(),0);
    }
    @Test public void frontAndBackAreNotEquivalent() {
        assertTrue(GuardState.facing(1,80));assertFalse(GuardState.facing(-1,80));assertFalse(GuardState.facing(0,80));
    }
    @Test public void rejectsMissingAndInvalidSettings() {
        var c=new YamlConfiguration();c.set("schema-version",2);
        assertThrows(IllegalArgumentException.class,()->SaberSettings.read(c));
    }
}
