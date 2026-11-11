package net.mandomc.gameplay.territory;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.mandomc.gameplay.territory.TerritoryPolicy.Zone.*;
public class TerritoryPolicyTest {
 private TerritoryPolicy policy(){return new TerritoryPolicy(YamlConfiguration.loadConfiguration(new InputStreamReader(
  getClass().getResourceAsStream("/territory/maps.yml"),StandardCharsets.UTF_8)));}
 @Test public void batuuIsSafeEverywhereIncludingBeyondTheRenderedWindow(){
  var p=policy();for(int x:new int[]{-29999984,-1,0,29999984})for(int z:new int[]{-29999984,-1,0,29999984}){
   assertEquals(SAFEZONE,p.zone("minecraft:spawnworld",x,93,z));assertFalse(p.edits("minecraft:spawnworld",x,93,z));assertFalse(p.combat("minecraft:spawnworld",x,93,z));
   var a=p.map("minecraft:spawnworld",Math.floorDiv(x,16),Math.floorDiv(z,16));assertTrue(a.contains(Math.floorDiv(x,16),Math.floorDiv(z,16)));
   assertEquals(1024,a.rows().stream().mapToLong(row->row.chars().filter(c->c=='S').count()).sum());
  }
 }
 @Test public void mandaloreSpawnCavesAndWildernessHaveDifferentPermissions(){
  var p=policy();assertEquals(SAFEZONE,p.zone("minecraft:mandalore_review",-18,70,-192));
  assertFalse(p.combat("minecraft:mandalore_review",-18,70,-192));assertFalse(p.edits("minecraft:mandalore_review",-18,70,-192));
  assertEquals(WARZONE,p.zone("minecraft:mandalore_review",0,8,0));assertTrue(p.combat("minecraft:mandalore_review",0,8,0));assertFalse(p.edits("minecraft:mandalore_review",0,8,0));
  assertEquals(WILDERNESS,p.zone("minecraft:mandalore_review",-69,76,-188));assertTrue(p.edits("minecraft:mandalore_review",-69,76,-188));
 }
 @Test public void exactHothAndRetiredLandingBoundariesArePreserved(){
  var p=policy();assertEquals(SAFEZONE,p.zone("minecraft:hoth",-47,80,-153));assertEquals(WARZONE,p.zone("minecraft:hoth",-237,-64,-121));
  assertEquals(WARZONE,p.zone("minecraft:hoth",237,319,237));assertEquals(SAFEZONE,p.zone("minecraft:hoth",238,100,0));
  assertEquals(SAFEZONE,p.zone("minecraft:hoth",0,320,0));assertFalse(p.spawning("minecraft:hoth",0,100,0));
  assertEquals(SAFEZONE,p.zone("minecraft:overworld",-144,73,-144));assertEquals(WILDERNESS,p.zone("minecraft:overworld",-145,73,-144));
  assertEquals(WILDERNESS,p.zone("minecraft:tatooine",1711,100,2201));assertTrue(p.edits("minecraft:tatooine",1711,100,2201));
 }
 @Test public void malformedNativePoliciesCannotStart(){
  var config=new YamlConfiguration();assertThrows(IllegalArgumentException.class,()->new TerritoryPolicy(config));
 }
 @Test public void originalIlumAllowsItsMechanismsWhileRemainingProtected(){
  var p=policy();assertTrue(p.wholeWorldSafe("minecraft:ilum"));assertTrue(p.mechanisms("minecraft:ilum"));
  assertFalse(p.edits("minecraft:ilum",27,-51,-60));assertFalse(p.combat("minecraft:ilum",27,-51,-60));
  assertFalse(p.mechanisms("minecraft:spawnworld"));assertFalse(p.mechanisms("minecraft:tatooine"));
 }
}
