package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.decor.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Verifies static scene state/protection, private dialogue/audio and two-process reconstruction. */
public final class SpawnDecorProbe extends JavaPlugin {
    private int checks;private DecorService decor;private MandoPlugin mando;private NativeProbePlayers actors;private DecorFeature duplicate;
    private final List<Player> speakers=new ArrayList<>();
    @Override public void onEnable(){later(140,this::begin);}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private void begin()throws Exception{
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");decor=Bukkit.getServicesManager().load(DecorService.class);
        check(decor!=null,"Exactly one decorative owner initialized");check(decor.bases().size()==9,"All nine recorded placements present");
        long count=Bukkit.getWorlds().stream().flatMap(w->w.getEntities().stream()).filter(e->e.getPersistentDataContainer().has(DecorService.MARKER,PersistentDataType.STRING)).count();
        check(count==9,"No duplicate marked bases anywhere");
        Properties previous=new Properties();Path first=getDataFolder().toPath().resolve("phase1.properties");if(Files.exists(first))try(var in=Files.newInputStream(first)){previous.load(in);}
        var snapshot=new Properties();
        boolean liveOnly=new java.io.File(getDataFolder(),"live-only").exists();
        for(var definition:decor.definitions()){
            var base=decor.bases().get(definition.id());var model=decor.model(definition.id());Location at=definition.location();
            check(base.isValid()&&!base.isDead(),definition.id()+" live base");
            check(base.getWorld()==at.getWorld()&&base.getLocation().distanceSquared(at)<.000001,definition.id()+" exact world and coordinates");
            check(base.getLocation().getYaw()==definition.yaw()&&base.getLocation().getPitch()==definition.pitch(),definition.id()+" exact captured yaw/pitch");
            check(base.isInvulnerable()&&!base.hasGravity()&&!base.isCollidable()&&!base.isPersistent()&&base.isSilent(),definition.id()+" frozen protected nonpersistent base");
            check(!base.canMove()&&!base.canTick(),definition.id()+" no native tick/motion controller");
            check(!model.modeled().shouldBeSaved()&&!model.modeled().isBaseEntityVisible()&&model.modeled().isInitialized(),definition.id()+" renderer ready without extra persistence");
            check(model.modeled().getModels().containsKey(definition.model()),definition.id()+" original requested rig attached");
            check(!model.active().canHurt()&&!model.active().isMainHitbox()&&model.active().isModelRotationLocked(),definition.id()+" no hurt flash/main hitbox or facing drift");
            check(!base.getPersistentDataContainer().has(new NamespacedKey("mandomc","vehicle_id")),definition.id()+" never a player-owned vehicle");
            check(base.getLocation().getChunk().getPluginChunkTickets().contains(mando),definition.id()+" chunk kept loaded");
            for(String animation:definition.animations())check(model.active().getAnimationHandler().getAnimation(animation)!=null,definition.id()+" required pose/idle present");
            model.active().getMountManager().ifPresent(manager->check(!manager.canDrive()&&!manager.canRide(),definition.id()+" model mounts disabled"));
            if(!liveOnly){
            var damage=new EntityDamageEvent(base,EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.EXPLOSION).build(),10);
            decor.damage(damage);check(damage.isCancelled(),definition.id()+" explosion damage blocked by owning feature");
            check(!base.teleport(at.clone().add(4,0,0)),definition.id()+" outside teleport refused");
            }
            snapshot.setProperty(definition.id(),base.getUniqueId().toString());
            if(!previous.isEmpty())check(!base.getUniqueId().toString().equals(previous.getProperty(definition.id())),definition.id()+" recreated once from configuration after restart");
        }
        if(liveOnly){
            Files.writeString(getDataFolder().toPath().resolve("LIVE-PASSED.txt"),"SPAWN DECOR LIVE PASSED: "+checks+" read-only native assertions\n");
            getLogger().info("SPAWN DECOR LIVE PASSED: "+checks+" read-only native assertions");return;
        }
        actors=new NativeProbePlayers("DecorProbe");
        for(var definition:decor.definitions())if(definition.npc()){
            var player=actors.create(definition.location().add(1,0,1));speakers.add(player);actors.drain(player);actors.otherPackets(player);
            var base=decor.bases().get(definition.id());var event=new PlayerInteractEntityEvent(player,base,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(event);
            check(event.isCancelled(),definition.id()+" interaction cannot manipulate native stand");
            var dialogue=actors.drain(player);check(dialogue.size()==1&&net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(dialogue.getFirst()).contains(definition.name()),definition.id()+" private character dialogue delivered");
            var other=actors.otherPackets(player);check(other!=null&&other.stream().anyMatch(name->name.contains("SoundPacket")),definition.id()+" original voice cue delivered nearby");
            Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,base,EquipmentSlot.HAND));check(actors.drain(player).isEmpty(),definition.id()+" repeated click dialogue throttled");
            check(!base.addPassenger(player)&&base.getPassengers().isEmpty(),definition.id()+" native riding prevented");
            var equipment=new PlayerArmorStandManipulateEvent(player,base,new ItemStack(Material.DIAMOND_HELMET),new ItemStack(Material.AIR),EquipmentSlot.HEAD,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(equipment);check(equipment.isCancelled(),definition.id()+" equipment theft blocked");
        }
        duplicate=new DecorFeature(mando);
        later(3,()->{
            check(decor==Bukkit.getServicesManager().load(DecorService.class)&&decor.bases().size()==9,"Duplicate lifecycle startup leaves the original single owner untouched");duplicate.close();duplicate=null;
            Files.createDirectories(getDataFolder().toPath());if(previous.isEmpty())try(var out=Files.newOutputStream(first)){snapshot.store(out,"Stable IDs; runtime UUIDs must be recreated without duplicates");}
            String phase=previous.isEmpty()?"PHASE1":"PHASE2";Files.writeString(getDataFolder().toPath().resolve(phase+"-PASSED.txt"),"SPAWN DECOR "+phase+" PASSED: "+checks+" native assertions\n");getLogger().info("SPAWN DECOR "+phase+" PASSED: "+checks+" native assertions");cleanup();
            if(new java.io.File(getDataFolder(),"standalone").exists())Bukkit.shutdown();else waitForChat(phase,0);
        });
    }
    private void waitForChat(String phase,int retries){
        var result=getDataFolder().toPath().getParent().resolve("ChatPolishProbe/PASSED.txt");
        if(Files.exists(result)||Files.exists(result.resolveSibling("FAILED.txt"))||retries>60){Bukkit.shutdown();return;}
        later(20,()->waitForChat(phase,retries+1));
    }
    private interface Check{void run()throws Exception;}
    private void later(long ticks,Check action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"SPAWN DECOR FAILED",error);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());}catch(Exception ignored){}cleanup();Bukkit.shutdown();}},ticks);}
    private void cleanup(){if(duplicate!=null){duplicate.close();duplicate=null;}if(actors!=null){actors.close();actors=null;}}
    @Override public void onDisable(){cleanup();}
}
