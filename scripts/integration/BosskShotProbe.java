package net.mandomc.verification;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.bootstrap.MandoPlugin;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent;
/** Focused shooting regression: no random navigation, world edits or real-player inventories. */
public final class BosskShotProbe extends JavaPlugin {
 private MandoPlugin plugin;private World world;private Mob boss;private Cow enemy;private int checks,shots,style=2;
 private final List<Player> players=new ArrayList<>();private final List<Chunk> chunks=new ArrayList<>();private final Listener observer=new Listener(){};
 @Override public void onEnable(){later(120,this::begin);}
 private void begin() throws Exception {
  plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
  var tatooine=Bukkit.getWorld(NamespacedKey.minecraft("tatooine"));
  check(tatooine!=null,"Tatooine auto-loads after restart");
  var landing=plugin.warps().destination("tatooine");
  check(landing.getWorld()==tatooine && Math.abs(landing.getX()-2451.5)<0.01 && landing.getY()==32 && Math.abs(landing.getZ()-1539.5)<0.01,"Tatooine warp resolves a safe standing position at legacy arrival");
  check(!tatooine.getGameRuleValue(GameRule.DO_MOB_SPAWNING) && !tatooine.getGameRuleValue(GameRule.DO_DAYLIGHT_CYCLE),"Tatooine preserves terrain and disables natural spawning");
  for(int x=49;x<=51;x++)for(int z=49;z<=51;z++){var c=world.getChunkAt(x,z);c.addPluginChunkTicket(this);chunks.add(c);}
  boss=(Mob)MythicBukkit.inst().getAPIHelper().spawnMythicMob("Bossk",new Location(world,810.5,301,810.5),1);boss.setAI(false);boss.setGravity(false);boss.setRemoveWhenFarAway(false);
  enemy=world.spawn(boss.getLocation().add(0,0,8),Cow.class,c->{c.setAI(false);c.setGravity(false);});
  Bukkit.getScheduler().runTaskTimer(this,()->{if(boss.isValid()&&enemy.isValid()){boss.setTarget(enemy);var api=MythicBukkit.inst().getAPIHelper();api.addThreat(boss,enemy,100);api.getMythicMobInstance(boss).setTarget(BukkitAdapter.adapt(enemy));}},1,1);
  suppress();
  Bukkit.getPluginManager().registerEvent(WeaponShootEvent.class,observer,EventPriority.MONITOR,(l,e)->{var shot=(WeaponShootEvent)e;if(shot.getShooter()!=boss)return;
   shot.getProjectile().remove();check(shot.getProjectile().getIntTag(net.mandomc.integrations.weapons.BosskShotDamage.STYLE)==style,"live projectile snapshots correct "+(style==1?"single":"burst")+" profile");shots++;
  },this,true);
  later(40,()->{netheriteBurst();cast("BosskBurst");later(60,()->{check(shots==3,"attack2 emits exactly three profiled projectiles");shots=0;style=1;cast("BosskAim");later(50,()->{check(shots==1,"attack4 emits exactly one profiled projectile");getLogger().info("BOSSK SHOT PROBE PASSED: "+checks+" assertions");cleanup();});});});
 }
 private void cast(String name){var api=MythicBukkit.inst().getAPIHelper();api.getMythicMobInstance(boss).clearSkillCooldowns();check(api.castSkill(boss,name,boss.getLocation(),List.of(enemy),List.of(),1),"cast "+name);suppress();}
 private void suppress(){var active=MythicBukkit.inst().getAPIHelper().getMythicMobInstance(boss);for(String s:List.of("BosskCloseCombo","BosskRangedBrain"))active.setSkillCooldownMillis((io.lumine.mythic.core.skills.AbstractSkill)MythicBukkit.inst().getSkillManager().getSkill(s).orElseThrow(),600000);}
 private interface Check{void run() throws Exception;}
 private void later(long d,Check c){Bukkit.getScheduler().runTaskLater(this,()->{try{c.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"BOSSK SHOT PROBE FAILED",e);Bukkit.getScheduler().cancelTasks(this);cleanup();}},d);}
 private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;getLogger().info("PASS: "+message);}
 private void cleanup(){HandlerList.unregisterAll(observer);if(boss!=null)boss.remove();if(enemy!=null)enemy.remove();if(plugin!=null)players.forEach(plugin.combat()::clear);players.clear();chunks.forEach(c->c.removePluginChunkTicket(this));chunks.clear();}
 @Override public void onDisable(){cleanup();}
    private void netheriteBurst() throws Exception {
        var victim=nativeTestPlayer(world,boss.getLocation().add(0,0,5));players.add(victim);victim.setGameMode(GameMode.SURVIVAL);
        victim.getInventory().setArmorContents(new org.bukkit.inventory.ItemStack[]{
            new org.bukkit.inventory.ItemStack(Material.NETHERITE_BOOTS),new org.bukkit.inventory.ItemStack(Material.NETHERITE_LEGGINGS),
            new org.bukkit.inventory.ItemStack(Material.NETHERITE_CHESTPLATE),new org.bukkit.inventory.ItemStack(Material.NETHERITE_HELMET)});
        // Unregistered fixture players do not run equipment ticks: install the same vanilla defensive totals.
        if(victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue()==0)victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR).setBaseValue(20);
        if(victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS).getValue()==0)victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS).setBaseValue(12);
        check(victim.getAttribute(org.bukkit.attribute.Attribute.ARMOR).getValue()==20,"fixture has full netherite armor protection");
        var wm=me.deecaad.weaponmechanics.WeaponMechanics.getInstance();
        var def=wm.getWeaponConfigurations().getObject("RelbyV10.Projectile",me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile.class);
        var weapon=plugin.items().create("relby_v10");
        for(int i=0;i<6;i++) {
            victim.setNoDamageTicks(0);
            var start=victim.getLocation().add(0,1.75,-5);var direction=new org.bukkit.util.Vector(0,0,1);
            var bolt=def.create(boss,start,direction,weapon,"RelbyV10",org.bukkit.inventory.EquipmentSlot.HAND);
            try {var box=me.deecaad.core.compatibility.HitBox.getHitbox(victim.getBoundingBox());box.setLivingEntity(victim);
                var hit=box.rayTrace(start.toVector(),direction);check(hit!=null,"netherite fixture receives head-height shot");
                double before=victim.getHealth();wm.getWeaponHandler().getHitHandler().handleHit(hit,bolt);
                check(victim.getHealth()<before,"actual nerfed Relby shot damages netherite without immunity masking it");
            }finally{bolt.remove();}
        }
        check(victim.getHealth()>10,"six close head-height hits leave over half netherite health: "+victim.getHealth());
        for(int style:List.of(1,2)) {
            victim.setHealth(20);victim.setNoDamageTicks(0);double before=victim.getHealth();
            for(int i=0;i<(style==1?1:3);i++) {
                var start=victim.getLocation().add(0,1.1,-5);var direction=new org.bukkit.util.Vector(0,0,1);
                var bolt=def.create(boss,start,direction,weapon,"RelbyV10",org.bukkit.inventory.EquipmentSlot.HAND);
                bolt.setIntTag(net.mandomc.integrations.weapons.BosskShotDamage.STYLE,style);
                try{var box=me.deecaad.core.compatibility.HitBox.getHitbox(victim.getBoundingBox());box.setLivingEntity(victim);
                    var hit=box.rayTrace(start.toVector(),direction);check(hit!=null,"Bossk profile shot intersects armored player");
                    wm.getWeaponHandler().getHitHandler().handleHit(hit,bolt);
                }finally{bolt.remove();}
            }
            double damage=before-victim.getHealth();
            check(Math.abs(damage-(style==1?6:8))<0.01,"netherite health loss matches "+(style==1?"three-heart single":"four-heart full burst")+": "+damage);
        }
        plugin.combat().clear(victim);
    }
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
