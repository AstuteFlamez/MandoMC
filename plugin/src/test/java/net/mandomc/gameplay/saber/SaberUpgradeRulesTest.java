package net.mandomc.gameplay.saber;
import org.junit.Test;
import static org.junit.Assert.*;
public class SaberUpgradeRulesTest {
    @Test public void totalAndIndividualPathCapsBothApply(){
        assertTrue(SaberUpgrades.eligible(5,2,6,3));assertFalse(SaberUpgrades.eligible(6,0,6,3));
        assertFalse(SaberUpgrades.eligible(3,3,6,3));
    }
}
