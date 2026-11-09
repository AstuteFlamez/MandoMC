package net.mandomc.gameplay.mobs;
import org.junit.Test;
import static org.junit.Assert.*;
import org.bukkit.Location;
public class DioxisSettingsTest {
    @Test public void rejectsUnboundedCloudsAndCrowdControl(){
        assertThrows(IllegalArgumentException.class,()->new DioxisSettings(25,120,Double.NaN,30,100,100,0,5,0.65));
        assertThrows(IllegalArgumentException.class,()->new DioxisSettings(25,120,4,1000,1000,100,0,5,0.65));
        assertThrows(IllegalArgumentException.class,()->new DioxisSettings(25,120,4,30,10,100,0,5,0.65));
        assertThrows(IllegalArgumentException.class,()->new DioxisSettings(25,120,4,30,100,100,4,5,0.65));
    }
    @Test public void throwHasVisibleArcAndFixedDodgeableLandingPoint(){
        var a=new Location(null,0,2,0);var b=new Location(null,10,0,0);
        assertEquals(a,BosskDioxis.arc(a,b,0,0.65));assertEquals(b,BosskDioxis.arc(a,b,1,0.65));
        assertEquals(5,BosskDioxis.arc(a,b,0.5,0.65).getX(),0.001);
        assertEquals(1.65,BosskDioxis.arc(a,b,0.5,0.65).getY(),0.001);
        assertEquals(2,a.getY(),0.001);assertEquals(0,b.getY(),0.001);
    }
}
