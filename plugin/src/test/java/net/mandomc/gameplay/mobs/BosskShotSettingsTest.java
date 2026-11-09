package net.mandomc.gameplay.mobs;
import org.junit.Test;
import static org.junit.Assert.*;
public class BosskShotSettingsTest {
 @Test public void singleAndWholeBurstHaveDifferentBudgets(){var s=new BosskShotSettings(6,8);assertEquals(6,s.perShot(true),0);assertEquals(8,s.perShot(false)*3,1e-9);}
 @Test public void invalidDamageIsRejected(){for(double d:new double[]{0,-1,21,Double.NaN,Double.POSITIVE_INFINITY})assertThrows(IllegalArgumentException.class,()->new BosskShotSettings(d,8));}
}
