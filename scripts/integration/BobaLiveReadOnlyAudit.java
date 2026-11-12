package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.boba.*;
import net.mandomc.gameplay.clans.*;
import net.mandomc.gameplay.decor.DecorService;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
/** Read-only post-release audit: no actors, inventory mutations, world edits or event changes. */
public final class BobaLiveReadOnlyAudit extends JavaPlugin implements Listener {
 private final List<String> rows=new ArrayList<>();private int checks;
 @Override public void onEnable(){Bukkit.getPluginManager().registerEvents(this,this);}
 @EventHandler public void loaded(ServerLoadEvent e){Bukkit.getScheduler().runTaskLater(this,this::audit,102);}
 private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);rows.add("PASS "+why);checks++;}
 private void audit(){try{
  var plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(plugin!=null&&plugin.isEnabled(),"MandoMC enabled");
  var failed=MandoPlugin.class.getDeclaredField("failed");failed.setAccessible(true);check(!failed.getBoolean(plugin),"full bootstrap/login gate healthy");
  check(Bukkit.getServicesManager().load(BobaEquipmentFeature.class)!=null,"Boba registered service live");
  for(String id:List.of("boba_jetpack","boba_flamethrower")){var item=plugin.items().create(id);check(item!=null&&id.equals(plugin.items().identify(item)),"live catalog persistent identity "+id);}
  var cfg=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"mobs/drops/jabba.yml"));
  check(cfg.getDouble("drops.BobaFett.equipment.boba_jetpack")==.1&&cfg.getDouble("drops.BobaFett.equipment.boba_flamethrower")==.15,"independent live gear drop registration");
  check(cfg.getString("drops.BobaFett.weapon").equals("ee3")&&cfg.getDouble("drops.BobaFett.weapon-chance")==.5&&cfg.getInt("drops.BobaFett.ammo-rolls")==3,"original Boba loot retained");
  check(!cfg.getStringList("drops.JawaBrown.salvage.pool").isEmpty(),"Jawa salvage retained");
  var before=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(),"before-jabba.yml"));for(var entry:before.getValues(true).entrySet())if(!(entry.getValue() instanceof org.bukkit.configuration.ConfigurationSection)&&!java.util.Objects.equals(entry.getValue(),cfg.get(entry.getKey())))throw new AssertionError("Existing loot changed: "+entry.getKey());check(true,"all pre-deployment loot values retained");
  check("26.1.2-v1".equals(System.getProperty("mandomc.redstone.agent")),"mandatory native redstone agent active");
  var listeners=HandlerList.getRegisteredListeners(plugin).stream().map(r->r.getListener()).distinct().toList();
  check(listeners.stream().filter(l->l.getClass().getSimpleName().equals("RedstoneCircuitBoundary")).count()==1,"one redstone boundary owner");
  var gray=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"mobs/jawas.yml"));
  var ids=gray.getConfigurationSection("npcs").getKeys(false);check(ids.size()==7,"seven saved gray NPC anchors");
  for(String id:ids){String p="npcs."+id;var world=Bukkit.getWorld(NamespacedKey.fromString(gray.getString(p+".world")));var at=new Location(world,gray.getDouble(p+".x"),gray.getDouble(p+".y"),gray.getDouble(p+".z"));var hosts=world.getLivingEntities().stream().filter(e->id.equals(e.getPersistentDataContainer().get(new NamespacedKey("mandomc","jawa_npc_v1"),PersistentDataType.STRING))).toList();check(hosts.size()==1&&hosts.getFirst().isValid()&&hosts.getFirst().getLocation().distanceSquared(at)<.01,"single gray NPC retained "+id);}
  var decor=Bukkit.getServicesManager().load(DecorService.class);check(decor!=null&&decor.bases().size()==9,"nine decor anchors retained");
  for(var d:decor.definitions()){var base=decor.bases().get(d.id());check(base!=null&&base.isValid()&&base.getLocation().distanceSquared(d.location())<.01,"decor base retained "+d.id());}
  var boards=listeners.stream().filter(l->l.getClass().getSimpleName().equals("WorldLeaderboards")).toList();check(boards.size()==1,"one leaderboard owner");var f=boards.getFirst().getClass().getDeclaredField("displays");f.setAccessible(true);var displays=(Map<?,?>)f.get(boards.getFirst());check(displays.size()==2&&displays.values().stream().allMatch(e->e instanceof TextDisplay d&&d.isValid()),"both world boards retained");
  var journal=plugin.getDataFolder().toPath().resolve("data/frozen-land/wilderness-hoth-20261111.journal");check(Files.exists(journal.resolveSibling(journal.getFileName()+".completed")),"frozen journal completed");var lines=Files.readAllLines(journal);check(lines.size()==2115,"2115 frozen original chunks retained");var land=Bukkit.getServicesManager().load(ClanService.class).land();for(String line:lines){var a=line.split(" ");var c=new ClanState.Chunk(a[0],Integer.parseInt(a[1]),Integer.parseInt(a[2]));if(!land.supported(c)||!land.hasBaseline(c)||!land.ready(c))throw new AssertionError("Frozen baseline unavailable "+line);}check(true,"all 2115 frozen baselines ready");
  var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var pack=String.valueOf(server.getClass().getMethod("getServerResourcePack").invoke(server));check(pack.contains("resource-pack-26.1.2-286525e138bc")&&pack.contains("918a1c7414a452526722296f473ff495afec8524"),"effective native pack URL/hash match published merged gear pack");
  check("jabba".equals(plugin.events().activeId())&&plugin.events().queuedId()==null,"fresh current Jabba event and next None restored");
  Files.createDirectories(getDataFolder().toPath());Files.write(getDataFolder().toPath().resolve("PASSED.txt"),rows);getLogger().info("LIVE BOBA READONLY AUDIT PASSED: "+checks+" checks at startup+102 ticks; no actors or gameplay changes");
 }catch(Throwable error){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());}catch(Exception ignored){}getLogger().log(java.util.logging.Level.SEVERE,"LIVE BOBA READONLY AUDIT FAILED",error);}finally{Bukkit.getPluginManager().disablePlugin(this);}}
}
