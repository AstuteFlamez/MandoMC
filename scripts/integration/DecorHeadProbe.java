package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import com.ticxo.modelengine.api.model.bone.BoneBehaviorTypes;
import com.ticxo.modelengine.api.model.render.DisplayRenderer;
import net.mandomc.gameplay.decor.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.joml.Quaternionf;

/** Samples the actual display renderer transforms with the idle animations running. */
public final class DecorHeadProbe extends JavaPlugin {
    private DecorService decor; private NativeProbePlayers actors; private int checks;
    private List<DecorDefinition> npcs; private final List<Player> players=new ArrayList<>();
    private final Map<String,Quaternionf> left=new HashMap<>(), torso=new HashMap<>(), visor=new HashMap<>();
    private final List<String> evidence=new ArrayList<>();
    public void onEnable(){later(140,this::begin);}
    private interface Step{void run()throws Exception;}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private void later(long ticks,Step step){Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"HEAD FAILED",e);try{save("FAILED",e.toString());}catch(Exception writeError){e.addSuppressed(writeError);}Bukkit.shutdown();}},ticks);}
    private void save(String name,String result)throws Exception{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name+".txt"),result+"\n"+String.join("\n",evidence));}
    private Quaternionf rotation(DecorDefinition d,String bone){
        var model=decor.model(d.id());
        // Embedded actors lack the client chunk acknowledgements that normally start tracking.
        model.modeled().getBase().getData().getMutableTracking().put(players.get(npcs.indexOf(d)).getUniqueId(),
                com.ticxo.modelengine.api.entity.CullType.NO_CULL);
        model.active().tick();
        var display=((DisplayRenderer)model.active().getModelRenderer()).getRendered().get(bone);
        check(display!=null,d.id()+" visible display bone "+bone);
        return new Quaternionf(display.getLeftRotation().get()).mul(display.getRightRotation().get()).normalize();
    }
    private void pointsAt(DecorDefinition d,Quaternionf rotation,float yaw,float pitch){
        var forward=rotation.transform(new org.joml.Vector3f(0,0,1));
        var expected=new Location(d.location().getWorld(),0,0,0,d.yaw()+yaw,pitch).getDirection();
        double dot=forward.x*expected.getX()+forward.y*expected.getY()+forward.z*expected.getZ();
        check(dot>Math.cos(Math.toRadians(10)),d.id()+" displayed face points toward player, with correct yaw/pitch signs");
    }
    private double angle(Quaternionf a,Quaternionf b){return Math.toDegrees(2*Math.acos(Math.min(1,Math.abs(a.dot(b)))));}
    private void target(float yaw,float pitch){for(int i=0;i<npcs.size();i++){
        var d=npcs.get(i);var base=decor.bases().get(d.id());var eye=base.getEyeLocation();
        var offset=new Location(eye.getWorld(),0,0,0,d.yaw()+yaw,pitch).getDirection().multiply(1);
        var destination=eye.add(offset).subtract(0,players.get(i).getEyeHeight(),0);players.get(i).teleport(destination);
    }}
    private void begin()throws Exception{
        decor=Bukkit.getServicesManager().load(DecorService.class);check(decor!=null,"single decor owner");
        npcs=decor.definitions().stream().filter(DecorDefinition::npc).toList();check(npcs.size()==5,"five characters");
        actors=new NativeProbePlayers("HeadProbe");for(var d:npcs)players.add(actors.create(d.location()));
        for(var d:npcs){var head=decor.model(d.id()).active().getBone("head").orElseThrow();
            check(!head.getImmutableBoneBehaviors().get(BoneBehaviorTypes.HEAD).isHidden(),d.id()+" real head driver replaces hidden fallback");
            check(head.getBoneBehavior(BoneBehaviorTypes.HEAD).orElseThrow().isLocal(),d.id()+" hierarchy includes child parts");
        }
        target(30,0);later(40,()->{capture();target(-30,0);later(40,this::yaw);});
    }
    private void capture(){for(var d:npcs){left.put(d.id(),rotation(d,"head"));torso.put(d.id(),rotation(d,"torso"));
        if(d.type().equals("boba_fett"))visor.put(d.id(),new Quaternionf(left.get(d.id())).invert().mul(rotation(d,"visor")));
    }}
    private void yaw()throws Exception{
        for(var d:npcs){var head=rotation(d,"head");double headAngle=angle(left.get(d.id()),head),bodyAngle=angle(torso.get(d.id()),rotation(d,"torso"));
            evidence.add(d.id()+" yaw headDisplayDelta="+headAngle+" torsoDisplayDelta="+bodyAngle+" before="+left.get(d.id())+" after="+head);
            pointsAt(d,head,-30,0);
            check(headAngle>45&&headAngle<75,d.id()+" rendered head independently traverses sixty degrees");
            check(bodyAngle<8,d.id()+" torso remains still inside neck cone");
            check(Math.abs(DecorLook.wrap(decor.model(d.id()).modeled().getYBodyRot()-d.yaw()))<.1,d.id()+" body stays at anchor yaw");
            if(d.type().equals("boba_fett"))check(angle(visor.get(d.id()),new Quaternionf(head).invert().mul(rotation(d,"visor")))<8,"Boba visor follows head without tearing");
        }
        target(0,-20);later(40,()->{capture();target(0,20);later(40,this::pitch);});
    }
    private void pitch()throws Exception{
        for(var d:npcs){var head=rotation(d,"head");pointsAt(d,head,0,20);double delta=angle(left.get(d.id()),head);evidence.add(d.id()+" pitch headDisplayDelta="+delta);
            check(delta>28&&delta<52,d.id()+" rendered head pitches through forty degrees");
            check(angle(torso.get(d.id()),rotation(d,"torso"))<8,d.id()+" pitching does not rotate torso");
            check(decor.bases().get(d.id()).getLocation().distanceSquared(d.location())<.000001,d.id()+" unchanged exact feet");
        }
        for(var d:decor.definitions())if(!d.npc()){
            check(decor.bases().get(d.id()).getYaw()==d.yaw(),d.id()+" fixed vehicle yaw");
            check(decor.bases().get(d.id()).getPitch()==d.pitch(),d.id()+" fixed vehicle pitch");
        }
        target(150,0);later(40,this::bodyFollow);
    }
    private void bodyFollow()throws Exception{
        for(var d:npcs){
            pointsAt(d,rotation(d,"head"),150,0);
            float body=decor.model(d.id()).active().getYBodyRot();
            check(Math.abs(DecorLook.wrap(body-d.yaw()-105))<.1,d.id()+" body follows beyond forty-five degrees");
            pointsAt(d,rotation(d,"torso"),105,0);
        }
        actors.close();actors=null;later(40,()->{for(var d:npcs)check(Math.abs(DecorLook.wrap(decor.model(d.id()).modeled().getYHeadRot()-d.yaw()))<.1,d.id()+" saved idle facing returns");save("PASSED","HEAD RENDER PASSED: "+checks+" assertions");getLogger().info("HEAD RENDER PASSED: "+checks);if(Bukkit.getPluginManager().getPlugin("DecorClickProbe")==null)Bukkit.shutdown();});
    }
    public void onDisable(){if(actors!=null){actors.close();actors=null;}}
}
