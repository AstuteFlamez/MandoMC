package net.mandomc.verification;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.bootstrap.MandoPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import com.ticxo.modelengine.api.ModelEngineAPI;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.core.compatibility.HitBox;

/** Temporary vendor integration fixture, removed before normal play. No actual player gear or world edits. */
public final class JabbaProbe extends JavaPlugin {
    private final List<Entity> entities = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    private final Listener observer = new Listener() {};
    private final Map<String, Mob> guards = new LinkedHashMap<>();
    private final List<Integer> shotTicks = new ArrayList<>();
    private World world;
    private MandoPlugin plugin;
    private int checks, started;
    private Mob shooter;
    private String expectedWeapon;
    private Entity meleeDamager;

    @Override public void onEnable() { later(120, this::begin); }
    private void begin() throws Exception {
        plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        world = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        for (int x=43; x<=47; x++) for (int z=43; z<=47; z++) {
            var chunk=world.getChunkAt(x,z); chunk.addPluginChunkTicket(this); chunks.add(chunk);
        }
        var axe = plugin.items().create("gamorrean_axe");
        var vanilla = new ItemStack(Material.IRON_AXE);
        check(axe != null && axe.getType()==Material.IRON_AXE, "axe has native iron-axe material");
        check("gamorrean_axe".equals(plugin.items().identify(axe)), "axe stable catalog identity");
        check(!plugin.rules().banned(axe), "axe usable under survival rules");
        check(axe.getData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS)
                .equals(vanilla.getData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS)),
                "axe native attack damage and speed exactly match iron axe");
        check(axe.getType().getMaxDurability()==vanilla.getType().getMaxDurability()
                && !axe.getItemMeta().isUnbreakable() && axe.getEnchantments().isEmpty(),
                "axe retains normal durability without legacy sharpness or unbreakable");
        check(axe.getItemMeta().getItemModel().toString().equals("mandomc:melee/gamorrean_axe"), "axe model resolves");
        for (String id:List.of("se14c","a280")) {
            var gun=plugin.items().create(id);
            check(id.toUpperCase(Locale.ROOT).equals(WeaponMechanicsAPI.getWeaponTitle(gun)), id+" real vendor weapon");
            check(gun.getItemMeta().getItemModel().toString().equals("mandomc:blasters/"+id), id+" namespaced model");
            check(!plugin.rules().banned(gun), id+" usable under survival rules");
        }
        int offset=0;
        for (String id:List.of("GamorreanGuard","QuarrenGuard","TwilekGuard")) {
            var mob=spawn(id,new Location(world,710.5+offset,301,710.5),1);guards.put(id,mob);
            var high=spawn(id,new Location(world,710.5+offset,305,710.5),5);
            check(high.getAttribute(Attribute.MAX_HEALTH).getBaseValue()-mob.getAttribute(Attribute.MAX_HEALTH).getBaseValue()==12,
                    id+" original +3 base health per level");
            check(mob.getAttribute(Attribute.ARMOR).getValue()==(id.equals("GamorreanGuard")?8:4),id+" original armor");
            check(mob.getEquipment().getItemInMainHand().getType().isAir(),id+" no random vanilla weapon");
            high.remove();offset+=8;
        }
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.entity.EntityDeathEvent.class,observer,
                EventPriority.HIGHEST,(l,e)->{var event=(org.bukkit.event.entity.EntityDeathEvent)e;
                    if(entities.contains(event.getEntity())){event.getDrops().clear();event.setDroppedExp(0);}},this,true);
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.entity.EntityDamageByEntityEvent.class,observer,
                EventPriority.MONITOR,(l,e)->{var event=(org.bukkit.event.entity.EntityDamageByEntityEvent)e;
                    if(event.getDamager()==guards.get("GamorreanGuard"))meleeDamager=event.getDamager();},this,true);
        Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class,observer,EventPriority.MONITOR,(l,e)->{
            var shot=(WeaponShootEvent)e;
            if(!entities.contains(shot.getShooter()))return;
            shot.getProjectile().remove();
            if(shot.getShooter()!=shooter)return;
            try {
                check(shot.getWeaponTitle().equals(expectedWeapon),"burst emits "+expectedWeapon);
                shotTicks.add(Bukkit.getCurrentTick()-started);
            } catch(Throwable error){fail(error);}
        },this,true);
        later(5,()->{
            for(var entry:guards.entrySet()) {
                var entity=entry.getValue();var modeled=ModelEngineAPI.getModeledEntity(entity);
                String mid=switch(entry.getKey()){case "GamorreanGuard"->"gamorrean_guard";case "QuarrenGuard"->"quarren_guard";default->"twilek_guard";};
                check(modeled!=null && modeled.getModels().containsKey(mid),entry.getKey()+" correct model attached");
                check(!modeled.isBaseEntityVisible(),entry.getKey()+" vanilla body hidden");
                check(entity.getBoundingBox().getWidthX()>0.5 && entity.getBoundingBox().getHeight()>1.9,entry.getKey()+" native collision retained");
                var blueprint=ModelEngineAPI.getBlueprint(mid);
                for(var a:blueprint.getAnimations().values()) if(a.getName().startsWith("attack"))
                    check(a.getLoopMode().name().equals("ONCE"),mid+" "+a.getName()+" finishes cleanly");
                drops(entity,entry.getKey());
            }
            damage(guards.get("QuarrenGuard"),"se14c");damage(guards.get("TwilekGuard"),"a280");
            melee();
        });
    }
    private void drops(Mob entity,String id) {
        var mob=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(entity);
        int weapons=0,ammo=0;
        String expected=switch(id){case "GamorreanGuard"->"gamorrean_axe";case "QuarrenGuard"->"SE14C";default->"A280";};
        for(int i=0;i<1000;i++) {
            var event=new MythicMobDeathEvent(mob,null,new ArrayList<>());
            Bukkit.getPluginManager().callEvent(event);
            int w=0,a=0;
            for(var item:event.getDrops()) {
                if(item.getAmount()!=1)throw new AssertionError("Loot roll is not a single item");
                if(expected.equals(plugin.items().identify(item)) || expected.equals(WeaponMechanicsAPI.getWeaponTitle(item)))w++;
                else if(item.isSimilar(plugin.items().create("standard_plasma_cells")))a++;
                else throw new AssertionError("Unexpected guard loot "+item);
            }
            if(w>1 || a>3 || (id.equals("GamorreanGuard")&&a!=0))throw new AssertionError("Duplicate/wrong guard loot");
            weapons+=w;ammo+=a;
        }
        check(weapons>(id.equals("GamorreanGuard")?180:130) && weapons<(id.equals("GamorreanGuard")?320:270),
                id+" original weapon drop chance: "+weapons+"/1000");
        check(id.equals("GamorreanGuard")?ammo==0:ammo>1350&&ammo<1650,id+" independent ammo rolls: "+ammo+" cells");
    }
    private void damage(Mob source,String id) throws Exception {
        var victim=player(source.getLocation().add(0,0,5));
        String title=id.toUpperCase(Locale.ROOT);
        var definition=WeaponMechanics.getInstance().getWeaponConfigurations().getObject(title+".Projectile",Projectile.class);
        check(definition!=null,title+" projectile reference resolves");
        var start=source.getLocation().add(0,0.5,0);var direction=victim.getLocation().add(0,0.5,0).toVector().subtract(start.toVector()).normalize();
        var bolt=definition.create(source,start,direction,plugin.items().create(id),title,EquipmentSlot.HAND);
        try {
            var box=HitBox.getHitbox(victim.getBoundingBox());box.setLivingEntity(victim);
            var hit=box.rayTrace(start.toVector(),direction);check(hit!=null,title+" ray hits real native player");
            double before=victim.getHealth();WeaponMechanics.getInstance().getWeaponHandler().getHitHandler().handleHit(hit,bolt);
            check(victim.getHealth()<before,title+" applies actual player damage");
            check(plugin.combat().tagged(victim),title+" retains mob attribution and combat tag");
            plugin.combat().clear(victim);
        }finally{bolt.remove();}
        var safe=player(world.getSpawnLocation());double hp=safe.getHealth();safe.damage(6,source);
        check(hp==safe.getHealth()&&!plugin.combat().tagged(safe),title+" safe-zone damage denied");
    }
    private void melee() throws Exception {
        var mob=guards.get("GamorreanGuard");var victim=world.spawn(mob.getLocation().add(0,0,2),Cow.class,c->{c.setAI(false);c.setGravity(false);});entities.add(victim);
        select(mob,victim);double hp=victim.getHealth();cast(mob,"Light_Slash",victim);
        later(19,()->{
            check(Math.abs(hp-victim.getHealth()-6)<0.01,"Gamorrean light slash deals original six damage after windup: "+hp+" -> "+victim.getHealth());
            var nativeVictim=player(mob.getLocation().add(0,0,2));
            check(meleeDamager==mob,"Gamorrean scripted hit retains the guard as Bukkit damager");
            nativeVictim.damage(6,meleeDamager);
            check(plugin.combat().tagged(nativeVictim),"attributed Gamorrean hit tags players for combat");
            plugin.combat().clear(nativeVictim);victim.setNoDamageTicks(0);victim.setHealth(victim.getAttribute(Attribute.MAX_HEALTH).getValue());
            cast(mob,"Light_Slash",victim);victim.teleport(mob.getLocation().add(0,0,12));
            later(19,()->{
                check(victim.getHealth()==victim.getAttribute(Attribute.MAX_HEALTH).getValue(),"leaving axe reach during windup avoids delayed hit");
                select(mob,null);burst("QuarrenGuard","quarren_attack","SE14C",()->burst("TwilekGuard","twilek_attack1","A280",()->burst("TwilekGuard","twilek_attack2","A280",this::finish)));
            });
        });
    }
    private void burst(String mobId,String skill,String weapon,Check next) throws Exception {
        shooter=guards.get(mobId);var target=world.spawn(shooter.getLocation().add(0,0,8),Cow.class,c->{c.setAI(false);c.setGravity(false);});entities.add(target);
        expectedWeapon=weapon;shotTicks.clear();select(shooter,target);started=Bukkit.getCurrentTick();cast(shooter,skill,target);
        check(shooter.getAttribute(Attribute.MOVEMENT_SPEED).getValue()==0,skill+" plants movement during burst");
        later(40,()->{
            var nominal=List.of(16,17,20,21,24,25);
            boolean timing=shotTicks.size()==6;
            for(int i=0;timing && i<6;i++) timing=shotTicks.get(i)>=nominal.get(i) && shotTicks.get(i)<=nominal.get(i)+2;
            check(timing,skill+" six-tick pre-turn plus legacy six-shot sequence within scheduler tolerance: "+shotTicks);
            check(Math.abs(shooter.getAttribute(Attribute.MOVEMENT_SPEED).getValue()-0.3)<0.001,skill+" restores movement");
            select(shooter,null);target.remove();shooter=null;next.run();
        });
    }
    private void finish() throws Exception {
        // A dead target must cancel the deferred shots while the shooter still recovers movement.
        shooter=guards.get("QuarrenGuard");expectedWeapon="SE14C";
        var target=world.spawn(shooter.getLocation().add(0,0,8),Cow.class,c->{c.setAI(false);c.setGravity(false);});entities.add(target);
        shotTicks.clear();select(shooter,target);started=Bukkit.getCurrentTick();cast(shooter,"quarren_attack",target);target.setHealth(0);
        later(40,()->{
            check(shotTicks.isEmpty(),"dead target cancels deferred ranged fire");
            check(Math.abs(shooter.getAttribute(Attribute.MOVEMENT_SPEED).getValue()-0.3)<0.001,"cancelled burst still restores movement");
            getLogger().info("JABBA PROBE PASSED: "+checks+" assertions");cleanup();
        });
    }
    private Mob spawn(String id,Location location,int level) throws Exception {
        var e=(Mob)MythicBukkit.inst().getAPIHelper().spawnMythicMob(id,location,level);entities.add(e);
        e.setAI(false);e.setGravity(false);e.setRemoveWhenFarAway(false);Bukkit.getMobGoals().removeAllGoals(e);
        MythicBukkit.inst().getAPIHelper().getMythicMobInstance(e).setDespawnMode(io.lumine.mythic.core.mobs.DespawnMode.NEVER);
        return e;
    }
    private void select(Mob e,LivingEntity t) {
        e.setTarget(t);var api=MythicBukkit.inst().getAPIHelper();var mob=api.getMythicMobInstance(e);
        if(t!=null){api.addThreat(e,t,100);mob.setTarget(BukkitAdapter.adapt(t));}
        else {mob.getThreatTable().dropCombat();mob.setTarget(null);}
    }
    private void cast(Mob e,String skill,LivingEntity target) {
        var api=MythicBukkit.inst().getAPIHelper();
        var mob=api.getMythicMobInstance(e);mob.clearSkillCooldowns();
        check(api.castSkill(e,skill,e.getLocation(),List.of(target),List.of(),1),"cast "+skill);
        for(String timer:List.of("Gamorrean_Brain","quarren_attack","twilek_attack")) {
            var loaded=(io.lumine.mythic.core.skills.AbstractSkill)MythicBukkit.inst().getSkillManager().getSkill(timer).orElseThrow();
            mob.setSkillCooldownMillis(loaded,60000);
        }
    }
    private interface Check {void run() throws Exception;}
    private void later(long ticks,Check action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable e){fail(e);}},ticks);}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;getLogger().info("PASS: "+message);}
    private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"JABBA PROBE FAILED",e);Bukkit.getScheduler().cancelTasks(this);cleanup();}
    private void cleanup(){HandlerList.unregisterAll(observer);entities.forEach(Entity::remove);entities.clear();if(plugin!=null)players.forEach(p->plugin.combat().clear(p));players.clear();chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();}
    @Override public void onDisable(){cleanup();}
    private Player player(Location location) throws Exception {var p=nativeTestPlayer(location.getWorld(),location);players.add(p);return p;}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "MobTest");
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
