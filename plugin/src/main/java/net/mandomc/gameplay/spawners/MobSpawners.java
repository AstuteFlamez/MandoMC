package net.mandomc.gameplay.spawners;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.integrations.mobs.MythicMobAccess;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import net.mandomc.integrations.models.SpawnerModel;
import net.kyori.adventure.text.Component;
import org.bukkit.scheduler.BukkitTask;

/** Persistent native spawner blocks; all spawned creatures use the existing Mythic definitions. */
public final class MobSpawners implements Listener, AutoCloseable {
    public static final NamespacedKey TYPE = new NamespacedKey("mandomc", "spawner_type_v1");
    public static final Map<String,String> TYPES = Map.of("massiff_spawner", "Massiff",
            "dewback_spawner", "Dewback", "bantha_spawner", "Bantha", "jawa_spawner", "JawaBrown");
    private record Site(UUID world, int x, int y, int z) {
        static Site of(Block b) { return new Site(b.getWorld().getUID(),b.getX(),b.getY(),b.getZ()); }
    }
    private final MandoPlugin plugin;
    private final MythicMobAccess mobs = new MythicMobAccess();
    private final Map<Site,Integer> sites = new HashMap<>();
    private static final NamespacedKey VISUAL=new NamespacedKey("mandomc","spawner_preview_v1");
    private record Preview(ArmorStand base,TextDisplay label,SpawnerModel model) implements AutoCloseable {
        boolean valid(){return base.isValid()&&label.isValid()&&model.valid();}
        @Override public void close(){model.close();base.remove();label.remove();}
    }
    private record Visual(String model,double scale,double baseHeight) { }
    private final Map<String,Visual> visuals=new HashMap<>();
    private final Map<Site,Preview> previews=new HashMap<>();
    private final BukkitTask task,rotation;
    private final int interval, range, cap;
    private final ConfigurationSection messages;
    private int clock;

    public MobSpawners(MandoPlugin plugin, ConfigurationSection config) throws ReflectiveOperationException {
        this.plugin=plugin;
        interval=config.getInt("interval-seconds");range=config.getInt("activation-range");cap=config.getInt("nearby-cap");
        messages=Objects.requireNonNull(config.getConfigurationSection("messages"));
        if(config.getInt("schema-version")!=1 || interval<10 || interval>600 || range<4 || range>32 || cap<1 || cap>16)
            throw new IllegalArgumentException("mobs/spawners.yml: invalid limits");
        for(String id:TYPES.keySet()){
            Objects.requireNonNull(plugin.items().create(id),"Missing spawner item "+id);
            var row=Objects.requireNonNull(config.getConfigurationSection("previews."+id),"Missing spawner preview "+id);
            String model=row.getString("model","");double scale=row.getDouble("scale",0),height=row.getDouble("base-height",-1);
            if(!model.matches("[a-z0-9_]+")||!Double.isFinite(scale)||scale<=0||scale>.5||!Double.isFinite(height)||height<.1||height>.8)
                throw new IllegalArgumentException("mobs/spawners.yml: invalid preview "+id);
            visuals.put(id,new Visual(model,scale,height));
        }
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(var world:Bukkit.getWorlds()){
            cleanStale(world.getEntities());for(var chunk:world.getLoadedChunks())load(chunk);
        }
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
        rotation=Bukkit.getScheduler().runTaskTimer(plugin,()->previews.values().forEach(p->p.model.rotate((Bukkit.getCurrentTick()*1.5f)%360)),2,2);
    }
    private static boolean known(String id) {return id!=null && TYPES.containsKey(id);}
    private void tell(Player p,String key) { p.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
            .deserialize(Objects.requireNonNull(messages.getString(key)))); }
    private String type(Block block) {
        return block.getState() instanceof CreatureSpawner spawner
                ?spawner.getPersistentDataContainer().get(TYPE,PersistentDataType.STRING):null;
    }
    private void load(Chunk chunk) {
        for(var state:chunk.getTileEntities())if(state instanceof CreatureSpawner spawner
                && known(spawner.getPersistentDataContainer().get(TYPE,PersistentDataType.STRING)))
        {
            hidePig(spawner);sites.put(Site.of(state.getBlock()),clock+interval);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void validate(BlockPlaceEvent e) {
        if(!known(plugin.items().identify(e.getItemInHand())))return;
        if(!plugin.territories().edits(e.getBlock().getLocation())) {
            e.setCancelled(true);tell(e.getPlayer(),"wilderness");
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void place(BlockPlaceEvent e) {
        String id=plugin.items().identify(e.getItemInHand());if(!known(id)||!e.canBuild())return;
        if(!(e.getBlock().getState() instanceof CreatureSpawner spawner))return;
        spawner.getPersistentDataContainer().set(TYPE,PersistentDataType.STRING,id);
        spawner.setSpawnedType(null);spawner.setDelay(32767);spawner.update();
        sites.put(Site.of(e.getBlock()),clock+interval);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakTool(BlockBreakEvent e) {
        if(!known(type(e.getBlock())))return;
        if(e.getPlayer().getGameMode()!=GameMode.CREATIVE && !e.getPlayer().getInventory().getItemInMainHand().getType().name().endsWith("_PICKAXE")) {
            e.setCancelled(true);tell(e.getPlayer(),"pickaxe");
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void broken(BlockBreakEvent e) {
        String id=type(e.getBlock());if(!known(id))return;
        var site=Site.of(e.getBlock());sites.remove(site);removePreview(site);e.setDropItems(false);e.setExpToDrop(0);
        if(e.getPlayer().getGameMode()!=GameMode.CREATIVE) {
            var at=e.getBlock().getLocation().add(.5,.5,.5);
            // Defer until native breaking has removed the spawner block.
            Bukkit.getScheduler().runTask(plugin,()->at.getWorld().dropItemNaturally(at,plugin.items().create(id)));
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void nativeSpawn(SpawnerSpawnEvent e) { if(known(type(e.getSpawner().getBlock())))e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void changeType(PlayerInteractEvent e) {
        if(e.getClickedBlock()!=null && known(type(e.getClickedBlock())) && e.getItem()!=null
                && e.getItem().getType().name().endsWith("_SPAWN_EGG"))e.setCancelled(true);
    }
    @EventHandler public void loaded(ChunkLoadEvent e) {load(e.getChunk());}
    @EventHandler public void unloaded(ChunkUnloadEvent e) {
        for(var site:List.copyOf(sites.keySet()))if(site.world.equals(e.getWorld().getUID())&&site.x>>4==e.getChunk().getX()&&site.z>>4==e.getChunk().getZ()){
            sites.remove(site);removePreview(site);
        }
    }
    @EventHandler public void entitiesLoaded(EntitiesLoadEvent e){cleanStale(e.getEntities());}
    private void cleanStale(List<Entity> entities){
        for(var entity:entities)if(entity.getPersistentDataContainer().has(VISUAL)){
            boolean active=previews.values().stream().anyMatch(p->p.base==entity||p.label==entity);
            if(!active)SpawnerModel.removeStale(entity);
        }
    }
    private void hidePig(CreatureSpawner spawner){
        if(spawner.getSpawnedType()!=null || !spawner.getPotentialSpawns().isEmpty()){
            spawner.setSpawnedType(null);spawner.setDelay(32767);spawner.update();
        }
    }
    private void removePreview(Site site){var old=previews.remove(site);if(old!=null)old.close();}
    private void preview(Site site,Block block,String id){
        var visual=visuals.get(id);var at=block.getLocation().add(.5,visual.baseHeight,.5);
        if(block.getWorld().getPlayers().stream().noneMatch(p->p.getLocation().distanceSquared(at)<=32*32)){removePreview(site);return;}
        var previous=previews.get(site);if(previous!=null&&previous.valid())return;
        removePreview(site);if(!SpawnerModel.available(visual.model))return;
        var base=plugin.territories().spawnNpc(EntityType.ARMOR_STAND,at,()->at.getWorld().spawn(at,ArmorStand.class,e->{
            e.setVisible(false);e.setMarker(true);e.setGravity(false);e.setInvulnerable(true);e.setSilent(true);e.setCollidable(false);
            e.setPersistent(false);e.setCanMove(false);e.setCanTick(false);e.setBasePlate(false);e.getPersistentDataContainer().set(VISUAL,PersistentDataType.BYTE,(byte)1);
        }));
        if(base==null||!base.isValid())return;
        SpawnerModel rendered=null;TextDisplay label=null;
        try{
            rendered=new SpawnerModel(base,visual.model,visual.scale);
            String name=id.equals("jawa_spawner")?"Jawa":TYPES.get(id);
            Component title=net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(messages.getString("label","<gold><count>x <mob>"),
                    net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("count","1"),net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("mob",name));
            label=at.getWorld().spawn(block.getLocation().add(.5,1.2,.5),TextDisplay.class,e->{
                e.text(title);e.setPersistent(false);e.setInvulnerable(true);e.setBillboard(Display.Billboard.CENTER);e.setShadowed(true);
                e.setDefaultBackground(false);e.setBackgroundColor(Color.fromARGB(0));e.setViewRange(.5f);
                e.getPersistentDataContainer().set(VISUAL,PersistentDataType.BYTE,(byte)1);
            });
            previews.put(site,new Preview(base,label,rendered));
        }catch(RuntimeException error){if(rendered!=null)rendered.close();base.remove();if(label!=null)label.remove();throw error;}
    }
    private void tick() {
        clock++;
        for(var entry:List.copyOf(sites.entrySet())) {
            var site=entry.getKey();var world=Bukkit.getWorld(site.world);
            if(world==null || !world.isChunkLoaded(site.x>>4,site.z>>4)) {sites.remove(site);removePreview(site);continue;}
            var block=world.getBlockAt(site.x,site.y,site.z);String id=type(block);
            if(!known(id)) {sites.remove(site);removePreview(site);continue;}
            if(block.getState() instanceof CreatureSpawner state)hidePig(state);
            preview(site,block,id);
            if(clock<entry.getValue())continue;
            sites.put(site,clock+interval);
            if(block.getState() instanceof CreatureSpawner nativeSpawner){nativeSpawner.setDelay(32767);nativeSpawner.update();}
            var at=block.getLocation().add(.5,.5,.5);
            if(!plugin.territories().edits(at) || world.getPlayers().stream().noneMatch(p->!p.isDead()
                    && (p.getGameMode()==GameMode.SURVIVAL || p.getGameMode()==GameMode.ADVENTURE)
                    && p.getLocation().distanceSquared(at)<=range*range))continue;
            if(world.getNearbyEntities(at,range,range,range).stream().filter(LivingEntity.class::isInstance)
                    .filter(e->TYPES.get(id).equals(mobs.type(e))).count()>=cap)continue;
            for(int attempt=0;attempt<12;attempt++) {
                var random=java.util.concurrent.ThreadLocalRandom.current();
                var target=at.clone().add(random.nextInt(-4,5),random.nextInt(-1,2),random.nextInt(-4,5));
                var floor=target.getBlock().getRelative(0,-1,0);
                boolean clear=true;
                // The largest shop creature is a Bantha. Require a full 3×3×3 free volume for all types.
                for(int x=-1;x<=1;x++)for(int y=0;y<3;y++)for(int z=-1;z<=1;z++) {
                    var b=target.getBlock().getRelative(x,y,z);
                    if(!b.isPassable() || b.isLiquid() || !plugin.territories().edits(b.getLocation())
                            || !world.getWorldBorder().isInside(b.getLocation()))clear=false;
                }
                if(!clear || !floor.getType().isSolid() || floor.getType()==Material.MAGMA_BLOCK
                        || !world.getNearbyEntities(target,1.5,1.5,1.5).stream().noneMatch(LivingEntity.class::isInstance))continue;
                EntityType base=switch(id){case "massiff_spawner"->EntityType.ZOMBIE;case "jawa_spawner"->EntityType.PIG;default->EntityType.HORSE;};
                var mob=plugin.territories().spawnSpawner(base,target,()->mobs.spawn(TYPES.get(id),target,1));
                if(mob!=null && mob.isValid() && !mob.isDead() && mob instanceof Mob nativeMob)nativeMob.setRemoveWhenFarAway(true);
                break;
            }
        }
    }
    @Override public void close() {task.cancel();rotation.cancel();previews.values().forEach(Preview::close);previews.clear();sites.clear();HandlerList.unregisterAll(this);}
}
