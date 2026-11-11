package net.mandomc.verification;
import java.nio.file.*;import java.util.*;
import java.security.MessageDigest;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.inventory.*;import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.bootstrap.MandoPlugin;import net.mandomc.gameplay.clans.*;
public final class FrozenLandProbe extends JavaPlugin {
 private MandoPlugin mando;private Player actor;private Object handle;private Map<UUID,Object> online;
 private FrozenLandPreparation failedPreparation;private ClanService clans;private ClanState.Clan clan;private int checks;private org.bukkit.block.Block edited;private org.bukkit.block.data.BlockData original;
 private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 private String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
 @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){fail(e);}},160);}
 private void run()throws Exception {
  check(getServer().getPort()==25590,"Frozen test is isolated");mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");clans=Bukkit.getServicesManager().load(ClanService.class);check(clans!=null,"Native clans enabled");
  var folder=mando.getDataFolder().toPath();var journal=folder.resolve("data/frozen-land/test-frozen2.journal");check(Files.exists(journal.resolveSibling(journal.getFileName()+".completed")),"Capture durably completed");
  check(Files.readAllLines(journal).size()==4,"Four finite chunks recorded");var world=Bukkit.getWorld(NamespacedKey.minecraft("mandalore_review"));
  for(var c:List.of(new ClanState.Chunk("minecraft:mandalore_review",13,13),new ClanState.Chunk("minecraft:mandalore_review",13,12),new ClanState.Chunk("minecraft:mandalore_review",12,13),new ClanState.Chunk("minecraft:mandalore_review",3,-9))) {check(clans.land().supported(c)&&clans.land().ready(c),"Prepared chunk ready "+c);}
  var retained=folder.resolve("data/land-baselines/minecraft_mandalore_review_3_-9.gz");check(sha(retained).equals(Files.readString(getDataFolder().toPath().resolve("preserved.sha256")).trim()),"Existing original preserved byte-exact");
  var outside=new ClanState.Chunk("minecraft:mandalore_review",12,12);check(!clans.land().supported(outside),"Outside frozen extent unsupported");check(!clans.land().prepare(world,12,12),"Player prepare cannot sample outside scope");check(!clans.land().hasBaseline(outside),"No outside baseline invented");
  var chunk=new ClanState.Chunk("minecraft:mandalore_review",13,13);var before=sha(clans.land().baselinePath(chunk));
  world.getChunkAt(13,13);edited=world.getBlockAt(208,279,208);original=edited.getBlockData();edited.setType(Material.GOLD_BLOCK,false);check(clans.land().prepare(world,13,13),"Prepared edits require no retry");check(sha(clans.land().baselinePath(chunk)).equals(before),"Later terrain edits never refresh frozen baseline");
  actor=actor(world,new Location(world,208,280,208));clan=clans.state().create("FrozenTest",actor.getUniqueId(),System.currentTimeMillis());
  var place=new org.bukkit.event.block.BlockPlaceEvent(edited,edited.getState(),edited.getRelative(org.bukkit.block.BlockFace.DOWN),new ItemStack(Material.GOLD_BLOCK),actor,true,EquipmentSlot.HAND);
  Bukkit.getPluginManager().callEvent(place);check(!place.isCancelled(),"First Survival placement in prepared chunk allowed without retry");
  var broken=new org.bukkit.event.block.BlockBreakEvent(edited,actor);Bukkit.getPluginManager().callEvent(broken);check(!broken.isCancelled(),"First Survival break in prepared chunk allowed without retry");
  check(sha(clans.land().baselinePath(chunk)).equals(before),"Native edit callbacks retain frozen original");
  check(mando.getCommand("c").execute(actor,"c",new String[]{"claim"}),"Actual claim command dispatch");check(clans.owner(actor.getLocation())==clan,"First claim succeeds immediately using durable backup");
  var hashes=getDataFolder().toPath().resolve("first-hashes.txt");var entries=Files.readAllLines(journal);String body=String.join("\n",entries)+"\n";
  if(Files.exists(hashes))check(body.equals(Files.readString(hashes)),"Restart preserves frozen baseline checksums and recovered journal");else Files.writeString(hashes,body);
  // Same capture-id cannot be silently repurposed to refresh or broaden the frozen scope.
  var config=folder.resolve("clans/prepared-land.yml");String text=Files.readString(config);Files.writeString(config,text+"- 'minecraft:mandalore_review 12 12'\n");boolean rejected=false;
  try{new FrozenLandPreparation(mando,clans);}catch(IllegalStateException expected){rejected=true;}finally{Files.writeString(config,text);}
  check(rejected,"Changed frozen scope with same capture-id rejected");check(!clans.land().supported(outside),"Rejected scope change does not broaden claims");
  partialFailure(config,text);
 }
 private void partialFailure(Path config,String originalText)throws Exception {
  Files.writeString(config,"schema-version: 1\ncapture-id: partial-failure\nchunks:\n- 'minecraft:mandalore_review 3 -9'\n- 'minecraft:missing_capture_world 0 0'\n");
  try{failedPreparation=new FrozenLandPreparation(mando,clans);}finally{Files.writeString(config,originalText);}
  Bukkit.getScheduler().runTaskLater(this,()->{try{
   check(failedPreparation.failed(),"Missing scoped world fails explicitly");check(failedPreparation.completed()==1&&failedPreparation.total()==2,"Partial completion accounted exactly");
   check(clans.land().supported(new ClanState.Chunk("minecraft:mandalore_review",3,-9)),"Verified partial original stays supported");
   var missing=new ClanState.Chunk("minecraft:missing_capture_world",0,0);check(!clans.land().supported(missing)&&!clans.land().hasBaseline(missing),"Missing world is never generated or claimed");
   check(Files.exists(mando.getDataFolder().toPath().resolve("data/frozen-land/partial-failure.journal.failed")),"Failure receipt durable");
   String result="FROZEN LAND PASSED: "+checks+" assertions";Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),result);getLogger().info(result);finish();Bukkit.shutdown();
  }catch(Throwable e){fail(e);}},30);
 }
 private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"FROZEN LAND FAILED",e);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),e.toString());}catch(Exception ignored){}finish();Bukkit.shutdown();}
 private void finish(){if(failedPreparation!=null){failedPreparation.close();failedPreparation=null;}if(edited!=null&&original!=null){edited.setBlockData(original,false);edited=null;}if(clans!=null&&clan!=null){clans.state().remove(clan);clans.save();clan=null;}cleanup();}
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
