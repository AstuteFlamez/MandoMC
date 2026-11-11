package net.mandomc.verification;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.credits.CreditLedger;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import com.ticxo.modelengine.api.ModelEngineAPI;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent;

/** Isolated native actors; no changes to real players, world blocks or production balances. */
public final class JawaProbe extends JavaPlugin {
    private final List<Player> actors=new ArrayList<>();private final Map<UUID,Object> handles=new HashMap<>();
    private final List<LivingEntity> entities=new ArrayList<>();private final List<Chunk> chunks=new ArrayList<>();
    private List<Object> worldPlayers;private Map<UUID,Object> online;private MandoPlugin mando;private World world;
    private Player buyer;private LivingEntity gray;private List<LivingEntity> group;private Cow attacker;private int checks,shots;
    private int castTick;private final List<Integer> shotTicks=new ArrayList<>();private final List<Double> shotTimes=new ArrayList<>();
    private InventoryView stale;private ItemStack bought;private long balance;private final Listener observer=new Listener(){};
    @Override public void onEnable(){later(120,this::begin);}
    @SuppressWarnings("unchecked") private void begin()throws Exception {
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando.jawas()!=null,"Jawa service initialized with native credits");
        check("Jawa_Ionization_Blaster".equals(WeaponMechanicsAPI.getWeaponTitle(mando.items().create("jawa_ionization_blaster"))),"catalog creates the real Jawa blaster");
        check(mando.items().create("ionized_energy_cells").getItemMeta().getItemModel().toString().equals("mandomc:ammo/ionized_energy_cells"),"ionized cells use their restored model");
        var homeWorld=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));
        var homeChunk=homeWorld.getChunkAt(-3,-1);homeChunk.addPluginChunkTicket(this);chunks.add(homeChunk);
        world=Bukkit.getWorld(NamespacedKey.minecraft("devworld"));
        var chunk=world.getChunkAt(4,4);chunk.addPluginChunkTicket(this);chunks.add(chunk);
        var level=world.getClass().getMethod("getHandle").invoke(world);worldPlayers=(List<Object>)level.getClass().getMethod("players").invoke(level);
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);
        buyer=actor(new Location(world,67.5,200,67.5));
        group=mando.jawas().spawnGroup(List.of(new Location(world,68.5,200,68.5),new Location(world,70.5,200,68.5),new Location(world,72.5,200,68.5)));entities.addAll(group);
        gray=(LivingEntity)MythicBukkit.inst().getAPIHelper().spawnMythicMob("JawaGray",new Location(world,67.5,200,69.5),1);entities.add(gray);
        attacker=world.spawn(new Location(world,68.5,200,77.5),Cow.class,c->{c.setAI(false);c.setGravity(false);c.setInvulnerable(true);});entities.add(attacker);
        for(var e:entities){e.setGravity(false);e.setVelocity(new org.bukkit.util.Vector());}
        gray.setAI(false);
        Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class,observer,EventPriority.MONITOR,(l,e)->{var event=(WeaponShootEvent)e;if(event.getShooter()==group.getFirst()){
            shots++;shotTicks.add(Bukkit.getCurrentTick()-castTick);
            var pose=ModelEngineAPI.getModeledEntity(group.getFirst()).getModel("jawa_brown").orElseThrow().getAnimationHandler().getAnimation("attack");
            shotTimes.add(pose==null?-1:pose.getTime());event.getProjectile().remove();}},this,true);
        later(20,()->{
            check(group.stream().allMatch(e->((Mob)e).getTarget()==null),"brown group starts neutral beside player");
            for(var entity:group){var modeled=ModelEngineAPI.getModeledEntity(entity);check(modeled!=null && modeled.getModel("jawa_brown").isPresent(),"brown rig attaches to native mob");}
            var model=ModelEngineAPI.getModeledEntity(gray);check(model!=null && model.getModel("jawa_black").isPresent(),"gray rig attaches to native mob");
            check(gray.isInvulnerable(),"gray base is invincible");
            var hit=new EntityDamageByEntityEvent(buyer,gray,EntityDamageEvent.DamageCause.ENTITY_ATTACK,5);Bukkit.getPluginManager().callEvent(hit);check(hit.isCancelled(),"even direct player damage to gray is cancelled");
            var home=new Location(Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")),-32.5344406861867,96,-9.51074454608026);
            check(home.getWorld().getNearbyEntities(home,2,2,2).stream().anyMatch(e->mando.jawas().gray(e)&&e.isInvulnerable()),"test gray buyer exists at captured player position");

            mando.credits().auctionTask(()->{mando.credits().ledger().adjust(buyer.getUniqueId(),"set",100000);return null;},(v,e)->{if(e!=null)fail(e);else later(1,this::buy);});
        });
    }
    private void buy(){
        mando.jawas().open(buyer,group.getFirst());stale=buyer.getOpenInventory();
        check(stale.getTopInventory().getSize()==36,"brown supply menu opens");
        check(!group.getFirst().hasAI(),"talking pauses Jawa navigation immediately");
        try {
            var second=actor(new Location(world,67.5,200,67.5));mando.jawas().open(second,group.getFirst());
            buyer.closeInventory();check(!group.getFirst().hasAI(),"Jawa stays still while a second customer is browsing");
            second.closeInventory();check(group.getFirst().hasAI(),"last customer closing restores previous AI");
            var marker=new NamespacedKey("mandomc","jawa_conversation_ai_v1");
            group.getFirst().getPersistentDataContainer().set(marker,org.bukkit.persistence.PersistentDataType.BYTE,(byte)1);group.getFirst().setAI(false);
            mando.jawas().loaded(new org.bukkit.event.world.EntitiesLoadEvent(group.getFirst().getLocation().getChunk(),List.of(group.getFirst())));
            check(group.getFirst().hasAI() && !group.getFirst().getPersistentDataContainer().has(marker),"saved conversation pause recovers after chunk/server reload");
            mando.jawas().open(buyer,group.getFirst());stale=buyer.getOpenInventory();
        }catch(Exception error){throw new IllegalStateException(error);}
        check(stale.getTopInventory().getItem(29).getType()==Material.GRAY_STAINED_GLASS_PANE && stale.getTopInventory().getItem(29).getItemMeta().isHideTooltip(),"unused menu slots use protected hidden filler");
        var preview=stale.getTopInventory().getItem(0);var offer=mando.jawas().settings().stock(group.getFirst().getUniqueId().getMostSignificantBits()^group.getFirst().getUniqueId().getLeastSignificantBits()).getFirst();
        bought=new ItemStack(preview.getType(),offer.amount());balance=100000-offer.price();
        var shift=new InventoryClickEvent(stale,InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);Bukkit.getPluginManager().callEvent(shift);check(shift.isCancelled(),"shift transfer cannot extract shop previews");
        click(buyer,0);click(buyer,0);
        later(30,()->{
            check(group.getFirst().hasAI(),"successful trade releases conversation movement pause");
            check(mando.credits().ledger().balance(buyer.getUniqueId())==balance,"purchase debits exactly once despite rapid repeated click");
            check(buyer.getInventory().containsAtLeast(bought,bought.getAmount()),"purchase delivers genuine stack without shop lore");
            check(mando.credits().ledger().auctions().claims().values().stream().noneMatch(c->c.owner().equals(buyer.getUniqueId())),"delivered supply claim acknowledged");
            var restarted=new CreditLedger(mando.getDataFolder().toPath().resolve("data/credits-data.yml"));check(restarted.balance(buyer.getUniqueId())==balance,"shop debit survives ledger restart");
            sell();
        });
    }
    private void sell(){
        buyer.getInventory().clear();buyer.getInventory().setItem(0,new ItemStack(Material.SAND,64));
        mando.jawas().open(buyer,gray);check(buyer.getOpenInventory().getTopInventory().getSize()==36,"gray buyer opens deposit menu");depositSlot(0);click(buyer,31);
        later(30,()->{
            check(buyer.getInventory().isEmpty(),"sold bundle is removed exactly once");
            check(mando.credits().ledger().balance(buyer.getUniqueId())==balance+200,"gray buyer pays existing credit account");balance+=200;
            mando.jawas().open(buyer,gray);click(buyer,31);later(5,()->{
                check(mando.credits().ledger().balance(buyer.getUniqueId())==balance,"missing bundle cannot create credits");
                buyer.getInventory().setItem(0,new ItemStack(Material.IRON_INGOT,8));
                mando.jawas().open(buyer,gray);
                depositSlot(0);click(buyer,31);
                later(5,()->{check(mando.credits().ledger().balance(buyer.getUniqueId())==balance,"plain iron cannot be sold through canonical resource buyback");buyer.closeInventory();aggression();});
            });
        });
    }
    private void aggression(){
        mando.jawas().open(buyer,group.getFirst());check(!group.getFirst().hasAI(),"conversation is paused before attack");
        var denied=new EntityDamageByEntityEvent(attacker,group.getFirst(),EntityDamageEvent.DamageCause.ENTITY_ATTACK,1);denied.setCancelled(true);Bukkit.getPluginManager().callEvent(denied);
        check(group.stream().allMatch(e->((Mob)e).getTarget()==null),"cancelled damage cannot provoke group");
        group.getFirst().damage(1,buyer);
        check(group.getFirst().hasAI(),"attack releases conversation pause before retaliation");
        getLogger().info("Retaliation diagnostic: " + group.stream().map(e->e.getUniqueId()+" valid="+e.isValid()+" type="+mando.jawas().brown(e)+" target="+((Mob)e).getTarget()).toList());
        check(group.stream().allMatch(e->((Mob)e).getTarget()==buyer),"attacking one brown Jawa provokes its group");
        for(var e:group){e.setAI(false);var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(e);check(active.getThreatTable().getThreat(BukkitAdapter.adapt(buyer))>0,"retaliation adds attacker to Mythic threat table");}
        var first=(Mob)group.getFirst();var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(first);
        Bukkit.getScheduler().runTaskTimer(this,()->{first.setTarget(attacker);active.setTarget(BukkitAdapter.adapt(attacker));active.getThreatTable().threatGain(BukkitAdapter.adapt(attacker),100);},1,1);
        later(10,()->{
            active.clearSkillCooldowns();shots=0;shotTicks.clear();shotTimes.clear();castTick=Bukkit.getCurrentTick();
            getLogger().info("Attack diagnostic: combat="+active.getThreatTable().inCombat()+" target="+active.hasTarget()+" los="+first.hasLineOfSight(attacker)+" distance="+first.getLocation().distance(attacker.getLocation()));
            check(MythicBukkit.inst().getAPIHelper().castSkill(first,"JawaAttack",first.getLocation(),List.of(attacker),List.of(),1),"Jawa blaster attack skill casts");
            later(54,()->{
                getLogger().info("Shot phase evidence: ticks="+shotTicks+" animation seconds="+shotTimes);
                check(shotTicks.size()>=2 && shotTicks.getFirst()>=27 && shotTicks.getFirst()<=30 && shotTicks.get(1)-shotTicks.getFirst()==1,"paired shots occur 22 ticks into the attack pose");
                check(shotTimes.size()>=2 && shotTimes.getFirst()>=1.0 && shotTimes.getFirst()<=1.25,"actual model recoil and projectile timing agree");
                check(shots>=2,"retaliating Jawa fires paired ionization shots: "+shots);check(((Mob)gray).getTarget()==null,"gray buyer stays passive");drops();
                getLogger().info("JAWA PROBE PASSED: "+checks+" assertions");cleanup();});
        });
    }

    private void drops(){
        var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(group.getFirst());int guns=0,cells=0,parts=0;var distinct=new HashSet<String>();
        var pool=Set.of("wiring_coil","basic_power_cell","rough_power_cell","basic_emitter_coil","makeshift_recoil_buffer");
        var ammo=mando.items().create("ionized_energy_cells");
        for(int i=0;i<1000;i++){
            var event=new io.lumine.mythic.bukkit.events.MythicMobDeathEvent(active,null,new ArrayList<>());Bukkit.getPluginManager().callEvent(event);int g=0,c=0,p=0;
            for(var item:event.getDrops())if("Jawa_Ionization_Blaster".equals(WeaponMechanicsAPI.getWeaponTitle(item)))g+=item.getAmount();else if(item.isSimilar(ammo))c+=item.getAmount();else if(pool.contains(mando.items().identify(item))){p+=item.getAmount();distinct.add(mando.items().identify(item));}else throw new AssertionError("Unexpected Jawa loot");
            if(g>1||c>3||p<1||p>2)throw new AssertionError("Jawa loot exceeded legacy bounds");guns+=g;cells+=c;parts+=p;
        }
        check(guns>60&&guns<180,"actual Mythic death adapter preserves 12% blaster roll");check(cells>1300&&cells<1700,"actual Mythic death adapter preserves three 50% cell rolls");check(parts>=1000&&parts<=2000&&distinct.equals(pool),"actual deaths roll 1–2 genuine parts across the full electrical pool");
    }
    private Player actor(Location point)throws Exception{var p=nativeTestPlayer(world,point);actors.add(p);p.setGameMode(GameMode.SURVIVAL);var h=p.getClass().getMethod("getHandle").invoke(p);handles.put(p.getUniqueId(),h);worldPlayers.add(h);online.put(p.getUniqueId(),h);return p;}
    private void click(Player player,int slot){if(!(player.getOpenInventory().getTopInventory().getHolder().getClass().getName().contains("JawaTraders")) && !(player.getOpenInventory().getTopInventory().getHolder() instanceof net.mandomc.gameplay.jawa.JawaSellInventory.Tray))return;var event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"Jawa menu blocks inventory transfer");}
    private void depositSlot(int slot){
        var view=buyer.getOpenInventory();int raw=-1;for(int i=36;i<72;i++)if(view.convertSlot(i)==slot){raw=i;break;}
        var event=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,raw,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"deposit inventory controls transfers");
    }
    private interface Check{void run()throws Exception;}
    private void later(long ticks,Check action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){fail(error);}},ticks);}
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;getLogger().info("PASS "+message);}
    private void fail(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"JAWA PROBE FAILED",error);cleanup();}
    private void cleanup(){
        Bukkit.getScheduler().cancelTasks(this);HandlerList.unregisterAll(observer);
        entities.forEach(e->{if(e.isValid())e.remove();});entities.clear();
        if(worldPlayers!=null)handles.values().forEach(worldPlayers::remove);if(online!=null)handles.keySet().forEach(online::remove);
        for(var actor:actors){actor.getInventory().clear();actor.closeInventory();}
        chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();
    }
    @Override public void onDisable(){cleanup();}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "Trade" + actors.size());
        Object handle = playerClass.getConstructor(serverClass, levelClass, profileClass, infoClass)
                .newInstance(server, level, profile, infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object flow = flowClass.getField("SERVERBOUND").get(null);
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Object connection = connectionClass.getConstructor(flowClass).newInstance(flow);
        Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie = cookieClass.getMethod("createInitial", profileClass, boolean.class).invoke(null, profile, false);
        Class<?> listenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object packetListener = listenerClass.getConstructor(serverClass, connectionClass, playerClass, cookieClass)
                .newInstance(server, connection, handle, cookie);
        playerClass.getField("connection").set(handle, packetListener);
        // Isolated players have no network client to send its loaded acknowledgement.
        var loadTimer = listenerClass.getDeclaredField("clientLoadedTimeoutTimer");
        loadTimer.setAccessible(true);loadTimer.setInt(packetListener, 0);
        var waiting = listenerClass.getDeclaredField("waitingForRespawn");
        waiting.setAccessible(true);waiting.setBoolean(packetListener, false);
        playerClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
}
