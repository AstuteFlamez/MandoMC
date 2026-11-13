package net.mandomc.gameplay.ilum;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.crates.CrateService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** The recovered Ilum course, with native NPCs and restart-safe temporary inventories. */
public final class ParkourService implements Listener, AutoCloseable {
    private static final double CONVERSATION_DISTANCE_SQUARED = 100;
    private static final NamespacedKey RUN = new NamespacedKey("mandomc","ilum_run");
    private static final NamespacedKey CHECKPOINT = new NamespacedKey("mandomc","ilum_checkpoint");
    private static final NamespacedKey YAW = new NamespacedKey("mandomc","ilum_checkpoint_yaw");
    private static final NamespacedKey RANKED = new NamespacedKey("mandomc","ilum_ranked");
    private static final NamespacedKey BEST = new NamespacedKey("mandomc","ilum_best_millis");
    private static final NamespacedKey LAST_REWARD = new NamespacedKey("mandomc","ilum_last_key");
    private static final NamespacedKey CONTROL = new NamespacedKey("mandomc","parkour_item");
    private static final NamespacedKey PREVIOUS_TEAM = new NamespacedKey("mandomc","ilum_team");
    private static final NamespacedKey PREVIOUS_COLLISION = new NamespacedKey("mandomc","ilum_collision");
    private static final NamespacedKey INSPECT_MODE = new NamespacedKey("mandomc","ilum_inspect_mode");
    private record Best(String name,long millis) { }
    private final MandoPlugin plugin;
    private final CrateService crates;
    private final YamlConfiguration config;
    private final ConfigurationSection language;
    private final World world;
    private final Location start;
    private final Map<Integer,Location> checkpoints=new java.util.LinkedHashMap<>();
    private final Map<String,Integer> plates=new HashMap<>();
    private final Map<UUID,Map<?,?>> npcs=new HashMap<>();
    private final List<Entity> displays=new ArrayList<>();
    private final Set<org.bukkit.Chunk> displayChunks=new HashSet<>();
    private final Set<UUID> internal=new HashSet<>();
    private final Set<UUID> inspecting=new HashSet<>();
    private final Set<UUID> conversations=new HashSet<>();
    private final Set<UUID> retries=new HashSet<>();
    private final Map<UUID,Long> clocks=new HashMap<>();
    private final Map<UUID,Best> bests=new HashMap<>();
    private final java.util.concurrent.ExecutorService writer=java.util.concurrent.Executors.newSingleThreadExecutor(r->{
        var thread=new Thread(r,"Ilum leaderboard writer");thread.setDaemon(true);return thread;});
    private final List<BukkitTask> dialogues=new ArrayList<>();
    private final BukkitTask ticker;
    private final long cooldown;
    private TextDisplay leaderboard;
    private boolean closed;

    public ParkourService(MandoPlugin plugin,CrateService crates,YamlConfiguration config) {
        this.plugin=plugin;this.crates=crates;this.config=config;
        if (config.getInt("schema-version")!=1) throw new IllegalArgumentException("parkour/ilum.yml: schema-version 1 required");
        language=java.util.Objects.requireNonNull(config.getConfigurationSection("messages"));
        world=java.util.Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.fromString(config.getString("world"))),"Original Ilum dimension missing");
        start=location(config.getList("start"));
        cooldown=Math.multiplyExact(config.getLong("reward-cooldown-hours",24),3_600_000);
        if (cooldown<3_600_000 || !crates.definitions().containsKey(config.getString("reward-crate"))) throw new IllegalArgumentException("Invalid Ilum reward/cooldown");
        for (var cp:config.getMapList("checkpoints")) {
            int number=((Number)cp.get("number")).intValue();
            var pos=(List<?>)cp.get("plate");var at=location(pos);
            if (number!=checkpoints.size()+1 || at.getBlock().getType()!=Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
                throw new IllegalArgumentException("Missing or unordered original checkpoint "+number);
            }
            plates.put(point(at.getBlock()),number);checkpoints.put(number,at.add(.5,1,.5));
        }
        if (checkpoints.size()!=4) throw new IllegalArgumentException("Original Ilum requires four checkpoints");
        world.setPVP(false);world.setDifficulty(org.bukkit.Difficulty.PEACEFUL);world.setTime(6000);world.setStorm(false);
        for (var rule:List.of(org.bukkit.GameRules.SPAWN_MOBS,org.bukkit.GameRules.ADVANCE_TIME,
                org.bukkit.GameRules.ADVANCE_WEATHER,org.bukkit.GameRules.MOB_GRIEFING,org.bukkit.GameRules.TNT_EXPLODES,
                org.bukkit.GameRules.COMMAND_BLOCKS_WORK)) world.setGameRule(rule,false);
        world.setGameRule(org.bukkit.GameRules.RANDOM_TICK_SPEED,0);
        world.setGameRule(org.bukkit.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER,0);
        world.setGameRule(org.bukkit.GameRules.KEEP_INVENTORY,true);
        readLeaderboard();
        Bukkit.getPluginManager().registerEvents(this,plugin);
        var command=java.util.Objects.requireNonNull(plugin.getCommand("parkour"));
        command.setExecutor((sender,cmd,label,args)->{
            if (!(sender instanceof Player player)) { sender.sendMessage(message("usage"));return true; }
            String action=args.length==0 ? "start" : args[0].toLowerCase(java.util.Locale.ROOT);
            switch(action) {
                case "start" -> request(player);
                case "leave" -> leave(player,true,false);
                case "checkpoint" -> {if(active(player))retry(player);else send(player,"outside");}
                case "restart" -> restart(player);
                case "status" -> send(player,"status","checkpoint",Integer.toString(active(player)?run(player).checkpoint():0),"time",format(elapsed(player)));
                case "top" -> showTop(player);
                case "inspect" -> inspect(player);
                default -> send(player,"usage");
            }
            return true;
        });
        command.setTabCompleter((sender,cmd,label,args)->args.length==1?List.of("start","leave","checkpoint","restart","status","top","inspect").stream().filter(s->s.startsWith(args[0])).toList():List.of());
        java.util.Objects.requireNonNull(plugin.getCommand("parkourfinish")).setExecutor((sender,cmd,label,args)->{
            if (!sender.hasPermission("mandomc.parkour.admin")) {sender.sendMessage(message("denied"));return true;}
            if (args.length!=1) {sender.sendMessage(message("finish-usage"));return true;}
            var player=Bukkit.getPlayerExact(args[0]);
            if (player==null) sender.sendMessage(message("offline"));else finish(player);
            return true;
        });
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,5,5);
        Bukkit.getScheduler().runTaskLater(plugin,this::spawnDisplays,20);
        for (var player:Bukkit.getOnlinePlayers()) if (ParkourInventory.stored(player)) recover(player);
    }

    public boolean active(Player player) { return ParkourInventory.stored(player); }
    public World world() { return world; }
    public Location start() { return start.clone(); }
    public Map<Integer,Location> checkpoints() { return Map.copyOf(checkpoints); }
    public ParkourRun run(Player player) {
        var data=player.getPersistentDataContainer();
        return new ParkourRun(UUID.fromString(data.get(RUN,PersistentDataType.STRING)),
                data.getOrDefault(CHECKPOINT,PersistentDataType.INTEGER,0),
                data.getOrDefault(RANKED,PersistentDataType.BYTE,(byte)1)==1);
    }
    private void saveRun(Player player,ParkourRun run) {
        var data=player.getPersistentDataContainer();
        data.set(RUN,PersistentDataType.STRING,run.id().toString());
        data.set(CHECKPOINT,PersistentDataType.INTEGER,run.checkpoint());
        data.set(RANKED,PersistentDataType.BYTE,(byte)(run.ranked()?1:0));
    }
    private Location location(List<?> values) {
        if (values==null || values.size()<3) throw new IllegalArgumentException("Ilum location missing");
        return new Location(world,((Number)values.get(0)).doubleValue(),((Number)values.get(1)).doubleValue(),
                ((Number)values.get(2)).doubleValue(),values.size()>3?((Number)values.get(3)).floatValue():0,
                values.size()>4?((Number)values.get(4)).floatValue():0);
    }
    private static String point(Block block) { return block.getX()+","+block.getY()+","+block.getZ(); }
    private Component message(String key,String... args) {
        String value=java.util.Objects.requireNonNull(language.getString(key),"Missing Ilum message "+key);
        for (int i=0;i<args.length;i+=2)value=value.replace("{"+args[i]+"}",args[i+1]);
        return MiniMessage.miniMessage().deserialize(value).decoration(TextDecoration.ITALIC,false);
    }
    private void send(Player player,String key,String... args) { player.sendMessage(message(key,args)); }
    private boolean teleport(Player player,Location location) {
        internal.add(player.getUniqueId());
        try { player.setFallDistance(0);return player.teleport(location); }
        finally { internal.remove(player.getUniqueId()); }
    }
    private Location exit() { return plugin.worlds().spawn(config.getString("return-world")); }
    public void request(Player player) {
        if (active(player)) { send(player,"welcome");return; }
        if (plugin.combat().tagged(player) || player.isInsideVehicle()) { send(player,"unsafe");return; }
        if (player.getPersistentDataContainer().has(CrateService.PENDING)) { send(player,"crate-pending");return; }
        if (player.getItemOnCursor()!=null&&!player.getItemOnCursor().isEmpty()) { send(player,"unsafe");return; }
        if (player.getWorld().equals(world)) {restoreInspectMode(player);begin(player);}
        else plugin.travel().request(player,()->start.clone());
    }
    public void begin(Player player) {
        if (active(player)) return;
        if (player.getPersistentDataContainer().has(CrateService.PENDING)) { teleport(player,exit());send(player,"crate-pending");return; }
        player.closeInventory();
        if (player.getItemOnCursor()!=null&&!player.getItemOnCursor().isEmpty()) { teleport(player,exit());send(player,"unsafe");return; }
        // Finish native item flights before the original inventory is placed in escrow.
        if(plugin.sabers()!=null)plugin.sabers().finish(player.getUniqueId());
        saveRun(player,new ParkourRun(UUID.randomUUID(),0,true));
        ParkourInventory.capture(player);
        player.getInventory().clear();player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(false);player.setWalkSpeed(.2f);
        player.setFoodLevel(20);player.setSaturation(20);player.setFireTicks(0);player.setHealth(player.getMaxHealth());
        for (var effect:List.copyOf(player.getActivePotionEffects()))player.removePotionEffect(effect.getType());
        player.getInventory().setItem(0,control(Material.BARRIER,"leave","<red>Leave Parkour"));
        player.getInventory().setItem(1,control(Material.COMPASS,"checkpoint","<yellow>Last Checkpoint"));
        player.getInventory().setItem(2,control(Material.RED_BED,"restart","<green>Restart Course"));
        player.getInventory().setHeldItemSlot(1);
        collision(player,true);
        clocks.put(player.getUniqueId(),System.nanoTime());
        player.saveData();
        if (!teleport(player,start)) { leave(player,true,true);return; }
        send(player,"welcome");
    }
    private ItemStack control(Material material,String id,String name) {
        var item=new ItemStack(material);var meta=item.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(name).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(Component.empty(),Component.text("Right-click to use").color(net.kyori.adventure.text.format.NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false)));
        meta.getPersistentDataContainer().set(CONTROL,PersistentDataType.STRING,id);item.setItemMeta(meta);return item;
    }
    private long elapsed(Player player) { return Math.max(0,(System.nanoTime()-clocks.getOrDefault(player.getUniqueId(),System.nanoTime()))/1_000_000); }
    public static String format(long millis) { return String.format(java.util.Locale.ROOT,"%d:%02d.%03d",millis/60000,(millis/1000)%60,millis%1000); }
    private Location checkpoint(Player player) {
        Location at=checkpoints.get(run(player).checkpoint());
        if (at==null)return start.clone();
        at=at.clone();at.setYaw(player.getPersistentDataContainer().getOrDefault(YAW,PersistentDataType.FLOAT,0f));return at;
    }
    public void retry(Player player) {
        if (!active(player))return;
        teleport(player,checkpoint(player));player.setFireTicks(0);player.setFallDistance(0);
        player.playSound(player.getLocation(),Sound.ENTITY_ENDERMAN_TELEPORT,.7f,1.1f);
    }
    public void restart(Player player) {
        if (!active(player)) {send(player,"outside");return;}
        conversations.remove(player.getUniqueId());
        saveRun(player,new ParkourRun(UUID.randomUUID(),0,true));
        player.getPersistentDataContainer().remove(YAW);
        clocks.put(player.getUniqueId(),System.nanoTime());player.saveData();teleport(player,start);send(player,"restart");
    }
    private void clearRun(Player player) {
        for (var key:List.of(RUN,CHECKPOINT,RANKED,YAW))player.getPersistentDataContainer().remove(key);
        clocks.remove(player.getUniqueId());conversations.remove(player.getUniqueId());retries.remove(player.getUniqueId());
    }
    public void leave(Player player,boolean returnToSpawn,boolean recovering) {
        if (active(player)) {
            ParkourInventory.restore(player);clearRun(player);collision(player,false);player.saveData();
            if (returnToSpawn)teleport(player,exit());
            send(player,recovering?"recovered":"left");
            crates.claim(player);
        } else if (player.getWorld().equals(world)) {
            restoreInspectMode(player);collision(player,false);if(returnToSpawn)teleport(player,exit());
        } else send(player,"outside");
    }
    private void recover(Player player) {
        try { leave(player,true,true); }
        catch(RuntimeException error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE,"Ilum inventory recovery failed for "+player.getUniqueId()+"; stored data retained",error);
            player.kick(Component.text("Your Ilum inventory needs recovery. Please contact an administrator."));
        }
    }
    public void finish(Player player) {
        if (!active(player) || !player.getWorld().equals(world) || !run(player).canFinish()) {send(player,"need-checkpoints");return;}
        Location finish=null;
        for (var npc:config.getMapList("npcs"))if(Boolean.TRUE.equals(npc.get("finish")))finish=location((List<?>)npc.get("location"));
        if (finish==null || player.getLocation().distanceSquared(finish)>CONVERSATION_DISTANCE_SQUARED) {
            send(player,"npc-interrupted");return;
        }
        var run=run(player);long millis=Math.max(1,elapsed(player));long now=System.currentTimeMillis();
        var data=player.getPersistentDataContainer();
        long last=data.getOrDefault(LAST_REWARD,PersistentDataType.LONG,0L);
        boolean reward=run.ranked()&&ParkourRun.rewardReady(last,now,cooldown);
        if (reward) {
            crates.enqueueKey(player,config.getString("reward-crate"),1);
            data.set(LAST_REWARD,PersistentDataType.LONG,now);
        }
        long best=data.getOrDefault(BEST,PersistentDataType.LONG,Long.MAX_VALUE);
        if(run.ranked()&&millis<best){best=millis;data.set(BEST,PersistentDataType.LONG,best);}
        // Restore gear, finish the run and award the key in the SAME atomic player-data commit.
        ParkourInventory.restore(player);clearRun(player);collision(player,false);player.saveData();
        if(run.ranked()){bests.put(player.getUniqueId(),new Best(player.getName(),best));saveLeaderboard();updateLeaderboard();}
        player.showTitle(Title.title(Component.text("Ilum complete"),Component.text(format(millis))));
        send(player,"finished","time",format(millis),"best",best==Long.MAX_VALUE?"--":format(best));
        if(reward)send(player,"key-earned");
        else if(!run.ranked())send(player,"practice");
        else send(player,"reward-cooldown","hours",Long.toString(Math.max(1,(last+cooldown-now+3_599_999)/3_600_000)));
        teleport(player,plugin.warps().destination("spawn"));crates.claim(player);
        plugin.getLogger().info("Ilum completed: "+player.getUniqueId()+" ranked="+run.ranked()+" key="+reward);
    }

    private void collision(Player player,boolean inside) {
        var board=Bukkit.getScoreboardManager().getMainScoreboard();
        var data=player.getPersistentDataContainer();var team=board.getTeam("ilum_parkour");
        if(inside){
            if(team==null)team=board.registerNewTeam("ilum_parkour");
            team.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE,org.bukkit.scoreboard.Team.OptionStatus.NEVER);
            if(!data.has(PREVIOUS_TEAM)){
                var original=board.getEntryTeam(player.getName());
                data.set(PREVIOUS_TEAM,PersistentDataType.STRING,original==null?"":original.getName());
                data.set(PREVIOUS_COLLISION,PersistentDataType.BYTE,(byte)(player.isCollidable()?1:0));
            }
            team.addEntry(player.getName());player.setCollidable(false);
        }else if(data.has(PREVIOUS_TEAM)){
            if(team!=null)team.removeEntry(player.getName());
            String original=data.get(PREVIOUS_TEAM,PersistentDataType.STRING);
            if(original!=null&&!original.isEmpty()&&board.getTeam(original)!=null)board.getTeam(original).addEntry(player.getName());
            player.setCollidable(data.getOrDefault(PREVIOUS_COLLISION,PersistentDataType.BYTE,(byte)1)==1);
            data.remove(PREVIOUS_TEAM);data.remove(PREVIOUS_COLLISION);
        }
    }
    private void restoreInspectMode(Player player) {
        String mode=player.getPersistentDataContainer().get(INSPECT_MODE,PersistentDataType.STRING);
        if(mode!=null){player.setGameMode(GameMode.valueOf(mode));player.getPersistentDataContainer().remove(INSPECT_MODE);}
    }
    private void inspect(Player player) {
        if(!player.hasPermission("mandomc.parkour.admin")){send(player,"denied");return;}
        if(active(player))leave(player,true,false);
        var data=player.getPersistentDataContainer();
        if(!data.has(INSPECT_MODE))data.set(INSPECT_MODE,PersistentDataType.STRING,player.getGameMode().name());
        inspecting.add(player.getUniqueId());
        if(!teleport(player,start)){inspecting.remove(player.getUniqueId());restoreInspectMode(player);return;}
        inspecting.remove(player.getUniqueId());
        player.setGameMode(GameMode.CREATIVE);send(player,"inspect");
    }

    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) {
        var player=event.getPlayer();
        if(player.getWorld().equals(world)){
            if(inspecting.remove(player.getUniqueId()))return;
            if(config.getBoolean("auto-enter-on-world-change",true))begin(player);
        }else if(event.getFrom().equals(world)){
            if(active(player))leave(player,false,false);
            restoreInspectMode(player);collision(player,false);
        }
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        var player=event.getPlayer();
        // Recover a rejected generated review's mode marker, without moving unrelated players.
        var oldMode=new NamespacedKey("ilumreview","previous-mode");
        var previous=player.getPersistentDataContainer().get(oldMode,PersistentDataType.STRING);
        if(previous!=null){
            player.setGameMode(GameMode.valueOf(previous));player.getPersistentDataContainer().remove(oldMode);
            if(player.getPersistentDataContainer().has(new NamespacedKey("ilumreview","room")))
                Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())teleport(player,exit());});
        }
        if(active(player)){Bukkit.getScheduler().runTask(plugin,()->recover(player));return;}
        restoreInspectMode(player);
        if(player.getWorld().equals(world))Bukkit.getScheduler().runTask(plugin,()->teleport(player,exit()));
        var best=bests.get(player.getUniqueId());
        long nativeBest=player.getPersistentDataContainer().getOrDefault(BEST,PersistentDataType.LONG,Long.MAX_VALUE);
        if(best!=null&&nativeBest==Long.MAX_VALUE)player.getPersistentDataContainer().set(BEST,PersistentDataType.LONG,best.millis());
        else if(nativeBest!=Long.MAX_VALUE){bests.put(player.getUniqueId(),new Best(player.getName(),nativeBest));saveLeaderboard();updateLeaderboard();}
    }
    @EventHandler(priority=EventPriority.LOWEST) public void quit(PlayerQuitEvent event) {
        if(active(event.getPlayer()))leave(event.getPlayer(),true,false);
        else if(event.getPlayer().getWorld().equals(world)){restoreInspectMode(event.getPlayer());teleport(event.getPlayer(),exit());}
        conversations.remove(event.getPlayer().getUniqueId());inspecting.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleportEvent(PlayerTeleportEvent event) {
        var player=event.getPlayer();
        if(!active(player)||internal.contains(player.getUniqueId())||!event.getTo().getWorld().equals(world))return;
        if(player.isOp()){saveRun(player,run(player).practice());player.saveData();send(player,"practice");}
        else{event.setCancelled(true);send(player,"commands");}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void move(PlayerMoveEvent event) {
        var player=event.getPlayer();
        if(event instanceof PlayerTeleportEvent||!active(player)||!event.hasChangedPosition())return;
        if(event.getTo().getY()<config.getDouble("fall-y",-90)||player.getFireTicks()>0){
            event.setTo(checkpoint(player));player.setFireTicks(0);player.setFallDistance(0);return;
        }
        Block block=event.getTo().getBlock();
        Integer number=plates.get(point(block));
        if(number==null)number=plates.get(point(block.getRelative(0,-1,0)));
        if(number==null||number<=run(player).checkpoint())return;
        var before=run(player);var after=before.reach(number);
        if(after.equals(before))return;
        saveRun(player,after);
        player.getPersistentDataContainer().set(YAW,PersistentDataType.FLOAT,Math.round(player.getLocation().getYaw()/90)*90f);
        player.saveData();
        player.showTitle(Title.title(message("checkpoint","number",number.toString()),Component.empty()));
        player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.7f,1.2f);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void damage(EntityDamageEvent event) {
        if(!event.getEntity().getWorld().equals(world))return;
        event.setCancelled(true);
        if(!(event.getEntity() instanceof Player player)||!active(player))return;
        switch(event.getCause()) {
            case FALL,VOID,FIRE,FIRE_TICK,LAVA,DROWNING,SUFFOCATION,HOT_FLOOR,FREEZE -> {
                if(retries.add(player.getUniqueId()))Bukkit.getScheduler().runTask(plugin,()->{
                    retries.remove(player.getUniqueId());if(player.isOnline()&&active(player))retry(player);
                });
            }
            default -> { }
        }
    }
    @EventHandler public void death(PlayerDeathEvent event) {if(active(event.getEntity())){event.setKeepInventory(true);event.getDrops().clear();event.setKeepLevel(true);}}
    @EventHandler public void respawn(PlayerRespawnEvent event) {if(active(event.getPlayer()))event.setRespawnLocation(checkpoint(event.getPlayer()));}
    @EventHandler(priority=EventPriority.LOWEST) public void controls(PlayerInteractEvent event) {
        var player=event.getPlayer();if(!active(player))return;
        if(event.getAction()==Action.PHYSICAL)return; // Original pressure-plate mechanisms remain functional.
        event.setUseItemInHand(Event.Result.DENY);
        if(event.getClickedBlock()!=null&&(event.getClickedBlock().getType().name().endsWith("_BUTTON")
                ||event.getClickedBlock().getType()==Material.LEVER||event.getClickedBlock().getType().name().endsWith("_DOOR")))return;
        var item=event.getItem();String control=item==null?null:item.getPersistentDataContainer().get(CONTROL,PersistentDataType.STRING);
        if(control==null)return;
        event.setCancelled(true);
        if(event.getHand()!=EquipmentSlot.HAND||(event.getAction()!=Action.RIGHT_CLICK_AIR&&event.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
        switch(control){case "leave"->leave(player,true,false);case "checkpoint"->retry(player);case "restart"->restart(player);default->{}}
    }
    @EventHandler public void inventory(InventoryClickEvent event) {
        if(event.getWhoClicked() instanceof Player player&&active(player)
                ||event.getView().getTopInventory().getHolder() instanceof Player owner&&active(owner))event.setCancelled(true);
    }
    @EventHandler public void drag(InventoryDragEvent event) {if(event.getWhoClicked() instanceof Player p&&active(p))event.setCancelled(true);}
    @EventHandler public void drop(PlayerDropItemEvent event) {if(active(event.getPlayer()))event.setCancelled(true);}
    @EventHandler public void pickup(EntityPickupItemEvent event) {if(event.getEntity() instanceof Player p&&active(p))event.setCancelled(true);}
    @EventHandler public void swap(PlayerSwapHandItemsEvent event) {if(active(event.getPlayer()))event.setCancelled(true);}
    @EventHandler public void food(FoodLevelChangeEvent event) {if(event.getEntity() instanceof Player p&&active(p)){event.setCancelled(true);event.setFoodLevel(20);}}
    @EventHandler public void fly(PlayerToggleFlightEvent event) {if(active(event.getPlayer())){event.setCancelled(true);event.getPlayer().setFlying(false);event.getPlayer().setAllowFlight(false);}}
    @EventHandler(priority=EventPriority.LOWEST) public void command(PlayerCommandPreprocessEvent event) {
        if(!active(event.getPlayer())||event.getPlayer().isOp())return;
        String command=event.getMessage().split(" ",2)[0].substring(1).toLowerCase(java.util.Locale.ROOT);
        if(command.contains(":"))command=command.substring(command.indexOf(':')+1);
        if(!List.of("parkour","ilum","spawn","msg","r","help").contains(command)){event.setCancelled(true);send(event.getPlayer(),"commands");}
    }

    private void spawnDisplays() {
        if(closed)return;
        for(var hint:config.getMapList("hints")){
            var lines=(List<?>)hint.get("lines");
            var at=location((List<?>)hint.get("location"));keepDisplayChunk(at);
            var text=world.spawn(at,TextDisplay.class,e->{
                e.text(MiniMessage.miniMessage().deserialize(String.join("\n",lines.stream().map(Object::toString).toList())));
                e.setBillboard(Display.Billboard.CENTER);e.setShadowed(true);e.setPersistent(false);e.setViewRange(.6f);
            });displays.add(text);
        }
        keepDisplayChunk(location(config.getList("leaderboard")));
        leaderboard=world.spawn(location(config.getList("leaderboard")),TextDisplay.class,e->{
            e.setBillboard(Display.Billboard.CENTER);e.setShadowed(true);e.setPersistent(false);e.setViewRange(.7f);
        });displays.add(leaderboard);updateLeaderboard();
        for(var data:config.getMapList("npcs")){
            var at=location((List<?>)data.get("location"));keepDisplayChunk(at);
            var territories=java.util.Objects.requireNonNull(Bukkit.getServicesManager().load(net.mandomc.gameplay.territory.TerritoryService.class));
            var npc=territories.spawnNpc(org.bukkit.entity.EntityType.MANNEQUIN,at,()->world.spawn(at,Mannequin.class,e->{
                var profile=Bukkit.createProfile(UUID.nameUUIDFromBytes(data.get("id").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures",data.get("texture").toString(),data.get("signature").toString()));
                e.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(profile));
                e.setDescription(Component.empty());e.setImmovable(true);e.setGravity(false);e.setInvulnerable(true);
                e.setSilent(true);e.setPersistent(false);e.setCollidable(false);
                e.customName(Component.text(data.get("name").toString(),net.kyori.adventure.text.format.NamedTextColor.AQUA));e.setCustomNameVisible(true);
            }));npcs.put(npc.getUniqueId(),data);displays.add(npc);
        }
        plugin.getLogger().info("Original Ilum NPCs ready: Ahsoka teleport, Obi-Wan finish, nine recovered hints.");
    }
    private void keepDisplayChunk(Location location) {
        var chunk=location.getChunk();if(chunk.addPluginChunkTicket(plugin))displayChunks.add(chunk);
    }
    @EventHandler public void npc(PlayerInteractEntityEvent event) {
        var data=npcs.get(event.getRightClicked().getUniqueId());
        if(data==null)return;
        event.setCancelled(true);
        if(event.getHand()!=EquipmentSlot.HAND)return;
        var player=event.getPlayer();
        if(!active(player)||run(player).checkpoint()<((Number)data.get("requires-checkpoint")).intValue()){send(player,"need-checkpoints");return;}
        if(player.getLocation().distanceSquared(event.getRightClicked().getLocation())>36)return;
        if(!conversations.add(player.getUniqueId())){send(player,"busy");return;}
        UUID runId=run(player).id();List<?> lines=(List<?>)data.get("dialogue");
        for(int i=0;i<Math.max(1,lines.size());i++){
            int line=i;
            dialogues.add(Bukkit.getScheduler().runTaskLater(plugin,()->{
                if(!player.isOnline()||!active(player)||!run(player).id().equals(runId)||!conversations.contains(player.getUniqueId()))return;
                Location at=location((List<?>)data.get("location"));
                if(!player.getWorld().equals(world)||player.getLocation().distanceSquared(at)>CONVERSATION_DISTANCE_SQUARED){
                    conversations.remove(player.getUniqueId());send(player,"npc-interrupted");return;
                }
                if(line<lines.size())send(player,"npc-line","npc",data.get("name").toString(),"line",lines.get(line).toString());
                if(line<lines.size()-1)return;
                conversations.remove(player.getUniqueId());
                if(Boolean.TRUE.equals(data.get("finish")))finish(player);else teleport(player,location((List<?>)data.get("teleport")));
            },i*50L));
        }
    }
    private void tick() {
        for(var player:world.getPlayers())if(player.getWorld().equals(world)&&active(player)){
            long best=player.getPersistentDataContainer().getOrDefault(BEST,PersistentDataType.LONG,0L);
            player.sendActionBar(message("timer","time",format(elapsed(player)),"best",best==0?"--":format(best),"checkpoint",Integer.toString(run(player).checkpoint())));
            player.setFoodLevel(20);player.setSaturation(20);
        }
        for(var entity:List.copyOf(displays))if(entity instanceof Mannequin npc&&npc.isValid()){
            Player closest=null;double distance=64;
            for(var player:world.getPlayers()){if(!player.getWorld().equals(world))continue;double d=player.getLocation().distanceSquared(npc.getLocation());if(d<distance){distance=d;closest=player;}}
            if(closest!=null){var delta=closest.getEyeLocation().toVector().subtract(npc.getEyeLocation().toVector());
                float yaw=(float)Math.toDegrees(Math.atan2(-delta.getX(),delta.getZ()));
                float pitch=(float)-Math.toDegrees(Math.atan2(delta.getY(),Math.hypot(delta.getX(),delta.getZ())));npc.setRotation(yaw,pitch);}
        }
        dialogues.removeIf(task->task.isCancelled()||(!Bukkit.getScheduler().isQueued(task.getTaskId())
                &&!Bukkit.getScheduler().isCurrentlyRunning(task.getTaskId())));
    }
    private void readLeaderboard() {
        File file=new File(plugin.getDataFolder(),"data/ilum-leaderboard.yml");
        if(!file.exists())file=new File(plugin.getDataFolder(),"data/legacy-ilum-times.json");
        if(!file.exists())return;
        for(var entry:YamlConfiguration.loadConfiguration(file).getMapList("players")){
            UUID id=UUID.fromString(entry.get("uuid").toString());
            long millis=entry.get("best-millis") instanceof Number n?n.longValue():Math.round(((Number)entry.get("best_time")).doubleValue()*1000);
            if(millis>0)bests.put(id,new Best(entry.get("name").toString(),millis));
        }
    }
    private List<Map.Entry<UUID,Best>> top() {return bests.entrySet().stream().sorted(java.util.Comparator.comparingLong(e->e.getValue().millis())).limit(10).toList();}
    private void showTop(Player player) {
        send(player,"top-title");int rank=0;
        for(var entry:top())send(player,"top-row","rank",Integer.toString(++rank),"name",entry.getValue().name(),"time",format(entry.getValue().millis()));
        if(rank==0)send(player,"empty-top");
    }
    private void updateLeaderboard() {
        if(leaderboard==null||!leaderboard.isValid())return;
        Component text=message("leaderboard-title");int rank=0;
        for(var entry:top())text=text.append(Component.newline()).append(message("top-row","rank",Integer.toString(++rank),"name",entry.getValue().name(),"time",format(entry.getValue().millis())));
        if(rank==0)text=text.append(Component.newline()).append(message("empty-top"));
        leaderboard.text(text.append(Component.newline()).append(message("leaderboard-footer")));
    }
    private void saveLeaderboard() {
        var yaml=new YamlConfiguration();var players=new ArrayList<Map<String,Object>>();
        bests.forEach((uuid,best)->players.add(Map.of("uuid",uuid.toString(),"name",best.name(),"best-millis",best.millis())));
        yaml.set("players",players);String snapshot=yaml.saveToString();
        var target=plugin.getDataFolder().toPath().resolve("data/ilum-leaderboard.yml");
        writer.submit(()->{try{
            Files.createDirectories(target.getParent());var temp=target.resolveSibling("ilum-leaderboard.yml.tmp");
            Files.writeString(temp,snapshot);Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }catch(Exception error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Ilum leaderboard index save failed; player bests remain in native player data",error);}});
    }
    @Override public void close() {
        closed=true;ticker.cancel();
        for(var player:List.copyOf(Bukkit.getOnlinePlayers()))if(active(player))leave(player,true,false);
        for(var task:dialogues)task.cancel();dialogues.clear();
        for(var entity:displays)entity.remove();
        for(var chunk:displayChunks)chunk.removePluginChunkTicket(plugin);displayChunks.clear();
        displays.clear();npcs.clear();HandlerList.unregisterAll(this);
        saveLeaderboard();writer.shutdown();
        try{if(!writer.awaitTermination(3,java.util.concurrent.TimeUnit.SECONDS))plugin.getLogger().warning("Ilum leaderboard index is still flushing");}
        catch(InterruptedException error){Thread.currentThread().interrupt();}
    }
}
