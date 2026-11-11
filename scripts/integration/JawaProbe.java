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
        Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class,observer,EventPriority.MONITOR,(l,e)->{var event=(WeaponShootEvent)e;if(group.stream().anyMatch(m->m==event.getShooter())){shots++;event.getProjectile().remove();}},this,true);
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
        check(stale.getTopInventory().getItem(30).getType()==Material.GRAY_STAINED_GLASS_PANE && stale.getTopInventory().getItem(30).getItemMeta().isHideTooltip(),"unused menu slots use protected hidden filler");
        var preview=stale.getTopInventory().getItem(0);var offer=mando.jawas().settings().stock(group.getFirst().getUniqueId().getMostSignificantBits()^group.getFirst().getUniqueId().getLeastSignificantBits()).getFirst();
        bought=new ItemStack(preview.getType(),offer.amount());balance=100000-offer.price();
        var shift=new InventoryClickEvent(stale,InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);Bukkit.getPluginManager().callEvent(shift);check(shift.isCancelled(),"shift transfer cannot extract shop previews");
        click(buyer,0);click(buyer,0);
        later(30,()->{
            check(mando.credits().ledger().balance(buyer.getUniqueId())==balance,"purchase debits exactly once despite rapid repeated click");
            check(buyer.getInventory().containsAtLeast(bought,bought.getAmount()),"purchase delivers genuine stack without shop lore");
            check(mando.credits().ledger().auctions().claims().values().stream().noneMatch(c->c.owner().equals(buyer.getUniqueId())),"delivered supply claim acknowledged");
            var restarted=new CreditLedger(mando.getDataFolder().toPath().resolve("data/credits-data.yml"));check(restarted.balance(buyer.getUniqueId())==balance,"shop debit survives ledger restart");
            sell();
        });
    }
    private void sell(){
        buyer.getInventory().clear();buyer.getInventory().setItem(0,new ItemStack(Material.SAND,64));
        mando.jawas().open(buyer,gray);check(buyer.getOpenInventory().getTopInventory().getSize()==36,"gray buyer opens resource menu");click(buyer,0);
        later(30,()->{
            check(buyer.getInventory().isEmpty(),"sold bundle is removed exactly once");
            check(mando.credits().ledger().balance(buyer.getUniqueId())==balance+200,"gray buyer pays existing credit account");balance+=200;
            mando.jawas().open(buyer,gray);click(buyer,0);later(5,()->{
                check(mando.credits().ledger().balance(buyer.getUniqueId())==balance,"missing bundle cannot create credits");
                var custom=mando.items().create("agrinium");custom.setAmount(8);buyer.getInventory().setItem(0,custom);
                mando.jawas().open(buyer,gray);
                var menu=buyer.getOpenInventory().getTopInventory();int nativeIron=-1;
                for(int i=0;i<28;i++){var stack=menu.getItem(i);if(stack!=null&&stack.getType()==Material.IRON_INGOT && mando.items().identify(stack)==null){nativeIron=i;break;}}
                if(nativeIron>=0)click(buyer,nativeIron);
                later(5,()->{check(mando.credits().ledger().balance(buyer.getUniqueId())==balance,"custom metal cannot be sold as ordinary iron");buyer.closeInventory();aggression();});
            });
        });
    }
    private void aggression(){
        var denied=new EntityDamageByEntityEvent(attacker,group.getFirst(),EntityDamageEvent.DamageCause.ENTITY_ATTACK,1);denied.setCancelled(true);Bukkit.getPluginManager().callEvent(denied);
        check(group.stream().allMatch(e->((Mob)e).getTarget()==null),"cancelled damage cannot provoke group");
        group.getFirst().damage(1,buyer);
        getLogger().info("Retaliation diagnostic: " + group.stream().map(e->e.getUniqueId()+" valid="+e.isValid()+" type="+mando.jawas().brown(e)+" target="+((Mob)e).getTarget()).toList());
        check(group.stream().allMatch(e->((Mob)e).getTarget()==buyer),"attacking one brown Jawa provokes its group");
        for(var e:group){e.setAI(false);var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(e);check(active.getThreatTable().getThreat(BukkitAdapter.adapt(buyer))>0,"retaliation adds attacker to Mythic threat table");}
        var first=(Mob)group.getFirst();var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(first);
        Bukkit.getScheduler().runTaskTimer(this,()->{first.setTarget(attacker);active.setTarget(BukkitAdapter.adapt(attacker));active.getThreatTable().threatGain(BukkitAdapter.adapt(attacker),100);},1,1);
        later(10,()->{
            active.clearSkillCooldowns();
            getLogger().info("Attack diagnostic: combat="+active.getThreatTable().inCombat()+" target="+active.hasTarget()+" los="+first.hasLineOfSight(attacker)+" distance="+first.getLocation().distance(attacker.getLocation()));
            check(MythicBukkit.inst().getAPIHelper().castSkill(first,"JawaAttack",first.getLocation(),List.of(attacker),List.of(),1),"Jawa blaster attack skill casts");
            later(50,()->{check(shots>=2,"retaliating Jawa fires paired ionization shots: "+shots);check(((Mob)gray).getTarget()==null,"gray buyer stays passive");drops();
                getLogger().info("JAWA PROBE PASSED: "+checks+" assertions");cleanup();});
        });
    }

    private void drops(){
        var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(group.getFirst());int guns=0,cells=0;
        var ammo=mando.items().create("ionized_energy_cells");
        for(int i=0;i<1000;i++){
            var event=new io.lumine.mythic.bukkit.events.MythicMobDeathEvent(active,null,new ArrayList<>());Bukkit.getPluginManager().callEvent(event);int g=0,c=0;
            for(var item:event.getDrops())if("Jawa_Ionization_Blaster".equals(WeaponMechanicsAPI.getWeaponTitle(item)))g+=item.getAmount();else if(item.isSimilar(ammo))c+=item.getAmount();else throw new AssertionError("Unexpected Jawa loot");
            if(g>1||c>3)throw new AssertionError("Jawa loot exceeded legacy bounds");guns+=g;cells+=c;
        }
        check(guns>60&&guns<180,"actual Mythic death adapter preserves 12% blaster roll");check(cells>1300&&cells<1700,"actual Mythic death adapter preserves three 50% cell rolls");
    }
    private Player actor(Location point)throws Exception{var p=nativeTestPlayer(world,point);actors.add(p);p.setGameMode(GameMode.SURVIVAL);var h=p.getClass().getMethod("getHandle").invoke(p);handles.put(p.getUniqueId(),h);worldPlayers.add(h);online.put(p.getUniqueId(),h);return p;}
    private void click(Player player,int slot){if(!(player.getOpenInventory().getTopInventory().getHolder().getClass().getName().contains("JawaTraders")))return;var event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"Jawa menu blocks inventory transfer");}
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
