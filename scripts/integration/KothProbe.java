package net.mandomc.verification;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.events.*;
import net.mandomc.gameplay.travel.*;
import net.mandomc.gameplay.arrival.*;
import net.mandomc.integrations.regions.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;

/** Isolated fake-player snapshots and real worlds/APIs. No real player gear or balances are touched. */
public final class KothProbe extends JavaPlugin {
    private int checks;
    private MandoPlugin plugin;
    private final List<Actor> actors=new ArrayList<>();
    private final List<KothEvent> fixtures=new ArrayList<>();
    @Override public void onEnable() {
        getCommand("kothprobe").setExecutor((sender,c,label,args)->{run();return true;});
        Bukkit.getScheduler().runTaskLater(this,this::run,80);
    }
    private void run() {
        checks=0;
        try {verify();getLogger().info("KOTH PROBE PASSED: "+checks+" assertions");}
        catch(Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"KOTH PROBE FAILED",error);}
        finally {cleanup();}
    }
    private void verify() throws Exception {
        plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        check(plugin.events()!=null && plugin.warps()!=null,"event and warp services initialized");
        check(Bukkit.getPluginCommand("warp").getPlugin()==plugin,"bare /warp belongs to MandoMC, not Essentials");
        check(Bukkit.getPluginCommand("warps").getPlugin()==plugin,"legacy /warps alias belongs to MandoMC");
        check(Bukkit.getPluginCommand("events").getPlugin()==plugin,"legacy /events alias belongs to MandoMC");
        World hoth=Bukkit.getWorld(NamespacedKey.minecraft("hoth"));
        check(hoth!=null,"recovered Hoth loaded under its own stable key");
        check(hoth.getWorldFolder().toPath().toString().contains("hoth"),"Hoth uses migrated dimension storage");
        for(String id:WarpService.ids()) {
            var point=plugin.warps().destination(id);
            if (!id.equals("tatooine")) new WorldGuardArrivalProtection().requireProtected(point);
            check(net.mandomc.gameplay.arrival.ArrivalDestinations.safeSpawn(point) != null,id+" arrival has safe footing and protection");
        }
        check(plugin.warps().destination("earth").getWorld().getKey().equals(NamespacedKey.minecraft("overworld")),"Earth reuses existing survival world");
        var arrival=plugin.warps().destination("hoth");
        check(arrival.getX()==-47 && arrival.getY()==80 && arrival.getZ()==-153,"Hoth arrival preserves recovered warp area");
        var hill=new Location(hoth,-148,27,141);
        getLogger().info("Hill block="+hill.getBlock().getType()+", below="+hill.clone().subtract(0,1,0).getBlock().getType());
        check(new SaberProtection().allowed(hill),"Hoth hill permits saber/reflected projectile combat");
        check(!new SaberProtection().allowed(arrival),"Hoth arrival denies saber combat");
        var query=WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        var regions=query.getApplicableRegions(BukkitAdapter.adapt(hill));
        check(regions.queryState(null,Flags.BLOCK_BREAK)==StateFlag.State.DENY,"hill terrain is protected against breaks");
        check(regions.queryState(null,Flags.BLOCK_PLACE)==StateFlag.State.DENY,"hill terrain is protected against placement");
        check(regions.queryState(null,Flags.TNT)==StateFlag.State.DENY,"explosions cannot destroy the recovered map");
        var registry=WorldGuard.getInstance().getFlagRegistry();
        var shoot=(StateFlag)registry.get("weapon-shoot");var damage=(StateFlag)registry.get("weapon-damage");
        check(regions.queryState(null,shoot)==StateFlag.State.ALLOW && regions.queryState(null,damage)==StateFlag.State.ALLOW,
                "WeaponMechanics can shoot and damage in Hoth warzone");
        check(hoth.getGameRuleValue(GameRule.DO_MOB_SPAWNING)==Boolean.FALSE,"natural mob spawning disabled on event map");
        var a=actor(hill);var b=actor(hill.clone().add(1,0,0));
        verifyCapture(hill,a,b);
        verifyMenusAndTravel(a);
        check(plugin.sabers().settings().recoveryDelay()==100,"saved five-second saber recharge tuning is now active");
    }
    private void verifyCapture(Location hill,Actor a,Actor b) throws Exception {
        var manager=plugin.events();check(manager.activeId()==null,"no live event is interrupted by verification");
        var originalBlock=hill.getBlock().getBlockData().getAsString();
        check(manager.start("koth"),"admin/event lifecycle starts recovered KOTH");
        check(!manager.start("koth"),"second simultaneous event is refused");
        boolean blocked=false;try {manager.reload();}catch(IllegalStateException expected){blocked=true;}
        check(blocked,"active config reload is refused without losing capture");
        var koth=(KothEvent)manager.event("koth");
        check(koth.settings().captureTicks()==3600,"live KOTH uses accepted three-minute capture");
        check(koth.eligible(a.player),"survival competitor is eligible");
        a.mode=GameMode.CREATIVE;check(!koth.eligible(a.player),"creative cannot capture");
        a.mode=GameMode.SPECTATOR;check(!koth.eligible(a.player),"spectator cannot capture");
        a.mode=GameMode.SURVIVAL;a.god=true;check(!koth.eligible(a.player),"invulnerable player cannot capture");a.god=false;
        a.nativePlayer.setMetadata("vanished",new org.bukkit.metadata.FixedMetadataValue(this,true));
        check(!koth.eligible(a.player),"vanished player cannot capture");a.nativePlayer.removeMetadata("vanished",this);
        koth.updateCapture(Map.of(a.id.toString(),a.player));
        for(int i=0;i<4;i++)koth.updateCapture(Map.of(a.id.toString(),a.player,b.id.toString(),b.player));
        check(koth.capture().ticks()==40 && koth.capture().holder().equals(a.id.toString()),"enemy presence does not reset current holder");
        koth.move(new org.bukkit.event.player.PlayerMoveEvent(a.player,hill,hill.clone().add(20,0,0)));
        check(koth.capture().holder()==null && koth.capture().ticks()==0,"leaving hill immediately clears control between samples");
        koth.updateCapture(Map.of(b.id.toString(),b.player));
        koth.quit(new org.bukkit.event.player.PlayerQuitEvent(b.player,net.kyori.adventure.text.Component.empty()));
        check(koth.capture().holder()==null,"logout immediately clears current holder");
        koth.updateCapture(Map.of(b.id.toString(),b.player));
        var source=org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
        koth.death(new org.bukkit.event.entity.PlayerDeathEvent(b.player,source,new ArrayList<>(),0,net.kyori.adventure.text.Component.empty(),false));
        check(koth.capture().holder()==null,"death immediately clears current holder");
        koth.updateCapture(Map.of(b.id.toString(),b.player));
        for(int i=0;i<359;i++)koth.updateCapture(Map.of(b.id.toString(),b.player));
        check(koth.running(),"capture does not finish early");
        koth.updateCapture(Map.of(b.id.toString(),b.player));
        check(!koth.running() && b.id.equals(koth.winner()),"continuous capture announces exactly one winning player");
        check(hill.getBlock().getBlockData().getAsString().equals(originalBlock),"winning creates no reward chest or terrain mutation");
        check(a.nativePlayer.getInventory().isEmpty() && b.nativePlayer.getInventory().isEmpty(),"winning grants no inventory reward");
        manager.stop(false);manager.reload();
        check(manager.queuedId().equals("koth"),"single-event weighted cooldown fallback queues KOTH");
        var configPath=plugin.getDataFolder().toPath().resolve("events/koth.yml");byte[] original=Files.readAllBytes(configPath);
        try {
            var cfg=YamlConfiguration.loadConfiguration(configPath.toFile());cfg.set("weight",-1);cfg.save(configPath.toFile());
            var before=manager.event("koth");boolean rejected=false;
            try{manager.reload();}catch(IllegalArgumentException expected){rejected=true;}
            check(rejected && manager.event("koth")==before,"invalid reload preserves previously validated definitions");
        }finally{Files.write(configPath,original);manager.reload();}
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mandomc:event disable koth");
        check(manager.queuedId()==null && !manager.start("koth"),"disabled KOTH is excluded from queue and starts");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"mandomc:event enable koth");
        check(manager.queue("koth"),"enabled KOTH can be queued again");
        manager.phase(HourlySchedule.Phase.START);check("koth".equals(manager.activeId()),"scheduled start uses the shared event lifecycle");
        manager.phase(HourlySchedule.Phase.STOP);check(manager.activeId()==null,"scheduled end cleans the running event");
        Files.write(configPath,original);manager.reload();
        var shortConfig=YamlConfiguration.loadConfiguration(configPath.toFile());
        shortConfig.set("settings.capture-seconds",1);shortConfig.set("settings.maximum-seconds",2);
        var timeout=new KothEvent(this,shortConfig);fixtures.add(timeout);timeout.start();
        for(int i=0;i<4;i++)timeout.sample(List.of());
        check(!timeout.running() && timeout.winner()==null,"uncaptured event times out without reward");
    }
    @SuppressWarnings("unchecked") private Map<UUID,?> pending(TeleportService service) throws Exception {
        var field=TeleportService.class.getDeclaredField("pending");field.setAccessible(true);return (Map<UUID,?>)field.get(service);
    }
    private void verifyMenusAndTravel(Actor a) throws Exception {
        var warps=plugin.warps();warps.open(a.player);
        check(a.view.getTopInventory().getSize()==54,"warp menu uses six-row galaxy layout");
        check(Arrays.stream(a.view.getTopInventory().getContents()).filter(Objects::nonNull)
                .filter(i->i.getType()!=Material.GRAY_STAINED_GLASS_PANE).count()==3,"only three current worlds appear in menu");
        for(var entry:Map.of(1,"earth",28,"batuu",39,"hoth").entrySet())
            check(a.view.getTopInventory().getItem(entry.getKey()).getItemMeta().getItemModel().toString().equals("mandomc:planets/"+entry.getValue()),
                    entry.getValue()+" occupies its galaxy slot with recovered planet icon");
        check(WarpService.canonical("batuu").equals("spawn"),"Batuu alias preserves existing spawn world route");
        var pane=a.view.getTopInventory().getItem(0);
        check(pane.getType()==Material.GRAY_STAINED_GLASS_PANE && pane.getItemMeta().isHideTooltip(),"unused warp slots use accepted hidden gray panes");
        var transfer=new InventoryClickEvent(a.view,InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);
        warps.click(transfer);check(transfer.isCancelled(),"warp menu blocks shift-transfer");
        var drag=new InventoryDragEvent(a.view,new ItemStack(Material.STONE),new ItemStack(Material.STONE),false,Map.of(0,new ItemStack(Material.STONE)));
        warps.drag(drag);check(drag.isCancelled(),"warp menu blocks inventory drag");
        check(!warps.request(a.player,"Tatooine") && !warps.request(a.player,"Ilum"),"legacy worlds outside current scope cannot be selected");
        int before=a.teleports;
        check(warps.request(a.player,"earth"),"direct warp starts shared travel request");
        check(a.teleports==before && pending(plugin.travel()).containsKey(a.id),"direct warp never bypasses warmup");
        check(!warps.request(a.player,"hoth"),"duplicate destinations share one pending request");
        plugin.travel().onQuit(new org.bukkit.event.player.PlayerQuitEvent(a.player,net.kyori.adventure.text.Component.empty()));
        plugin.combat().tag(a.player);
        try{check(!warps.request(a.player,"hoth"),"combat denies public warp before warmup");}
        finally{plugin.combat().clear(a.player);}
        warps.open(a.player);
        var click=new InventoryClickEvent(a.view,InventoryType.SlotType.CONTAINER,39,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        warps.click(click);check(click.isCancelled() && pending(plugin.travel()).containsKey(a.id),"Hoth GUI route uses same travel request as direct command");
        plugin.travel().onQuit(new org.bukkit.event.player.PlayerQuitEvent(a.player,net.kyori.adventure.text.Component.empty()));
        plugin.events().open(a.player);check(a.view.getTopInventory().getItem(15).getType()==Material.PAPER,"legacy event menu displays current/next/chances");
        var eventClick=new InventoryClickEvent(a.view,InventoryType.SlotType.CONTAINER,13,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        plugin.events().click(eventClick);check(eventClick.isCancelled() && pending(plugin.travel()).containsKey(a.id),"event menu travel also uses shared combat/warmup contract");
        plugin.travel().onQuit(new org.bukkit.event.player.PlayerQuitEvent(a.player,net.kyori.adventure.text.Component.empty()));
        var config=YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"teleport.yml"));
        var language=YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"messages.yml"));
        try(var reader=new java.io.InputStreamReader(plugin.getResource("messages.yml"),java.nio.charset.StandardCharsets.UTF_8)) {
            language.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        var isolated=new TeleportService(this,config,language,plugin.combat());
        try {
            var tick=TeleportService.class.getDeclaredMethod("tick");tick.setAccessible(true);
            check(isolated.request(a.player,()->warps.destination("earth")),"resolved Earth destination accepts normal shared warmup");
            for(int i=0;i<config.getInt("warmup-seconds")*20-1;i++)tick.invoke(isolated);
            check(a.teleports==before,"travel does not complete before full countdown");tick.invoke(isolated);
            check(a.teleports==before+1 && a.location.getWorld().getKey().equals(NamespacedKey.minecraft("overworld")),"completed warp reaches existing Earth landing");
            isolated.request(a.player,()->warps.destination("hoth"));a.location.add(.1,0,0);tick.invoke(isolated);
            check(pending(isolated).isEmpty(),"movement cancels destination warmup");
            isolated.request(a.player,()->warps.destination("hoth"));
            var source=org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
            isolated.onDamage(new org.bukkit.event.entity.EntityDamageEvent(a.player,org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL,source,1));
            check(pending(isolated).isEmpty(),"positive damage cancels warp warmup");
        }finally{isolated.close();}
        a.admin=false;Bukkit.getPluginCommand("event").execute(a.player,"event",new String[]{"start","koth"});
        check(plugin.events().activeId()==null,"non-admin cannot start events");
    }
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "KothTestPlayer");
        Object handle = playerClass.getConstructor(serverClass, levelClass, profileClass, infoClass)
                .newInstance(server, level, profile, infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object flow = flowClass.getField("SERVERBOUND").get(null);
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Object connection = connectionClass.getConstructor(flowClass).newInstance(flow);
        Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie = cookieClass.getMethod("createInitial", profileClass, boolean.class).invoke(null, profile, false);
        Class<?> listenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object packetListener = listenerClass.getConstructor(serverClass, connectionClass, playerClass, cookieClass)
                .newInstance(server, connection, handle, cookie);
        playerClass.getField("connection").set(handle, packetListener);
        playerClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }

    private Actor actor(Location location) throws Exception {var a=new Actor(nativeTestPlayer(location.getWorld(),location),location);actors.add(a);return a;}
    private final class Actor {
        final Player nativePlayer;
        final Player player;
        final UUID id;
        Location location;
        InventoryView view;
        int teleports;
        boolean admin;
        boolean god;
        GameMode mode=GameMode.SURVIVAL;
        Actor(Player nativePlayer,Location location) {
            this.nativePlayer=nativePlayer;this.id=nativePlayer.getUniqueId();this.location=location.clone();
            player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)-> {
                return switch(method.getName()) {
                    case "isOnline" -> true;
                    case "isInvulnerable" -> god;
                    case "getGameMode" -> mode;
                    case "getLocation" -> this.location.clone();
                    case "getWorld" -> this.location.getWorld();
                    case "hasPermission" -> !((String)args[0]).equals("mandomc.event.admin") || admin;
                    case "getPassengers" -> List.of();
                    case "isInsideVehicle" -> false;
                    case "openInventory" -> {view=makeView((Inventory)args[0]);yield view;}
                    case "getOpenInventory" -> view;
                    case "closeInventory" -> {view=makeView(Bukkit.createInventory(null,9));yield null;}
                    case "teleport" -> {this.location=((Location)args[0]).clone();teleports++;yield true;}
                    case "toString" -> "KothProbePlayer";
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy==args[0];
                    default -> method.invoke(nativePlayer,args);
                };
            });
            view=makeView(Bukkit.createInventory(null,9));
        }
        private InventoryView makeView(Inventory top) {
            return (InventoryView)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{InventoryView.class},(proxy,method,args)-> {
                return switch(method.getName()) {
                    case "getTopInventory" -> top;
                    case "getBottomInventory" -> nativePlayer.getInventory();
                    case "getPlayer" -> player;
                    case "getType" -> InventoryType.CHEST;
                    case "getInventory" -> (int)args[0]<top.getSize()?top:nativePlayer.getInventory();
                    case "convertSlot" -> (int)args[0];
                    default -> null;
                };
            });
        }
    }
    private void check(boolean ok,String message) {if(!ok)throw new IllegalStateException(message);checks++;getLogger().info("PASS "+message);}
    private void cleanup() {
        fixtures.forEach(KothEvent::close);fixtures.clear();
        if(plugin!=null)for(var a:actors) {
            if(plugin.travel()!=null)plugin.travel().onQuit(new org.bukkit.event.player.PlayerQuitEvent(a.player,net.kyori.adventure.text.Component.empty()));
            if(plugin.combat()!=null)plugin.combat().clear(a.player);
            a.nativePlayer.getInventory().clear();
        }
        actors.clear();
    }
    @Override public void onDisable() {cleanup();}
}
