package net.mandomc.verification;

import com.ticxo.modelengine.api.ModelEngineAPI;
import java.nio.file.*;
import java.util.*;
import net.mandomc.gameplay.clans.*;
import net.mandomc.gameplay.decor.DecorService;
import net.mandomc.integrations.mobs.MythicMobAccess;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.HandlerList;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only live audit. Creates no actors/entities, changes no gameplay state, never stops Paper. */
public final class LiveHerdSmoke extends JavaPlugin {
    private final List<String> rows = new ArrayList<>();
    private final Map<String,UUID> gray = new LinkedHashMap<>();
    private int checks;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> run(false), 122);
    }
    private void check(boolean ok,String label) {
        if (!ok) throw new AssertionError(label);
        checks++; rows.add("PASS " + label);
    }
    private void run(boolean second) {
        try {
            var mando=Bukkit.getPluginManager().getPlugin("MandoMC");
            check(mando!=null&&mando.isEnabled(),"MandoMC enabled");
            var listeners=HandlerList.getRegisteredListeners(mando).stream().map(r->r.getListener()).distinct().toList();
            for(String suffix:List.of("HerdBootstrap","HerdAnimals","HerdBabies","HerdTempt","HerdModels"))
                check(listeners.stream().filter(l->l.getClass().getSimpleName().equals(suffix)).count()==1,"one herd lifecycle owner: "+suffix);
            var cfg=YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"mobs/jawas.yml"));
            var ids=cfg.getConfigurationSection("npcs").getKeys(false);
            check(ids.size()==7,"seven saved gray anchors");
            var marker=new NamespacedKey("mandomc","jawa_npc_v1");
            for(String id:ids) {
                String p="npcs."+id;
                var world=Bukkit.getWorld(NamespacedKey.fromString(cfg.getString(p+".world")));
                var at=new Location(world,cfg.getDouble(p+".x"),cfg.getDouble(p+".y"),cfg.getDouble(p+".z"));
                var hosts=world.getLivingEntities().stream().filter(e->id.equals(e.getPersistentDataContainer().get(marker,PersistentDataType.STRING))).toList();
                check(hosts.size()==1,"single gray host "+id);
                var e=hosts.getFirst();
                if(second) check(e.getUniqueId().equals(gray.get(id)),"stable gray UUID "+id); else gray.put(id,e.getUniqueId());
                check(e.getLocation().distanceSquared(at)<.01,"saved gray position "+id);
                var modeled=ModelEngineAPI.getModeledEntity(e);
                check(modeled!=null&&!modeled.isDestroyed()&&!modeled.isBaseEntityVisible(),"hidden base and live modeled entity "+id);
                var model=modeled.getModel("jawa_black").orElseThrow();
                check(modeled.getModels().size()==1&&!model.isDestroyed()&&!model.isRemoved(),"single live gray rig "+id);
                check(model.getBones().keySet().containsAll(Set.of("body","head","face","tip","left_arm","right_arm","blaster")),"complete gray bones "+id);
                var face=model.getBone("face").orElseThrow();
                check(face.getDefaultTint().asRGB()==0xff0000&&face.getBlockLight()==15&&face.getSkyLight()==15,"red fullbright gray eyes "+id);
                check(e.getHealth()>0&&e.getHealth()<=e.getMaxHealth(),"valid actual gray health "+id);
                rows.add("GRAY "+id+" "+e.getUniqueId());
            }
            var decor=Bukkit.getServicesManager().load(DecorService.class);
            check(decor!=null&&decor.bases().size()==9,"nine decor anchors");
            for(var definition:decor.definitions()) {
                var base=decor.bases().get(definition.id());
                check(base!=null&&base.isValid()&&base.getLocation().distanceSquared(definition.location())<.01,"saved decor base "+definition.id());
                var model=decor.model(definition.id());
                check(model!=null&&!model.active().isDestroyed()&&!model.modeled().isBaseEntityVisible(),"live decor model "+definition.id());
            }
            var journal=mando.getDataFolder().toPath().resolve("data/frozen-land/wilderness-hoth-20261111.journal");
            check(Files.exists(journal.resolveSibling(journal.getFileName()+".completed"))&&!Files.exists(journal.resolveSibling(journal.getFileName()+".failed")),"frozen capture completed without failure");
            var lines=Files.readAllLines(journal);check(lines.size()==2115,"2115 frozen originals");
            var land=Bukkit.getServicesManager().load(ClanService.class).land();
            for(String line:lines) {
                var a=line.split(" ");var chunk=new ClanState.Chunk(a[0],Integer.parseInt(a[1]),Integer.parseInt(a[2]));
                if(!land.supported(chunk)||!land.hasBaseline(chunk)||!land.ready(chunk))throw new AssertionError("Frozen original unavailable: "+line);
            }
            check(true,"all 2115 frozen baselines remain ready");
            var boards=listeners.stream().filter(l->l.getClass().getSimpleName().equals("WorldLeaderboards")).toList();
            check(boards.size()==1,"one world leaderboard owner");
            var field=boards.getFirst().getClass().getDeclaredField("displays");field.setAccessible(true);
            var displays=(Map<?,?>)field.get(boards.getFirst());check(displays.size()==2,"both current world boards present");
            for(var entry:displays.entrySet())check(entry.getValue() instanceof TextDisplay display&&display.isValid(),"live board "+entry.getKey());
            var mobs=new MythicMobAccess();int managed=0;
            for(var world:Bukkit.getWorlds())for(var entity:world.getLivingEntities())if(mobs.managed(entity)) {
                managed++;check(entity.getHealth()>0&&entity.getHealth()<=entity.getMaxHealth(),"actual loaded mob health "+entity.getUniqueId());
                rows.add("MOB "+mobs.type(entity)+" hp="+entity.getHealth()+"/"+entity.getMaxHealth()+" level="+mobs.level(entity));
            }
            rows.add("SNAPSHOT "+(second?2:1)+" managed="+managed+" checks="+checks);
            if(!second)Bukkit.getScheduler().runTaskLater(this,()->run(true),102);
            else {
                Files.createDirectories(getDataFolder().toPath());Files.write(getDataFolder().toPath().resolve("PASSED.txt"),rows);
                getLogger().info("LIVE HERD SMOKE PASSED "+checks+" assertions; two snapshots; no actors created");
                Bukkit.getPluginManager().disablePlugin(this);
            }
        }catch(Throwable error) {
            try {Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());}catch(Exception ignored){}
            getLogger().log(java.util.logging.Level.SEVERE,"LIVE HERD SMOKE FAILED",error);Bukkit.getPluginManager().disablePlugin(this);
        }
    }
}
