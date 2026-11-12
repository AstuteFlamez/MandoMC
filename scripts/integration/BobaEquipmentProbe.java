package net.mandomc.verification;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.mandomc.gameplay.boba.*;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Native Paper items, GLIDER/equipment, real CraftPlayer attribution and swept projectile collisions.
 * Runs only in a disposable empty runtime. No network client is connected; no visual/feel claim. */
public final class BobaEquipmentProbe extends JavaPlugin implements Listener {
    private BobaEquipment equipment;
    private JetpackEquipmentMenu menu;
    private ItemCatalog items;
    private Player player;
    private World world;
    private final List<Entity> entities=new ArrayList<>();
    private final AtomicLong clock=new AtomicLong(1_000_000_000L);
    private int checks,damageEvents;
    private boolean denyDamage,denyIgnition,deniedZone;
    private Cow target;
    private final List<Integer> damageAt=new ArrayList<>();
    private final List<ItemStack> deathDrops=new ArrayList<>();
    @Override public void onEnable() { later(40,this::begin); }
    private void begin() throws Exception {
        world=Bukkit.getWorlds().getFirst();
        world.getChunkAt(0,0).addPluginChunkTicket(this);
        world.getChunkAt(0,0).load();
        world.setPVP(true);world.setGameRule(GameRule.DO_MOB_SPAWNING,false);world.setTime(18000);
        for(String name:List.of("items.yml","items/boba-equipment.yml","equipment/boba.yml","messages.yml","jabba-mobs.yml"))
            saveResource(name,true);
        var presentation=YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(),"items.yml"));
        presentation.set("external-items",null);presentation.save(new java.io.File(getDataFolder(),"items.yml"));
        items=new ItemCatalog(getDataFolder().toPath());items.reload();
        check(items.ids().equals(Set.of(BobaEquipment.JETPACK,BobaEquipment.FLAMETHROWER)),"both independent persistent catalog IDs load");
        lootHook();
        player=nativePlayer(new Location(world,0.5,90,0.5,0,0));player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().setItemInMainHand(items.create(BobaEquipment.FLAMETHROWER));
        player.getInventory().setItemInOffHand(items.create(BobaEquipment.JETPACK));
        var armor=new ItemStack(Material.NETHERITE_CHESTPLATE);var meta=armor.getItemMeta();
        ((org.bukkit.inventory.meta.Damageable)meta).setDamage(37);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION,4,true);armor.setItemMeta(meta);
        player.getInventory().setChestplate(armor);
        var settings=BobaEquipmentSettings.read(YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(),"equipment/boba.yml")));
        var language=YamlConfiguration.loadConfiguration(new java.io.File(getDataFolder(),"messages.yml"));
        equipment=new BobaEquipment(this,items,settings,language.getConfigurationSection("boba-equipment"),l->!deniedZone,clock::get);
        Bukkit.getPluginManager().registerEvents(this,this);
        menu=new JetpackEquipmentMenu(this,items,equipment,language.getConfigurationSection("boba-equipment"));
        menuOwnership(armor);
        nativeInventory();
        fuelAndGlider(armor);
        later(3,()->{attachment();flameStart();});
    }
    private void lootHook() throws Exception {
        var mythic=Bukkit.getPluginManager().getPlugin("MythicMobs");
        if(mythic==null)throw new IllegalStateException("Probe requires MythicMobs in its disposable runtime");
        var rule=new net.mandomc.gameplay.mobs.JabbaDropsSettings.Drop(BobaEquipment.JETPACK,0,"",0,0,
                List.of(new net.mandomc.gameplay.mobs.JabbaDropsSettings.Extra(BobaEquipment.JETPACK,1),
                new net.mandomc.gameplay.mobs.JabbaDropsSettings.Extra(BobaEquipment.FLAMETHROWER,1)));
        new net.mandomc.integrations.mobs.JabbaDrops(this,items,
                new net.mandomc.gameplay.mobs.JabbaDropsSettings(Map.of("BobaFett",rule)));
        var api=mythic.getClass().getMethod("getAPIHelper").invoke(mythic);
        var mob=(Entity)api.getClass().getMethod("spawnMythicMob",String.class,Location.class).invoke(api,"BobaFett",new Location(world,8,90,8));
        entities.add(mob);
        var active=api.getClass().getMethod("getMythicMobInstance",Entity.class).invoke(api,mob);
        var type=Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent",true,mythic.getClass().getClassLoader());
        var loot=new ArrayList<ItemStack>();loot.add(new ItemStack(Material.STONE));
        var event=(Event)type.getConstructors()[0].newInstance(active,null,loot);
        Bukkit.getPluginManager().callEvent(event);
        check(loot.size()==3&&loot.getFirst().getType()==Material.STONE,"actual Mythic death hook appends equipment without replacing existing loot");
        check(loot.stream().filter(i->BobaEquipment.JETPACK.equals(items.identify(i))).count()==1,"actual Mythic hook creates exactly one independent jetpack");
        check(loot.stream().filter(i->BobaEquipment.FLAMETHROWER.equals(items.identify(i))).count()==1,"actual Mythic hook creates exactly one independent flamethrower");
    }
    private void menuOwnership(ItemStack armor) {
        menu.open(player);
        var view=player.getOpenInventory();
        check(view.getTopInventory().getItem(0).getType()==Material.GRAY_STAINED_GLASS_PANE
                &&view.getTopInventory().getItem(0).getItemMeta().isHideTooltip(),"equipment menu uses protected shared gray filler");
        var hotbar=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                4,org.bukkit.event.inventory.ClickType.NUMBER_KEY,org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP,0);
        Bukkit.getPluginManager().callEvent(hotbar);
        check(hotbar.isCancelled()&&equipment.equipped(player),"menu hotbar swap is cancelled without equipment transfer");
        var click=new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                4,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);
        check(click.isCancelled()&&!equipment.equipped(player),"native left-click menu event safely removes offhand jetpack");
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.inventory.InventoryClickEvent(view,
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,4,org.bukkit.event.inventory.ClickType.LEFT,
                org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
        check(!equipment.equipped(player),"repeated menu click cannot duplicate or reverse a pending transfer");
        player.closeInventory();
        check(!equipment.equipped(player)&&player.getInventory().getChestplate().equals(armor),"menu removal preserves chestplate");
        check(menu.change(player)&&equipment.equipped(player),"equipment menu equips one native owned jetpack");
        var snapshot=player.getInventory().getStorageContents();
        for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        check(!menu.change(player)&&equipment.equipped(player),"full inventory rejects removal without item loss");
        player.getInventory().setStorageContents(snapshot);
    }
    private void nativeInventory() {
        var inv=player.getInventory();inv.setItem(0,items.create(BobaEquipment.FLAMETHROWER));
        inv.setItem(1,new ItemStack(Material.COAL,2));inv.setItem(8,new ItemStack(Material.COAL,3));
        inv.setItem(9,new ItemStack(Material.COAL,64));inv.setItem(2,new ItemStack(Material.CHARCOAL,64));
        check(BobaInventory.coal(inv)==5,"coal counts across hotbar stacks only, excluding charcoal and storage");
        for(int i=0;i<5;i++)check(BobaInventory.coalUnit(inv),"native hotbar coal unit consumed "+i);
        check(!BobaInventory.coalUnit(inv),"storage coal and charcoal cannot power the jetpack");
        check(inv.getItem(9).getAmount()==64&&inv.getItem(2).getAmount()==64,"excluded stacks unchanged");
        check(!equipment.toggle(player),"no fuel rejects native activation");
        inv.setItem(1,new ItemStack(Material.COAL,2));
        inv.setItem(8,new ItemStack(Material.COAL,5));
    }
    private void tick() throws Exception {var m=BobaEquipment.class.getDeclaredMethod("tick");m.setAccessible(true);m.invoke(equipment);}
    private void fuelAndGlider(ItemStack armor) throws Exception {
        check(equipment.toggle(player),"first activation purchases fuel");
        check(BobaInventory.coal(player.getInventory())==6,"first coal is consumed immediately");
        clock.addAndGet(250_000_000);check(equipment.toggle(player),"voluntary shutdown succeeds");
        check(Math.abs(equipment.seconds(player)-6.75)<.00001,"partial paid interval retained on shutdown");
        player.setVelocity(new org.bukkit.util.Vector(0,-.1,0));
        for(int i=0;i<10;i++) {check(equipment.toggle(player),"rapid resume "+i);clock.addAndGet(10_000_000);equipment.toggle(player);}
        check(BobaInventory.coal(player.getInventory())==6,"rapid toggles buy no free second");
        check(Math.abs(player.getVelocity().getY()+.1)<.00001,"midair toggles cannot mint repeated upward launch impulses");
        check(Math.abs(equipment.seconds(player)-6.65)<.00001,"all powered fractions charged exactly");
        var jet=player.getInventory().getItemInOffHand();
        check(jet.hasData(io.papermc.paper.datacomponent.DataComponentTypes.GLIDER),"offhand item has actual GLIDER component");
        check(jet.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE).slot()==EquipmentSlot.OFF_HAND,"native equip slot matches offhand");
        var handle=player.getClass().getMethod("getHandle").invoke(player);
        var living=Class.forName("net.minecraft.world.entity.LivingEntity");
        var slotClass=Class.forName("net.minecraft.world.entity.EquipmentSlot");
        var nativeItem=Class.forName("net.minecraft.world.item.ItemStack");
        var off=slotClass.getField("OFFHAND").get(null);
        Object n=handle.getClass().getMethod("getItemBySlot",slotClass).invoke(handle,off);
        check((boolean)living.getMethod("canGlideUsing",nativeItem,slotClass).invoke(null,n,off),"actual server canGlideUsing accepts jetpack offhand");
        player.setGliding(true);check(player.isGliding(),"native glide pose can start without chest replacement");
        check(player.getInventory().getChestplate().equals(armor),"chest armor, enchantments and existing durability preserved");
        equipment.toggle(player);clock.addAndGet(6_650_000_000L);tick();
        check(!equipment.powered(player),"zero fuel stops thrust at the exact paid boundary");
        check(player.isGliding(),"depletion leaves native unpowered glide");
        check(BobaInventory.coal(player.getInventory())==0,"all seven coal fund exactly seven powered seconds");
        check(player.getInventory().getItem(9).getAmount()==64,"depletion never falls back to inventory coal");
        player.setGliding(false);
        player.getInventory().setItem(1,new ItemStack(Material.COAL,6));
        check(equipment.toggle(player),"hotbar refueling resumes thrust");
        equipment.toggle(player);
    }
    private void attachment() throws Exception {
        player.setGliding(false);player.setSneaking(false);player.setRotation(0,0);
        var display=equipment.attachment(player);getLogger().info("attachment="+display+" online="+player.isOnline()+" dead="+player.isDead()+" equipped="+equipment.equipped(player));check(display!=null&&display.isValid(),"separate attached model actually exists");
        check(!display.isPersistent()&&display.getItemStack().getItemMeta().getItemModel().toString().endsWith("boba_jetpack_attached"),"attachment is ephemeral and uses extracted geometry");
        player.setBodyYaw(90);tick();var first=display.getTransformation().getLeftRotation();
        player.setRotation(-90,60);player.setBodyYaw(90);tick();
        check(display.getYaw()==0&&display.getPitch()==0,"entity transform does not reapply head rotation over torso matrix");
        check(first.equals(display.getTransformation().getLeftRotation(),.0001f),"standing attachment follows torso rather than independently turned head");
        var standing=display.getLocation();player.setSneaking(true);tick();
        check(display.getY()<standing.getY(),"sneaking attachment follows lowered torso");player.setSneaking(false);
        player.setGliding(true);tick();
        var top=new org.joml.Vector3f(0,1,0).rotate(display.getTransformation().getLeftRotation());
        check(Math.abs(top.y)<.9,"gliding attachment rotates with horizontal torso");
        player.setGliding(false);player.setRotation(0,0);player.setBodyYaw(0);
        Bukkit.getPluginManager().callEvent(new PlayerToggleSneakEvent(player,true));
        Bukkit.getPluginManager().callEvent(new PlayerToggleSneakEvent(player,false));
        Bukkit.getPluginManager().callEvent(new PlayerToggleSneakEvent(player,true));
        check(equipment.powered(player),"real double-sneak input events activate flight with main-hand flamethrower");
        player.setGliding(true);
    }
    private void flameStart() {
        var inv=player.getInventory();inv.setItem(3,new ItemStack(Material.FIRE_CHARGE,2));
        target=world.spawn(new Location(world,.5,90,3.5),Cow.class,c->{c.setAI(false);c.setGravity(false);});entities.add(target);
        target.setHealth(10);damageEvents=0;damageAt.clear();
        var use=new PlayerInteractEvent(player,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                inv.getItemInMainHand(),null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(use);
        check(equipment.flames().activeBursts()==1,"real main-hand use event starts native flame burst");
        var offhandUse=new PlayerInteractEvent(player,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                inv.getItemInOffHand(),null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.OFF_HAND);
        Bukkit.getPluginManager().callEvent(offhandUse);
        check(equipment.flames().activeBursts()==1&&equipment.equipped(player),"offhand use event cannot duplicate burst or steal flight equipment");
        check(inv.getItem(3).getAmount()==1,"exactly one fire charge consumed at accepted burst start");
        for(int i=0;i<20;i++)check(!equipment.flames().activate(player),"held/repeated use cannot bypass burst/cooldown "+i);
        check(inv.getItem(3).getAmount()==1,"rejected repeated use consumes no ammo");
        later(44,()->{
            check(equipment.powered(player),"flamethrower burst leaves powered jetpack control independent");
            check(damageEvents>0&&damageEvents<=4,"real flame packets produce bounded native damage intervals: "+damageEvents);
            for(int i=1;i<damageAt.size();i++)check(damageAt.get(i)-damageAt.get(i-1)>=10,"dense particles cannot multiply per-target damage");
            check(target.getFireTicks()>0||target.getHealth()<10,"accepted native damage burns target");
            check(equipment.flames().activeBursts()==0,"all fixed-duration packets expire");
            check(inv.getItem(3).getAmount()==1,"one ammo funds entire fixed burst and packet flight");
            wall();
        });
    }
    private void ready() {player.getPersistentDataContainer().set(new NamespacedKey("mandomc","flamethrower_next_use_ms"),PersistentDataType.LONG,0L);player.setCooldown(Material.BLAZE_ROD,0);}
    private void wall() {
        target.setFireTicks(0);target.setHealth(10);damageEvents=0;
        for(int x=-2;x<=2;x++)for(int y=89;y<=93;y++)world.getBlockAt(x,y,2).setType(Material.STONE,false);
        ready();check(equipment.flames().activate(player),"wall does not prevent paying for a burst");
        later(44,()->{
            check(damageEvents==0&&target.getHealth()==10&&target.getFireTicks()==0,"actual swept packets neither damage nor ignite through solid wall");
            for(int x=-2;x<=2;x++)for(int y=89;y<=93;y++)check(world.getBlockAt(x,y,2).getType()==Material.STONE,"flame never changes terrain");
            for(int x=-2;x<=2;x++)for(int y=89;y<=93;y++)world.getBlockAt(x,y,2).setType(Material.AIR,false);
            denied();
        });
    }
    private void denied() {
        ready();check(!equipment.flames().activate(player),"empty storage rejects burst");
        player.getInventory().setItem(30,new ItemStack(Material.FIRE_CHARGE,3));
        deniedZone=true;check(!equipment.flames().activate(player),"shared region policy denies firing");
        check(player.getInventory().getItem(30).getAmount()==3,"region denial consumes no ammo");deniedZone=false;
        denyDamage=true;damageEvents=0;check(equipment.flames().activate(player),"accepted burst can encounter protected target");
        later(44,()->{
            check(target.getHealth()==10&&target.getFireTicks()==0,"cancelled real damage cannot ignite or bypass protection");
            denyDamage=false;denyIgnition=true;ready();check(equipment.flames().activate(player),"new paid burst after cooldown expiry");
            later(44,()->{
                check(target.getHealth()<10&&target.getFireTicks()==0,"combust cancellation respected independently from damage");
                denyIgnition=false;armorAndFriendlyFire();cleanupCases();
            });
        });
    }
    private void armorAndFriendlyFire() throws Exception {
        Player other=nativePlayer(new Location(world,20,90,20));other.setGameMode(GameMode.SURVIVAL);
        other.setHealth(20);other.setNoDamageTicks(0);other.damage(4,player);
        double bare=20-other.getHealth();check(bare>0,"native attributed hit can damage unarmored player");
        var armor=new ItemStack(Material.NETHERITE_CHESTPLATE);armor.addEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION,4);
        other.getInventory().setChestplate(armor);other.setHealth(20);other.setNoDamageTicks(0);other.damage(4,player);
        check(20-other.getHealth()<bare,"native armor and enchantment protection remain effective");
        var board=Bukkit.getScoreboardManager().getNewScoreboard();var team=board.registerNewTeam("boba_test");
        team.setAllowFriendlyFire(false);team.addEntry(player.getName());team.addEntry(other.getName());
        player.setScoreboard(board);other.setScoreboard(board);other.setHealth(20);other.setNoDamageTicks(0);other.damage(4,player);
        check(other.getHealth()==20,"native attributed hits respect scoreboard friendly fire");
        team.unregister();player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        other.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }
    private void cleanupCases() throws Exception {
        player.getInventory().setItemInOffHand(null);tick();
        check(equipment.attachment(player)==null&&!equipment.powered(player),"unequipping removes model and thrust");
        player.getInventory().setItemInOffHand(items.create(BobaEquipment.JETPACK));equipment.toggle(player);tick();
        var first=equipment.attachment(player);
        Bukkit.getPluginManager().callEvent(new PlayerChangedWorldEvent(player,world));
        check(!first.isValid()&&!equipment.powered(player),"world-change listener removes actual attachment and thrust");
        equipment.toggle(player);first=equipment.attachment(player);
        player.getPersistentDataContainer().set(new NamespacedKey("mandomc","flamethrower_next_use_ms"),PersistentDataType.LONG,System.currentTimeMillis()+3000);
        Bukkit.getPluginManager().callEvent(new PlayerQuitEvent(player,net.kyori.adventure.text.Component.empty()));
        check(!first.isValid()&&equipment.attachment(player)==null,"logout listener removes actual attachment");
        check(!equipment.flames().activate(player),"logout retains persistent flame cooldown");
        equipment.toggle(player);first=equipment.attachment(player);
        player.setHealth(0);
        check(player.isDead()&&!first.isValid()&&!equipment.powered(player),"actual native player death removes attachment and thrust");
        check(deathDrops.stream().filter(i->BobaEquipment.JETPACK.equals(items.identify(i))).mapToInt(ItemStack::getAmount).sum()==1,"native full-loot death drops exactly one jetpack");
        check(deathDrops.stream().filter(i->BobaEquipment.FLAMETHROWER.equals(items.identify(i))).mapToInt(ItemStack::getAmount).sum()==1,"native full-loot death drops exactly one flamethrower");
        check(deathDrops.stream().anyMatch(i->i.getType()==Material.NETHERITE_CHESTPLATE&&((org.bukkit.inventory.meta.Damageable)i.getItemMeta()).getDamage()==37),"native death retains original chestplate durability in its drop");
        equipment.close();check(equipment.attachment(player)==null,"shutdown removes all attachments");
        getLogger().info("BOBA EQUIPMENT PROBE PASSED: "+checks+" assertions");cleanup();Bukkit.shutdown();
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void died(PlayerDeathEvent e) {if(e.getEntity()==player)e.getDrops().forEach(i->deathDrops.add(i.clone()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void deny(EntityDamageByEntityEvent e) { if(e.getEntity()==target&&denyDamage)e.setCancelled(true); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void ignite(EntityCombustByEntityEvent e) { if(e.getEntity()==target&&denyIgnition)e.setCancelled(true); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(EntityDamageByEntityEvent e) {
        if(e.getEntity()==target&&e.getDamager()==player&&e.getCause()!=EntityDamageEvent.DamageCause.FIRE_TICK) {
            damageEvents++;damageAt.add(Bukkit.getCurrentTick());
        }
    }
    private Player nativePlayer(Location location) throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),
                playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level=world.getClass().getMethod("getHandle").invoke(world);
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"BobaEquipTest");
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection"),
                cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie"),listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object connection=connectionClass.getConstructor(flowClass).newInstance(flowClass.getField("SERVERBOUND").get(null));
        Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Object listener=listenerClass.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);
        playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);
        var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,location.getX(),location.getY(),location.getZ());
        Object list=serverClass.getMethod("getPlayerList").invoke(server);
        for(Class<?> c=list.getClass();c!=null;c=c.getSuperclass())try {
            var map=c.getDeclaredField("playersByUUID");map.setAccessible(true);((Map<UUID,Object>)map.get(list)).put((UUID)profileClass.getMethod("id").invoke(profile),handle);break;
        }catch(NoSuchFieldException ignored) { }
        return (Player)playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
    private interface Work {void run() throws Exception;}
    private void later(int delay,Work work) {Bukkit.getScheduler().runTaskLater(this,()->{
        try {work.run();} catch(Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"BOBA EQUIPMENT PROBE FAILED",error);cleanup();Bukkit.shutdown();}
    },delay);}
    private void check(boolean ok,String reason) {if(!ok)throw new AssertionError(reason);checks++;getLogger().info("PASS "+reason);}
    private void cleanup() {
        if(menu!=null)menu.close();if(equipment!=null)equipment.close();entities.forEach(Entity::remove);
        HandlerList.unregisterAll((Listener)this);Bukkit.getScheduler().cancelTasks(this);
    }
    @Override public void onDisable() {cleanup();}
}
