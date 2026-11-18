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
    private final BukkitTask task;
    private final int interval, range, cap;
    private final ConfigurationSection messages;
    private int clock;

    public MobSpawners(MandoPlugin plugin, ConfigurationSection config) throws ReflectiveOperationException {
        this.plugin=plugin;
        interval=config.getInt("interval-seconds");range=config.getInt("activation-range");cap=config.getInt("nearby-cap");
        messages=Objects.requireNonNull(config.getConfigurationSection("messages"));
        if(config.getInt("schema-version")!=1 || interval<10 || interval>600 || range<4 || range>32 || cap<1 || cap>16)
            throw new IllegalArgumentException("mobs/spawners.yml: invalid limits");
        for(String id:TYPES.keySet())Objects.requireNonNull(plugin.items().create(id),"Missing spawner item "+id);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(var world:Bukkit.getWorlds())for(var chunk:world.getLoadedChunks())load(chunk);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,20);
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
            sites.put(Site.of(state.getBlock()),clock+interval);
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
        spawner.setSpawnedType(EntityType.PIG);spawner.setDelay(32767);spawner.update();
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
        sites.remove(Site.of(e.getBlock()));e.setDropItems(false);e.setExpToDrop(0);
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
    @EventHandler public void unloaded(ChunkUnloadEvent e) {sites.keySet().removeIf(s->s.world.equals(e.getWorld().getUID())&&s.x>>4==e.getChunk().getX()&&s.z>>4==e.getChunk().getZ());}
    private void tick() {
        clock++;
        for(var entry:List.copyOf(sites.entrySet())) {
            var site=entry.getKey();var world=Bukkit.getWorld(site.world);
            if(world==null || !world.isChunkLoaded(site.x>>4,site.z>>4)) {sites.remove(site);continue;}
            if(clock<entry.getValue())continue;
            sites.put(site,clock+interval);
            var block=world.getBlockAt(site.x,site.y,site.z);String id=type(block);
            if(!known(id)) {sites.remove(site);continue;}
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
    @Override public void close() {task.cancel();sites.clear();HandlerList.unregisterAll(this);}
}
