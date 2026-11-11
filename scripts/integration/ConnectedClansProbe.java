package net.mandomc.verification;
import java.nio.file.*;import java.util.*;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.inventory.meta.BookMeta;import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.bootstrap.MandoPlugin;import net.mandomc.gameplay.clans.*;
import net.kyori.adventure.text.Component;import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
public final class ConnectedClansProbe extends JavaPlugin {
 private MandoPlugin mando;private ClanService clans;private ClanState.Clan clan;private int checks;
 private final List<Player> actors=new ArrayList<>();private final Map<UUID,Object> handles=new HashMap<>();private Map<UUID,Object> online;private List<Object> globalPlayers;private Map<String,Object> names;
 private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 private void command(Player p,String... args){check(mando.getCommand("c").execute(p,"c",args),"Actual clan command executes "+String.join(" ",args));}
 private void move(Player p,World w,int x,int z){check(p.teleport(new Location(w,x*16+.5,280,z*16+.5)),"Move to test chunk "+x+","+z);p.setGravity(false);}
 @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){fail(e);}},180);}
 @SuppressWarnings("unchecked") private void run()throws Exception {
  check(getServer().getPort()==25590,"Connected-clan tests isolated");mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");clans=Bukkit.getServicesManager().load(ClanService.class);check(clans.settings().members()==4&&clans.settings().chunks()==4,"Runtime four members and four chunks");
  var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);var f=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");f.setAccessible(true);online=(Map<UUID,Object>)f.get(list);globalPlayers=(List<Object>)list.getClass().getMethod("getPlayers").invoke(list);
  var nameField=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByName");nameField.setAccessible(true);names=(Map<String,Object>)nameField.get(list);
  var world=Bukkit.getWorld(NamespacedKey.minecraft("mandalore_review"));Player leader=actor(new Location(world,192.5,280,192.5));Player fourth=actor(leader.getLocation()),stale=actor(leader.getLocation()),fifth=actor(leader.getLocation());
  check(Bukkit.getPlayerExact(fourth.getName())==fourth,"Native invite target lookup works");
  command(leader,"create","ConnectedTest");clan=clans.member(leader);check(clan!=null,"Native clan created");UUID offline1=UUID.randomUUID(),offline2=UUID.randomUUID();clans.state().join(clan,offline1,4,System.currentTimeMillis());clans.state().join(clan,offline2,4,System.currentTimeMillis());
  command(leader,"invite",fourth.getName());command(leader,"invite",stale.getName());command(fourth,"join",clan.name);check(clan.members.size()==4&&clans.member(fourth)==clan,"Fourth member joins");command(stale,"join",clan.name);check(clan.members.size()==4&&clans.member(stale)==null,"Stale simultaneous invite cannot become fifth");
  command(leader,"invite",fifth.getName());clan.members.remove(offline2);command(fifth,"join",clan.name);check(clan.members.size()==3&&clans.member(fifth)==null,"Full clan cannot issue a usable invitation");command(stale,"join",clan.name);check(clan.members.size()==4&&clans.member(stale)==clan,"Previously invited player joins once slot is free");
  clan.members.add(UUID.randomUUID());var retained=Set.copyOf(clan.members);command(leader,"invite",fifth.getName());command(fifth,"join",clan.name);check(clan.members.equals(retained)&&clans.member(fifth)==null,"Over-limit legacy clan retained without further joins");clans.save().get(5,java.util.concurrent.TimeUnit.SECONDS);var saved=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(mando.getDataFolder().toPath().resolve("data/clans.yml").toFile());check(saved.getMapList("clans").stream().anyMatch(row->clan.id.toString().equals(row.get("id"))&&((List<?>)row.get("members")).size()==5),"Over-limit membership persists without forced removal");
  for(var c:List.of(new ClanState.Chunk(world.getKey().toString(),12,12),new ClanState.Chunk(world.getKey().toString(),13,12),new ClanState.Chunk(world.getKey().toString(),13,13),new ClanState.Chunk(world.getKey().toString(),12,13)))check(clans.land().ready(c)&&clans.land().supported(c),"Frozen original ready "+c);
  command(leader,"claim");check(clans.owner(leader.getLocation())==clan,"First prepared claim succeeds immediately");move(leader,world,13,13);command(leader,"claim");check(clans.state().claims.values().stream().filter(clan.id::equals).count()==1,"Diagonal-only native claim denied");
  move(leader,world,13,12);command(leader,"claim");check(clans.owner(leader.getLocation())==clan,"Cardinal edge accepted");move(leader,world,13,13);command(leader,"claim");check(clans.owner(leader.getLocation())==clan,"Third adjoining claim accepted");move(leader,world,13,12);command(leader,"unclaim");check(clans.owner(leader.getLocation())==clan,"Native bridge unclaim denied before land release");
  move(leader,world,12,13);command(leader,"claim");check(clans.state().claims.values().stream().filter(clan.id::equals).count()==4,"Four-claim square allowed");move(leader,world,13,12);command(leader,"unclaim");check(clans.owner(leader.getLocation())==null,"Square corner removal retains connected land");
  command(leader,"unclaimall");check(clans.state().claims.values().stream().noneMatch(clan.id::equals),"Unclaimall clears intentional whole territory");
  check(mando.getCommand("tutorial").execute(leader,"tutorial",new String[0]),"Handbook command executes");BookMeta book=null;org.bukkit.inventory.ItemStack stack=null;for(var item:leader.getInventory().getContents())if(item!=null&&item.getItemMeta() instanceof BookMeta meta){book=meta;stack=item;break;}check(book!=null&&book.getPageCount()==11,"Eleven handbook pages retained");check(PlainTextComponentSerializer.plainText().serialize(book.page(7)).replace('\n',' ').contains("Max 4 players"),"Clans page now says four players");
  var oldMeta=(BookMeta)stack.getItemMeta();oldMeta.pages(java.util.stream.IntStream.range(0,11).<Component>mapToObj(i->Component.text("Old five-player guide")).toList());stack.setItemMeta(oldMeta);leader.getInventory().setItem(0,stack);mando.getCommand("tutorial").execute(leader,"tutorial",new String[0]);check(PlainTextComponentSerializer.plainText().serialize(((BookMeta)leader.getInventory().getItem(0).getItemMeta()).page(7)).replace('\n',' ').contains("Max 4 players"),"Existing handbook refreshes four-member rule in place");
  Files.createDirectories(getDataFolder().toPath());String result="CONNECTED CLANS PASSED: "+checks+" assertions";Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),result);getLogger().info(result);cleanup();Bukkit.shutdown();
 }
 private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"CONNECTED CLANS FAILED",e);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),e.toString());}catch(Exception ignored){}cleanup();Bukkit.shutdown();}
    @SuppressWarnings("unchecked") private Player actor(Location at)throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),
                playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level=at.getWorld().getClass().getMethod("getHandle").invoke(at.getWorld());
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"ClanCheck"+actors.size());
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class.forName("net.minecraft.world.entity.Entity").getField("valid").setBoolean(handle,true);
        Class<?> flow=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection");
        Object connection=connectionClass.getConstructor(flow).newInstance(flow.getField("SERVERBOUND").get(null));
        Class<?> cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Class<?> listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object listener=listenerClass.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);
        playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);
        var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,at.getX(),at.getY(),at.getZ());
        Player player=(Player)playerClass.getMethod("getBukkitEntity").invoke(handle);
        actors.add(player);handles.put(player.getUniqueId(),handle);online.put(player.getUniqueId(),handle);
        level.getClass().getMethod("addNewPlayer",playerClass).invoke(level,handle);
        globalPlayers.add(handle);names.put(player.getName().toLowerCase(java.util.Locale.ROOT),handle);player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);return player;
    }
 @SuppressWarnings("unchecked") private void cleanup(){
  if(clans!=null&&clan!=null){clans.state().remove(clan);clans.save();clan=null;}
  for(var p:List.copyOf(actors))try{var h=handles.get(p.getUniqueId());p.closeInventory();if(online!=null)online.remove(p.getUniqueId());if(globalPlayers!=null)globalPlayers.remove(h);if(names!=null)names.remove(p.getName().toLowerCase(java.util.Locale.ROOT));if(mando!=null&&mando.hud()!=null)mando.hud().detach(p);for(var w:Bukkit.getWorlds()){var level=w.getClass().getMethod("getHandle").invoke(w);((List<Object>)level.getClass().getMethod("players").invoke(level)).remove(h);}h.getClass().getMethod("discard").invoke(h);}catch(Exception e){getLogger().warning("Fixture cleanup: "+e);}actors.clear();handles.clear();
 }
 @Override public void onDisable(){cleanup();}
}
