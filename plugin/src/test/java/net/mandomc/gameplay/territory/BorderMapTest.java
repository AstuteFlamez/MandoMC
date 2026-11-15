package net.mandomc.gameplay.territory;
import java.io.InputStreamReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;
public class BorderMapTest {
 @Test public void nabooViewMatchesIts500BlockBorderWithoutChangingOutsideProtection(){
  var c=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/territory/maps.yml")));var p=new TerritoryPolicy(c);
  var a=ChunkAtlas.borderView(p,"minecraft:naboo","Naboo",-13552,3440,500,59);
  assertEquals(-863,a.minX());assertEquals(-832,a.maxX());assertEquals(199,a.minZ());assertEquals(230,a.maxZ());assertEquals(32,a.rows().size());
  assertEquals(22,a.rows().stream().flatMapToInt(String::chars).filter(v->v=='S').count());
  assertEquals(100,a.rows().stream().flatMapToInt(String::chars).filter(v->v=='W').count());
  assertEquals(TerritoryPolicy.Zone.SAFEZONE,p.zone("minecraft:naboo",-13480,59,3080));assertFalse(a.contains(-843,192));
 }
 @Test public void unboundedWorldsKeepTheirExistingMapAndAlignedBordersDoNotAddARow(){
  var c=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/territory/maps.yml")));var p=new TerritoryPolicy(c);
  assertNull(ChunkAtlas.borderView(p,"minecraft:naboo","Naboo",0,0,59999968,59));
  var a=ChunkAtlas.borderView(p,"minecraft:hoth","Hoth",0,0,512,70);assertEquals(-16,a.minX());assertEquals(15,a.maxX());assertEquals(32,a.rows().size());
 }
}
