package net.mandomc.verification;

import com.ticxo.modelengine.api.ModelEngineAPI;

import me.deecaad.core.compatibility.HitBox;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent;

import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.vehicles.VehicleService;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Run only in a disposable isolated Paper copy. Exercises real vendor/native damage and a full
 * minute timer.
 */
public final class VehicleProbe extends JavaPlugin {
    private MandoPlugin plugin;
    private World world;
    private final List<BlockState> blocks = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final List<UUID> ids = new ArrayList<>();
    private final Listener observer = new Listener() {};
    private int checks;
    private UUID survivor;
    private Location blast;
    private List<BlockState> damaged;
    private long explodedAt;

    @Override
    public void onEnable() {
        later(120, this::begin);
    }

    private void later(long delay, Checked action) {
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            try {
                                action.run();
                            } catch (Throwable e) {
                                fail(e);
                            }
                        },
                        delay);
    }

    private interface Checked {
        void run() throws Exception;
    }

    private void begin() throws Exception {
        plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(
                plugin.vehicles() != null && plugin.explosions() != null,
                "vehicle and explosion services initialized");
        world = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        var phase = new java.io.File(getDataFolder(), "restart.yml");
        if (phase.exists()) {
            verifyRestart(YamlConfiguration.loadConfiguration(phase));
            return;
        }
        check(!new java.io.File(plugin.getDataFolder(), "combat.yml").exists(), "old config moved");
        check(
                new java.io.File(plugin.getDataFolder(), "combat/combat.yml").exists(),
                "organized config loaded");
        for (int x = 790; x <= 950; x++)
            for (int z = 790; z <= 820; z++) {
                var block = world.getBlockAt(x, 280, z);
                blocks.add(block.getState());
                block.setType(Material.STONE, false);
            }
        int index = 0;
        for (String type : List.of("speederbike", "xwing", "tiefighter", "uwing")) {
            Location point = new Location(world, 800.5 + index * 35, 281, 800.5);
            Player owner = player(point.clone().add(0, 1, 10));
            var item = plugin.items().create(type);
            check(item != null, "catalog item " + type);
            UUID id = plugin.vehicles().deploy(owner, item, point);
            check(id != null, "deploy " + type);
            ids.add(id);
            check(item.getAmount() == 0, "one item consumed " + type);
            var entity = plugin.vehicles().entity(id);
            check(entity != null, "live base " + type);
            check(
                    !entity.isPersistent(),
                    "runtime entity cannot duplicate journal at restart " + type);
            index++;
        }
        later(20, this::vehicles);
    }

    private void vehicles() throws Exception {
        int index = 0;
        for (UUID id : ids) {
            var entity = plugin.vehicles().entity(id);
            var modeled = ModelEngineAPI.getModeledEntity(entity);
            check(
                    modeled != null && !modeled.isBaseEntityVisible(),
                    "attached hidden-base model " + index);
            check(
                    entity.getBoundingBox().getWidthX() > 1
                            && entity.getBoundingBox().getHeight() > .7,
                    "model hitbox replaces pig bounds " + index);
            check(
                    !plugin.vehicles().mount(players.get((index + 1) % 4), id, true),
                    "non-placer pilot denied " + index);
            index++;
        }
        var owner = players.get(0);
        var bike = plugin.vehicles().entity(ids.get(0));
        check(plugin.vehicles().mount(owner, ids.get(0), true), "placer can pilot");
        check(!plugin.vehicles().pack(owner, ids.get(0)), "occupied vehicle cannot pack");
        var controller = ModelEngineAPI.getMountPairManager().getController(owner.getUniqueId());
        check(controller != null, "custom surface controller installed");
        controller.setInput(
                new com.ticxo.modelengine.api.mount.controller.MountController.MountInput(
                        0, 1, false, false));
        var model = ModelEngineAPI.getModeledEntity(bike);
        var active = model.getModels().values().iterator().next();
        controller.updateDriverMovement(model.getBase().getMoveController(), active);
        check(
                bike.getVelocity().lengthSquared() > 0,
                "surface controller produces native movement");
        bike.setVelocity(new Vector());
        ModelEngineAPI.getModeledEntity(bike)
                .getModels()
                .values()
                .iterator()
                .next()
                .getMountManager()
                .orElseThrow()
                .dismountDriver();
        check(
                ModelEngineAPI.getMountPairManager().getController(owner.getUniqueId()) == null,
                "driver dismount clears movement controller");
        var uwing = plugin.vehicles().entity(ids.get(3));
        var passenger = player(uwing.getLocation().add(0, 1, 10));
        check(
                plugin.vehicles().mount(passenger, ids.get(3), false),
                "unrelated passenger can board U-Wing");
        var mount =
                ModelEngineAPI.getModeledEntity(uwing)
                        .getModels()
                        .values()
                        .iterator()
                        .next()
                        .getMountManager()
                        .orElseThrow();
        check(mount.getDriver() == null, "passenger does not become pilot");
        mount.dismountAll();
        check(
                plugin.vehicles().mount(players.get(3), ids.get(3), true),
                "aircraft placer can pilot");
        var flight =
                ModelEngineAPI.getMountPairManager().getController(players.get(3).getUniqueId());
        flight.setInput(
                new com.ticxo.modelengine.api.mount.controller.MountController.MountInput(
                        0, 1, true, false));
        var airModel = ModelEngineAPI.getModeledEntity(uwing);
        flight.updateDriverMovement(
                airModel.getBase().getMoveController(),
                airModel.getModels().values().iterator().next());
        check(uwing.getVelocity().getY() > 0, "aerial controller applies native climb velocity");
        mount.dismountDriver();
        Player attacker = player(bike.getLocation().add(0, 1, 8));
        double health = bike.getHealth();
        bike.damage(12, attacker);
        check(
                Math.abs(health - bike.getHealth() - 12) < .001,
                "native melee damage applies exactly once");
        check(!plugin.vehicles().pack(owner, ids.get(0)), "recently damaged vehicle cannot pack");
        plugin.combat().clear(attacker);
        final boolean[] cancel = {true};
        Bukkit.getPluginManager()
                .registerEvent(
                        EntityDamageEvent.class,
                        observer,
                        EventPriority.NORMAL,
                        (l, e) -> {
                            if (cancel[0] && ((EntityDamageEvent) e).getEntity().equals(bike))
                                ((EntityDamageEvent) e).setCancelled(true);
                        },
                        this,
                        true);
        health = bike.getHealth();
        bike.damage(30, attacker);
        check(bike.getHealth() == health, "cancelled melee leaves health unchanged");
        check(!plugin.combat().tagged(attacker), "cancelled hit creates no combat tag");
        cancel[0] = false;
        verifyBlaster(attacker, bike);
        Player safe = player(world.getSpawnLocation());
        health = bike.getHealth();
        bike.damage(20, safe);
        check(bike.getHealth() == health, "safe-zone attacker cannot damage a vehicle");
        var empty = plugin.items().create("speederbike");
        check(
                plugin.vehicles().deploy(safe, empty, world.getSpawnLocation()) == null,
                "safe-zone placement denied");
        verifyExplosions();
    }

    private void verifyBlaster(Player attacker, LivingEntity victim) throws Exception {
        var definition =
                WeaponMechanics.getInstance()
                        .getWeaponConfigurations()
                        .getObject("E11.Projectile", Projectile.class);
        var start = victim.getLocation().add(0, .7, -8);
        var direction = new Vector(0, 0, 1);
        var bolt =
                definition.create(
                        attacker,
                        start,
                        direction,
                        plugin.items().create("e11"),
                        "E11",
                        EquipmentSlot.HAND);
        var box = HitBox.getHitbox(victim.getBoundingBox());
        box.setLivingEntity(victim);
        var hit = box.rayTrace(start.toVector(), direction);
        check(hit != null, "E11 ray intersects actual vehicle hitbox");
        final double[] finalDamage = {0};
        final int[] nativeHits = {0};
        Bukkit.getPluginManager()
                .registerEvent(
                        WeaponDamageEntityEvent.class,
                        observer,
                        EventPriority.MONITOR,
                        (l, e) -> {
                            var damage = (WeaponDamageEntityEvent) e;
                            if (damage.getVictim().equals(victim))
                                finalDamage[0] = damage.getFinalDamage();
                        },
                        this,
                        true);
        Bukkit.getPluginManager()
                .registerEvent(
                        EntityDamageEvent.class,
                        observer,
                        EventPriority.MONITOR,
                        (l, e) -> {
                            if (((EntityDamageEvent) e).getEntity().equals(victim)) nativeHits[0]++;
                        },
                        this,
                        true);
        double before = victim.getHealth();
        WeaponMechanics.getInstance().getWeaponHandler().getHitHandler().handleHit(hit, bolt);
        bolt.remove();
        check(finalDamage[0] > 0, "WeaponMechanics resolves positive vehicle damage");
        check(nativeHits[0] == 1, "one native damage event per blaster hit");
        check(
                Math.abs(before - victim.getHealth() - finalDamage[0]) < .001,
                "blaster damage applied once");
        double realBefore = victim.getHealth();
        var realStart = victim.getLocation().add(0, .7, -8);
        var movingBolt =
                definition.create(
                        attacker,
                        realStart,
                        new Vector(0, 0, 1),
                        plugin.items().create("e11"),
                        "E11",
                        EquipmentSlot.HAND);
        WeaponMechanics.getInstance().getProjectileSpawner().spawn(movingBolt);
        later(
                10,
                () -> {
                    check(
                            victim.getHealth() < realBefore,
                            "scheduled E11 projectile physically hits vehicle");
                    check(nativeHits[0] == 2, "scheduled projectile adds exactly one hit");
                    movingBolt.remove();
                });
    }

    private void verifyExplosions() throws Exception {
        blast = new Location(world, 970.5, 284.5, 800.5);
        for (int x = 964; x <= 977; x++)
            for (int y = 280; y <= 288; y++)
                for (int z = 794; z <= 807; z++) {
                    var block = world.getBlockAt(x, y, z);
                    blocks.add(block.getState());
                    block.setType(Material.STONE, false);
                }
        var chest = world.getBlockAt(970, 284, 800);
        chest.setType(Material.CHEST, false);
        ((org.bukkit.block.Chest) chest.getState())
                .getBlockInventory()
                .addItem(new org.bukkit.inventory.ItemStack(Material.DIAMOND, 3));
        var bedrock = world.getBlockAt(971, 284, 800);
        bedrock.setType(Material.BEDROCK, false);
        var source = world.spawn(blast, TNTPrimed.class, t -> t.setFuseTicks(2000));
        source.setGravity(false);
        var preview = plugin.explosions().candidates(blast);
        check(preview.size() > 64, "more than cap eligible blocks available");
        var originals = preview.stream().map(org.bukkit.block.Block::getState).toList();
        Bukkit.getPluginManager()
                .registerEvent(
                        EntityExplodeEvent.class,
                        observer,
                        EventPriority.MONITOR,
                        (l, e) -> {
                            var event = (EntityExplodeEvent) e;
                            getLogger()
                                    .info(
                                            "BLAST EVENT: result="
                                                    + event.getExplosionResult()
                                                    + ", cancelled="
                                                    + event.isCancelled()
                                                    + ", blocks="
                                                    + event.blockList().size());
                        },
                        this,
                        false);
        world.createExplosion(blast, 4, false, true, source);
        source.remove();
        explodedAt = System.currentTimeMillis();
        damaged = originals.stream().filter(s -> s.getBlock().getType().isAir()).toList();
        check(
                damaged.size() == 64,
                "native explosion removes exactly 64 eligible blocks; actual="
                        + damaged.size()
                        + ", pending="
                        + plugin.explosions().pendingCount());
        check(plugin.explosions().pendingCount() == 64, "one journal entry per destroyed block");
        for (var s : damaged)
            check(
                    s.getLocation().add(.5, .5, .5).distanceSquared(blast) <= 16,
                    "damage stays in four-block radius");
        check(chest.getType() == Material.CHEST, "container preserved");
        check(
                ((org.bukkit.block.Chest) chest.getState())
                        .getBlockInventory()
                        .contains(Material.DIAMOND, 3),
                "container inventory preserved");
        check(bedrock.getType() == Material.BEDROCK, "unbreakable block preserved");
        final boolean[] cancelled = {true};
        Bukkit.getPluginManager()
                .registerEvent(
                        EntityExplodeEvent.class,
                        observer,
                        EventPriority.HIGH,
                        (l, e) -> {
                            if (cancelled[0]) ((EntityExplodeEvent) e).setCancelled(true);
                        },
                        this,
                        true);
        Bukkit.getPluginManager()
                .registerEvent(
                        org.bukkit.event.block.BlockExplodeEvent.class,
                        observer,
                        EventPriority.HIGH,
                        (l, e) -> {
                            if (cancelled[0])
                                ((org.bukkit.event.block.BlockExplodeEvent) e).setCancelled(true);
                        },
                        this,
                        true);
        int pending = plugin.explosions().pendingCount();
        world.createExplosion(blast.clone().add(5, 0, 0), 4, false, true);
        check(
                plugin.explosions().pendingCount() == pending,
                "cancelled blast creates no restoration entries");
        cancelled[0] = false;
        var protectedCenter = world.getSpawnLocation();
        var protectedBefore = plugin.explosions().candidates(protectedCenter);
        check(protectedBefore.isEmpty(), "WorldGuard safe zone yields no destructible blocks");
        var dead = plugin.vehicles().entity(ids.get(1));
        var killer = player(dead.getLocation().add(0, 2, 12));
        final int[] blasts = {0};
        Bukkit.getPluginManager()
                .registerEvent(
                        EntityExplodeEvent.class,
                        observer,
                        EventPriority.MONITOR,
                        (l, e) -> blasts[0]++,
                        this,
                        true);
        dead.damage(1000, killer);
        check(
                plugin.vehicles().entity(ids.get(1)) == null,
                "vehicle death removes active identity");
        check(blasts[0] == 1, "vehicle death creates exactly one attributed native explosion");
        check(
                plugin.explosions().pendingCount() > pending,
                "vehicle wreck explosion damages nearby terrain");
        check(!dead.isValid(), "dead vehicle base removed");
        verifyWeaponExplosion(killer);
        later(320, this::pack);
        later(
                1180,
                () ->
                        check(
                                damaged.stream().allMatch(s -> s.getBlock().getType().isAir()),
                                "holes remain before the one-minute deadline"));
        later(1240, this::finish);
    }

    private void verifyWeaponExplosion(Player shooter) {
        Location point = new Location(world, 1020.5, 283.5, 800.5);
        for (int x = 1018; x <= 1022; x++)
            for (int y = 281; y <= 285; y++)
                for (int z = 798; z <= 802; z++) {
                    var b = world.getBlockAt(x, y, z);
                    blocks.add(b.getState());
                    b.setType(Material.STONE, false);
                }
        var projectile =
                WeaponMechanics.getInstance()
                        .getWeaponConfigurations()
                        .getObject("E11.Projectile", Projectile.class)
                        .create(
                                shooter,
                                point,
                                new Vector(0, 0, 1),
                                plugin.items().create("e11"),
                                "E11",
                                EquipmentSlot.HAND);
        var event =
                new me.deecaad.weaponmechanics.weapon.weaponevents.ProjectileExplodeEvent(
                        projectile,
                        new ArrayList<>(List.of(point.getBlock())),
                        null,
                        new it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap<>(),
                        null);
        Bukkit.getPluginManager().callEvent(event);
        check(event.getBlocks().isEmpty(), "WeaponMechanics native block damage is disabled");
        later(
                2,
                () -> {
                    check(
                            point.getBlock().getType().isAir(),
                            "WeaponMechanics explosion routed into shared restoration");
                    check(
                            plugin.explosions().reserved(point.getBlock()),
                            "vendor explosion uses shared durable journal");
                    projectile.remove();
                });
    }

    private void pack() throws Exception {
        var owner = players.get(0);
        plugin.combat().clear(owner);
        var bike = plugin.vehicles().entity(ids.get(0));
        double remaining = bike.getHealth();
        var info =
                ModelEngineAPI.getModeledEntity(bike)
                        .getModels()
                        .values()
                        .iterator()
                        .next()
                        .getMountManager()
                        .orElseThrow();
        getLogger()
                .info(
                        "PACK TRACE: ground="
                                + bike.isOnGround()
                                + ", velocity="
                                + bike.getVelocity()
                                + ", position="
                                + bike.getLocation()
                                + ", riders="
                                + info.hasRiders()
                                + ", combat="
                                + plugin.combat().tagged(owner)
                                + ", slot="
                                + owner.getInventory().firstEmpty());
        check(plugin.vehicles().pack(owner, ids.get(0)), "landed empty cooled-down vehicle packs");
        var packed =
                Arrays.stream(owner.getInventory().getContents())
                        .filter(Objects::nonNull)
                        .filter(i -> "speederbike".equals(plugin.items().identify(i)))
                        .findFirst()
                        .orElseThrow();
        var stale = packed.clone();
        var location = new Location(world, 800.5, 281, 800.5);
        UUID redeployed = plugin.vehicles().deploy(owner, packed, location);
        check(ids.get(0).equals(redeployed), "packing preserves authoritative identity");
        check(
                Math.abs(plugin.vehicles().entity(redeployed).getHealth() - remaining) < .001,
                "packing preserves damage");
        var thief = player(location.clone().add(0, 1, 9));
        check(
                plugin.vehicles().deploy(thief, stale, location.clone().add(10, 0, 0)) == null,
                "stale copied item cannot duplicate deployed vehicle");
        survivor = redeployed;
    }

    private void finish() throws Exception {
        check(System.currentTimeMillis() - explodedAt >= 60000, "full real minute elapsed");
        for (var original : damaged)
            check(
                    original.getBlockData().equals(original.getBlock().getBlockData()),
                    "original block state regenerated");
        plugin.explosions().restore(true);
        var data = new YamlConfiguration();
        data.set("id", survivor.toString());
        data.set("health", plugin.vehicles().entity(survivor).getHealth());
        getDataFolder().mkdirs();
        data.save(new java.io.File(getDataFolder(), "restart.yml"));
        getLogger()
                .info(
                        "VEHICLE PROBE PHASE ONE PASSED: "
                                + checks
                                + " assertions; restart to check persistence");
        cleanup();
        Bukkit.shutdown();
    }

    private void verifyRestart(YamlConfiguration state) {
        UUID id = UUID.fromString(state.getString("id"));
        var entity = plugin.vehicles().entity(id);
        check(entity != null, "deployed vehicle restored on restart");
        check(
                Math.abs(entity.getHealth() - state.getDouble("health")) < .001,
                "health preserved across restart");
        check(
                world.getEntities().stream()
                                .filter(
                                        e ->
                                                id.toString()
                                                        .equals(
                                                                e.getPersistentDataContainer()
                                                                        .get(
                                                                                VehicleService.ID,
                                                                                org.bukkit
                                                                                        .persistence
                                                                                        .PersistentDataType
                                                                                        .STRING)))
                                .count()
                        == 1,
                "only one entity restored per identity");
        check(plugin.explosions().pendingCount() == 0, "shutdown restoration journal is empty");
        getLogger().info("VEHICLE PROBE RESTART PASSED: " + checks + " assertions");
        Bukkit.shutdown();
    }

    private Player player(Location l) throws Exception {
        var p = nativeTestPlayer(world, l);
        players.add(p);
        return p;
    }

    private void check(boolean ok, String reason) {
        if (!ok) throw new IllegalStateException(reason);
        checks++;
        getLogger().info("PASS " + reason);
    }

    private void fail(Throwable e) {
        getLogger().log(java.util.logging.Level.SEVERE, "VEHICLE PROBE FAILED", e);
        cleanup();
        Bukkit.shutdown();
    }

    private void cleanup() {
        HandlerList.unregisterAll(observer);
        if (plugin != null && plugin.combat() != null) players.forEach(plugin.combat()::clear);
        for (var b : blocks) b.update(true, false);
        blocks.clear();
    }

    @Override
    public void onDisable() {
        cleanup();
    }

    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry
        // or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server =
                Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile =
                profileClass
                        .getConstructor(UUID.class, String.class)
                        .newInstance(UUID.randomUUID(), "VehicleTest");
        Object handle =
                playerClass
                        .getConstructor(serverClass, levelClass, profileClass, infoClass)
                        .newInstance(
                                server,
                                level,
                                profile,
                                infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object flow = flowClass.getField("SERVERBOUND").get(null);
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Object connection = connectionClass.getConstructor(flowClass).newInstance(flow);
        Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie =
                cookieClass
                        .getMethod("createInitial", profileClass, boolean.class)
                        .invoke(null, profile, false);
        Class<?> listenerClass =
                Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object packetListener =
                listenerClass
                        .getConstructor(serverClass, connectionClass, playerClass, cookieClass)
                        .newInstance(server, connection, handle, cookie);
        playerClass.getField("connection").set(handle, packetListener);
        // Isolated players have no network client to send its loaded acknowledgement.
        var loadTimer = listenerClass.getDeclaredField("clientLoadedTimeoutTimer");
        loadTimer.setAccessible(true);
        loadTimer.setInt(packetListener, 0);
        var waiting = listenerClass.getDeclaredField("waitingForRespawn");
        waiting.setAccessible(true);
        waiting.setBoolean(packetListener, false);
        playerClass
                .getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
}
