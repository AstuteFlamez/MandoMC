package net.mandomc.gameplay.territory;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.mandomc.gameplay.territory.TerritoryPolicy.Zone.*;
public class TatooineTerritoryTest {
 private YamlConfiguration config(){return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/territory/maps.yml"),StandardCharsets.UTF_8));}
 @Test public void palaceWarTownAndRoadSafeOtherSettlementsWilderness(){
  var p=new TerritoryPolicy(config());String w="minecraft:tatooine_review";
  assertEquals(SAFEZONE,p.zone(w,-125,70,-118));assertEquals(SAFEZONE,p.zone(w,-35,75,-142));
  assertEquals(WARZONE,p.zone(w,166,121,127));assertEquals(WILDERNESS,p.zone(w,-144,72,105));
  assertEquals(WILDERNESS,p.zone(w,-5,66,171));assertFalse(p.spawning(w,-125,70,-118));
  assertTrue(p.spawning(w,166,121,127));assertTrue(p.spawning(w,-144,72,105));
  assertFalse(p.edits(w,166,121,127));assertTrue(p.edits(w,-144,72,105));
 }
 @Test public void everyMapCellMatchesProtectionSpawningAndFullHeightClaimRules(){
  var p=new TerritoryPolicy(config());String w="minecraft:tatooine_review";var a=p.map(w,0,0);assertNotNull(a);
  for(int z=-16;z<=15;z++)for(int x=-16;x<=15;x++)for(int y:new int[]{-32,70,223}){
   char c=a.zone(x,z);assertEquals(c=='S'?SAFEZONE:c=='W'?WARZONE:WILDERNESS,p.zone(w,x*16,y,z*16));
   assertEquals(c=='.',p.wildernessChunk(w,x,z));assertEquals(c!='S',p.spawning(w,x*16,y,z*16));
  }
  assertEquals(SAFEZONE,p.zone("minecraft:spawnworld",0,90,0));assertEquals(WARZONE,p.zone("minecraft:mandalore_review",0,8,0));
 }
 @Test public void optionalAtlasPreservesOldConfigAndRejectsDuplicateWorld(){
  var c=config();c.set("additional-maps",null);assertNull(new TerritoryPolicy(c).map("minecraft:tatooine_review",0,0));
  c=config();c.set("additional-maps.tatooine.world",c.getString("world"));final var bad=c;
  assertThrows(IllegalArgumentException.class,()->new TerritoryPolicy(bad));
 }
}
