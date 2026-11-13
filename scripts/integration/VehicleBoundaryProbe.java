package net.mandomc.verification;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.vehicles.*;
import net.mandomc.gameplay.territory.*;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.mount.controller.MountController;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Runs only in a disposable runtime: real mount controller and swept native bolts across the atlas. */
public final class VehicleBoundaryProbe extends JavaPlugin {
    private MandoPlugin plugin;
    private Player pilot;
    private LivingEntity vehicle, victim;
    private UUID id;
    private Map<UUID,Object> online;
    private VehicleWeaponSettings old;
    private Map<String,VehicleWeaponSettings> weapons;
    private TerritoryService territory;
    private int edge, z, checks;
    private World world;
    private me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile held;
    private boolean fixed = Boolean.getBoolean("vehicle.fixed");
    @Override public void onEnable() { later(100, this::begin); }
    @SuppressWarnings("unchecked") private void begin() throws Exception {
        plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        territory=Bukkit.getServicesManager().load(TerritoryService.class);
        world=Bukkit.getWorld(NamespacedKey.minecraft("mandalore_review"));
        if(world==null)throw new IllegalStateException("Mandalore missing");
        boolean found=false;
        for(int zz=-240;zz<240&&!found;zz+=16) for(int x=-240;x<240;x+=16) {
            if(territory.zone(new Location(world,x,281,zz+8))==TerritoryPolicy.Zone.WARZONE
                    &&territory.zone(new Location(world,x-1,281,zz+8))==TerritoryPolicy.Zone.WILDERNESS) {
                edge=x;z=zz+8;found=true;break;
            }
        }
        check(found,"real wilderness/warzone atlas boundary located");
        for(int x=edge-65;x<=edge+35;x++)for(int zz=z-15;zz<=z+15;zz++) {
            world.getChunkAt(x>>4,zz>>4).load();world.getBlockAt(x,280,zz).setType(Material.STONE,false);
            for(int y=281;y<292;y++)world.getBlockAt(x,y,zz).setType(Material.AIR,false);
        }
        Location start=new Location(world,edge-40,281,z+.5,-90,0);
        pilot=nativeTestPlayer(world,start);
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object list=server.getClass().getMethod("getPlayerList").invoke(server);
        var lookup=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");
        lookup.setAccessible(true);online=(Map<UUID,Object>)lookup.get(list);
        online.put(pilot.getUniqueId(),pilot.getClass().getMethod("getHandle").invoke(pilot));
        var item=plugin.items().create("xwing");pilot.getInventory().setItemInMainHand(item);
        id=plugin.vehicles().deploy(pilot,item,start);check(id!=null,"real aircraft deployed in wilderness");
        vehicle=plugin.vehicles().entity(id);
        later(20,()->{
            var stranger=nativeTestPlayer(world,start.clone().add(2,0,0));
            check(!plugin.vehicles().mount(stranger,id,true),"non-owner still cannot take pilot seat");
            check(plugin.vehicles().mount(pilot,id,true),"owner pilot mounts aircraft");
            var field=plugin.vehicleCannons().getClass().getDeclaredField("weapons");field.setAccessible(true);
            weapons=(Map<String,VehicleWeaponSettings>)field.get(plugin.vehicleCannons());old=weapons.get("xwing");
            weapons.put("xwing",new VehicleWeaponSettings(8,2,0,"proton_torpedo",1,List.of("weapon_one"),80,1,old.sound(),true));
            var ammo=plugin.items().create("proton_torpedo");ammo.setAmount(64);pilot.getInventory().clear();pilot.getInventory().setItem(0,ammo);
            later(10,this::control);
        });
    }
    private Location muzzle() {return plugin.vehicles().firingPlatform(pilot,List.of("weapon_one")).muzzles().getFirst();}
    private LivingEntity target(Location location) {return world.spawn(location,Cow.class,c->{c.setAI(false);c.setGravity(false);c.setSilent(true);});}
    private void control() throws Exception {
        var platform=plugin.vehicles().firingPlatform(pilot,List.of("weapon_one"));
        victim=target(muzzle().clone().add(platform.direction().clone().multiply(8)).subtract(0,.5,0));
        check(territory.edits(victim.getLocation()),"wilderness target remains outside warzone");
        check(plugin.vehicleCannons().fire(pilot),"valid wilderness cannon volley emitted");
        later(16,()->{
            check(victim.getHealth()<victim.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),"wilderness vehicle damage remains effective");victim.remove();
            crossing();
        });
    }
    private void crossing() throws Exception {
        vehicle.setRotation(-90,0);pilot.setRotation(-90,0);
        var origin=muzzle();var direction=plugin.vehicles().firingPlatform(pilot,List.of("weapon_one")).direction();
        getLogger().info("Boundary="+edge+" z="+z+" origin="+origin+" direction="+direction);
        double distance=(edge+5-origin.getX())/direction.getX();
        victim=target(origin.clone().add(direction.clone().multiply(distance)).subtract(0,.5,0));
        check(territory.zone(victim.getLocation())==TerritoryPolicy.Zone.WARZONE,"pre-fired crossing target is in restricted warzone");
        if (fixed) {
            var settings=new me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.ProjectileSettings();
            held=new me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile(
                    settings,pilot,pilot.getEyeLocation(),new org.bukkit.util.Vector(1,0,0),
                    new org.bukkit.inventory.ItemStack(Material.BLAZE_ROD),"E11",EquipmentSlot.HAND,null,null,null);
            Bukkit.getPluginManager().callEvent(new me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent(held));
        }
        check(plugin.vehicleCannons().fire(pilot),"pre-fired crossing projectile starts in valid wilderness");
        later(65,()->{
            check(fixed?victim.getHealth()==10:victim.getHealth()<10,
                    fixed?"crossing projectile denied at boundary/impact":"REPRODUCED pre-fired projectile damages warzone target");
            victim.remove();drive(0);
        });
    }
    private void drive(int tick) throws Exception {
        var controller=ModelEngineAPI.getMountPairManager().getController(pilot.getUniqueId());
        controller.setInput(new MountController.MountInput(0,1,false,false));
        var modeled=ModelEngineAPI.getModeledEntity(vehicle);var active=modeled.getModels().values().iterator().next();
        controller.updateDriverMovement(modeled.getBase().getMoveController(),active);
        if(territory.zone(vehicle.getLocation())!=TerritoryPolicy.Zone.WARZONE || vehicle.getLocation().getX()<edge+14) {
            if(tick>240)throw new IllegalStateException("Aircraft did not cross boundary: "+vehicle.getLocation());
            later(1,()->drive(tick+1));return;
        }
        controller.setInput(new MountController.MountInput(0,0,false,false));
        check(vehicle.getLocation().getX()>edge,"actual native movement drove aircraft into warzone");
        int ammo=pilot.getInventory().getItem(0).getAmount();
        boolean emitted=plugin.vehicleCannons().fire(pilot);
        check(fixed?!emitted:emitted,fixed?"moved aircraft cannot fire in warzone":"REPRODUCED wilderness-deployed aircraft fires after driving into warzone");
        if(fixed)check(pilot.getInventory().getItem(0).getAmount()==ammo,"denied fire consumes no ammunition");
        if (fixed) extras();
        getLogger().info("VEHICLE BOUNDARY "+(fixed?"FIX PASSED":"BASELINE REPRODUCED")+": "+checks+" assertions");
        cleanup();Bukkit.shutdown();
    }
    private void extras() throws Exception {
        // This event has the exact pinned vendor projectile and event types, with launch attribution.
        var projectile=held;
        var model=ModelEngineAPI.getModeledEntity(vehicle);
        model.getModels().values().forEach(a->a.getMountManager().ifPresent(m->{m.dismountDriver();m.dismountAll();}));
        check(plugin.vehicles().mountedType(pilot)==null,"pilot dismount really clears all seat roles");
        Object handle=pilot.getClass().getMethod("getHandle").invoke(pilot);
        handle.getClass().getMethod("setPos",double.class,double.class,double.class).invoke(handle,edge-40.0,281.0,z+.5);
        victim=target(new Location(world,edge-35,281,z+.5));
        var source=new me.deecaad.weaponmechanics.weapon.damage.ProjectileDamageSource(projectile,
                me.deecaad.weaponmechanics.weapon.damage.DamagePoint.BODY);
        var constructor=me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent.class.getConstructors()[0];
        var parameters=constructor.getParameterTypes();Object[] args=new Object[parameters.length];
        args[0]=source;args[1]=EquipmentSlot.HAND;args[2]=victim;
        for(int i=3;i<args.length;i++)if(parameters[i]==double.class)args[i]=4.0;else if(parameters[i]==int.class)args[i]=0;
        var event=(me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent)constructor.newInstance(args);
        Bukkit.getPluginManager().callEvent(event);
        check(event.isCancelled(),"vendor impact retains vehicle source after pilot dismounts into wilderness");
        var entities=new it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<LivingEntity>();entities.put(victim,1.0);
        var blast=new me.deecaad.weaponmechanics.weapon.weaponevents.ProjectileExplodeEvent(projectile,new ArrayList<>(),null,entities,null);
        Bukkit.getPluginManager().callEvent(blast);
        check(blast.isCancelled(),"vendor explosion from restricted launch platform is cancelled before damage");
        victim.remove();
        victim=target(new Location(world,edge+5,281,z+.5));
        victim.damage(2,vehicle);check(victim.getHealth()==10,"native vehicle entity/collision damage denied in warzone");
        // Ordinary dismounted PvP retains the warzone combat policy.
        victim.damage(2,pilot);check(victim.getHealth()<10,"ordinary player combat can still damage a warzone target");victim.remove();
        // A destruction blast near the boundary must not splash damage across it.
        victim=target(new Location(world,edge+.5,281,z+.5));
        plugin.explosions().getClass().getMethod("explodeVehicle",Location.class,Entity.class)
                .invoke(plugin.explosions(),new Location(world,edge-.5,281,z+.5),pilot);
        check(victim.getHealth()==10,"vehicle destruction explosion protects the warzone splash target");victim.remove();
        victim=target(new Location(world,edge-35,281,z+.5));
        var safe=new Location(world,-32,281,-216);
        check(territory.zone(safe)==TerritoryPolicy.Zone.SAFEZONE,"safezone protection control located");
        victim.teleport(safe);victim.damage(2,pilot);
        check(victim.getHealth()==10,"existing safezone target protection remains enforced");victim.remove();
    }
    private interface Checked {void run() throws Exception;}
    private void later(long ticks,Checked step) {Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"VEHICLE BOUNDARY FAILED",error);cleanup();Bukkit.shutdown();}},ticks);}
    private void check(boolean ok,String why){if(!ok)throw new IllegalStateException(why);checks++;getLogger().info("PASS "+why);}
    private void cleanup(){
        Bukkit.getScheduler().cancelTasks(this);if(victim!=null)victim.remove();
        if(old!=null)weapons.put("xwing",old);
        if(pilot!=null){var pair=ModelEngineAPI.getMountPairManager().getController(pilot.getUniqueId());
            if(vehicle!=null){var model=ModelEngineAPI.getModeledEntity(vehicle);if(model!=null)model.getModels().values().forEach(a->a.getMountManager().ifPresent(m->{m.dismountDriver();m.dismountAll();}));}
            if(online!=null)online.remove(pilot.getUniqueId());}
    }
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry
        // or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server =
                Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile =
                profileClass
                        .getConstructor(UUID.class, String.class)
                        .newInstance(UUID.randomUUID(), "VehicleTest");
        Object handle =
                playerClass
                        .getConstructor(serverClass, levelClass, profileClass, infoClass)
                        .newInstance(
                                server,
                                level,
                                profile,
                                infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object flow = flowClass.getField("SERVERBOUND").get(null);
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Object connection = connectionClass.getConstructor(flowClass).newInstance(flow);
        Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie =
                cookieClass
                        .getMethod("createInitial", profileClass, boolean.class)
                        .invoke(null, profile, false);
        Class<?> listenerClass =
                Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object packetListener =
                listenerClass
                        .getConstructor(serverClass, connectionClass, playerClass, cookieClass)
                        .newInstance(server, connection, handle, cookie);
        playerClass.getField("connection").set(handle, packetListener);
        // Isolated players have no network client to send its loaded acknowledgement.
        var loadTimer = listenerClass.getDeclaredField("clientLoadedTimeoutTimer");
        loadTimer.setAccessible(true);
        loadTimer.setInt(packetListener, 0);
        var waiting = listenerClass.getDeclaredField("waitingForRespawn");
        waiting.setAccessible(true);
        waiting.setBoolean(packetListener, false);
        playerClass
                .getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
}
