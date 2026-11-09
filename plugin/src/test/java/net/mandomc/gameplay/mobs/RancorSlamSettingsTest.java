package net.mandomc.gameplay.mobs;
import org.junit.Test;
import static org.junit.Assert.*;
public class RancorSlamSettingsTest {
 @Test public void rejectsUnboundedAndNonfiniteVisualBudgets(){
  assertThrows(IllegalArgumentException.class,()->new RancorSlamSettings(Double.NaN,2,2,20,256,4));
  assertThrows(IllegalArgumentException.class,()->new RancorSlamSettings(14,0,2,20,256,4));
  assertThrows(IllegalArgumentException.class,()->new RancorSlamSettings(14,2,0,20,256,4));
  assertThrows(IllegalArgumentException.class,()->new RancorSlamSettings(14,2,2,200,256,4));
  assertThrows(IllegalArgumentException.class,()->new RancorSlamSettings(14,2,2,20,10000,4));
  assertThrows(IllegalArgumentException.class,()->new RancorSlamSettings(14,2,2,20,256,100));
 }
 @Test public void shippedWaveCoversLegacySlamRadiusAndExpiresQuickly(){
  var s=new RancorSlamSettings(14,2,2,20,256,4);
  assertEquals(7,(int)Math.ceil(s.radius()/s.ringStep()));
  assertTrue(Math.ceil(s.radius()/s.ringStep())*s.ringInterval()+s.lifetime()<=40);
 }
}
