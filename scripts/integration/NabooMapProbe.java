package net.mandomc.verification;
import java.nio.file.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.zip.ZipFile;
import net.mandomc.gameplay.territory.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Exercise the actual /c map command and the selected resource-pack ZIP without changing player state. */
public final class NabooMapProbe extends JavaPlugin {
 private int checks;
 private void check(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);checks++;}
 @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),"NABOO MAP/PACK PASSED: "+checks);getLogger().info("NABOO MAP/PACK PASSED: "+checks);}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"NABOO MAP/PACK FAILED",e);try{getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),e.toString());}catch(Exception ignored){}}},100);}
 private void run()throws Exception {
  var world=Bukkit.getWorld(NamespacedKey.minecraft("naboo"));check(world!=null,"Naboo loaded");
  var location=new Location(world,-13341.430043026525,59,3209.6097442130404);UUID uuid=UUID.randomUUID();List<Component> lines=new ArrayList<>();
  var actor=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
   case "getLocation"->location.clone();case "getWorld"->world;case "getUniqueId"->uuid;case "getName"->"NabooMapFixture";
   case "getGameMode"->GameMode.SURVIVAL;case "hasPermission","isOnline"->true;
   case "sendMessage"->{if(a[0]instanceof Component c)lines.add(c);yield null;}
   case "hashCode"->uuid.hashCode();case "equals"->p==a[0];default->null;
  });
  check(Bukkit.getPluginCommand("c").execute(actor,"c",new String[]{"map"}),"Actual clan-map command dispatch");
  var text=lines.stream().map(PlainTextComponentSerializer.plainText()::serialize).toList();
  var rows=text.stream().filter(s->s.codePoints().filter(c->c==0x25a0||c=='+').count()==32).toList();
  check(rows.size()==32,"Exactly 32 rows intersect the 500-block border");check(rows.getFirst().startsWith("199 "),"First visible chunk row 199");check(rows.getLast().startsWith("230 "),"Last visible chunk row 230");
  check(text.stream().anyMatch(s->s.contains("500 × 500")),"Exact block-size caption");
  check(text.stream().anyMatch(s->s.contains("X -863..-832")&&s.contains("Z 199..230")),"Map extent matches actual world border");
  var policy=Bukkit.getServicesManager().load(TerritoryService.class).policy();check(policy.zone("minecraft:naboo",-13480,59,3080)==TerritoryPolicy.Zone.SAFEZONE,"Protection beyond the view remains intact");
  var properties=new java.util.Properties();try(var in=Files.newInputStream(Path.of("server.properties"))){properties.load(in);}
  String hash=properties.getProperty("resource-pack-sha1");Path cache=Path.of("resource-packs");check(Files.isRegularFile(cache.resolve("pinned.json")),"Development selection pinned");
  boolean found=false;
  try(var files=Files.list(cache)){
   for(Path path:files.filter(p->p.toString().endsWith(".zip")).toList()){
    byte[] data=Files.readAllBytes(path);String sha=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-1").digest(data));if(!sha.equals(hash))continue;
    found=true;try(var zip=new ZipFile(path.toFile())){
     for(String n:List.of("assets/mandomc/items/planets/naboo.json","assets/mandomc/models/item/planets/naboo.json","assets/mandomc/textures/item/planets/naboo.png"))check(zip.getEntry(n)!=null,"Selected pack contains "+n);
     try(var input=zip.getInputStream(zip.getEntry("assets/mandomc/textures/item/planets/naboo.png"))){byte[] png=input.readAllBytes();check(java.nio.ByteBuffer.wrap(png,16,8).getInt()==32&&java.nio.ByteBuffer.wrap(png,20,4).getInt()==32,"Native 32x32 icon in selected pack");}
    }
    break;
   }
  }
  check(found,"Selected pack's actual checksum matches cached bytes");
 }
}
