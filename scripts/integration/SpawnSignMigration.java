package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.trophies.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import static net.mandomc.gameplay.trophies.TrophyJournal.*;

/** One-shot, fail-closed import of explicitly exported SpawnWorld signs. Never replaces occupied terrain. */
public final class SpawnSignMigration extends JavaPlugin {
    private static final NamespacedKey DISPLAY=new NamespacedKey("mandomc","rancor_trophy_display_v1");
    private RancorTrophies service;private MandoPlugin mando;private List<Entry> imports;
    private UUID resetPlayer;private boolean alreadyDone;
    private final Set<Chunk> chunks=new HashSet<>();
    private void require(boolean allowed,String reason){if(!allowed)throw new IllegalStateException(reason);}
    private Block block(Cell c){return Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.fromString(c.world())),"Import world not loaded").getBlockAt(c.x(),c.y(),c.z());}
    private NamespacedKey marker(Cell c){return new NamespacedKey("mandomc","trophy_"+(c.x()&15)+"_"+c.y()+"_"+(c.z()&15));}
    private boolean owns(Entry e,Cell c){return e.id().toString().equals(block(c).getChunk().getPersistentDataContainer().get(marker(c),PersistentDataType.STRING));}
    @Override public void onEnable(){
        try{
            mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");require(mando!=null&&mando.isEnabled(),"MandoMC must be ready");
            service=Objects.requireNonNull(Bukkit.getServicesManager().load(RancorTrophies.class),"Decoration owner missing");
            var root=getDataFolder().toPath();require(Files.isRegularFile(root.resolve("signs.yml")),"Explicit exported placements required");
            imports=List.copyOf(new TrophyJournal(root.resolve("signs.yml"),16).entries().values());require(!imports.isEmpty(),"No signs to import");
            resetPlayer=UUID.fromString(Files.readString(root.resolve("reset-player.txt")).trim());alreadyDone=Files.exists(root.resolve("COMPLETE.yml"));
            require(mando.tutorial()!=null,"Interactive tutorial must be loaded");
            if(!alreadyDone){
                require(!Bukkit.getOfflinePlayer(resetPlayer).hasPlayedBefore(),"Player still has native played-before data");
                require(mando.credits().ledger().top(Integer.MAX_VALUE).stream().noneMatch(e->e.getKey().equals(resetPlayer)),"Player credit account was not cleared");
                require(!mando.credits().ledger().tutorialRewarded(resetPlayer),"Tutorial completion was not cleared");
            }
            var before=new YamlConfiguration();
            for(var e:imports){
                require(e.width()==3&&e.phase()==Phase.PLACED&&e.cell().world().equals("minecraft:spawnworld"),"Only placed three-block SpawnWorld signs can migrate");
                var item=ItemStack.deserializeBytes(Base64.getDecoder().decode(e.item()));require(service.sign(item)&&item.getAmount()==1,"Export is not a single crates_sign");
                var existing=service.journal().entries().get(e.id());
                require(existing==null||existing.equals(e)||existing.placed().equals(e),"Existing journal identity differs");
                for(var c:e.cells()){
                    var b=block(c);if(chunks.add(b.getChunk()))b.getChunk().addPluginChunkTicket(this);
                    var occupying=service.journal().at(c);require(occupying==null||occupying.id().equals(e.id()),"Another decoration occupies the target");
                    require(b.getType().isAir()||b.getType()==Material.BARRIER&&owns(e,c),"Target is occupied: "+c);
                    require(b.getWorld().getWorldBorder().isInside(b.getLocation()),"Target outside world border");
                    require(b.getY()>=b.getWorld().getMinHeight()&&b.getY()<b.getWorld().getMaxHeight(),"Target outside world height");
                    before.set("cells."+c.x()+"_"+c.y()+"_"+c.z(),b.getBlockData().getAsString());
                }
            }
            if(!alreadyDone){
                if(!Files.exists(root.resolve("before.yml")))before.save(root.resolve("before.yml").toFile());
                for(var e:imports){
                    var current=service.journal().entries().get(e.id());
                    if(current==null){current=new Entry(e.id(),e.owner(),e.cell(),e.yaw(),e.face(),e.item(),Phase.PREPARED,e.width());service.journal().reserve(current);}
                    for(var c:e.cells()){
                        var b=block(c);b.setType(Material.BARRIER,false);
                        b.getChunk().getPersistentDataContainer().set(marker(c),PersistentDataType.STRING,e.id().toString());
                    }
                    if(current.phase()==Phase.PREPARED)service.journal().replace(current,e);
                }
            }
            Bukkit.getScheduler().runTaskLater(this,()->{try{verify();}catch(Exception failure){fail(failure);}},60);
        }catch(Exception failure){fail(failure);}
    }
    private void verify()throws Exception{
        var report=new YamlConfiguration();report.set("schema-version",1);report.set("signs",imports.size());report.set("collision-cells",imports.size()*3);
        report.set("reset-player-new-before-first-join",true);
        for(var e:imports){
            require(e.equals(service.journal().entries().get(e.id())),"Journal placement changed");
            for(var c:e.cells())require(block(c).getType()==Material.BARRIER&&owns(e,c),"Missing owned collision cell");
            var b=block(e.cell());var visual=b.getWorld().getNearbyEntities(b.getLocation().add(.5,.5,.5),1,1,1).stream()
                    .filter(v->e.id().toString().equals(v.getPersistentDataContainer().get(DISPLAY,PersistentDataType.STRING))).toList();
            require(visual.size()==1&&visual.getFirst() instanceof ItemDisplay,"Exactly one owned model required");
            var display=(ItemDisplay)visual.getFirst();require(service.sign(display.getItemStack()),"Displayed item is not the imported sign");
            require(display.getItemDisplayTransform()==ItemDisplay.ItemDisplayTransform.NONE,"Incorrect model transform");
            report.set("entries."+e.id()+".world",e.cell().world());report.set("entries."+e.id()+".x",e.cell().x());report.set("entries."+e.id()+".y",e.cell().y());report.set("entries."+e.id()+".z",e.cell().z());report.set("entries."+e.id()+".yaw",e.yaw());
        }
        for(var world:chunks.stream().map(Chunk::getWorld).distinct().toList())world.save();
        report.save(getDataFolder().toPath().resolve("COMPLETE.yml").toFile());
        getLogger().info("SPAWN SIGN MIGRATION VERIFIED: "+imports.size()+" signs, "+imports.size()*3+" owned cells; fresh-player reset verified.");
        if(Boolean.getBoolean("mandomc.migration-fixture"))Bukkit.shutdown();
    }
    private void fail(Exception error){getLogger().log(java.util.logging.Level.SEVERE,"SPAWN SIGN MIGRATION FAILED; stopping before player use",error);Bukkit.shutdown();}
    @Override public void onDisable(){chunks.forEach(c->c.removePluginChunkTicket(this));}
}
