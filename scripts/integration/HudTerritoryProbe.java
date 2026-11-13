package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.clans.*;
import net.mandomc.gameplay.territory.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
public final class HudTerritoryProbe extends JavaPlugin {
 private MandoPlugin mando;private Player actor;private Object handle;private Map<UUID,Object> online;
 private ClanService clans;private ClanState.Clan clan,viewer,third;private ClanState.Chunk claim;
 private Scoreboard board;private Objective objective;private int checks;private boolean full;
 private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 private String line(int n){return PlainTextComponentSerializer.plainText().serialize(objective.getScore("mmc_line_"+n).customName());}
 private net.kyori.adventure.text.format.TextColor color(Component c,String word,net.kyori.adventure.text.format.TextColor inherited){var effective=c.color()==null?inherited:c.color();if(c instanceof net.kyori.adventure.text.TextComponent t&&t.content().contains(word))return effective;for(var child:c.children()){var found=color(child,word,effective);if(found!=null)return found;}return null;}
 private void valueColor(int row,String word,net.kyori.adventure.text.format.NamedTextColor expected){check(expected.equals(color(objective.getScore("mmc_line_"+row).customName(),word,null)),"Native territory value color "+word+":"+expected);}
 private void later(Runnable r){Bukkit.getScheduler().runTaskLater(this,()->{try{r.run();}catch(Throwable e){fail(e);}},12);}
 @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{begin();}catch(Throwable e){fail(e);}},100);}
 private void stable(){stable(14);}
 private void stable(int rows){check(actor.getScoreboard()==board,"Same scoreboard instance");check(board.getObjective("mandomc_hud").equals(objective),"Same objective");check(board.getEntries().stream().filter(e->e.startsWith("mmc_line_")).count()==rows,"Exact active row count "+rows);for(int i=0;i<rows;i++)check(objective.getScore("mmc_line_"+i).getScore()==14-i,"Stable row order "+i);}
 private void begin()throws Exception {
  mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando!=null&&mando.hud()!=null,"Native HUD enabled");
  var f=mando.hud().getClass().getDeclaredField("settings");f.setAccessible(true);var settings=(net.mandomc.gameplay.hud.HudSettings)f.get(mando.hud());
  check(settings.kills().contains("<kills>")&&settings.kills().contains("<deaths>"),"Kills/deaths share template");
  check(settings.lines().stream().filter(s->s.contains("<territory>")).count()==1,"One normal territory row");check(settings.combatLines().stream().filter(s->s.contains("<territory>")).count()==0,"No combat territory row");check(settings.lines().size()==14&&settings.combatLines().size()==13,"Normal fourteen, combat thirteen rows");
  full=Files.exists(getDataFolder().toPath().resolve("full-suite"));if(!full){success();return;}check(getServer().getPort()==25590,"Actors confined to disposable server");
  var zones=Bukkit.getServicesManager().load(TerritoryService.class);var spawn=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")).getSpawnLocation();
  check(zones.mechanisms(Bukkit.getWorld(NamespacedKey.minecraft("ilum")).getSpawnLocation())&&!zones.mechanisms(spawn),"Original Ilum-only mechanism policy retained");
  var spawned=new java.util.ArrayList<org.bukkit.entity.Entity>();
  try{zones.spawnNpc(org.bukkit.entity.EntityType.PIG,spawn,()->{spawned.add(spawn.getWorld().spawnEntity(spawn,org.bukkit.entity.EntityType.PIG));spawned.add(spawn.getWorld().spawnEntity(spawn,org.bukkit.entity.EntityType.PIG));return true;});check(spawned.get(0).isValid(),"Original approved NPC spawn works");check(!spawned.get(1).isValid(),"NPC allowance is one use, not a spawn bypass");}finally{spawned.forEach(org.bukkit.entity.Entity::remove);}
  var world=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));actor=actor(world,world.getSpawnLocation());actor.setStatistic(Statistic.PLAYER_KILLS,7);actor.setStatistic(Statistic.DEATHS,3);mando.hud().attach(actor);board=actor.getScoreboard();objective=board.getObjective("mandomc_hud");
  check(line(12).equals("play.mandomc.net"),"Static play footer in normal layout");check(line(8).equals("⚔ ᴋɪʟʟѕ/ᴅᴇᴀᴛʜѕ: 7/3"),"Combined actual kills and deaths");check(line(9).equals("⚑ ᴛᴇʀʀɪᴛᴏʀʏ: Safezone"),"Batuu safezone");valueColor(9,"Safezone",net.kyori.adventure.text.format.NamedTextColor.GOLD);stable();
  var target=Bukkit.getWorld(NamespacedKey.minecraft("mandalore_review"));check(target!=null,"Mandalore available");check(actor.teleport(new Location(target,240,280,240)),"Cross-world movement succeeds");actor.setGravity(false);
  later(()->wilderness(target));
 }
 private void steps(Runnable... actions){var i=new int[]{0};Runnable[] next=new Runnable[1];next[0]=()->{if(i[0]<actions.length){actions[i[0]++].run();later(next[0]);}};later(next[0]);}
 private TerritoryIdentity identity(){return Bukkit.getServicesManager().load(TerritoryService.class).identity(actor,actor.getLocation());}
 private void wilderness(World world){
  check(line(9).endsWith("Wilderness"),"Cross-world wilderness updates on cycle");valueColor(9,"Wilderness",net.kyori.adventure.text.format.NamedTextColor.GRAY);stable();
  clans=Bukkit.getServicesManager().load(ClanService.class);check(clans!=null,"Authoritative clan service");claim=new ClanState.Chunk(world.getKey().toString(),15,15);check(!clans.state().claims.containsKey(claim),"Fixture chunk unclaimed");clan=clans.state().create("HudClaim",UUID.randomUUID(),System.currentTimeMillis());clans.state().claims.put(claim,clan.id);
  steps(
   ()->{check(line(9).endsWith("HudClaim"),"Same-chunk claim change shows owner");check(identity().relation()==TerritoryIdentity.Relation.OTHER,"Foreign claim blue relation");valueColor(9,"HudClaim",net.kyori.adventure.text.format.NamedTextColor.BLUE);stable();clan.members.add(actor.getUniqueId());},
   ()->{check(identity().relation()==TerritoryIdentity.Relation.OWN,"Membership change becomes green own claim");valueColor(9,"HudClaim",net.kyori.adventure.text.format.NamedTextColor.GREEN);clan.members.remove(actor.getUniqueId());viewer=clans.state().create("HudViewer",actor.getUniqueId(),System.currentTimeMillis());third=clans.state().create("HudThird",UUID.randomUUID(),System.currentTimeMillis());for(int i=0;i<10;i++)clans.state().kill(viewer.id,clan.id,System.currentTimeMillis());},
   ()->{check(identity().relation()==TerritoryIdentity.Relation.OTHER,"Eligible but unstarted raid stays blue");check(clans.state().breach(viewer.id,clan.id,System.currentTimeMillis(),60000,10),"Actual raid started");},
   ()->{check(identity().relation()==TerritoryIdentity.Relation.RAID,"Attacker sees active defender claim red");valueColor(9,"HudClaim",net.kyori.adventure.text.format.NamedTextColor.RED);var pair=viewer.id.compareTo(clan.id)<0?new ClanState.Pair(viewer.id,clan.id):new ClanState.Pair(clan.id,viewer.id);clans.state().rivalries.put(pair,new ClanState.Rivalry(0,viewer.id,System.currentTimeMillis()-1));},
   ()->{check(identity().relation()==TerritoryIdentity.Relation.OTHER,"Raid expiry returns blue");for(int i=0;i<10;i++)clans.state().kill(third.id,clan.id,System.currentTimeMillis());check(clans.state().breach(third.id,clan.id,System.currentTimeMillis(),60000,10),"Unrelated raid started");},
   ()->{check(identity().relation()==TerritoryIdentity.Relation.OTHER,"Third-party raid stays blue");var prior=identity();clans.state().claims.put(new ClanState.Chunk(world.getKey().toString(),15,14),clan.id);check(actor.teleport(new Location(world,240,280,224)),"Move into adjacent same-owner claim");actor.setGravity(false);check(prior.equals(identity()),"Contiguous same owner has identical transition identity");},
   ()->{check(line(9).endsWith("HudClaim"),"Adjacent claim keeps owner label");clans.state().claims.entrySet().removeIf(e->e.getValue().equals(clan.id));},
   ()->{check(line(9).endsWith("Wilderness"),"Same-chunk unclaim refreshes");check(actor.teleport(new Location(world,0,280,0)),"Move to warzone");actor.setGravity(false);},
   ()->{check(line(9).endsWith("Warzone"),"Warzone movement refreshes");valueColor(9,"Warzone",net.kyori.adventure.text.format.NamedTextColor.DARK_RED);mando.combat().tag(actor);},
   ()->{check(line(9).matches("⚔ ɪɴ ᴄᴏᴍʙᴀᴛ: [0-9]+\\.[0-9]s"),"Combat countdown retained");check(line(8).contains("ᴅᴏ ɴᴏᴛ ʟᴏɢ ᴏᴜᴛ")&&line(10).contains("ᴅᴏ ɴᴏᴛ ʟᴏɢ ᴏᴜᴛ"),"Both combat warnings retained");noTerritory();check(line(11).equals("play.mandomc.net"),"Static play footer in shortened combat layout");stable(13);mando.combat().clear(actor);},
   ()->{check(line(8).endsWith("7/3"),"Stats return after combat");check(line(9).endsWith("Warzone"),"Territory returns to normal slot");stable();lifecycle();}
  );
 }
 private void noTerritory(){
  check(!objective.getScore("mmc_line_13").isScoreSet(),"Surplus last entry actually removed");
  for(String key:board.getEntries())if(key.startsWith("mmc_line_")&&objective.getScore(key).isScoreSet()){
   var value=PlainTextComponentSerializer.plainText().serialize(objective.getScore(key).customName());
   check(!value.contains("ᴛᴇʀʀɪᴛᴏʀʏ")&&!value.contains("⚑"),"No stale territory in "+key);
  }
 }
 private void toggle(){mando.getCommand("scoreboard").execute(actor,"sb",new String[0]);}
 private void lifecycle(){
  mando.combat().tag(actor);toggle();
  later(()->{check(objective.getDisplaySlot()==null,"Hidden during combat");noTerritory();stable(13);mando.combat().tag(actor);
   check(mando.combat().correctMovement(actor,Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")).getSpawnLocation()),"Internal world transition while tagged");actor.setGravity(false);
   later(()->{noTerritory();stable(13);toggle();
    later(()->{check(objective.getDisplaySlot()==DisplaySlot.SIDEBAR,"Unhide during combat");noTerritory();mando.combat().clear(actor);
     later(()->{check(line(9).endsWith("Safezone"),"World transition restores current territory after clear");stable();
      var respawn=new org.bukkit.event.player.PlayerRespawnEvent(actor,actor.getLocation(),false,false,false,org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH);Bukkit.getPluginManager().callEvent(respawn);
      mando.combat().tag(actor);
      later(()->{noTerritory();toggle();
       Bukkit.getScheduler().runTaskLater(this,()->{try{check(!mando.combat().tagged(actor),"Natural combat deadline expired");stable();check(line(9).endsWith("Safezone"),"Hidden expiry updates territory model");toggle();
        later(()->{check(objective.getDisplaySlot()==DisplaySlot.SIDEBAR,"Reveal expired normal layout");check(line(8).endsWith("7/3"),"Kills/deaths restored");stable();try{success();}catch(Exception e){fail(e);}});
       }catch(Throwable e){fail(e);}},340);
      });
     });
    });
   });
  });
 }
 private void success()throws Exception{String result="HUD TERRITORY PASSED: "+checks+" native assertions";Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),result);getLogger().info(result);finish();if(full)Bukkit.shutdown();}
 private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"HUD TERRITORY FAILED",e);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),e.toString());}catch(Exception ignored){}finish();if(getServer().getPort()==25590)Bukkit.shutdown();}
 private void finish(){if(clans!=null){for(var c:new ClanState.Clan[]{clan,viewer,third})if(c!=null)clans.state().remove(c);}if(actor!=null&&mando!=null){mando.combat().clear(actor);mando.hud().detach(actor);}cleanup();}
    @SuppressWarnings("unchecked") private Player actor(World world,Location at)throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),
                playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object list=server.getClass().getMethod("getPlayerList").invoke(server);
        var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);
        Object level=world.getClass().getMethod("getHandle").invoke(world);
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"HandbookCheck");
        handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class.forName("net.minecraft.world.entity.Entity").getField("valid").setBoolean(handle,true);
        Class<?> flow=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection");
        Object connection=connectionClass.getConstructor(flow).newInstance(flow.getField("SERVERBOUND").get(null));
        Class<?> cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie");Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Class<?> listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");Object listener=listenerClass.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);
        playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);
        var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,at.getX(),at.getY(),at.getZ());
        var player=(Player)playerClass.getMethod("getBukkitEntity").invoke(handle);online.put(player.getUniqueId(),handle);
        level.getClass().getMethod("addNewPlayer",playerClass).invoke(level,handle);player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);return player;
    }
    @SuppressWarnings("unchecked") private void cleanup(){
        if(actor==null)return;
        try {
            actor.setItemOnCursor(null);
            if(actor.getOpenInventory().getTopInventory() instanceof CraftingInventory c)c.setMatrix(new ItemStack[4]);
            actor.closeInventory();online.remove(actor.getUniqueId());
            for(var w:Bukkit.getWorlds()){Object level=w.getClass().getMethod("getHandle").invoke(w);((List<Object>)level.getClass().getMethod("players").invoke(level)).remove(handle);}
            handle.getClass().getMethod("discard").invoke(handle);
        }catch(Exception error){getLogger().warning("Book fixture cleanup: "+error);}
        actor=null;
    }
 @Override public void onDisable(){finish();}
}
