package net.mandomc.verification;
import java.nio.file.*;
import java.util.*;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.render.DisplayRenderer;
import net.mandomc.gameplay.decor.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Native client interaction packets using renderer entity IDs and the actual registered relay. */
public final class DecorClickProbe extends JavaPlugin {
    private NativeProbePlayers actors;private DecorService decor;private int checks,index,round;
    private final List<String> firstLines=new ArrayList<>();
    private final List<Player> players=new ArrayList<>();private final List<String> evidence=new ArrayList<>();
    public void onEnable(){later(new java.io.File(getDataFolder(),"after-scene").exists()?360:120,this::begin);}
    private void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private interface Step{void run()throws Exception;}
    private void later(long ticks,Step step){Bukkit.getScheduler().runTaskLater(this,()->{try{step.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"DECOR CLICK FAILED",e);save("FAILED",e.toString());if(actors!=null)actors.close();Bukkit.shutdown();}},ticks);}
    private void save(String name,String text){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(name+".txt"),text+"\n"+String.join("\n",evidence));}catch(Exception e){throw new RuntimeException(e);}}
    private void begin()throws Exception{
        decor=Bukkit.getServicesManager().load(DecorService.class);check(decor!=null,"Decor service present");actors=new NativeProbePlayers("ClickProbe");
        for(var d:decor.definitions())if(d.npc()){players.add(actors.create(d.location().add(1,0,0)));}
        later(10,this::click);
    }
    private void click()throws Exception{
        var d=decor.definitions().stream().filter(DecorDefinition::npc).toList().get(index);var player=players.get(index);actors.drain(player);actors.otherPackets(player);
        var active=decor.model(d.id()).active();var renderer=(DisplayRenderer)active.getModelRenderer();var hitbox=renderer.getHitbox();
        var parser=Class.forName("com.ticxo.modelengine.v26_1.parser.model.DisplayParser",true,ModelEngineAPI.class.getClassLoader());
        var spawnMethod=parser.getDeclaredMethod("hitboxSpawn",DisplayRenderer.Hitbox.class);spawnMethod.setAccessible(true);
        var spawn=spawnMethod.invoke(parser.getConstructor().newInstance(),hitbox);
        check(spawn!=null,"Actual ModelEngine client spawn packet includes transparent NPC interaction box");
        check((int)spawn.getClass().getMethod("getId").invoke(spawn)==hitbox.getHitboxId(),"Native packet target is the actual spawned interaction entity ID");
        boolean baseTarget=round==1;
        if(round==1)player.getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(Material.DIAMOND));
        int id=baseTarget?decor.bases().get(d.id()).getEntityId():hitbox.getHitboxId();var tracker=ModelEngineAPI.getInteractionTracker();var relay=tracker.getEntityRelay(id);var model=tracker.getModelRelay(id);
        evidence.add(d.id()+" target="+id+" relay="+relay+" model="+(model!=null)+" main="+active.isMainHitbox()+" visible="+hitbox.isHitboxVisible()+" width="+hitbox.getWidth().get()+" height="+hitbox.getHeight().get());
        nativePacket(player,id,relay!=null?relay:model!=null?decor.bases().get(d.id()).getEntityId():null,"MAIN_HAND");
        later(3,()->{
            var chat=actors.drain(player);var other=actors.otherPackets(player);evidence.add("result="+chat.stream().map(c->net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c)).toList()+" sound="+other);
            check(chat.size()==1,"Actual native right-click delivers one private line for "+d.id());
            String text=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(chat.getFirst());
            check(text.contains(d.name())&&text.startsWith("ᴍᴀɴᴅᴏᴍᴄ » "),"Private chat contains NPC speaker and current system prefix");
            if(round==0)firstLines.add(text);else check(!text.equals(firstLines.get(index)),"Written dialogue rotates to the next character line");
            check(other==null||other.stream().noneMatch(s->s.contains("SoundPacket")),"NPC interaction produces written chat and no audio for "+d.id());
            nativePacket(player,id,relay!=null?relay:model!=null?decor.bases().get(d.id()).getEntityId():null,"OFF_HAND");
            later(2,()->{check(actors.drain(player).isEmpty(),"Offhand does not duplicate dialogue");var packets=actors.otherPackets(player);check(packets==null||packets.stream().noneMatch(s->s.contains("SoundPacket")),"Offhand does not duplicate sound");
                index++;if(index<players.size())click();else if(round==0){round=1;index=0;later(62,this::click);}else{save("PASSED","DECOR CLICK PASSED: "+checks+" native assertions");getLogger().info("DECOR CLICK PASSED: "+checks+" native assertions");actors.close();actors=null;Bukkit.shutdown();}
            });
        });
    }
    private void nativePacket(Player player,int id,Integer relay,String handName)throws Exception{
        var handle=player.getClass().getMethod("getHandle").invoke(player);var listener=handle.getClass().getField("connection").get(handle);
        var hand=Class.forName("net.minecraft.world.InteractionHand");var vec=Class.forName("net.minecraft.world.phys.Vec3");var packetType=Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket");
        var packet=packetType.getConstructor(int.class,hand,vec,boolean.class).newInstance(id,hand.getField(handName).get(null),vec.getConstructor(double.class,double.class,double.class).newInstance(0,1,0),false);
        if(relay==null){listener.getClass().getMethod("handleInteract",packetType).invoke(listener,packet);return;}
        var wrapper=Class.forName("com.ticxo.modelengine.v26_1.network.patch.ServerboundInteractPacketWrapper",true,ModelEngineAPI.class.getClassLoader());
        var wrapped=wrapper.getConstructor(int.class,packetType).newInstance(relay,packet);
        wrapper.getMethod("handle",Class.forName("net.minecraft.network.protocol.game.ServerGamePacketListener")).invoke(wrapped,listener);
    }
    public void onDisable(){if(actors!=null){actors.close();actors=null;}}
}
