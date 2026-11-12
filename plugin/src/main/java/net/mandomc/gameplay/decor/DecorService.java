package net.mandomc.gameplay.decor;

import java.util.*;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.ModelMountEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.integrations.models.DecorModel;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Config-owned scenery; player vehicle records and Mythic combat instances are never used. */
public final class DecorService implements Listener,AutoCloseable {
    public static final NamespacedKey MARKER=new NamespacedKey("mandomc","spawn_decor_id");
    private record Live(DecorDefinition definition,ArmorStand base,DecorModel model){}
    private final MandoPlugin plugin;
    private final YamlConfiguration config;
    private final Map<String,DecorDefinition> definitions=new LinkedHashMap<>();
    private final Map<String,Live> live=new LinkedHashMap<>();
    private final Map<UUID,Long> interactions=new HashMap<>();
    private final Map<String,Long> voices=new HashMap<>();
    private final Map<String,Long> lastVoice=new HashMap<>();
    private final Set<Chunk> chunks=new HashSet<>();
    private final Set<UUID> internalTeleports=new HashSet<>();
    private final BukkitTask ticker;
    private final Random random=new Random();
    private final double voiceRange;
    private final int ambientMin,ambientMax;
    private long nextAmbient;
    private boolean closing;
    public DecorService(MandoPlugin plugin,YamlConfiguration config){
        this.plugin=plugin;this.config=config;
        if(config.getInt("schema-version")!=1)throw new IllegalArgumentException("decor/placements.yml: schema-version1 required");
        voiceRange=config.getDouble("voice-range",7);ambientMin=config.getInt("ambient-min-seconds",80);ambientMax=config.getInt("ambient-max-seconds",140);
        if(!Double.isFinite(voiceRange)||voiceRange<1||voiceRange>16||ambientMin<30||ambientMax<ambientMin)throw new IllegalArgumentException("Invalid decor voice range/cooldown");
        for(var entry:config.getMapList("placements")){
            var definition=DecorDefinition.read(entry);
            if(definitions.putIfAbsent(definition.id(),definition)!=null)throw new IllegalArgumentException("Duplicate decor id "+definition.id());
            var at=definition.location();
            if(!at.getWorld().getWorldBorder().isInside(at)||at.getY()<at.getWorld().getMinHeight()||at.getY()+2>=at.getWorld().getMaxHeight())throw new IllegalArgumentException("Decor outside world bounds: "+definition.id());
            if(ModelEngineAPI.getBlueprint(definition.model())==null)throw new IllegalArgumentException("Missing decor model "+definition.model());
        }
        if(definitions.isEmpty()||definitions.size()>32)throw new IllegalArgumentException("Decor requires 1..32 placements");
        try{
            for(var definition:definitions.values()){
                var chunk=definition.location().getChunk();if(chunk.addPluginChunkTicket(plugin))chunks.add(chunk);
            }
            // Remove only prior instances carrying this feature's private marker; user vehicles/NPCs stay untouched.
            for(var world:Bukkit.getWorlds())for(var entity:List.copyOf(world.getEntities())){
                if(entity.getPersistentDataContainer().has(MARKER,PersistentDataType.STRING)){
                    var modeled=ModelEngineAPI.getModeledEntity(entity.getUniqueId());if(modeled!=null)modeled.destroy();entity.remove();
                }
            }
            for(var definition:definitions.values())spawn(definition);
        }catch(RuntimeException error){removeAll();throw error;}
        Bukkit.getPluginManager().registerEvents(this,plugin);
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
        plugin.getLogger().info("Spawn decor ready: "+live.size()+" fixed model placements; no combat AI or player vehicle ownership.");
    }
    private void spawn(DecorDefinition definition){
        Location at=definition.location();
        var territory=Objects.requireNonNull(Bukkit.getServicesManager().load(net.mandomc.gameplay.territory.TerritoryService.class));
        var base=territory.spawnNpc(EntityType.ARMOR_STAND,at,()->at.getWorld().spawn(at,ArmorStand.class,entity->{
            entity.setVisible(false);entity.setGravity(false);entity.setInvulnerable(true);entity.setSilent(true);entity.setCollidable(false);
            entity.setPersistent(false);entity.setRemoveWhenFarAway(false);entity.setArms(false);entity.setBasePlate(false);
            entity.setCanMove(false);entity.setCanTick(false);entity.setFireTicks(0);
            entity.getPersistentDataContainer().set(MARKER,PersistentDataType.STRING,definition.id());
        }));
        try{live.put(definition.id(),new Live(definition,base,new DecorModel(base,definition)));}
        catch(RuntimeException error){base.remove();throw error;}
        voices.put(definition.id(),System.currentTimeMillis()+1000L*random.nextInt(ambientMin,ambientMax+1));
    }
    public List<DecorDefinition> definitions(){return List.copyOf(definitions.values());}
    public Map<String,ArmorStand> bases(){var result=new LinkedHashMap<String,ArmorStand>();live.forEach((id,value)->result.put(id,value.base));return Map.copyOf(result);}
    public DecorModel model(String id){return live.get(id).model;}
    private Live owned(Entity entity){
        String id=entity.getPersistentDataContainer().get(MARKER,PersistentDataType.STRING);
        var value=id==null?null:live.get(id);return value!=null&&value.base.getUniqueId().equals(entity.getUniqueId())?value:null;
    }
    private void tick(){
        if(closing)return;
        long now=System.currentTimeMillis();
        for(var definition:definitions.values()){
            var value=live.get(definition.id());
            if(value==null||!value.base.isValid()||value.base.isDead()){
                if(value!=null)value.model.close();spawn(definition);continue;
            }
            Location at=definition.location();var base=value.base;
            if(!base.getWorld().equals(at.getWorld())||base.getLocation().distanceSquared(at)>.000001){
                internalTeleports.add(base.getUniqueId());try{base.teleport(at);}finally{internalTeleports.remove(base.getUniqueId());}
            }
            base.setRotation(at.getYaw(),at.getPitch());base.setVelocity(new Vector());base.setFireTicks(0);
            if(definition.npc()&&now>=nextAmbient&&now>=voices.getOrDefault(definition.id(),Long.MAX_VALUE)){
                boolean near=at.getWorld().getPlayers().stream().anyMatch(p->p.getLocation().distanceSquared(at)<=voiceRange*voiceRange);
                if(near){voice(value);nextAmbient=now+20_000;}
                voices.put(definition.id(),now+1000L*random.nextInt(ambientMin,ambientMax+1));
            }
        }
        interactions.entrySet().removeIf(entry->entry.getValue()<now-60_000);
    }
    private void voice(Live value){
        if(value.definition.voices().isEmpty())return;
        long now=System.currentTimeMillis();if(now<lastVoice.getOrDefault(value.definition.id(),0L))return;
        lastVoice.put(value.definition.id(),now+8000);
        var at=value.definition.location();String sound=value.definition.voices().get(random.nextInt(value.definition.voices().size()));
        for(var player:at.getWorld().getPlayers())if(player.getLocation().distanceSquared(at)<=voiceRange*voiceRange)
            player.playSound(at,sound,SoundCategory.VOICE,.7f,1);
    }
    private void talk(Player player,Live value){
        if(!value.definition.npc()||!player.getWorld().equals(value.base.getWorld())||player.getLocation().distanceSquared(value.base.getLocation())>36)return;
        long now=System.currentTimeMillis();if(now-interactions.getOrDefault(player.getUniqueId(),0L)<3000)return;
        interactions.put(player.getUniqueId(),now);
        String line=value.definition.dialogue().get(random.nextInt(value.definition.dialogue().size()));
        Component text=MiniMessage.miniMessage().deserialize(config.getString("messages.npc-line"),
                Placeholder.unparsed("npc",value.definition.name()),Placeholder.unparsed("line",line));
        // Chat presentation is optional; rendering/placement has no dependency on its implementation.
        try{
            var type=Class.forName("net.mandomc.gameplay.messaging.MessageService",true,plugin.getClass().getClassLoader());
            Object service=Bukkit.getServicesManager().load(type);
            if(service!=null)text=(Component)type.getMethod("known",String.class,String.class,Component.class).invoke(service,"decor/placements.yml","messages.npc-line",text);
        }catch(ClassNotFoundException absent){/* Independent feature build retains its original dialogue. */}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot format decorative NPC dialogue",error);}
        player.sendMessage(text);voice(value);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEntityEvent event){
        var value=owned(event.getRightClicked());if(value==null)return;event.setCancelled(true);
        if(event.getHand()==EquipmentSlot.HAND)talk(event.getPlayer(),value);
    }
    @EventHandler public void modelInteract(BaseEntityInteractEvent event){
        var value=live.values().stream().filter(v->v.base.getUniqueId().equals(event.getBaseEntity().getUUID())).findFirst().orElse(null);
        if(value!=null&&event.getSlot()==EquipmentSlot.HAND&&event.getAction()!=BaseEntityInteractEvent.Action.ATTACK)talk(event.getPlayer(),value);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void mount(ModelMountEvent event){
        UUID id=event.getVehicle().getModeledEntity().getBase().getUUID();
        if(live.values().stream().anyMatch(v->v.base.getUniqueId().equals(id)))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void nativeMount(EntityMountEvent event){if(owned(event.getMount())!=null)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void teleport(EntityTeleportEvent event){if(owned(event.getEntity())!=null&&!internalTeleports.contains(event.getEntity().getUniqueId()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void manipulate(PlayerArmorStandManipulateEvent event){if(owned(event.getRightClicked())!=null)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.LOWEST) public void damage(EntityDamageEvent event){if(owned(event.getEntity())!=null)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void burn(EntityCombustEvent event){if(owned(event.getEntity())!=null)event.setCancelled(true);}
    private void removeAll(){
        for(var value:List.copyOf(live.values())){value.model.close();value.base.remove();}live.clear();
        chunks.forEach(chunk->chunk.removePluginChunkTicket(plugin));chunks.clear();
    }
    @Override public void close(){closing=true;ticker.cancel();HandlerList.unregisterAll(this);removeAll();interactions.clear();voices.clear();lastVoice.clear();}
}
