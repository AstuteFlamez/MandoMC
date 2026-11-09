package net.mandomc.verification;

import java.lang.reflect.Proxy;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.saber.SaberService;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile;

/** Real world, item and vendor APIs; isolated fake-player inventories, never user gear. */
public final class SaberProbe extends JavaPlugin {
    private int checks;
    private SaberService sabers;
    private TestPlayer player;
    private ArmorStand shooter;
    private final List<WeaponProjectile> projectiles=new ArrayList<>();
    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this,()-> {
        try {verify();} catch(Throwable e) {getLogger().log(java.util.logging.Level.SEVERE,"SABER PROBE FAILED",e);cleanup();}
    },60); }
    private void verify() throws Exception {
        var plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");sabers=plugin.sabers();
        var item=plugin.items().create("vader");check(sabers.isSaber(item),"Vader has stable native shield identity");
        check(item.getMaxStackSize()==1,"saber cannot stack");
        check(item.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).iterator().next().getAmount()==9.5,"native melee adds configured damage");
        check(item.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.ATTACK_SPEED).iterator().next().getAmount()==-3,"native attack speed and charge retained");
        check(item.getItemMeta().lore().get(1).equals(net.kyori.adventure.text.Component.empty()),"badge row followed by blank line");
        var world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        var point=new Location(world,-150,300,-150,0,0);world.getChunkAt(point).addPluginChunkTicket(this);
        player=new TestPlayer("SaberProbe",point);player.storage.setItem(0,item);
        var packConfig=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"sabers.yml")).getConfigurationSection("upstream-pack");
        var pack=new net.mandomc.integrations.packs.SaberPack(this,packConfig);
        org.bukkit.event.HandlerList.unregisterAll(pack);
        pack.request(player.player);
        check(player.packRequests==1,"join delivery works without a prior pack-loaded callback");
        pack.loaded(new org.bukkit.event.player.PlayerResourcePackStatusEvent(player.player,UUID.fromString(packConfig.getString("main-pack-id")),org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        check(player.packRequests==1 && player.forcedPack,"main pack completion requests required upstream pack");
        check(player.packUrl.equals(packConfig.getString("url")),"upstream delivered directly from pinned official URL");
        pack.loaded(new org.bukkit.event.player.PlayerResourcePackStatusEvent(player.player,UUID.fromString(packConfig.getString("id")),org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        check(player.packRequests==1,"upstream completion cannot trigger an infinite reload loop");
        var offhand=new org.bukkit.event.player.PlayerInteractEvent(player.player,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,item,null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.OFF_HAND);
        sabers.interact(offhand);check(offhand.useItemInHand()==org.bukkit.event.Event.Result.DENY,"offhand cannot bypass saber guard");
        check(sabers.allowed(point),"combat permitted outside landing");
        check(!sabers.allowed(world.getSpawnLocation()),"landing blocks saber combat");
        check(!sabers.allowed(Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")).getSpawnLocation()),"SpawnWorld blocks saber combat");
        check(sabers.canReflect(player.player,new Vector(0,0,-1)),"front bolt can be reflected");
        check(!sabers.canReflect(player.player,new Vector(0,0,1)),"rear bolt cannot be reflected");
        check(!sabers.canReflect(player.player,new Vector(1,0,0)),"side bolt cannot be reflected");
        player.blocking=false;check(!sabers.canReflect(player.player,new Vector(0,0,-1)),"idle saber does not reflect");player.blocking=true;
        shooter=world.spawn(point.clone().add(0,0,5),ArmorStand.class,e->{e.setPersistent(false);e.setGravity(false);});
        var def=WeaponMechanics.getInstance().getWeaponConfigurations().getObject("E11.Projectile",Projectile.class);
        var original=def.create(shooter,point.clone().add(0,1.62,4),new Vector(0,0,-2),WeaponMechanicsAPI.generateWeapon("E11"),"E11",EquipmentSlot.HAND);projectiles.add(original);
        var reflected=(WeaponProjectile)plugin.saberProjectiles().createReflection(original,player.player);projectiles.add(reflected);
        check(reflected.getShooter()==player.player,"reflected E11 belongs to defender");
        check(original.getShooter()==shooter,"original shooter identity remains immutable");
        check(reflected.getMotion().getZ()>0 && reflected.getMotion().length()==2,"reflection follows defender aim and preserves speed");
        check(reflected.getIntTag("mandomc_saber_reflections")==1,"reflection count survives new projectile");
        check(reflected.getWeaponTitle().equals("E11"),"reflection retains weapon damage configuration");
        shooter.remove();
        verifyDurability(plugin, world);
        verifyDamageBasedReflection(plugin, world);
        verifyNativeBlocking(plugin, world);
        verifyStunAndQuietRecovery(plugin, world);
        verifyThrowContacts(plugin, world);
        verifyMotion(plugin, world);
        verifyAirborneOwnership(plugin, world);
        check(sabers.launch(player.player),"sneak throw can launch");
        check(player.storage.getItem(0)==null || player.storage.getItem(0).getType().isAir(),"hand and inventory slot are empty during throw");
        check(player.player.getPersistentDataContainer().has(new NamespacedKey(plugin,"saber_flight_item"), org.bukkit.persistence.PersistentDataType.BYTE_ARRAY),"airborne item has persisted ownership for session recovery");
        check(!sabers.launch(player.player),"second simultaneous throw is refused");
        check(!sabers.canReflect(player.player,new Vector(0,0,-1)),"in-flight saber cannot reflect");
        var event=new InventoryClickEvent(player.view,InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.NOTHING);
        sabers.inventory(event);check(event.isCancelled(),"reserved inventory cannot shift-transfer");
        Bukkit.getScheduler().runTaskLater(this,()-> {
            try {
                check(!sabers.busy(player.player),"throw finishes within bounded lifetime");
                check(player.storage.getItem(0).getItemMeta().getItemModel().toString().equals("mandomc:sabers/vader"),"finished throw restores existing item presentation");
                check(Arrays.stream(player.storage.getContents()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum()==1,"return never duplicates inventory item");
                check(sabers.launch(player.player),"throw available after cooldown");
                sabers.quit(new org.bukkit.event.player.PlayerQuitEvent(player.player,net.kyori.adventure.text.Component.empty()));
                check(!sabers.busy(player.player) && player.storage.getItem(0).getItemMeta().getItemModel().toString().equals("mandomc:sabers/vader"),"logout restores reserved item and removes flight");
                for(int i=0;i<5;i++)sabers.reflected(player.player, 20);
                player.blocking=true;check(!sabers.canReflect(player.player,new Vector(0,0,-1)),"guard overheat disables reflection despite blocking state");
                getLogger().info("SABER PROBE PASSED: "+checks+" assertions");cleanup();
            }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SABER PROBE FAILED",e);cleanup();}
        },340);
    }
    private void verifyDurability(MandoPlugin plugin, World world) throws Exception {
        var actor = new TestPlayer("SaberDurabilityTest", new Location(world, -150, 300, -150));
        var item = plugin.items().create("vader");
        var name = net.kyori.adventure.text.Component.text("Durability test saber");
        var meta = item.getItemMeta();meta.displayName(name);item.setItemMeta(meta);
        actor.storage.setItem(0, item);
        var guardsField = SaberService.class.getDeclaredField("guards");guardsField.setAccessible(true);
        @SuppressWarnings("unchecked") var guards = (Map<UUID,net.mandomc.gameplay.saber.GuardState>) guardsField.get(sabers);
        var sync = SaberService.class.getDeclaredMethod("syncDurability", Player.class);sync.setAccessible(true);
        var tickField = SaberService.class.getDeclaredField("tick");tickField.setAccessible(true);
        int now = tickField.getInt(sabers);
        try {
            sabers.reflected(actor.player, 4);
            int max = Material.SHIELD.getMaxDurability();
            check(damage(actor.storage.getItem(0)) == Math.round(max * .2), "4-damage reflection drains native durability by 20 percent at 5x");
            actor.storage.setItem(1, plugin.items().create("vader"));sync.invoke(sabers, actor.player);
            check(damage(actor.storage.getItem(1)) == damage(actor.storage.getItem(0)), "switching sabers cannot reset charge");
            var wear = new org.bukkit.event.player.PlayerItemDamageEvent(actor.player, actor.storage.getItem(0), 1000);
            Bukkit.getPluginManager().callEvent(wear);
            check(wear.isCancelled(), "native shield wear cannot break the charge-display saber");
            var ordinary = new org.bukkit.event.player.PlayerItemDamageEvent(actor.player, new ItemStack(Material.IRON_PICKAXE), 1);
            Bukkit.getPluginManager().callEvent(ordinary);
            check(!ordinary.isCancelled(), "ordinary tool wear remains native");
            var state = guards.get(actor.id);
            for (int t = now + 1; t <= now + sabers.settings().recoveryDelay() + 39; t++) state.cool(t, sabers.settings());
            sync.invoke(sabers, actor.player);
            check(damage(actor.storage.getItem(0)) == 0, "reflection charge slowly repairs completely");
            check(actor.storage.getItem(0).getItemMeta().displayName().equals(name), "durability updates preserve custom name");
            guards.remove(actor.id);
            check(sabers.launch(actor.player), "fully recharged saber launches");
            state = guards.get(actor.id);
            check(state.heat() == 0 && state.recovering(), "throw queues gradual drain without an instant charge jump");
            int previous = 0;
            var storedKey = new NamespacedKey(plugin, "saber_flight_item");
            for (int t = 1; t <= 20; t++) {
                state.cool(now + t, sabers.settings());sync.invoke(sabers, actor.player);
                var stored = ItemStack.deserializeBytes(actor.data.get(storedKey, org.bukkit.persistence.PersistentDataType.BYTE_ARRAY));
                int current = damage(stored);
                check(current > previous, "airborne durability and saved item drain gradually at tick " + t);
                previous = current;
            }
            check(actor.storage.getItem(0) == null || actor.storage.getItem(0).getType().isAir(), "draining throw keeps original hand empty");
            sabers.finish(actor.id);
            check(damage(actor.storage.getItem(0)) == Math.round(max * .75), "catch restores saber at 25 percent durability");
            for (int t = 21; t < 20 + sabers.settings().recoveryDelay(); t++) state.cool(now + t, sabers.settings());
            check(state.heat() == 75, "throw waits full five-second quiet period before repairing");
            state.cool(now + 20 + sabers.settings().recoveryDelay(), sabers.settings());sync.invoke(sabers, actor.player);
            check(state.heat() == 74.5 && damage(actor.storage.getItem(0)) < previous, "throw repairs at same half-point per tick as reflection");
            guards.remove(actor.id);sync.invoke(sabers, actor.player);
            for (int i = 0; i < 5; i++) sabers.reflected(actor.player, 20);
            check(damage(actor.storage.getItem(0)) == max - 1 && sabers.isSaber(actor.storage.getItem(0)),
                    "empty charge leaves saber intact with an empty bar");
            check(!sabers.launch(actor.player), "depleted saber cannot launch during lockout");
            check(actor.messages.stream().noneMatch(text -> text.contains("Saber heat")), "no saber heat action-bar message is sent");
        } finally {sabers.finish(actor.id);guards.remove(actor.id);}
    }
    private int damage(ItemStack item) {
        return ((org.bukkit.inventory.meta.Damageable)item.getItemMeta()).getDamage();
    }
    private void verifyDamageBasedReflection(MandoPlugin plugin, World world) throws Exception {
        var mobs = new net.mandomc.integrations.mobs.MythicMobAccess();
        var guardsField = SaberService.class.getDeclaredField("guards");guardsField.setAccessible(true);
        @SuppressWarnings("unchecked") var guards = (Map<UUID,net.mandomc.gameplay.saber.GuardState>)guardsField.get(sabers);
        for (String scenario : new String[]{"e11", "cycler", "head", "armored", "tusken1", "tusken5", "cancelled"}) {
            var location = new Location(world, -150, 300, -150, 0, 0);
            Player nativePlayer = nativeTestPlayer(world, location);
            nativePlayer.getInventory().setItem(0, plugin.items().create("vader"));
            if (scenario.equals("armored")) {
                nativePlayer.getInventory().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
                nativePlayer.getAttribute(org.bukkit.attribute.Attribute.ARMOR).setBaseValue(8);
            }
            boolean[] blocking = {false};
            Player actor = nativePlayer;
            String weapon = scenario.equals("e11") ? "E11" : "CyclerRifle";
            boolean tusken = scenario.startsWith("tusken");
            LivingEntity shooter = tusken ? mobs.spawn("TuskenRaiderSniper", location.clone().add(0, 0, 5), scenario.endsWith("5") ? 5 : 1)
                    : world.spawn(location.clone().add(0, 0, 5), ArmorStand.class);
            shooter.setAI(false);shooter.setGravity(false);shooter.setPersistent(false);
            var observer = new org.bukkit.event.Listener() {};
            double[] expected = {Double.NaN};double[] base = {Double.NaN};
            var point = scenario.equals("head") ? me.deecaad.weaponmechanics.weapon.damage.DamagePoint.HEAD
                    : me.deecaad.weaponmechanics.weapon.damage.DamagePoint.BODY;
            try {
                Bukkit.getPluginManager().registerEvent(me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent.class,
                        observer, org.bukkit.event.EventPriority.HIGHEST, (ignored, event) -> {
                            var damage = (me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent)event;
                            if (damage.getVictim() == actor && !blocking[0]) {
                                expected[0] = damage.getFinalDamage();base[0] = damage.getBaseDamage();damage.setCancelled(true);
                            }
                        }, this, false);
                if (scenario.equals("cancelled")) Bukkit.getPluginManager().registerEvent(
                        me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent.class, observer,
                        org.bukkit.event.EventPriority.HIGH, (ignored, event) -> {
                            if (blocking[0]) ((org.bukkit.event.Cancellable)event).setCancelled(true);
                        }, this, false);
                var config = WeaponMechanics.getInstance().getWeaponConfigurations();
                var definition = config.getObject(weapon + ".Projectile", Projectile.class);
                for (boolean active : new boolean[]{false, true}) {
                    blocking[0] = active;
                    if (active) {
                        actor.startUsingItem(EquipmentSlot.HAND);
                        actor.setActiveItemRemainingTime(actor.getActiveItemRemainingTime() - 10);
                        check(actor.isBlocking(), scenario + " uses real native saber blocking state");
                    } else actor.clearActiveItem();
                    var bolt = definition.create(shooter, shooter.getEyeLocation(), new Vector(0, 0, -2),
                            tusken ? null : WeaponMechanicsAPI.generateWeapon(weapon), weapon, EquipmentSlot.HAND);
                    try {
                        WeaponMechanics.getInstance().getWeaponHandler().getDamageHandler().tryUse(
                                new me.deecaad.weaponmechanics.weapon.damage.ProjectileDamageSource(bolt, point), actor,
                                config.getDouble(weapon + ".Damage.Base_Damage"), EquipmentSlot.HAND);
                    } finally {bolt.remove();}
                }
                if (scenario.equals("cancelled")) check(!guards.containsKey(actor.getUniqueId()), "cancelled shot spends no durability");
                else {
                    check(expected[0] > 0 && Math.abs(guards.get(actor.getUniqueId()).heat() - Math.min(100, expected[0] * 5)) < 1e-8,
                            scenario + " reflection spends 5x unblocked vendor damage, capped at capacity: " + expected[0]);
                    check(damage(actor.getInventory().getItemInMainHand()) == Math.min(Material.SHIELD.getMaxDurability() - 1, Math.round(expected[0] * 5 / 100 * Material.SHIELD.getMaxDurability())),
                            scenario + " computed shot damage appears in durability bar");
                    if (scenario.equals("armored")) check(expected[0] < 15, "armor reduces intercepted damage and durability cost");
                    if (tusken) check(base[0] == (scenario.endsWith("5") ? 8 : 6), scenario + " reflection uses mob-level damage");
                }
                check(nativePlayer.getHealth() == 20, scenario + " reflected/cancelled shot does not hurt defender");
            } finally {
                org.bukkit.event.HandlerList.unregisterAll(observer);guards.remove(actor.getUniqueId());
                if (tusken) mobs.remove(shooter);else shooter.remove();
                nativePlayer.getInventory().clear();
            }
        }
    }
    private void verifyNativeBlocking(MandoPlugin plugin, World world) throws Exception {
        var point = new Location(world, -150, 300, -150, 0, 0);
        Player actor = nativeTestPlayer(world, point);
        actor.getInventory().setItem(0, plugin.items().create("vader"));
        actor.startUsingItem(EquipmentSlot.HAND);
        actor.setActiveItemRemainingTime(actor.getActiveItemRemainingTime() - 10);
        check(actor.isBlocking(), "native player is blocking before durability updates");
        var attacker = world.spawn(point.clone().add(0, 0, 4), ArmorStand.class);
        var arrow = world.spawn(point.clone().add(0, 1.5, 1), Arrow.class);
        var guardsField = SaberService.class.getDeclaredField("guards");guardsField.setAccessible(true);
        @SuppressWarnings("unchecked") var guards = (Map<UUID,net.mandomc.gameplay.saber.GuardState>)guardsField.get(sabers);
        try {
            double total = 0;
            for (double amount : new double[]{6, 12}) {
                var modifiers = new EnumMap<org.bukkit.event.entity.EntityDamageEvent.DamageModifier,Double>(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.class);
                modifiers.put(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE, amount);
                modifiers.put(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BLOCKING, -amount);
                Map<org.bukkit.event.entity.EntityDamageEvent.DamageModifier,com.google.common.base.Function<Double,Double>> functions = new EnumMap<>(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.class);
                functions.put(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BASE, value -> 0d);
                functions.put(org.bukkit.event.entity.EntityDamageEvent.DamageModifier.BLOCKING, value -> -value);
                var source = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.MOB_ATTACK).withDirectEntity(attacker).withCausingEntity(attacker).build();
                var event = new org.bukkit.event.entity.EntityDamageByEntityEvent(attacker, actor,
                        org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, modifiers, functions);
                Bukkit.getPluginManager().callEvent(event);total += amount * 5;
                check(guards.get(actor.getUniqueId()).heat() == total, "native blocking spends five times " + amount + " intercepted damage");
                check(actor.isBlocking(), "durability change preserves the native blocking action");
            }
            arrow.setShooter(attacker);arrow.setVelocity(new Vector(0, 0, -2));
            var arrowSource = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ARROW)
                    .withDirectEntity(arrow).withCausingEntity(attacker).build();
            var arrowHit = new org.bukkit.event.entity.EntityDamageByEntityEvent(arrow, actor,
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause.PROJECTILE, arrowSource, 7.5);
            Bukkit.getPluginManager().callEvent(arrowHit);
            check(arrowHit.isCancelled() && arrow.getShooter().equals(actor), "native arrow is reflected with defender ownership");
            check(guards.get(actor.getUniqueId()).heat() == Math.min(100, total + 7.5 * 5), "arrow reflection charges native damage at 5x exactly once, capped at empty");
        } finally {guards.remove(actor.getUniqueId());actor.getInventory().clear();attacker.remove();arrow.remove();}
    }
    private void verifyStunAndQuietRecovery(MandoPlugin plugin, World world) throws Exception {
        var actor = new TestPlayer("SaberStunTest", new Location(world, -150, 300, -150));
        actor.storage.setItem(0, plugin.items().create("vader"));
        var guardsField = SaberService.class.getDeclaredField("guards");guardsField.setAccessible(true);
        @SuppressWarnings("unchecked") var guards = (Map<UUID,net.mandomc.gameplay.saber.GuardState>)guardsField.get(sabers);
        var tickField = SaberService.class.getDeclaredField("tick");tickField.setAccessible(true);
        var update = SaberService.class.getDeclaredMethod("updateGuard", UUID.class, net.mandomc.gameplay.saber.GuardState.class);update.setAccessible(true);
        int now = tickField.getInt(sabers);
        var target = world.spawn(actor.location.clone().add(0, 0, 3), ArmorStand.class);
        var source = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
        try {
            check(sabers.settings().damageMultiplier() == 5 && sabers.settings().recoveryDelay() == 100,
                    "running server uses 5x damage cost and five-second quiet recovery");
            sabers.reflected(actor.player, 5.02);
            check(!sabers.launch(actor.player) && !sabers.busy(actor.player), "74.9 percent cannot launch or reserve the saber");
            check(actor.messages.getLast().contains("You are too tired to throw your saber"), "low charge uses requested throw feedback");
            var state = new net.mandomc.gameplay.saber.GuardState();state.hit(now, sabers.settings(), 5);
            guards.put(actor.id, state);
            check(sabers.launch(actor.player), "exactly 75 percent can launch");
            for (int t = 1; t <= 20; t++) {tickField.setInt(sabers, now + t);update.invoke(sabers, actor.id, state);}
            check(!sabers.busy(actor.player) && countSabers(actor) == 1, "throw reaching zero immediately ends flight and restores exactly one saber");
            check(!actor.data.has(new NamespacedKey(plugin,"saber_flight_item")), "zero-charge recall clears saved airborne ownership");
            check(actor.cooldown == 80 && !actor.blocking, "zero applies shield cooldown and clears active blocking");
            actor.blocking = true;
            check(!sabers.canReflect(actor.player, new Vector(0,0,-1)), "stunned saber cannot reflect even with forced blocking state");
            var swing = new org.bukkit.event.player.PlayerAnimationEvent(actor.player);sabers.swing(swing);
            check(swing.isCancelled(), "stunned saber cannot swing");
            check(actor.messages.getLast().contains("You are too tired to attack"), "stunned swing uses requested attack feedback");
            var use = new org.bukkit.event.player.PlayerInteractEvent(actor.player, org.bukkit.event.block.Action.RIGHT_CLICK_AIR,
                    actor.storage.getItem(0), null, org.bukkit.block.BlockFace.SELF, EquipmentSlot.HAND);
            sabers.interact(use);
            check(use.useItemInHand() == org.bukkit.event.Event.Result.DENY && !actor.blocking, "stunned saber cannot start blocking");
            check(actor.messages.getLast().contains("You are too tired to block"), "stunned block uses requested feedback");
            var attack = new org.bukkit.event.entity.EntityDamageByEntityEvent(actor.player, target,
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 10);
            sabers.attack(attack);check(attack.isCancelled(), "stunned saber cannot deal melee damage");
            var block = world.getBlockAt(-150,301,-150);
            var mine = new org.bukkit.event.block.BlockDamageEvent(actor.player, block, actor.storage.getItem(0), false);
            sabers.mine(mine);check(mine.isCancelled(), "stunned saber cannot start mining");
            var broken = new org.bukkit.event.block.BlockBreakEvent(block, actor.player);
            sabers.breakBlock(broken);check(broken.isCancelled(), "stunned saber cannot instantly break blocks");
            actor.storage.setItem(0, plugin.items().create("vader"));
            check(!sabers.launch(actor.player), "switching saber items cannot bypass stun");
            tickField.setInt(sabers, now + 110);
            var hurt = new org.bukkit.event.entity.EntityDamageEvent(actor.player,
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL, source, 2);
            sabers.hurt(hurt);
            tickField.setInt(sabers, now + 120);update.invoke(sabers, actor.id, state);
            check(state.heat() == 100 && state.disabled(now + 120), "taking damage restarts quiet timer and keeps empty saber stunned");
            tickField.setInt(sabers, now + 209);
            var cancelled = new org.bukkit.event.entity.EntityDamageEvent(actor.player,
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL, source, 0);
            sabers.hurt(cancelled);update.invoke(sabers, actor.id, state);
            check(state.heat() == 100, "no regeneration before a full five quiet seconds");
            tickField.setInt(sabers, now + 210);update.invoke(sabers, actor.id, state);
            check(state.heat() == 99.5 && !state.disabled(now + 210), "zero-damage activity does not delay slow recovery or stun release");
            var resumed = new org.bukkit.event.player.PlayerAnimationEvent(actor.player);sabers.swing(resumed);
            check(!resumed.isCancelled() && !sabers.launch(actor.player), "normal attacks recover before the 75-percent throw threshold");
            var hit = new org.bukkit.event.entity.EntityDamageByEntityEvent(actor.player, target,
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 2);
            sabers.damaged(hit);
            tickField.setInt(sabers, now + 309);update.invoke(sabers, actor.id, state);
            check(state.heat() == 99.5, "dealing damage also restarts five-second quiet timer");
            tickField.setInt(sabers, now + 310);update.invoke(sabers, actor.id, state);
            check(state.heat() == 99, "repair resumes at unchanged half-point per tick");
        } finally {sabers.finish(actor.id);guards.remove(actor.id);tickField.setInt(sabers, now);target.remove();}
    }
    private void verifyThrowContacts(MandoPlugin plugin, World world) throws Exception {
        Location point = new Location(world, -154, 300, -154, 0, 0);
        Player owner = nativeTestPlayer(world, point);
        owner.getInventory().setItem(0, plugin.items().create("vader"));
        ItemDisplay display = world.spawn(point.clone().add(0, 1, 0), ItemDisplay.class,
                entity -> {entity.setPersistent(false);entity.setGravity(false);});
        Cow cow = world.spawn(point.clone().add(0, .3, 4), Cow.class, entity -> {
            entity.setPersistent(false);entity.setAI(false);entity.setGravity(false);
            entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(200);
            entity.setHealth(200);
        });
        Cow other = world.spawn(point.clone().add(0, .3, 7), Cow.class,
                entity -> {entity.setPersistent(false);entity.setAI(false);entity.setGravity(false);});
        var flightClass = Class.forName("net.mandomc.gameplay.saber.SaberService$Flight");
        var constructor = flightClass.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var reserve = SaberService.class.getDeclaredMethod("reserve", flightClass);reserve.setAccessible(true);
        var flightsField = SaberService.class.getDeclaredField("flights");flightsField.setAccessible(true);
        @SuppressWarnings("unchecked") var flights = (Map<UUID,Object>) flightsField.get(sabers);
        var move = SaberService.class.getDeclaredMethod("move", flightClass, Vector.class);move.setAccessible(true);
        var find = SaberService.class.getDeclaredMethod("findTarget", Player.class);find.setAccessible(true);
        var track = SaberService.class.getDeclaredMethod("trackTarget", flightClass);track.setAccessible(true);
        var targetField = flightClass.getDeclaredField("target");targetField.setAccessible(true);
        var offsetField = flightClass.getDeclaredField("targetOffset");offsetField.setAccessible(true);
        var locationField = flightClass.getDeclaredField("location");locationField.setAccessible(true);
        var recallingField = flightClass.getDeclaredField("recalling");recallingField.setAccessible(true);
        org.bukkit.block.Block block = world.getBlockAt(-154, 301, -148);
        var oldBlock = block.getBlockData();
        var listener = new org.bukkit.event.Listener() {};
        try {
            // The farther cow is closer to the crosshair's horizontal ray (less vertical angle).
            check(find.invoke(sabers, owner) == other, "single target favors crosshair alignment over nearest mob");
            other.remove();
            check(find.invoke(sabers, owner) == cow, "visible mob inside aim cone acquires lock");
            Object flight = constructor.newInstance(owner, display, cow, sabers.settings());
            flights.put(owner.getUniqueId(), flight);reserve.invoke(sabers, flight);
            cow.teleport(cow.getLocation().add(.5, 0, 0));
            track.invoke(sabers, flight);
            Vector correction = (Vector) offsetField.get(flight);
            check(correction.length() > 0 && correction.length() <= .150001, "lock steers gently toward moving mob");
            cow.teleport(cow.getLocation().add(3, 0, 0));
            track.invoke(sabers, flight);
            check(targetField.get(flight) == null, "lock drops when mob moves beyond correction limit");
            cow.teleport(point.clone().add(0, .3, 4));
            cow.setNoDamageTicks(17);cow.setLastDamage(3);
            check((boolean) move.invoke(sabers, flight, new Vector(0, 0, 4)), "outgoing contact segment moves");
            double afterFirst = cow.getHealth();
            check(afterFirst < 200, "outward crossing deals real attributed damage");
            check(cow.getNoDamageTicks() == 17 && cow.getLastDamage() == 3, "saber contact preserves unrelated hurt immunity");
            move.invoke(sabers, flight, new Vector(0, 0, .1));
            check(cow.getHealth() == afterFirst, "continuous overlap does not deal another hit");
            move.invoke(sabers, flight, new Vector(0, 0, 2));
            move.invoke(sabers, flight, new Vector(0, 0, -3));
            check(cow.getHealth() < afterFirst, "return crossing damages same mob again within hurt window");
            double afterReturn = cow.getHealth();
            move.invoke(sabers, flight, new Vector(0, 0, -2));
            Bukkit.getPluginManager().registerEvent(org.bukkit.event.entity.EntityDamageByEntityEvent.class, listener,
                    org.bukkit.event.EventPriority.HIGHEST, (ignored, event) -> {
                        if (((org.bukkit.event.entity.EntityDamageByEntityEvent) event).getEntity() == cow)
                            ((org.bukkit.event.entity.EntityDamageByEntityEvent) event).setCancelled(true);
                    }, this);
            move.invoke(sabers, flight, new Vector(0, 0, 4));
            check(cow.getHealth() == afterReturn, "other plugins can cancel a repeat contact");
            org.bukkit.event.HandlerList.unregisterAll(listener);
            block.setType(Material.STONE, false);
            move.invoke(sabers, flight, new Vector(0, 0, 4));
            check((boolean) recallingField.get(flight) && ((Location) locationField.get(flight)).getZ() < -148,
                    "solid wall interrupts sweep before crossing it");
            cow.teleport(point.clone().add(0, .3, 8));
            check(find.invoke(sabers, owner) == null, "wall prevents acquiring mob behind it");
            sabers.finish(owner.getUniqueId());
            // Both endpoints can be outside the protected landing while the segment crosses it.
            display = world.spawn(new Location(world, -145, 301, -135), ItemDisplay.class,
                    entity -> {entity.setPersistent(false);entity.setGravity(false);});
            flight = constructor.newInstance(owner, display, null, sabers.settings());
            flights.put(owner.getUniqueId(), flight);reserve.invoke(sabers, flight);
            move.invoke(sabers, flight, new Vector(18, 0, 0));
            check(((Location) locationField.get(flight)).getX() < -144 && (boolean) recallingField.get(flight),
                    "swept path cannot cross protected zone between allowed endpoints");
            sabers.finish(owner.getUniqueId());
            block.setBlockData(oldBlock, false);
            cow.teleport(point.clone().add(0, .3, .95));cow.setHealth(200);cow.setVelocity(new Vector());
            other = world.spawn(point.clone().add(0, .3, -.95), Cow.class, entity -> {
                entity.setPersistent(false);entity.setAI(false);entity.setGravity(false);
                entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).setBaseValue(200);entity.setHealth(200);
            });
            display = world.spawn(point.clone().add(-3, 1, 0), ItemDisplay.class,
                    entity -> {entity.setPersistent(false);entity.setGravity(false);});
            flight = constructor.newInstance(owner, display, null, sabers.settings());
            flights.put(owner.getUniqueId(), flight);reserve.invoke(sabers, flight);
            final Vector[] knockback = {null};
            Bukkit.getPluginManager().registerEvent(io.papermc.paper.event.entity.EntityKnockbackEvent.class, listener,
                    org.bukkit.event.EventPriority.MONITOR, (ignored, event) -> {
                        var hit = (io.papermc.paper.event.entity.EntityKnockbackEvent) event;
                        if (hit.getEntity() == cow && !hit.isCancelled()) knockback[0] = hit.getKnockback();
                    }, this);
            move.invoke(sabers, flight, new Vector(6, 0, 0));
            check(cow.getHealth() < 200 && other.getHealth() < 200,
                    "blade edges cleave two nearby mobs while the pivot misses both bodies");
            check(knockback[0] != null && knockback[0].lengthSquared() > .01
                            && knockback[0].clone().setY(0).normalize().dot(new Vector(1, 0, 0)) > .98,
                    "accepted hit knockback follows blade travel rather than owner position: " + knockback[0]);
            org.bukkit.event.HandlerList.unregisterAll(listener);
            double health = cow.getHealth();
            Bukkit.getPluginManager().registerEvent(io.papermc.paper.event.entity.EntityKnockbackEvent.class, listener,
                    org.bukkit.event.EventPriority.HIGHEST, (ignored, event) -> {
                        var hit = (io.papermc.paper.event.entity.EntityKnockbackEvent) event;
                        if (hit.getEntity() == cow) hit.setCancelled(true);
                    }, this);
            cow.setVelocity(new Vector());
            move.invoke(sabers, flight, new Vector(-6, 0, 0));
            check(cow.getHealth() < health && cow.getVelocity().lengthSquared() < 1e-9,
                    "repeat blade-edge hit respects another plugin cancelling knockback");
        } finally {
            org.bukkit.event.HandlerList.unregisterAll(listener);
            block.setBlockData(oldBlock, false);
            sabers.finish(owner.getUniqueId());display.remove();cow.remove();other.remove();
            owner.getInventory().clear();
        }
    }

    private void verifyMotion(MandoPlugin plugin, World world) throws Exception {
        check(sabers.settings().range() == 18 && sabers.settings().spinDegrees() == 38,
                "runtime uses longer range and slightly slower spin");
        var flightClass = Class.forName("net.mandomc.gameplay.saber.SaberService$Flight");
        var constructor = flightClass.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var reserve = SaberService.class.getDeclaredMethod("reserve", flightClass);reserve.setAccessible(true);
        var flightsField = SaberService.class.getDeclaredField("flights");flightsField.setAccessible(true);
        @SuppressWarnings("unchecked") var flights = (Map<UUID,Object>) flightsField.get(sabers);
        var step = SaberService.class.getDeclaredMethod("step", flightClass);step.setAccessible(true);
        var locationField = flightClass.getDeclaredField("location");locationField.setAccessible(true);
        var origin = new Location(world, -150, 300, -150, 0, 0);
        var returnChunk = world.getChunkAt(-10, -9);
        returnChunk.addPluginChunkTicket(this);
        try {
            for (boolean walking : new boolean[]{false, true}) {
                var actor = new TestPlayer("SaberMotionTest", origin.clone());
                actor.storage.setItem(0, plugin.items().create("vader"));
                var display = world.spawn(actor.player.getEyeLocation(), ItemDisplay.class, entity -> {
                    entity.setPersistent(false);entity.setGravity(false);
                });
                try {
                    Object flight = constructor.newInstance(actor.player, display, null, sabers.settings());
                    flights.put(actor.id, flight);reserve.invoke(sabers, flight);
                    Location previous = (Location) locationField.get(flight);
                    double farthest = 0, largestStep = 0;
                    int ticks = 0;
                    while (sabers.busy(actor.player) && ticks++ < 100) {
                        if (walking) actor.location.add(-.18, 0, 0);
                        step.invoke(sabers, flight);
                        Location current = (Location) locationField.get(flight);
                        farthest = Math.max(farthest, current.getZ() - origin.getZ());
                        largestStep = Math.max(largestStep, previous.distance(current));
                        previous = current.clone();
                    }
                    if (walking) check(largestStep < 2, "walking owner does not cause a sudden return jump");
                    else check(farthest > 17.5, "real flight reaches the extended 18-block turnaround");
                    check(ticks < 65 && !sabers.busy(actor.player)
                                    && previous.distance(actor.player.getEyeLocation()) < .6,
                            walking ? "clean moving-hand catch restores ownership" : "clean stationary catch completes without timeout");
                } finally {
                    sabers.finish(actor.id);display.remove();
                }
            }
        } finally {
            returnChunk.removePluginChunkTicket(this);
        }
    }

    private void verifyAirborneOwnership(MandoPlugin plugin, World world) throws Exception {
        var flightClass = Class.forName("net.mandomc.gameplay.saber.SaberService$Flight");
        var constructor = flightClass.getDeclaredConstructors()[0];constructor.setAccessible(true);
        var reserve = SaberService.class.getDeclaredMethod("reserve", flightClass);reserve.setAccessible(true);
        var recover = SaberService.class.getDeclaredMethod("recoverStored", Player.class);recover.setAccessible(true);
        var flightsField = SaberService.class.getDeclaredField("flights");flightsField.setAccessible(true);
        @SuppressWarnings("unchecked") var flights = (Map<UUID,Object>) flightsField.get(sabers);
        var key = new NamespacedKey(plugin, "saber_flight_item");
        for (String mode : new String[]{"catch", "occupied", "death", "keep", "recover", "full"}) {
            var actor = new TestPlayer("SaberOwnershipTest", new Location(world, -150, 300, -150));
            ItemStack original = plugin.items().create("vader");
            var meta = original.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("Personal test saber"));
            original.setItemMeta(meta);actor.storage.setItem(0, original.clone());
            var display = world.spawn(actor.player.getEyeLocation(), ItemDisplay.class, entity -> {
                entity.setPersistent(false);entity.setGravity(false);
            });
            try {
                Object flight = constructor.newInstance(actor.player, display, null, sabers.settings());
                flights.put(actor.id, flight);reserve.invoke(sabers, flight);
                switch (mode) {
                    case "catch" -> {
                        var loose = world.dropItem(actor.location, new ItemStack(Material.STONE));
                        try {
                            var pickup = new org.bukkit.event.entity.EntityPickupItemEvent(actor.player, loose, 0);
                            sabers.pickup(pickup);check(pickup.isCancelled(), "pickup cannot fill the airborne saber slot");
                        } finally {loose.remove();}
                        sabers.finish(actor.id);sabers.finish(actor.id);
                        check(original.equals(actor.storage.getItem(0)) && countSabers(actor) == 1,
                                "catch restores original custom metadata exactly once");
                    }
                    case "occupied" -> {
                        actor.storage.setItem(0, new ItemStack(Material.STONE));sabers.finish(actor.id);
                        check(actor.storage.getItem(0).getType() == Material.STONE && countSabers(actor) == 1,
                                "return preserves a command-filled slot and restores saber elsewhere");
                    }
                    case "death", "keep" -> {
                        var source = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
                        var event = new org.bukkit.event.entity.PlayerDeathEvent(actor.player, source,
                                new ArrayList<>(), 0, net.kyori.adventure.text.Component.empty(), false);
                        event.setKeepInventory(mode.equals("keep"));sabers.death(event);sabers.finish(actor.id);
                        if (mode.equals("keep")) check(original.equals(actor.storage.getItem(0)) && event.getDrops().isEmpty(),
                                "keep-inventory death restores airborne saber without a drop");
                        else check(countSabers(actor) == 0 && event.getDrops().size() == 1 && event.getDrops().getFirst().equals(original),
                                "normal death drops airborne saber exactly once");
                    }
                    case "recover" -> {
                        flights.remove(actor.id);display.remove();
                        recover.invoke(sabers, actor.player);recover.invoke(sabers, actor.player);
                        check(original.equals(actor.storage.getItem(0)) && countSabers(actor) == 1,
                                "saved in-flight ownership recovers once with all item data");
                    }
                    case "full" -> {
                        for (int i = 0; i < 36; i++) actor.storage.setItem(i, new ItemStack(Material.STONE, 64));
                        sabers.finish(actor.id);
                        var drops = world.getNearbyEntities(actor.location, 1, 1, 1).stream()
                                .filter(entity -> entity instanceof Item item && actor.id.equals(item.getOwner()))
                                .map(entity -> (Item) entity).toList();
                        try {
                            check(drops.size() == 1 && drops.getFirst().getItemStack().equals(original),
                                    "full inventory returns one owner-bound saber drop without losing metadata");
                        } finally {drops.forEach(Item::remove);}
                    }
                }
                check(!actor.data.has(key), "completed " + mode + " clears stored flight ownership");
            } finally {sabers.finish(actor.id);display.remove();}
        }
    }

    private long countSabers(TestPlayer actor) {
        return Arrays.stream(actor.storage.getContents()).filter(Objects::nonNull).filter(sabers::isSaber).count();
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
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "SaberContactTest");
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
        playerClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }

    private void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);checks++;getLogger().info("PASS "+message);}
    private void cleanup(){if(sabers!=null&&player!=null)sabers.finish(player.id);if(shooter!=null)shooter.remove();projectiles.forEach(WeaponProjectile::remove);if(player!=null)player.location.getChunk().removePluginChunkTicket(this);}
    @Override public void onDisable(){cleanup();}
    private static final class TestPlayer {
        private int packRequests;
        private boolean forcedPack;
        private String packUrl;
        private final List<String> messages = new ArrayList<>();
        private Location location;
        private boolean blocking=true;
        private int cooldown;
        private final UUID id = UUID.randomUUID();
        private final Inventory storage = Bukkit.createInventory(null, 36);
        private final org.bukkit.persistence.PersistentDataContainer data = new ItemStack(Material.PAPER).getItemMeta().getPersistentDataContainer();
        private boolean allowed = true;
        private boolean online = true;
        private InventoryView view;
        private final Player player;
        private TestPlayer(String name, Location location) {
            this.location=location;
            var inventory = (PlayerInventory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {PlayerInventory.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getHeldItemSlot")) return 0;
                        if (method.getName().equals("getItemInMainHand")) return storage.getItem(0);
                        if (method.getName().equals("setItemInMainHand")) {storage.setItem(0,(ItemStack) args[0]);return null;}
                        if (method.getName().equals("getStorageContents")) return storage.getContents();
                        try { return Inventory.class.getMethod(method.getName(), method.getParameterTypes()).invoke(storage, args); }
                        catch (NoSuchMethodException ignored) { return fallback(method.getReturnType()); }
                    });
            player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "sendActionBar" -> {messages.add(args[0].toString());yield null;}
                    case "addResourcePack" -> {packRequests++;packUrl=(String)args[1];forcedPack=(boolean)args[4];yield null;}
                    case "getLocation" -> this.location.clone();
                    case "getEyeLocation" -> this.location.clone().add(0,1.62,0);
                    case "getWorld" -> this.location.getWorld();
                    case "isBlocking" -> blocking;
                    case "setCooldown" -> {cooldown=(int)args[1];yield null;}
                    case "getCooldown" -> cooldown;
                    case "getActiveItem" -> storage.getItem(0);
                    case "getActiveItemHand" -> EquipmentSlot.HAND;
                    case "clearActiveItem" -> {blocking=false;yield null;}
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "isOnline" -> online;
                    case "hasPermission" -> allowed;
                    case "getInventory" -> inventory;
                    case "getPersistentDataContainer" -> data;
                    case "getOpenInventory" -> view;
                    case "openInventory" -> { view = makeView((Inventory) args[0]); yield view; }
                    case "closeInventory" -> { view = makeView(Bukkit.createInventory(null, 9)); yield null; }
                    case "toString" -> name;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> fallback(method.getReturnType());
                };
            });
            view = makeView(Bukkit.createInventory(null, 9));
        }
        private InventoryView makeView(Inventory top) {
            return (InventoryView) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {InventoryView.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getTopInventory" -> top;
                        case "getBottomInventory" -> storage;
                        case "getPlayer" -> player;
                        case "getType" -> InventoryType.CHEST;
                        case "convertSlot" -> (int) args[0] < 54 ? args[0] : (int) args[0] - 54;
                        case "getInventory" -> (int) args[0] < 54 ? top : storage;
                        case "getItem" -> (int) args[0] < 54 ? top.getItem((int) args[0]) : storage.getItem((int) args[0] - 54);
                        default -> fallback(method.getReturnType());
                    });
        }
        private static Object fallback(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == double.class) return 0d;
            if (type == float.class) return 0f;
            if (type == long.class) return 0L;
            return null;
        }
    }
}
