package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes;
import com.ticxo.modelengine.api.model.render.DisplayRenderer;
import net.mandomc.gameplay.jawa.JawaLook;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.joml.Quaternionf;

/** Samples the actual display renderer transforms with the idle animations running. */
public final class GrayJawaLookProbe extends JavaPlugin {
    private record Datum(String id,Location location,float yaw,float pitch) { }
    private record View(ModeledEntity modeled,ActiveModel active) { }
    private final Map<String,UUID> hosts=new HashMap<>();
    private final Map<String,ActiveModel> before=new HashMap<>();
    private final Map<String,Quaternionf> tips=new HashMap<>();
    private LivingEntity host(String id){return (LivingEntity)Bukkit.getEntity(hosts.get(id));}
    private View model(Datum d){var m=ModelEngineAPI.getModeledEntity(host(d.id()));return new View(m,m.getModel("jawa_black").orElseThrow());}
    private Location eye(Datum d){return host(d.id()).getLocation().add(0,
            model(d).active().getBone("head").orElseThrow().getBlueprintBone().getGlobalPosition().y*model(d).active().getScale().y(),0);}
    private void appearance(Datum d){var h=host(d.id());var m=model(d);var f=m.active().getBone("face").orElseThrow();
        check(!m.modeled().isBaseEntityVisible(),d.id()+" pig hidden");
        check(f.getDefaultTint().asRGB()==0xff0000&&f.getBlockLight()==15&&f.getSkyLight()==15,d.id()+" red fullbright eyes");
        check(m.active().getBone("body").orElseThrow().getDefaultTint().asRGB()==0xffffff,d.id()+" gray robe tint preserved");
        check(m.modeled().getModels().size()==1&&h.getUniqueId().equals(hosts.get(d.id())),d.id()+" same host and single model");
        check(h.getLocation().distanceSquared(d.location())<.000001,d.id()+" exact anchor");
    }
    private NativeProbePlayers actors; private int checks;
    private List<Datum> npcs; private final List<Player> players=new ArrayList<>();
    private final Map<String,Quaternionf> left=new HashMap<>(), torso=new HashMap<>(), visor=new HashMap<>();
    private final List<String> evidence=new ArrayList<>();
    public void onEnable(){if(Bukkit.getPort()!=25588)throw new IllegalStateException("Disposable runtime only");later(160,this::begin);}
    private interface Step{void run()throws Exception;}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private void later(long ticks,Step step){Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"HEAD FAILED",e);try{save("FAILED",e.toString());}catch(Exception writeError){e.addSuppressed(writeError);}Bukkit.shutdown();}},ticks);}
    private void save(String name,String result)throws Exception{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name+".txt"),result+"\n"+String.join("\n",evidence));}
    private Quaternionf rotation(Datum d,String bone){
        var model=model(d);
        // Embedded actors do not acknowledge client chunks: register and exempt the actual test viewer.
        var data=model.modeled().getBase().getData();var viewer=players.get(npcs.indexOf(d)).getUniqueId();
        data.setCullExempt(viewer,true);
        data.getMutableTracking().put(viewer,com.ticxo.modelengine.api.entity.CullType.NO_CULL);
        data.cullUpdate();
        check(data.hasTracking(),d.id()+" renderer is actively ticking rather than returning stale transforms");
        model.active().tick();
        var display=((DisplayRenderer)model.active().getModelRenderer()).getRendered().get(bone);
        check(display!=null,d.id()+" visible display bone "+bone);
        return new Quaternionf(display.getLeftRotation().get()).mul(display.getRightRotation().get()).normalize();
    }
    private void pointsAt(Datum d,Quaternionf rotation,float yaw,float pitch){
        var forward=rotation.transform(new org.joml.Vector3f(0,0,1));
        var expected=new Location(d.location().getWorld(),0,0,0,d.yaw()+yaw,pitch).getDirection();
        double dot=forward.x*expected.getX()+forward.y*expected.getY()+forward.z*expected.getZ();
        if(dot<=Math.cos(Math.toRadians(10)))evidence.add("Facing failure "+d.id()+" expected="+yaw+"/"+pitch+" forward="+forward+" ybh="+model(d).active().getYBodyRot()+"/"+model(d).active().getYHeadRot()+" pitch="+model(d).active().getXHeadRot()+" players="+d.location().getWorld().getPlayers().stream().map(p->p.getName()+":"+p.getLocation()+"/"+p.getGameMode()+" dead="+p.isDead()+" online="+p.isOnline()+" vanished="+p.getMetadata("vanished")).toList());
        check(dot>Math.cos(Math.toRadians(10)),d.id()+" displayed face points toward player, with correct yaw/pitch signs");
    }
    private double angle(Quaternionf a,Quaternionf b){return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(a.dot(b)))));}
    private void target(float yaw,float pitch){for(int i=0;i<npcs.size();i++){
        var d=npcs.get(i);var base=host(d.id());var eye=eye(d);
        var offset=new Location(eye.getWorld(),0,0,0,d.yaw()+yaw,pitch).getDirection().multiply(1);
        var destination=eye.add(offset).subtract(0,players.get(i).getEyeHeight(),0);players.get(i).teleport(destination);
    }}
    private void begin()throws Exception{
        var plugin=Bukkit.getPluginManager().getPlugin("MandoMC");
        var config=YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"mobs/jawas.yml"));
        var definitions=new ArrayList<Datum>();var key=new NamespacedKey(plugin,"jawa_npc_v1");
        for(String id:config.getConfigurationSection("npcs").getKeys(false)){
            String p="npcs."+id;var home=new Location(Bukkit.getWorld(NamespacedKey.fromString(config.getString(p+".world"))),config.getDouble(p+".x"),config.getDouble(p+".y"),config.getDouble(p+".z"),(float)config.getDouble(p+".yaw"),(float)config.getDouble(p+".pitch"));
            var matches=home.getWorld().getNearbyEntities(home,2,2,2).stream().filter(e->id.equals(e.getPersistentDataContainer().get(key,PersistentDataType.STRING))).toList();
            check(matches.size()==1,id+" exactly one anchored buyer");hosts.put(id,matches.getFirst().getUniqueId());definitions.add(new Datum(id,home,home.getYaw(),home.getPitch()));
        }
        npcs=List.copyOf(definitions);check(npcs.size()==7,"seven gray buyers");
        actors=new NativeProbePlayers("HeadProbe");for(var d:npcs)players.add(actors.create(d.location()));
        for(var player:players)player.setInvulnerable(true);
        for(var d:npcs){appearance(d);var head=model(d).active().getBone("head").orElseThrow();
            check(!head.getImmutableBoneBehaviors().get(BoneBehaviorTypes.HEAD).isHidden(),d.id()+" real head driver replaces hidden fallback");
            check(head.getBoneBehavior(BoneBehaviorTypes.HEAD).orElseThrow().isLocal(),d.id()+" hierarchy includes child parts");
        }
        target(30,0);later(40,()->{capture();target(-30,0);later(40,this::yaw);});
    }
    private void capture(){for(var d:npcs){left.put(d.id(),rotation(d,"head"));torso.put(d.id(),rotation(d,"body"));
        visor.put(d.id(),new Quaternionf(left.get(d.id())).invert().mul(rotation(d,"face")));
        tips.put(d.id(),new Quaternionf(left.get(d.id())).invert().mul(rotation(d,"tip")));
    }}
    private void yaw()throws Exception{
        for(var d:npcs){var head=rotation(d,"head");double headAngle=angle(left.get(d.id()),head),bodyAngle=angle(torso.get(d.id()),rotation(d,"body"));
            evidence.add(d.id()+" yaw headDisplayDelta="+headAngle+" torsoDisplayDelta="+bodyAngle+" before="+left.get(d.id())+" after="+head);
            pointsAt(d,head,-30,0);
            check(headAngle>45&&headAngle<75,d.id()+" rendered head independently traverses sixty degrees");
            check(bodyAngle<8,d.id()+" torso remains still inside neck cone");
            check(Math.abs(JawaLook.wrap(model(d).modeled().getYBodyRot()-d.yaw()))<.1,d.id()+" body stays at anchor yaw");
            check(angle(visor.get(d.id()),new Quaternionf(head).invert().mul(rotation(d,"face")))<8,d.id()+" red-eye face follows head");
            check(angle(tips.get(d.id()),new Quaternionf(head).invert().mul(rotation(d,"tip")))<8,d.id()+" hood tip follows head");
        }
        target(0,-20);later(40,()->{capture();target(0,20);later(40,this::pitch);});
    }
    private void pitch()throws Exception{
        for(var d:npcs){var head=rotation(d,"head");pointsAt(d,head,0,20);double delta=angle(left.get(d.id()),head);evidence.add(d.id()+" pitch headDisplayDelta="+delta);
            check(delta>28&&delta<52,d.id()+" rendered head pitches through forty degrees");
            check(angle(torso.get(d.id()),rotation(d,"body"))<8,d.id()+" pitching does not rotate torso");
            check(host(d.id()).getLocation().distanceSquared(d.location())<.000001,d.id()+" unchanged exact feet");
        }
        nearest();
    }
    private void nearest()throws Exception{
        target(0,0);var d=npcs.getFirst();
        var eye=eye(d);
        var location=eye.clone().add(new Location(eye.getWorld(),0,0,0,d.yaw()+20,0).getDirection().multiply(1.8)).subtract(0,players.getFirst().getEyeHeight(),0);
        var next=actors.create(location);next.setInvulnerable(true);next.teleport(location);
        var spectator=actors.create(eye.clone().add(.1,0,0));spectator.setGameMode(GameMode.SPECTATOR);
        var hidden=actors.create(eye.clone().add(-.1,0,0));hidden.setMetadata("vanished",new org.bukkit.metadata.FixedMetadataValue(this,true));hidden.setInvulnerable(true);
        later(40,()->{
            pointsAt(d,rotation(d,"head"),0,0);
            players.getFirst().teleport(new Location(eye.getWorld(),200,140,200));
            later(40,()->{
                pointsAt(d,rotation(d,"head"),20,0);
                next.teleport(new Location(eye.getWorld(),200,140,200));spectator.teleport(new Location(eye.getWorld(),200,140,200));hidden.teleport(new Location(eye.getWorld(),200,140,200));
                target(150,0);later(40,this::bodyFollow);
            });
        });
    }
    private void bodyFollow()throws Exception{
        for(var d:npcs){
            pointsAt(d,rotation(d,"head"),150,0);
            float body=model(d).active().getYBodyRot();
            check(Math.abs(JawaLook.wrap(body-d.yaw()-105))<.1,d.id()+" body follows beyond forty-five degrees");
            pointsAt(d,rotation(d,"body"),105,0);
        }
        for(int i=0;i<npcs.size();i++){var d=npcs.get(i);before.put(d.id(),model(d).active());
            if(i%2==0)model(d).modeled().removeModel("jawa_black").orElseThrow().destroy();
        }
        later(40,this::recovered);
    }
    private void recovered()throws Exception{
        for(int i=0;i<npcs.size();i++){var d=npcs.get(i);appearance(d);
            check((model(d).active()!=before.get(d.id()))==(i%2==0),d.id()+" repair only replaces missing rig");
            check(!model(d).active().getBone("head").orElseThrow().getImmutableBoneBehaviors().get(BoneBehaviorTypes.HEAD).isHidden(),d.id()+" recovered real head driver");
            pointsAt(d,rotation(d,"head"),150,0);
        }
        for(var d:npcs)model(d).modeled().getBase().getData().getMutableTracking().clear();
        actors.close();actors=null;later(40,()->{for(var d:npcs)check(Math.abs(JawaLook.wrap(model(d).modeled().getYHeadRot()-d.yaw()))<.1,d.id()+" saved idle facing returns");save("PASSED","GRAY HEAD RENDER PASSED: "+checks+" assertions");getLogger().info("GRAY HEAD RENDER PASSED: "+checks);Bukkit.shutdown();});
    }
    public void onDisable(){if(actors!=null){actors.close();actors=null;}}
}
