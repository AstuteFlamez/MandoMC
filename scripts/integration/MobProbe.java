package net.mandomc.verification;

import java.util.*;
import java.lang.reflect.Proxy;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.mobs.*;
import net.mandomc.integrations.mobs.MythicMobAccess;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.attribute.Attribute;
import net.kyori.adventure.text.Component;
import io.lumine.mythic.bukkit.MythicBukkit;
import com.ticxo.modelengine.api.ModelEngineAPI;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent;
import me.deecaad.core.compatibility.HitBox;

/** Native, unregistered test players; temporary high-altitude entities and restored biome cells. */
public final class MobProbe extends JavaPlugin {
    private MandoPlugin plugin;
    private MythicMobAccess mobs;
    private World world;
    private Location point;
    private int checks;
    private final List<LivingEntity> entities = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final Map<Block, Biome> biomes = new HashMap<>();
    private final List<BlockState> blocks = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    private final Listener observer = new Listener() {};
    private final List<me.deecaad.weaponmechanics.weapon.projectile.AProjectile> projectiles = new ArrayList<>();
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {try {verify();} catch(Throwable e) {fail(e);}}, 120);
    }
    private void verify() throws Exception {
        plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(plugin.mobs() != null, "sandy population service initialized");
        mobs = new MythicMobAccess();
        world = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        point = new Location(world, 600.5, 301, 600.5);
        for (int x=36;x<=38;x++) for(int z=36;z<=38;z++) {var chunk=world.getChunkAt(x,z);chunk.addPluginChunkTicket(this);chunks.add(chunk);}
        for (int x=588;x<=612;x++) for (int z=588;z<=612;z++) {
            var floor=world.getBlockAt(x,300,z);
            if (!floor.getType().isAir()) throw new IllegalStateException("Verification site is not empty sky");
            blocks.add(floor.getState());floor.setType(Material.SANDSTONE,false);
        }
        for (int x=588;x<=612;x+=4) for (int z=588;z<=612;z+=4) {
            var block=world.getBlockAt(x,300,z);biomes.put(block,block.getBiome());block.setBiome(Biome.DESERT);
        }
        var spawns = plugin.mobs();
        check(spawns.suppress(CreatureSpawnEvent.SpawnReason.NATURAL, point), "desert natural vanilla spawning denied");
        check(spawns.suppress(CreatureSpawnEvent.SpawnReason.PATROL, point), "vanilla patrols denied in selected biome");
        check(!spawns.suppress(CreatureSpawnEvent.SpawnReason.CUSTOM, point), "custom mobs remain eligible");
        check(!spawns.suppress(CreatureSpawnEvent.SpawnReason.BREEDING, point), "player breeding preserved");
        check(!spawns.suppress(CreatureSpawnEvent.SpawnReason.SPAWNER, point), "spawner gameplay preserved");
        var cell=point.getBlock();
        for (var biome:List.of(Biome.BADLANDS,Biome.ERODED_BADLANDS)) {cell.setBiome(biome);check(spawns.suppress(CreatureSpawnEvent.SpawnReason.NATURAL,point),"selected biome suppresses "+biome);}
        cell.setBiome(Biome.BEACH);check(!spawns.suppress(CreatureSpawnEvent.SpawnReason.NATURAL,point), "beaches preserve vanilla spawning");cell.setBiome(Biome.DESERT);
        var surface=SandySpawns.class.getDeclaredMethod("surface",World.class,int.class,int.class);surface.setAccessible(true);
        check(surface.invoke(spawns,world,600,600)!=null,"safe sandy surface accepted");
        var sample=SandySpawns.class.getDeclaredMethod("sites",Location.class,int.class,List.class);sample.setAccessible(true);
        @SuppressWarnings("unchecked") var sites=(List<Location>)sample.invoke(spawns,point,6,List.of());
        check(sites.size()==6,"six safe spaced encounter sites found before spawning");
        var members=List.of(new EncounterPlan.Member("TuskenRaiderSniper",1),new EncounterPlan.Member("TuskenRaiderMelee",2),new EncounterPlan.Member("Bantha",3),new EncounterPlan.Member("Dewback",4),new EncounterPlan.Member("Massiff",5),new EncounterPlan.Member("TuskenRaiderSniper",5));
        entities.addAll(spawns.spawn(new EncounterPlan(members,3),sites));
        for(int i=0;i<entities.size();i++) {
            var e=entities.get(i);e.setAI(false);e.setGravity(false);e.setRemoveWhenFarAway(false);
            MythicBukkit.inst().getAPIHelper().getMythicMobInstance(e).setDespawnMode(io.lumine.mythic.core.mobs.DespawnMode.NEVER);
            check(mobs.type(e).equals(members.get(i).type()),"vendor identity "+members.get(i).type());
            check(mobs.level(e)==members.get(i).level(),"vendor level survives spawn "+members.get(i).level());
            check(e.getPersistentDataContainer().has(new NamespacedKey(plugin,"sandy_encounter_v1")),"natural ownership persists");
        }
        var interaction = new PlayerInteractEntityEvent(player(point),entities.get(2),EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(interaction);
        check(interaction.isCancelled(), "bantha rejects vanilla feeding and saddling");
        var mount = new EntityMountEvent(players.getLast(),entities.get(2));
        Bukkit.getPluginManager().callEvent(mount);
        check(mount.isCancelled(), "bantha cannot be mounted through native pig behavior");
        double low=entities.get(0).getAttribute(Attribute.MAX_HEALTH).getValue(),high=entities.get(5).getAttribute(Attribute.MAX_HEALTH).getValue();
        check(high>low,"higher-level sniper has more health");
        check(entities.get(0).getAttribute(Attribute.MOVEMENT_SPEED).getValue()==entities.get(5).getAttribute(Attribute.MOVEMENT_SPEED).getValue(),"levels do not change movement speed");
        check("CyclerRifle".equals(WeaponMechanicsAPI.getWeaponTitle(plugin.items().create("cycler_rifle"))),"catalog generates actual cycler weapon");
        check(plugin.items().create("cycler_rifle").getItemMeta().getItemModel().toString().equals("mandomc:blasters/cycler"),"cycler item uses shipped model");
        verifyDamage(entities.get(0),6);
        verifyDamage(entities.get(5),8);
        verifyGamemode();
        Bukkit.getScheduler().runTaskLater(this, () -> {try {verifyModelsAndSkill();} catch(Throwable e) {fail(e);}}, 5);
    }
    private void verifyDamage(LivingEntity sniper, double expected) throws Exception {
        Location origin=point.clone().add(0,0,30);sniper.teleport(origin);sniper.setGravity(false);
        Player victim=player(origin.clone().add(0,0,5));
        var definition=WeaponMechanics.getInstance().getWeaponConfigurations().getObject("CyclerRifle.Projectile",Projectile.class);
        check(definition!=null,"cycler projectile definition resolves");
        var weapon=plugin.items().create("cycler_rifle");
        var start=origin.clone().add(0,.5,0);var direction=victim.getLocation().add(0,.5,0).toVector().subtract(start.toVector()).normalize();
        double[] observedBase = {Double.NaN};
        Bukkit.getPluginManager().registerEvent(WeaponDamageEntityEvent.class,observer,EventPriority.MONITOR,(listener,event)-> {
            var damage=(WeaponDamageEntityEvent)event;
            if(damage.getShooter().equals(sniper) && damage.getVictim().equals(victim)) observedBase[0]=damage.getBaseDamage();
        },this,true);
        var bolt=definition.create(sniper,start,direction,weapon,"CyclerRifle",EquipmentSlot.HAND);
        try {
            var box=HitBox.getHitbox(victim.getBoundingBox());box.setLivingEntity(victim);var hit=box.rayTrace(start.toVector(),direction);
            check(hit!=null,"cycler ray intersects native player");
            double before=victim.getHealth();WeaponMechanics.getInstance().getWeaponHandler().getHitHandler().handleHit(hit,bolt);
            check(Math.abs(observedBase[0]-expected)<0.001,"NPC cycler base damage scaled to "+expected);
            check(victim.getHealth()<before && victim.getHealth()>0,"real cycler hit retains vendor location/damage modifiers");
            check(plugin.combat().tagged(victim),"Tusken projectile tags player for combat");plugin.combat().clear(victim);
        } finally {bolt.remove();}
        Player safe=player(world.getSpawnLocation());double health=safe.getHealth();safe.damage(6,sniper);check(safe.getHealth()==health,"safe zone denies Tusken damage");check(!plugin.combat().tagged(safe),"denied hit creates no combat tag");
    }
    private void verifyGamemode() throws Exception {
        for(GameMode mode:List.of(GameMode.CREATIVE,GameMode.SPECTATOR)) {
            Player actor=player(point);actor.setGameMode(mode);
            Player joined=(Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)-> {
                if(method.getName().equals("hasPlayedBefore")||method.getName().equals("isOnline"))return true;
                return method.invoke(actor,args);
            });
            for(var listener:PlayerJoinEvent.getHandlerList().getRegisteredListeners()) if(listener.getPlugin().getName().equals("Multiverse-Core")) listener.callEvent(new PlayerJoinEvent(joined,Component.empty()));
            for(var listener:PlayerChangedWorldEvent.getHandlerList().getRegisteredListeners()) if(listener.getPlugin().getName().equals("Multiverse-Core")) listener.callEvent(new PlayerChangedWorldEvent(joined,Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"))));
            Bukkit.getScheduler().runTaskLater(this,()-> {try {check(actor.getGameMode()==mode,"Multiverse login/world-change preserve "+mode);} catch(Throwable e) {fail(e);}},4);
        }
    }
    private void verifyModelsAndSkill() throws Exception {
        for(var entity:entities) {
            var model=ModelEngineAPI.getModeledEntity(entity);
            check(model!=null && !model.getModels().isEmpty(),"attached model for "+mobs.type(entity));
            check(!model.isBaseEntityVisible(),"vanilla base hidden for "+mobs.type(entity));
        }
        var sniper=entities.get(0);
        var target=world.spawn(sniper.getLocation().add(0,0,8),Cow.class,c->{c.setAI(false);c.setGravity(false);c.setRemoveWhenFarAway(false);});
        entities.add(target);
        Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class,observer,EventPriority.MONITOR,(listener,event)-> {
            var shot=(WeaponShootEvent)event;
            if(shot.getShooter().equals(sniper)) {
                check(shot.getWeaponTitle().equals("CyclerRifle"),"Mythic attack skill fires the CyclerRifle");
                projectiles.add(shot.getProjectile());
            }
        },this,true);
        ((Mob)sniper).setTarget(target);
        MythicBukkit.inst().getAPIHelper().addThreat(sniper,target,100);
        MythicBukkit.inst().getAPIHelper().getMythicMobInstance(sniper).setTarget(io.lumine.mythic.bukkit.BukkitAdapter.adapt(target));
        var api=MythicBukkit.inst().getAPIHelper();
        check(api.castSkill(sniper,"MandoTuskenCycler",sniper.getLocation(),List.of(target),List.of(),1),"Mythic ranged skill executes");
        Bukkit.getScheduler().runTaskLater(this,()-> {
            try {
                check(!projectiles.isEmpty(),"ranged skill emits a real WeaponMechanics projectile");
                var bolt = (me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile)projectiles.getFirst();
                check(bolt.getShooter().equals(sniper), "real NPC shot preserves Tusken ownership");
                check(bolt.getWeaponStack() == null, "probe covers NPC shot without an inventory stack");
                var reflected = (me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile)plugin.saberProjectiles().createReflection(bolt,player(point));
                check(reflected.getWeaponStack()!=null, "NPC shot can enter saber reflection without null-stack failure");
                reflected.remove();
                for(var entity:entities) check(!entity.isDead(),"verification mobs remain alive before cleanup");
                getLogger().info("MOB PROBE PASSED: "+checks+" assertions");cleanup();
            } catch(Throwable e) {fail(e);}
        },25);
    }
    private Player player(Location location) throws Exception {var p=nativeTestPlayer(location.getWorld(),location);players.add(p);return p;}
    private void check(boolean ok,String message) {if(!ok)throw new IllegalStateException(message);checks++;if(!message.equals("test surface occupies empty sky"))getLogger().info("PASS "+message);}
    private void fail(Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"MOB PROBE FAILED",error);Bukkit.getScheduler().cancelTasks(this);cleanup();}
    private void cleanup() {
        HandlerList.unregisterAll(observer);projectiles.forEach(p->p.remove());projectiles.clear();
        if(mobs!=null)for(var e:entities)if(e.isValid())mobs.remove(e);entities.clear();
        if(plugin!=null && plugin.combat()!=null)players.forEach(plugin.combat()::clear);
        for(var state:blocks)state.update(true,false);blocks.clear();
        biomes.forEach(Block::setBiome);biomes.clear();
        chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();
    }
    @Override public void onDisable() {cleanup();}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "MobTest");
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
        // Isolated players have no network client to send its loaded acknowledgement.
        var loadTimer = listenerClass.getDeclaredField("clientLoadedTimeoutTimer");
        loadTimer.setAccessible(true);loadTimer.setInt(packetListener, 0);
        var waiting = listenerClass.getDeclaredField("waitingForRespawn");
        waiting.setAccessible(true);waiting.setBoolean(packetListener, false);
        playerClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }

}
