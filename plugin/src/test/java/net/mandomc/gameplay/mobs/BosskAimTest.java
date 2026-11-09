package net.mandomc.gameplay.mobs;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.Test;
import static org.junit.Assert.*;
import net.mandomc.integrations.weapons.BosskAim;
public class BosskAimTest {
 @Test public void stationaryTargetDoesNotShift(){var target=new Location(null,20,1,0);assertEquals(target,BosskAim.lead(new Location(null,0,1,0),target,new Vector(),2.75,10));}
 @Test public void leadsMovingTargetToActualIntercept(){var source=new Location(null,0,1,0);var target=new Location(null,20,1,0);var velocity=new Vector(0,0,0.28);var aim=BosskAim.lead(source,target,velocity,2.75,10);double time=source.toVector().distance(aim.toVector())/2.75;assertEquals(time*0.28,aim.getZ(),0.001);assertEquals(20,aim.getX(),0.001);}
 @Test public void boundsPrediction(){var aim=BosskAim.lead(new Location(null,0,1,0),new Location(null,100,1,0),new Vector(0,0,0.28),2.75,10);assertTrue(aim.getZ()<=2.8+1e-9);}
}
