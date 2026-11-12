package net.mandomc.verification;

import com.ticxo.modelengine.api.ModelEngineAPI;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import java.nio.file.Files;
import java.util.*;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.integrations.mobs.*;
import net.mandomc.integrations.models.HerdModels;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Run only in a disposable Paper world with MythicMobs and ModelEngine; never on a player server. */
public final class HerdProbe extends JavaPlugin implements Listener {
    private MythicMobAccess mobs;
    private HerdBabies babies;
    private HerdTempt tempt;
    private LivingEntity growing;
    private Mob foodMob;
    private final Map<UUID, List<ItemStack>> drops = new HashMap<>();
    private final List<String> checks = new ArrayList<>();
    private Location point;
    private java.nio.file.Path state;
    private record Case(String type, LivingEntity adult, LivingEntity baby) {}
    private final List<Case> cases = new ArrayList<>();
    private ItemCatalog items;
    private final List<LivingEntity[]> collisionPairs = new ArrayList<>();

    @Override public void onEnable() {
        if (Bukkit.getPort() != 25576 || !Bukkit.getWorlds().getFirst().getName().equals("herd_test")) {
            getLogger().severe("HerdProbe requires the disposable herd_test world on localhost port 25576");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { start(); } catch (Throwable error) { fail(error); }
        }, 120);
    }

    private void start() throws Exception {
        mobs = new MythicMobAccess();
        babies = new HerdBabies(this, mobs);
        new HerdModels(this);
        tempt = new HerdTempt(this, mobs, Material.HAY_BLOCK, Material.CACTUS);
        for (String resource : List.of("items/catalog.yml", "items/definitions/materials.yml",
                "items/definitions/dewback.yml")) {
            if (!getDataFolder().toPath().resolve(resource).toFile().exists()) saveResource(resource, false);
        }
        var dropFile = getDataFolder().toPath().resolve("mobs/drops/dewback.yml");
        Files.createDirectories(dropFile.getParent());
        Files.writeString(dropFile, "schema-version: 1\nitem: dewback_leg\nchance: 1.0\n");
        items = new ItemCatalog(getDataFolder().toPath());
        items.reload();
        var hide = new org.bukkit.configuration.file.YamlConfiguration();
        hide.set("minimum", 1); hide.set("maximum", 3);
        new BanthaDrops(this, items, hide);
        new DewbackDrops(this, items);
        Bukkit.getPluginManager().registerEvents(this, this);
        var world = Bukkit.getWorlds().getFirst();
        point = new Location(world, 0.5, 10, 0.5);
        for (int x=-16;x<=16;x++) for (int z=-16;z<=16;z++) {
            world.getBlockAt(x,9,z).setType(Material.SANDSTONE);
            // The wall rejection check persists blocks across disposable-server runs.
            for (int y=10;y<16;y++) world.getBlockAt(x,y,z).setType(Material.AIR);
        }
        world.getChunkAt(point).addPluginChunkTicket(this);
        state = getDataFolder().toPath().resolve("restart-uuid.txt");
        if (Files.exists(state)) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    var saved = Bukkit.getEntity(UUID.fromString(Files.readString(state).trim()));
                    check(saved instanceof Ageable && !((Ageable) saved).isAdult(), "native baby age survives restart");
                    check("Bantha".equals(mobs.type(saved)), "Mythic identity survives restart");
                    check(saved.isPersistent(), "offspring persistence survives restart");
                    int loadedAge = ((Ageable) saved).getAge();
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        try {
                            scaled((LivingEntity) saved, 0.5);
                            check(((Ageable) saved).getAge() > loadedAge, "native growth resumes after restart");
                            saved.remove();
                            finish("PASSED-restart.txt");
                        } catch (Throwable error) { fail(error); }
                    }, 10);
                } catch (Throwable error) { fail(error); }
            }, 10);
            return;
        }
        for (var entity : world.getLivingEntities()) {
            if (net.mandomc.gameplay.mobs.HerdAge.herd(mobs.type(entity))) mobs.remove(entity);
        }
        for (String type : List.of("Bantha", "Dewback")) {
            var adult = mobs.spawn(type, point, 3);
            var baby = babies.spawn(type, point.clone().add(5,0,0), 3);
            adult.setAI(false); baby.setAI(false);
            check(((Ageable) adult).isAdult(), type + " natural spawn stays adult");
            check(!((Ageable) baby).isAdult(), type + " offspring has native baby age");
            cases.add(new Case(type, adult, baby));
        }
        foodMob = (Mob) mobs.spawn("Bantha", point, 1);
        foodMob.setAI(false);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { afterModels(); } catch (Throwable error) { fail(error); }
        }, 10);
    }

    private void afterModels() throws Exception {
        var world = point.getWorld();
        for (var sample : cases) {
            var type = sample.type(); var adult = sample.adult(); var baby = sample.baby();
            scaled(adult, 1); scaled(baby, .5);
            var before = adult.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue();
            check(before == baby.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getBaseValue(),
                    type + " age does not alter configured level health");
            adult.setHealth(0); baby.setHealth(0);
            var adultDrops = drops.get(adult.getUniqueId());
            var babyDrops = drops.get(baby.getUniqueId());
            check(babyDrops != null && babyDrops.isEmpty(), type + " baby has zero final death drops");
            if (type.equals("Bantha")) {
                int wool = adultDrops.stream().filter(i -> i.getType() == Material.BROWN_WOOL).mapToInt(ItemStack::getAmount).sum();
                check(wool >= 1 && wool <= 3, "adult Bantha gives 1–3 vanilla brown wool");
                check(adultDrops.stream().anyMatch(i -> "bantha_hide".equals(items.identify(i))), "adult Bantha retains hide");
            } else {
                check(adultDrops.stream().anyMatch(i -> "dewback_leg".equals(items.identify(i))), "adult Dewback retains food");
            }
        }
        var ordinary = world.spawn(point, Pig.class);
        ordinary.setBaby(); ordinary.setHealth(0);
        check(drops.get(ordinary.getUniqueId()).stream().anyMatch(i -> i.getType() == Material.EMERALD),
                "ordinary baby animals keep their separate death pipeline");
        for (String species : List.of("Bantha", "Dewback")) {
            var adult = mobs.spawn(species, point.clone().add(-8,0,0), 1);
            var baby = babies.spawn(species, point.clone().add(-4,0,0), 1);
            adult.setAI(false); baby.setAI(false);
            collisionPairs.add(new LivingEntity[] { adult, baby });
        }
        growing = babies.spawn("Dewback", point, 1);
        ((Ageable) growing).setAge(-2);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                for (var pair : collisionPairs) {
                    var a = pair[0].getBoundingBox(); var b = pair[1].getBoundingBox();
                    check(Math.abs(a.getWidthX() / 2 - b.getWidthX()) < .01
                            && Math.abs(a.getHeight() / 2 - b.getHeight()) < .01
                            && Math.abs(a.getWidthZ() / 2 - b.getWidthZ()) < .01,
                            mobs.type(pair[0]) + " actual collision is uniformly half adult size");
                    mobs.remove(pair[0]); mobs.remove(pair[1]);
                }
                check(((Ageable) growing).isAdult(), "native baby grows into adult");
                scaled(growing, 1);
                mobs.remove(growing);
                food();
            } catch (Throwable error) { fail(error); }
        }, 10);
    }

    private void scaled(LivingEntity entity, double expected) {
        var model = ModelEngineAPI.getModeledEntity(entity).getModels().values().iterator().next();
        check(model.getScale().x() == expected && model.getScale().y() == expected && model.getScale().z() == expected,
                "uniform visual scale " + expected);
        check(model.getHitboxScale().x() == expected, "matching hitbox scale " + expected);
    }

    private void food() throws Exception {
        var mob = foodMob;
        mob.setAI(true);
        var player = nativeTestPlayer(point.getWorld(), point.clone().add(8,0,0));
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object level = point.getWorld().getClass().getMethod("getHandle").invoke(point.getWorld());
        level.getClass().getMethod("addNewPlayer", handle.getClass()).invoke(level, handle);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                foodChecks(mob, player, handle, level);
                var baby = babies.spawn("Bantha", point.clone().add(0,0,5), 2);
                ((Ageable) baby).setAge(-24000);
                Files.writeString(state, baby.getUniqueId().toString());
                finish("PASSED.txt");
            } catch (Throwable error) { fail(error); }
        }, 10);
    }

    private void foodChecks(Mob mob, Player player, Object handle, Object level) throws Exception {
        try {
            var goal = (HerdTempt.FoodGoal) Bukkit.getMobGoals().getGoal(mob, HerdTempt.FoodGoal.KEY);
            check(goal != null, "food goal attaches after real Mythic model setup");
            for (String species : List.of("Bantha", "Dewback")) {
                var baby = babies.spawn(species, point.clone().add(1,0,0), 2);
                baby.damage(10000, player);
                check(drops.get(baby.getUniqueId()).isEmpty(), species + " player kill gives no baby loot");
            }
            player.getInventory().setItemInMainHand(new ItemStack(Material.HAY_BLOCK));
            check(goal.accepts(player), "main hand Bantha food attracts");
            check(goal.shouldActivate(), "native world search finds food holder");
            goal.start(); goal.tick();
            getLogger().info("Native food path: ground=" + mob.isOnGround() + " width=" + mob.getWidth()
                    + " location=" + mob.getLocation() + " target=" + player.getLocation());
            check(mob.getPathfinder().hasPath(), "food uses real native pathfinding");
            player.getInventory().setItemInMainHand(new ItemStack(Material.CACTUS));
            check(!goal.shouldStayActive(), "switching to other species food stops attraction");
            goal.stop(); check(!mob.getPathfinder().hasPath(), "food removal clears native path");
            player.getInventory().setItemInOffHand(new ItemStack(Material.HAY_BLOCK));
            check(goal.accepts(player), "off hand food attracts");
            player.setGameMode(GameMode.SPECTATOR);
            check(!goal.accepts(player), "spectators cannot attract");
            player.setGameMode(GameMode.SURVIVAL);
            for(int y=10;y<14;y++) for(int z=-2;z<=2;z++) point.getWorld().getBlockAt(4,y,z).setType(Material.STONE);
            check(!goal.accepts(player), "solid wall blocks attraction");
            var dewback = (Mob) mobs.spawn("Dewback", point.clone().add(10,0,0), 1);
            check(new HerdTempt.FoodGoal(dewback, Material.CACTUS).accepts(player), "Dewback follows its cactus");
            mobs.remove(dewback);
        } finally {
            Class<?> reason = Class.forName("net.minecraft.world.entity.Entity$RemovalReason");
            level.getClass().getMethod("removePlayerImmediately", handle.getClass(), reason)
                    .invoke(level, handle, reason.getField("DISCARDED").get(null));
            mobs.remove(mob);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL) public void customLoot(MythicMobDeathEvent event) {
        event.getDrops().add(new ItemStack(Material.DIAMOND));
    }
    @EventHandler(priority = EventPriority.NORMAL) public void vanillaLoot(EntityDeathEvent event) {
        event.getDrops().add(new ItemStack(Material.EMERALD)); event.setDroppedExp(9);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void finalLoot(EntityDeathEvent event) {
        drops.put(event.getEntity().getUniqueId(), List.copyOf(event.getDrops()));
        if (event.getEntity().getPersistentDataContainer().has(net.mandomc.gameplay.mobs.HerdAge.KIND)
                && event.getEntity() instanceof Ageable ageable && !ageable.isAdult()) check(event.getDroppedExp() == 0, "baby XP is zero");
    }
    private void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks.add(label); getLogger().info("PASS " + label);
    }
    private void finish(String file) throws Exception {
        Files.write(getDataFolder().toPath().resolve(file), checks);
        Bukkit.shutdown();
    }
    private void fail(Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE, "Herd verification failed", error);
        try { Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"), error.toString()); } catch (Exception ignored) {}
        Bukkit.shutdown();
    }
    @Override public void onDisable() { if (tempt != null) tempt.close(); }
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
