package net.mandomc.verification;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import net.mandomc.bootstrap.MandoPlugin;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.nio.file.*;

/** Disposable-runtime only: missing rig recovery, identity, foreign ownership and real restarts. */
public final class GrayJawaModelProbe extends JavaPlugin {
 private MandoPlugin mando;private YamlConfiguration config;private NamespacedKey key;
 private final Map<String,UUID> hosts=new LinkedHashMap<>();private final Map<UUID,ActiveModel> before=new HashMap<>();private final List<Chunk> chunks=new ArrayList<>();private int checks;
 public void onEnable(){if(Bukkit.getPort()!=25593)throw new IllegalStateException("Never install this probe live");later(80,this::start);}
 private void later(long ticks,Runnable action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable failure){getLogger().log(java.util.logging.Level.SEVERE,"GRAY MODEL FAILED",failure);Bukkit.shutdown();}},ticks);}
 private void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
 private void start(){mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");config=YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"mobs/jawas.yml"));key=new NamespacedKey(mando,"jawa_npc_v1");
  for(String id:config.getConfigurationSection("npcs").getKeys(false)){var l=home(id);var c=l.getChunk();if(!chunks.contains(c)){c.addPluginChunkTicket(this);chunks.add(c);}}
  later(100,this::healthy);
 }
 private Location home(String id){String p="npcs."+id;return new Location(Bukkit.getWorld(NamespacedKey.fromString(config.getString(p+".world"))),config.getDouble(p+".x"),config.getDouble(p+".y"),config.getDouble(p+".z"));}
 private LivingEntity host(String id){var list=home(id).getWorld().getNearbyEntities(home(id),8,8,8).stream().filter(e->id.equals(e.getPersistentDataContainer().get(key,PersistentDataType.STRING))).toList();check(list.size()==1,"one host per anchor "+id+" count="+list.size());return (LivingEntity)list.getFirst();}
 private ActiveModel inspect(String id){var e=host(id);check(e.getLocation().distanceSquared(home(id))<.01,"exact saved position "+id);var m=ModelEngineAPI.getModeledEntity(e);check(m!=null&&!m.isDestroyed(),"modeled entity exists "+id);check(!m.isBaseEntityVisible(),"pig base hidden "+id);check(m.getModels().size()==1,"single model owner "+id);var a=m.getModel("jawa_black").orElseThrow();check(!a.isDestroyed()&&!a.isRemoved(),"live gray rig "+id);check(a.getBones().keySet().containsAll(Set.of("body","head","face","tip","left_arm","right_arm","blaster")),"complete gray bones "+id);var f=a.getBone("face").orElseThrow();check(f.getDefaultTint().asRGB()==0xff0000&&f.getBlockLight()==15&&f.getSkyLight()==15,"red fullbright eyes "+id);check(a.getBone("body").orElseThrow().getDefaultTint().asRGB()==0xffffff,"robe unchanged "+id);check(e.isInvulnerable(),"invincible "+id);return a;}
 private void healthy(){
  var ids=config.getConfigurationSection("npcs").getKeys(false);check(ids.size()==7,"all7 saved anchors");
  var saved=Path.of(getDataFolder().toString(),"hosts.yml");var old=YamlConfiguration.loadConfiguration(saved.toFile());
  for(String id:ids){var e=host(id);hosts.put(id,e.getUniqueId());before.put(e.getUniqueId(),inspect(id));if(old.contains(id))check(old.getString(id).equals(e.getUniqueId().toString()),"same host after real restart "+id);}
  int i=0;for(String id:ids){var e=host(id);var m=ModelEngineAPI.getModeledEntity(e);if(i++%2==0)m.removeModel("jawa_black").orElseThrow().destroy();else m.setBaseEntityVisible(true);}
  later(100,this::recovered);
 }
 private void recovered(){int i=0;for(String id:hosts.keySet()){var a=inspect(id);var e=host(id);check(e.getUniqueId().equals(hosts.get(id)),"repair preserves host UUID "+id);check((a!=before.get(e.getUniqueId()))==(i++%2==0),"only missing rigs replaced "+id);before.put(e.getUniqueId(),a);}
  // Foreign owners cannot be overwritten by the repair boundary.
  var l=home(hosts.keySet().iterator().next()).clone().add(0,10,0);var pig=(Pig)l.getWorld().spawnEntity(l,EntityType.PIG);pig.setAI(false);var modeled=ModelEngineAPI.getOrCreateModeledEntity(pig);var foreign=ModelEngineAPI.createActiveModel("jawa_brown");modeled.addModel(foreign,true);modeled.setBaseEntityVisible(true);var repair=new net.mandomc.integrations.models.GrayJawaModels(s->{});check(!repair.ensure(pig,0),"foreign owner refused");check(modeled.getModels().size()==1&&modeled.getModel("jawa_brown").orElseThrow()==foreign&&modeled.isBaseEntityVisible(),"foreign model and visibility unchanged");repair.close();ModelEngineAPI.removeModeledEntity(pig).destroy();pig.remove();
  later(80,this::stable);
 }
 private void stable(){for(String id:hosts.keySet())check(inspect(id)==before.get(hosts.get(id)),"healthy sweeps keep same active rig "+id);
  if(new java.io.File(getDataFolder(),"phase1").exists()) {
   var c=home("batuu_market_01").getChunk();for(var held:chunks)held.removePluginChunkTicket(this);chunks.clear();c.getWorld().removePluginChunkTickets(mando);getLogger().info("Chunk test tickets="+c.getPluginChunkTickets()+" forced="+c.isForceLoaded());
   c.getWorld().unloadChunkRequest(c.getX(),c.getZ());
   later(220,()->{check(!c.getWorld().isChunkLoaded(c.getX(),c.getZ()),"anchor chunk remains unloaded");for(String id:hosts.keySet()){var loaded=home(id).getChunk();if(!chunks.contains(loaded)){loaded.addPluginChunkTicket(this);chunks.add(loaded);}}later(100,()->{for(String id:hosts.keySet()){inspect(id);check(host(id).getUniqueId().equals(hosts.get(id)),"chunk cycle keeps UUID "+id);}finish();});});
  }else finish();
 }
 private void finish(){
  var data=new YamlConfiguration();hosts.forEach((id,uuid)->data.set(id,uuid.toString()));try{data.save(new java.io.File(getDataFolder(),"hosts.yml"));}catch(Exception e){throw new IllegalStateException(e);}
  getLogger().info("GRAY MODEL PASSED "+checks+" assertions; all7 intact; phase="+(new java.io.File(getDataFolder(),"phase1").exists()?2:1));
  try{getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("phase1"),"passed");}catch(Exception e){throw new IllegalStateException(e);}Bukkit.shutdown();
 }
}
