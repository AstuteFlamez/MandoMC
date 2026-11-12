package net.mandomc.gameplay.jawa;

import java.io.File;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.commands.RootCommand;
import net.mandomc.gameplay.credits.CreditLedger;
import net.mandomc.gameplay.ui.MenuItems;
import net.mandomc.integrations.mobs.JawaMobs;
import net.mandomc.integrations.models.ModelFacing;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Encounter placement, neutral group policy and NPC shops. Mythic owns rig, locomotion and the blaster attack pose. */
public final class JawaTraders implements Listener, AutoCloseable {
    private final MandoPlugin plugin;
    private final YamlConfiguration config;
    private final JawaSettings settings;
    private final JawaMobs vendor;
    private final ModelFacing facing;
    private final net.mandomc.integrations.models.GrayJawaModels grayModels;
    private final JawaSellInventory buyer;
    private final JawaCategoryGroups categoryGroups;
    private final Set<String> pendingGroups=new HashSet<>(), failedGroups=new HashSet<>();
    private final Map<UUID,Mob> combatants=new HashMap<>();
    private final JawaAttackClock attackClock;
    private final JawaIonization ionization;
    private final net.mandomc.integrations.weapons.IonizationHits ionizationHits;
    public JawaIonization ionization(){return ionization;}
    private final BukkitTask attackTask;
    private final NamespacedKey group, born, npc, paused, categoryKey;
    private final Map<String, UUID> anchors=new HashMap<>();
    private record Conversation(Mob entity, boolean hadAI, Set<UUID> viewers) { }
    private final Map<UUID,Conversation> conversations=new HashMap<>();
    private final Random random=new Random();
    private final BukkitTask population, idle;
    private boolean healthy=true;
    private boolean placing;
    private boolean reconcileScheduled;
    private int clock;
    private static final class Menu implements InventoryHolder {
        final UUID viewer, jawa; final boolean buying; final Inventory inventory;
        final Map<Integer,JawaSettings.Offer> offers=new HashMap<>();
        Menu(Player player,Entity entity,boolean buying,Component title) {
            viewer=player.getUniqueId();jawa=entity.getUniqueId();this.buying=buying;
            inventory=Bukkit.createInventory(this,36,title);MenuItems.fill(inventory);
        }
        @Override public Inventory getInventory(){return inventory;}
    }
    public JawaTraders(MandoPlugin plugin,YamlConfiguration config,RootCommand root) throws Exception {
        this.plugin=plugin;this.config=config;
        try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(plugin.getResource("mobs/jawas.yml")),java.nio.charset.StandardCharsets.UTF_8)) {
            config.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        settings=JawaSettings.read(config);
        vendor=new JawaMobs();facing=new ModelFacing();
        grayModels=new net.mandomc.integrations.models.GrayJawaModels(plugin.getLogger()::info);
        categoryGroups=new JawaCategoryGroups(plugin.getDataFolder().toPath().resolve("data/jawa-category-groups.yml"));
        attackClock=new JawaAttackClock(JawaAttackClock.Settings.read(Objects.requireNonNull(config.getConfigurationSection("combat-timing"))),random);
        group=new NamespacedKey(plugin,"jawa_group_v1");born=new NamespacedKey(plugin,"jawa_born_v1");npc=new NamespacedKey(plugin,"jawa_npc_v1");paused=new NamespacedKey(plugin,"jawa_conversation_ai_v1");categoryKey=new NamespacedKey(plugin,"jawa_category_v1");
        for(String key:List.of("brown-title","gray-title","buy-price","sell-price","buy-hint","sell-hint","balance","too-far","angry","missing-items","inventory-full","placed","usage"))
            if(!config.isString("text."+key))throw new IllegalArgumentException("mobs/jawas.yml: missing text."+key);
        for(var offer:settings.supplies()) item(offer);
        for(var offer:settings.purchases()) item(offer);
        ionization=new JawaIonization(plugin,Objects.requireNonNull(config.getConfigurationSection("ionization")));
        ionizationHits=new net.mandomc.integrations.weapons.IonizationHits(plugin,ionization);
        buyer=new JawaSellInventory(plugin,config,settings,this::canTrade);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(var world:Bukkit.getWorlds()) removeOrphans(world.getEntities());
        root.register("jawa","mandomc.admin.status",this::command,(s,a)->a.length==1?List.of("gray","brown","remove","status","models"):List.of());
        population=Bukkit.getScheduler().runTaskTimer(plugin,this::population,100,settings.interval()*20L);
        idle=Bukkit.getScheduler().runTaskTimer(plugin,this::idle,100,5);
        attackTask=Bukkit.getScheduler().runTaskTimer(plugin,this::combatTick,100,1);
        Bukkit.getScheduler().runTaskLater(plugin,this::reconcileGroups,2);
    }
    public JawaSettings settings(){return settings;}
    private Component text(String key,String... values) {
        var tags=new ArrayList<net.kyori.adventure.text.minimessage.tag.resolver.TagResolver>();
        for(int i=0;i<values.length;i+=2)tags.add(Placeholder.unparsed(values[i],values[i+1]));
        return MiniMessage.miniMessage().deserialize(Objects.requireNonNull(config.getString("text."+key),key),
                tags.toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new)).decoration(TextDecoration.ITALIC,false);
    }
    private void send(Player player,String key){player.sendMessage(text(key));}
    public boolean brown(Entity entity){return entity!=null && vendor.access().type(entity).equals("JawaBrown");}
    public boolean gray(Entity entity){return entity!=null && vendor.access().type(entity).equals("JawaGray");}
    private ItemStack item(JawaSettings.Offer offer) {
        ItemStack result;
        if(offer.item().startsWith("minecraft:")) {
            Material material=Material.matchMaterial(offer.item());
            if(material==null || !material.isItem() || material.isAir())throw new IllegalArgumentException("Unknown Jawa item: "+offer.item());
            result=new ItemStack(material);
        }else result=plugin.items().create(offer.item());
        if(result==null || offer.amount()>result.getMaxStackSize())throw new IllegalArgumentException("Invalid Jawa bundle: "+offer.id());
        result.setAmount(offer.amount());return result;
    }
    /** The durable group ledger reserves categories even for members in unloaded chunks. */
    public JawaSettings.Category category(LivingEntity entity) {
        String groupId=entity.getPersistentDataContainer().get(group,PersistentDataType.STRING);
        String id=groupId==null?entity.getPersistentDataContainer().get(categoryKey,PersistentDataType.STRING):categoryGroups.category(groupId,entity.getUniqueId());
        var selected=id==null?null:settings.category(id);
        if(selected==null && groupId!=null){ensureGroup(groupId);return null;}
        if(selected==null)selected=settings.category(entity.getUniqueId().getMostSignificantBits()^entity.getUniqueId().getLeastSignificantBits());
        entity.getPersistentDataContainer().set(categoryKey,PersistentDataType.STRING,selected.id());return selected;
    }
    private List<LivingEntity> groupMembers(String id) {
        var members=new ArrayList<LivingEntity>();
        for(var world:Bukkit.getWorlds())for(var entity:world.getLivingEntities())
            if(id.equals(entity.getPersistentDataContainer().get(group,PersistentDataType.STRING)) && brown(entity))members.add(entity);
        return members;
    }
    private void ensureGroup(String id) {
        if(pendingGroups.contains(id) || failedGroups.contains(id))return;
        try {UUID.fromString(id);}catch(IllegalArgumentException invalid){failedGroups.add(id);plugin.getLogger().warning("Invalid saved Jawa group ID; group shops are unavailable: "+id);return;}
        var observed=new HashMap<UUID,String>();for(var entity:groupMembers(id))observed.put(entity.getUniqueId(),entity.getPersistentDataContainer().get(categoryKey,PersistentDataType.STRING));
        if(observed.isEmpty())return;
        if(observed.keySet().stream().allMatch(member->settings.category(Objects.toString(categoryGroups.category(id,member),""))!=null)) {
            for(var entity:groupMembers(id))entity.getPersistentDataContainer().set(categoryKey,PersistentDataType.STRING,categoryGroups.category(id,entity.getUniqueId()));return;
        }
        pendingGroups.add(id);
        plugin.credits().auctionTask(()->{categoryGroups.assign(id,observed,settings.categories().stream().map(JawaSettings.Category::id).toList());return null;},(ignored,error)->{
            pendingGroups.remove(id);
            if(error!=null){failedGroups.add(id);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Jawa category assignment rejected; duplicate/excess group shops remain unavailable: "+id,error);return;}
            for(var entity:groupMembers(id)) {
                String assigned=categoryGroups.category(id,entity.getUniqueId());
                if(assigned==null){ensureGroup(id);continue;}
                String previous=entity.getPersistentDataContainer().get(categoryKey,PersistentDataType.STRING);
                entity.getPersistentDataContainer().set(categoryKey,PersistentDataType.STRING,assigned);
                if(!Objects.equals(previous,assigned))endConversation(entity.getUniqueId());
            }
        });
    }
    private void reconcileGroups() {
        var groups=new HashSet<String>();
        for(var world:Bukkit.getWorlds())for(var entity:world.getLivingEntities())if(brown(entity)) {
            if(entity instanceof Mob mob)combatants.put(entity.getUniqueId(),mob);
            String id=entity.getPersistentDataContainer().get(group,PersistentDataType.STRING);if(id!=null)groups.add(id);
        }
        groups.forEach(this::ensureGroup);
    }
    private void combatTick() {
        long now=Bukkit.getCurrentTick();
        for(var entry:List.copyOf(combatants.entrySet())) {
            var mob=entry.getValue();var target=vendor.target(mob);
            if(!mob.isValid() || mob.isDead()){combatants.remove(entry.getKey());attackClock.disengage(entry.getKey());continue;}
            if(target==null || !target.isValid() || target.isDead() || target.getWorld()!=mob.getWorld()
                    || target.getLocation().distanceSquared(mob.getLocation())>32*32
                    || target instanceof Player p && (p.getGameMode()==GameMode.CREATIVE || p.getGameMode()==GameMode.SPECTATOR)
                    || !plugin.territories().combat(mob.getLocation()) || !plugin.territories().combat(target.getLocation())) {
                attackClock.disengage(entry.getKey());continue;
            }
            if(mob.hasLineOfSight(target) && attackClock.ready(entry.getKey(),target.getUniqueId(),now))vendor.attack(mob,target);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void spawned(CreatureSpawnEvent event) {
        if(event.getEntityType()!=EntityType.PIG)return;
        var entity=event.getEntity();Bukkit.getScheduler().runTask(plugin,()->{if(entity.isValid() && brown(entity) && entity instanceof Mob mob)combatants.put(entity.getUniqueId(),mob);});
    }
    public void open(Player player,LivingEntity entity) {
        boolean buying=brown(entity);
        if(!buying && !gray(entity))return;
        if(!buying){buyer.open(player,entity);return;}
        if(!canTrade(player,entity))return;
        var category=category(entity);
        if(category==null){String id=entity.getPersistentDataContainer().get(group,PersistentDataType.STRING);send(player,failedGroups.contains(id)?"category-unavailable":"category-loading");return;}
        var menu=new Menu(player,entity,true,text("brown-title","category",category.name()));
        var offers=category.offers();
        for(int i=0;i<offers.size();i++) {
            var offer=offers.get(i);var preview=item(offer);var meta=preview.getItemMeta();
            var lore=new ArrayList<Component>(meta.hasLore()?meta.lore():List.of());lore.add(Component.empty());
            lore.add(text(buying?"buy-price":"sell-price","amount",String.valueOf(offer.amount()),"price",CreditLedger.format(offer.price())));
            lore.add(text(buying?"buy-hint":"sell-hint"));meta.lore(lore);preview.setItemMeta(meta);
            menu.inventory.setItem(i,preview);menu.offers.put(i,offer);
        }
        var info=new ItemStack(Material.BOOK);var infoMeta=info.getItemMeta();
        infoMeta.displayName(text("category-title","category",category.name()));
        infoMeta.lore(List.of(text("category-info","category",category.name())));info.setItemMeta(infoMeta);menu.inventory.setItem(30,info);
        var balance=new ItemStack(Material.GOLD_NUGGET);var meta=balance.getItemMeta();
        meta.displayName(text("balance","balance",CreditLedger.format(plugin.credits().ledger().balance(player.getUniqueId()))));balance.setItemMeta(meta);
        menu.inventory.setItem(31,balance);player.openInventory(menu.inventory);
        if(buying && player.getOpenInventory().getTopInventory().getHolder()==menu && entity instanceof Mob mob) {
            var conversation=conversations.computeIfAbsent(entity.getUniqueId(),id->{
                mob.getPersistentDataContainer().set(paused,PersistentDataType.BYTE,(byte)(mob.hasAI()?1:0));
                return new Conversation(mob,mob.hasAI(),new HashSet<>());
            });
            conversation.viewers().add(player.getUniqueId());
            mob.getPathfinder().stopPathfinding();mob.setAI(false);mob.setVelocity(new org.bukkit.util.Vector());
            facing.face(mob,player.getEyeLocation(),15);
        }
    }
    private void resume(UUID id) {
        var conversation=conversations.remove(id);
        if(conversation!=null) {
            if(conversation.entity().isValid() && !conversation.entity().isDead())conversation.entity().setAI(conversation.hadAI());
            conversation.entity().getPersistentDataContainer().remove(paused);
        }
    }
    private void endConversation(UUID id) {
        var conversation=conversations.get(id);
        if(conversation!=null)for(UUID viewer:List.copyOf(conversation.viewers())) {
            var player=Bukkit.getPlayer(viewer);
            if(player!=null && player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu && menu.jawa.equals(id))player.closeInventory();
        }
        resume(id);
    }
    @EventHandler public void closed(InventoryCloseEvent event) {
        if(!(event.getInventory().getHolder() instanceof Menu menu))return;
        var conversation=conversations.get(menu.jawa);
        if(conversation!=null){conversation.viewers().remove(event.getPlayer().getUniqueId());if(conversation.viewers().isEmpty())resume(menu.jawa);}
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        for(UUID id:List.copyOf(conversations.keySet())) {
            var conversation=conversations.get(id);conversation.viewers().remove(event.getPlayer().getUniqueId());
            if(conversation.viewers().isEmpty())resume(id);
        }
    }
    @EventHandler public void died(EntityDeathEvent event){endConversation(event.getEntity().getUniqueId());}
    private boolean canTrade(Player player,LivingEntity entity) {
        if(!entity.isValid() || entity.isDead() || player.getWorld()!=entity.getWorld() || player.getLocation().distanceSquared(entity.getLocation())>36) {
            send(player,"too-far");return false;
        }
        if(plugin.combat().tagged(player) || entity instanceof Mob mob && (brown(entity)?vendor.target(mob):mob.getTarget())!=null){send(player,"angry");return false;}
        return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEntityEvent event) {
        if(!brown(event.getRightClicked()) && !gray(event.getRightClicked()))return;
        event.setCancelled(true);
        if(event.getHand()==EquipmentSlot.HAND && event.getRightClicked() instanceof LivingEntity entity)
            Bukkit.getScheduler().runTask(plugin,()->open(event.getPlayer(),entity));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player) || !menu.viewer.equals(player.getUniqueId())
                || event.getRawSlot()<0 || event.getRawSlot()>=36 || event.getClick()!=ClickType.LEFT)return;
        var offer=menu.offers.get(event.getRawSlot());if(offer==null)return;
        if(!(Bukkit.getEntity(menu.jawa) instanceof LivingEntity jawa) || (menu.buying?!brown(jawa):!gray(jawa)) || !canTrade(player,jawa))return;
        var stack=item(offer);
        if(!menu.buying && !player.getInventory().containsAtLeast(stack,stack.getAmount())){send(player,"missing-items");return;}
        if(menu.buying && !fits(player,stack)){send(player,"inventory-full");return;}
        // Auction escrow handles serialization, save failures, disconnects and claims after restart.
        plugin.credits().auctions().supply(player,stack,offer.price(),menu.buying,()->{});
        player.closeInventory();
    }
    private boolean fits(Player player,ItemStack stack) {
        int room=0;
        for(var slot:player.getInventory().getStorageContents()) {
            if(slot==null || slot.isEmpty())room+=stack.getMaxStackSize();
            else if(slot.isSimilar(stack))room+=Math.max(0,slot.getMaxStackSize()-slot.getAmount());
        }
        return room>=stack.getAmount();
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event){if(gray(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void protect(EntityDamageByEntityEvent event) {
        Entity source=event.getDamager();if(source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)source=shooter;
        if((brown(source)||brown(event.getEntity())) && (!plugin.territories().combat(source.getLocation()) || !plugin.territories().combat(event.getEntity().getLocation())))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void retaliate(EntityDamageByEntityEvent event) {
        if(!brown(event.getEntity()) || event.getFinalDamage()<=0)return;
        Entity source=event.getDamager();if(source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)source=shooter;
        if(!(source instanceof LivingEntity attacker) || brown(attacker) || gray(attacker))return;
        String id=event.getEntity().getPersistentDataContainer().get(group,PersistentDataType.STRING);
        var members=new ArrayList<LivingEntity>();members.add((LivingEntity)event.getEntity());
        if(id!=null)for(var entity:event.getEntity().getNearbyEntities(32,16,32))
            if(entity instanceof LivingEntity living && brown(entity) && id.equals(entity.getPersistentDataContainer().get(group,PersistentDataType.STRING)))members.add(living);
        for(var member:members){endConversation(member.getUniqueId());vendor.retaliate(member,attacker);if(member instanceof Mob mob)mob.setTarget(attacker);}
        for(var player:event.getEntity().getWorld().getPlayers())
            if(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu && members.stream().anyMatch(m->m.getUniqueId().equals(menu.jawa)))player.closeInventory();
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void targeting(EntityTargetLivingEntityEvent event) {
        if(gray(event.getEntity()) || brown(event.getEntity()) && event.getTarget()!=null &&
                (!plugin.territories().combat(event.getEntity().getLocation()) || !plugin.territories().combat(event.getTarget().getLocation())
                || brown(event.getTarget()) || gray(event.getTarget())))event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breed(EntityBreedEvent event){if(brown(event.getMother())||gray(event.getMother())||brown(event.getFather())||gray(event.getFather()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void mount(EntityMountEvent event){if(brown(event.getMount())||gray(event.getMount()))event.setCancelled(true);}
    private List<LivingEntity> browns(World world){return world.getLivingEntities().stream().filter(this::brown).toList();}
    public List<LivingEntity> spawnGroup(List<Location> sites) {
        if(sites.isEmpty() || sites.size()>settings.categories().size())throw new IllegalArgumentException("Jawa group must have 1–"+settings.categories().size()+" members for distinct categories");
        String id=UUID.randomUUID().toString();var created=new ArrayList<LivingEntity>();
        var pool=new ArrayList<>(settings.categories());Collections.shuffle(pool,random);
        try {
            for(var site:sites) {
                var entity=vendor.access().spawn("JawaBrown",site,1);
                if(entity==null || !entity.isValid())throw new IllegalStateException("Jawa spawn denied");
                created.add(entity);entity.getPersistentDataContainer().set(group,PersistentDataType.STRING,id);
                entity.getPersistentDataContainer().set(born,PersistentDataType.LONG,System.currentTimeMillis());
                entity.getPersistentDataContainer().set(categoryKey,PersistentDataType.STRING,pool.get(created.size()-1).id());
                if(entity instanceof Mob mob)combatants.put(entity.getUniqueId(),mob);
            }
            ensureGroup(id);return List.copyOf(created);
        }catch(RuntimeException error){created.forEach(vendor.access()::remove);throw error;}
    }
    private Location surface(World world,int x,int z) {
        if(!world.isChunkLoaded(Math.floorDiv(x,16),Math.floorDiv(z,16)))return null;
        var floor=world.getHighestBlockAt(x,z,HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if(!Set.of(Material.SAND,Material.SANDSTONE,Material.SMOOTH_SANDSTONE,Material.RED_SAND,Material.TERRACOTTA).contains(floor.getType()))return null;
        var site=floor.getLocation().add(.5,1,.5);
        if(!site.getBlock().isPassable() || !site.clone().add(0,1,0).getBlock().isPassable()
                || !world.getWorldBorder().isInside(site) || !plugin.territories().edits(site) || !plugin.territories().spawning(site))return null;
        if(world.getPlayers().stream().filter(p->p.getGameMode()!=GameMode.SPECTATOR).anyMatch(p->p.getLocation().distanceSquared(site)<settings.minDistance()*settings.minDistance()))return null;
        return site;
    }
    private void population() {
        if(!healthy)return;
        try {
            for(String key:settings.worlds()) {
                var world=Bukkit.getWorld(NamespacedKey.fromString(key));if(world==null)continue;
                var current=browns(world);
                for(var entity:current) {
                    long created=entity.getPersistentDataContainer().getOrDefault(born,PersistentDataType.LONG,System.currentTimeMillis());
                    if(System.currentTimeMillis()-created>settings.lifetime()*1000L && world.getPlayers().stream().noneMatch(p->p.getLocation().distanceSquared(entity.getLocation())<48*48))vendor.access().remove(entity);
                }
                current=browns(world);int size=random.nextInt(settings.minimum(),settings.maximum()+1);
                if(current.size()+size>settings.cap())continue;
                var players=world.getPlayers().stream().filter(p->p.getGameMode()!=GameMode.SPECTATOR).toList();if(players.isEmpty())continue;
                var player=players.get(random.nextInt(players.size()));
                for(int attempt=0;attempt<16;attempt++) {
                    double angle=random.nextDouble()*Math.PI*2,distance=random.nextDouble(settings.minDistance(),settings.maxDistance());
                    int x=(int)Math.floor(player.getX()+Math.cos(angle)*distance),z=(int)Math.floor(player.getZ()+Math.sin(angle)*distance);
                    var center=surface(world,x,z);if(center==null)continue;
                    if(current.stream().anyMatch(e->e.getLocation().distanceSquared(center)<32*32))continue;
                    var sites=new ArrayList<Location>();sites.add(center);
                    for(int j=1;j<size;j++){var site=surface(world,x+j*2,z+(j%2)*2);if(site!=null)sites.add(site);}
                    if(sites.size()!=size)continue;
                    spawnGroup(sites);break;
                }
            }
        }catch(RuntimeException error){healthy=false;plugin.getLogger().log(java.util.logging.Level.SEVERE,"Jawa spawning stopped; repair configuration before restarting",error);}
    }
    /** A removed/replaced distant NPC may only become available when its chunk loads again. */
    private void removeOrphans(List<Entity> entities) {
        for(var entity:entities) {
            Byte previous=entity.getPersistentDataContainer().get(paused,PersistentDataType.BYTE);
            if(previous!=null && entity instanceof LivingEntity living) {
                living.setAI(previous==1);entity.getPersistentDataContainer().remove(paused);
            }
            String id=entity.getPersistentDataContainer().get(npc,PersistentDataType.STRING);
            if(id!=null && !config.isConfigurationSection("npcs."+id))vendor.access().remove(entity);
        }
    }
    @EventHandler public void loaded(org.bukkit.event.world.EntitiesLoadEvent event){
        removeOrphans(event.getEntities());
        if(!reconcileScheduled){reconcileScheduled=true;Bukkit.getScheduler().runTask(plugin,()->{reconcileScheduled=false;reconcileGroups();});}
    }
    @EventHandler public void unloaded(org.bukkit.event.world.EntitiesUnloadEvent event){for(var entity:event.getEntities()){combatants.remove(entity.getUniqueId());attackClock.disengage(entity.getUniqueId());grayModels.unloaded(entity.getUniqueId());}}
    private Location anchor(String id) {
        String p="npcs."+id;var key=NamespacedKey.fromString(config.getString(p+".world",""));var world=key==null?null:Bukkit.getWorld(key);
        return world==null?null:new Location(world,config.getDouble(p+".x"),config.getDouble(p+".y"),config.getDouble(p+".z"),(float)config.getDouble(p+".yaw"),(float)config.getDouble(p+".pitch"));
    }
    private void idle() {
        clock++;
        for(UUID id:List.copyOf(conversations.keySet())) {
            var conversation=conversations.get(id);
            if(!conversation.entity().isValid() || conversation.entity().isDead()){endConversation(id);continue;}
            conversation.viewers().removeIf(viewer->{var player=Bukkit.getPlayer(viewer);return player==null || !player.isOnline()
                    || !(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu) || !menu.jawa.equals(id);});
            if(conversation.viewers().isEmpty()){resume(id);continue;}
            var mob=conversation.entity();mob.getPathfinder().stopPathfinding();mob.setAI(false);mob.setVelocity(new org.bukkit.util.Vector());
            var player=Bukkit.getPlayer(conversation.viewers().iterator().next());
            if(player!=null && player.getWorld()==mob.getWorld())facing.face(mob,player.getEyeLocation(),10);
        }
        var entries=config.getConfigurationSection("npcs");if(entries==null)return;
        for(String id:entries.getKeys(false)) {
            var home=anchor(id);if(home==null || !home.getWorld().isChunkLoaded(home.getBlockX()>>4,home.getBlockZ()>>4))continue;
            LivingEntity entity=anchors.containsKey(id)&&Bukkit.getEntity(anchors.get(id)) instanceof LivingEntity found?found:null;
            if(entity==null || !entity.isValid()) {
                // Reuse persisted NPCs after restart instead of creating another copy.
                entity=home.getWorld().getNearbyEntities(home,8,8,8).stream().filter(e->id.equals(e.getPersistentDataContainer().get(npc,PersistentDataType.STRING)))
                        .filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).findFirst().orElse(null);
                if(entity==null){entity=plugin.territories().spawnNpc(EntityType.PIG,home,()->vendor.access().spawn("JawaGray",home,1));if(entity==null || !entity.isValid())continue;}
                entity.getPersistentDataContainer().set(npc,PersistentDataType.STRING,id);vendor.access().persist(entity);anchors.put(id,entity.getUniqueId());
            }
            grayModels.ensure(entity,clock*5L);
            entity.setInvulnerable(true);entity.setCollidable(false);entity.setSilent(true);entity.setRemoveWhenFarAway(false);
            if(entity instanceof Mob mob){mob.setAI(false);mob.setTarget(null);}
            if(entity.getWorld()!=home.getWorld() || entity.getLocation().distanceSquared(home)>.01)entity.teleport(home);
            var viewer=home.getWorld().getPlayers().stream().filter(p->p.getGameMode()!=GameMode.SPECTATOR && p.getLocation().distanceSquared(home)<25)
                    .min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(home))).orElse(null);
            var direction=home.clone();direction.setYaw(home.getYaw()+(float)(25*Math.sin(clock*.06)));
            var look=viewer==null?home.clone().add(direction.getDirection().multiply(4)).add(0,.8,0):viewer.getEyeLocation();
            facing.face(entity,look,6);
        }
        for(var player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu menu) {
            var source=Bukkit.getEntity(menu.jawa);
            if(!(source instanceof LivingEntity living) || !source.isValid() || source.getWorld()!=player.getWorld()
                    || source.getLocation().distanceSquared(player.getLocation())>36 || plugin.combat().tagged(player)
                    || living instanceof Mob mob && (brown(living)?vendor.target(mob):mob.getTarget())!=null)player.closeInventory();
        }
    }
    private void command(org.bukkit.command.CommandSender sender,String[] args) {
        if(args.length==1 && args[0].equals("status")){sender.sendMessage(Component.text("Jawa spawning: "+healthy+"; gray NPCs: "+anchors.size()));return;}
        if(args.length==1 && args[0].equals("models")) {
            var entries=config.getConfigurationSection("npcs");
            if(entries!=null)for(String id:entries.getKeys(false)) {
                var home=anchor(id);if(home==null){sender.sendMessage(Component.text(id+" world unavailable"));continue;}
                if(!home.getWorld().isChunkLoaded(home.getBlockX()>>4,home.getBlockZ()>>4)){sender.sendMessage(Component.text(id+" unloaded"));continue;}
                var found=home.getWorld().getNearbyEntities(home,8,8,8).stream().filter(e->id.equals(e.getPersistentDataContainer().get(npc,PersistentDataType.STRING))).filter(LivingEntity.class::isInstance).map(LivingEntity.class::cast).toList();
                sender.sendMessage(Component.text(id+" count="+found.size()+" "+found.stream().map(grayModels::audit).toList()));
            }
            return;
        }
        if(!(sender instanceof Player player))return;
        if(args.length==1 && args[0].equals("brown")){
            var site=player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(3));
            spawnGroup(List.of(site,site.clone().add(2,0,0)));return;
        }
        if(args.length==2 && args[1].matches("[a-z0-9_]{1,32}") && Set.of("gray","remove").contains(args[0])) {
            if(placing){send(player,"saving");return;}
            String id=args[1];
            var candidate=YamlConfiguration.loadConfiguration(new java.io.StringReader(config.saveToString()));
            String p="npcs."+id;candidate.set(p,null);
            if(args[0].equals("gray")) {
                var l=player.getLocation();candidate.set(p+".world",l.getWorld().getKey().toString());
                candidate.set(p+".x",l.getX());candidate.set(p+".y",l.getY());candidate.set(p+".z",l.getZ());candidate.set(p+".yaw",l.getYaw());candidate.set(p+".pitch",l.getPitch());
            }
            String snapshot=candidate.saveToString();placing=true;
            plugin.credits().auctionTask(()->{net.mandomc.config.AtomicData.write(new File(plugin.getDataFolder(),"mobs/jawas.yml").toPath(),snapshot);return null;},(r,error)->{
                placing=false;
                if(error!=null){
                    plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot save Jawa placement",error);
                    if(player.isOnline())send(player,"save-failed");return;
                }
                try {config.loadFromString(snapshot);} catch(org.bukkit.configuration.InvalidConfigurationException invalid) {
                    throw new IllegalStateException("Cannot apply saved Jawa placement",invalid);
                }
                var old=anchors.remove(id);if(old!=null && Bukkit.getEntity(old)!=null)vendor.access().remove(Bukkit.getEntity(old));
                if(player.isOnline())send(player,"placed");
            });return;
        }
        player.sendMessage(text("usage"));
    }
    @Override public void close() {
        population.cancel();idle.cancel();attackTask.cancel();grayModels.close();attackClock.clear();combatants.clear();ionizationHits.close();ionization.close();buyer.close();
        for(UUID id:List.copyOf(conversations.keySet()))endConversation(id);
        HandlerList.unregisterAll(this);
        for(var player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu)player.closeInventory();
    }
}
