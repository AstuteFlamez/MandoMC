package net.mandomc.verification;

import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.vehicles.*;
import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

/** Disposable native cannon, ammo, attributed-hit and held-wrench checks. */
public final class VehicleWeaponsProbe extends JavaPlugin implements Listener {
    private MandoPlugin plugin;
    private World world;
    private final List<Player> players = new ArrayList<>();
    private final List<UUID> ids = new ArrayList<>();
    private final List<BlockState> blocks = new ArrayList<>();
    private final List<Entity> fixtures = new ArrayList<>();
    private Map<UUID, Object> online;
    private int checks, hits;
    private UUID target;
    private Player expectedShooter;
    private Player torpedoShooter;
    private boolean cancelTorpedo;
    private Location blastCenter;
    private int torpedoBlasts;

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        try {
        var data = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.File(Bukkit.getPluginManager().getPlugin("MandoMC").getDataFolder(), "data/vehicles.yml"));
        var saved = data.getConfigurationSection("vehicles");
        if (saved != null) for (String id : saved.getKeys(false)) {
            if ("speederbike".equals(saved.getString(id + ".type")))
                check(saved.getDouble(id + ".health") <= 50, "speeder cap is durably checkpointed before restoration ticks");
        }
        } catch (Throwable error) {
            getLogger().log(java.util.logging.Level.SEVERE, "VEHICLE WEAPONS REPAIR PROBE FAILED", error);
            Bukkit.shutdown(); return;
        }
        later(120, this::begin);
    }

    @SuppressWarnings("unchecked")
    private void begin() throws Exception {
        plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(plugin.vehicleCannons() != null && plugin.vehicleRepairs() != null, "cannons and repairs initialized");
        world = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object list = server.getClass().getMethod("getPlayerList").invoke(server);
        var lookup = Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");
        lookup.setAccessible(true); online = (Map<UUID, Object>) lookup.get(list);
        for (int x = 875; x <= 950; x++) for (int z = 875; z <= 900; z++) {
            var block = world.getBlockAt(x, 280, z); blocks.add(block.getState()); block.setType(Material.STONE, false);
        }
        int i = 0;
        for (String type : List.of("speederbike", "xwing", "tiefighter", "uwing")) {
            var location = new Location(world, 885.5 + 16 * i++, 281, 885.5, 0, 0);
            var owner = nativeTestPlayer(world, location); players.add(owner);
            online.put(owner.getUniqueId(), owner.getClass().getMethod("getHandle").invoke(owner));
            check(owner.isOnline(), "native fixture has online attribution without login");
            var item = plugin.items().create(type); owner.getInventory().setItemInMainHand(item);
            var id = plugin.vehicles().deploy(owner, item, location); ids.add(id);
            check(id != null, "deploy " + type);
            if (type.equals("speederbike")) check(plugin.vehicles().entity(id).getHealth() == 50
                    && plugin.vehicles().maxHealth(id) == 50, "speederbike spawns with fifty maximum health");
        }
        later(20, this::volleys);
    }

    private void volleys() throws Exception {
        for (int i = 0; i < players.size(); i++) {
            var owner = players.get(i); var id = ids.get(i);
            if (i > 0) {
                plugin.vehicles().entity(id).setRotation(i == 1 ? 0 : i * 90, 0);
                owner.setRotation(i == 1 ? 0 : i * 90, -15);
            }
            check(plugin.vehicles().mount(owner, id, true), "pilot mounts for cannon test " + i);
            String vehicle = plugin.vehicles().mountedType(owner);
            String ammo = vehicle.equals("xwing") ? "proton_torpedo" : (vehicle.equals("speederbike") || vehicle.equals("uwing"))
                    ? "standard_plasma_cells" : "tibanna_gas_cells";
            int cost = vehicle.equals("xwing") ? 4 : 2;
            var names = cost == 4 ? List.of("weapon_one", "weapon_two", "weapon_three", "weapon_four")
                    : List.of("weapon_one", "weapon_two");
            var platform = plugin.vehicles().firingPlatform(owner, names);
            check(platform != null && platform.muzzles().size() == cost, "all recovered cannon bones exist: " + vehicle);
            if (vehicle.equals("tiefighter")) {
                var wrong = plugin.items().create("standard_plasma_cells"); wrong.setAmount(64);
                owner.getInventory().setItem(0, wrong);
                check(!plugin.vehicleCannons().fire(owner) && owner.getInventory().getItem(0).getAmount() == 64,
                        "TIE rejects standard cells and requires Tibanna gas cells");
            }

            if (vehicle.equals("uwing")) {
                var wrong = plugin.items().create("tibanna_gas_cells"); wrong.setAmount(64);
                owner.getInventory().clear(); owner.getInventory().setItem(0, wrong);
                check(!plugin.vehicleCannons().fire(owner) && owner.getInventory().getItem(0).getAmount() == 64,
                        "U-wing rejects Tibanna without spending it");
            }
            owner.getInventory().clear();
            owner.getInventory().setItem(0, new ItemStack(Material.ECHO_SHARD, 64));
            check(!plugin.vehicleCannons().fire(owner), "lookalike ammo rejected: " + vehicle);
            var cells = plugin.items().create(ammo); cells.setAmount(cost - 1); owner.getInventory().setItem(0, cells);
            check(!plugin.vehicleCannons().fire(owner) && owner.getInventory().getItem(0).getAmount() == cost - 1,
                    "insufficient volley ammo spends nothing: " + vehicle);
            cells.setAmount(cost); owner.getInventory().setItem(0, cells);
            int before = plugin.vehicleCannons().activeBolts();
            check(plugin.vehicleCannons().fire(owner), "legacy volley fires: " + vehicle);
            check(plugin.vehicleCannons().activeBolts() == before + cost, "one rendered bolt per cannon: " + vehicle);
            verifyFacing();
            check(owner.getInventory().getItem(0) == null, "exact volley ammo cost: " + vehicle);
            owner.getInventory().setItem(0, plugin.items().create(ammo));
            check(!plugin.vehicleCannons().fire(owner), "duplicate same-tick input cannot double fire: " + vehicle);
        }
        var gunner = nativeTestPlayer(world, plugin.vehicles().entity(ids.get(3)).getLocation());
        players.add(gunner); online.put(gunner.getUniqueId(), gunner.getClass().getMethod("getHandle").invoke(gunner));
        check(plugin.vehicles().mount(gunner, ids.get(3), VehicleModels.Seat.GUNNER), "U-wing gunner boards for built-in cannons");
        var gunnerAmmo = plugin.items().create("standard_plasma_cells"); gunnerAmmo.setAmount(64);
        gunner.getInventory().setItemInMainHand(gunnerAmmo);
        check(plugin.vehicleCannons().fire(gunner) && gunner.getInventory().getItemInMainHand().getAmount() == 62,
                "U-wing gunner fires twin cannons with own ammunition");
        var platform = plugin.vehicles().firingPlatform(gunner, List.of("weapon_one", "weapon_two"));
        check(platform.excluded().contains(players.get(3).getUniqueId()) && platform.excluded().contains(gunner.getUniqueId()),
                "cannons exclude both pilot and gunner");
        later(2, () -> {
            var owner = players.get(1);
            var cells = plugin.items().create("proton_torpedo"); cells.setAmount(64); owner.getInventory().setItem(0, cells);
            check(!plugin.vehicleCannons().fire(owner) && owner.getInventory().getItem(0).getAmount() == 64,
                    "X-wing two-second cooldown preserves ammo");
            later(45, this::impact);
        });
    }

    @SuppressWarnings("unchecked")
    private void impact() throws Exception {
        check(plugin.vehicleCannons().activeBolts() == 0, "range cap removes every bolt and display");
        var owner = players.getFirst();
        var field = plugin.vehicleCannons().getClass().getDeclaredField("weapons"); field.setAccessible(true);
        var configs = (Map<String, VehicleWeaponSettings>) field.get(plugin.vehicleCannons());
        var old = configs.get("speederbike");
        configs.put("speederbike", new VehicleWeaponSettings(5, 2.5, 0, "standard_plasma_cells", 1,
                List.of("weapon_one"), 100, 8, old.sound()));
        var platform = plugin.vehicles().firingPlatform(owner, List.of("weapon_one"));
        var location = platform.muzzles().getFirst().clone().add(platform.direction().clone().multiply(7));
        var victim = world.spawn(location, Pig.class, pig -> { pig.setAI(false); pig.setGravity(false); });
        fixtures.add(victim); target = victim.getUniqueId(); expectedShooter = owner;
        var ammo = plugin.items().create("standard_plasma_cells"); ammo.setAmount(64); owner.getInventory().setItem(0, ammo);
        check(plugin.vehicleCannons().fire(owner), "single-bolt collision fixture emitted");
        later(12, () -> {
            check(hits == 1, "direct hit and splash use one damage event per victim per bolt");
            check(victim.getHealth() == 5, "recovered five damage applied exactly once");
            check(plugin.combat().tagged(owner), "attributed cannon damage tags shooter in combat");
            victim.remove();
            plugin.combat().clear(owner);
            protection(owner, configs, old);
        });
    }

    private void protection(Player owner, Map<String, VehicleWeaponSettings> configs, VehicleWeaponSettings old) throws Exception {
        var manager = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer()
                .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
        var platform = plugin.vehicles().firingPlatform(owner, List.of("weapon_one"));
        var origin = platform.muzzles().getFirst();
        var location = origin.clone().add(platform.direction().clone().multiply(7));
        var victim = world.spawn(location, Pig.class, pig -> { pig.setAI(false); pig.setGravity(false); });
        fixtures.add(victim); target = victim.getUniqueId(); int beforeHits = hits;
        var region = new com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion("vehicle_cannon_probe",
                com.sk89q.worldedit.math.BlockVector3.at(location.getBlockX()-2, location.getBlockY()-2, location.getBlockZ()-2),
                com.sk89q.worldedit.math.BlockVector3.at(location.getBlockX()+2, location.getBlockY()+2, location.getBlockZ()+2));
        region.setPriority(10000); region.setFlag(com.sk89q.worldguard.protection.flags.Flags.PVP,
                com.sk89q.worldguard.protection.flags.StateFlag.State.DENY); manager.addRegion(region);
        check(plugin.vehicleCannons().fire(owner), "outside safe zone can emit toward boundary");
        later(12, () -> {
            check(victim.getHealth() == 10 && hits == beforeHits, "protected target and path suppress cannon damage");
            manager.removeRegion(region.getId());
            var playerLocation = owner.getLocation();
            var sourceRegion = new com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion("vehicle_cannon_source_probe",
                    com.sk89q.worldedit.math.BlockVector3.at(playerLocation.getBlockX()-1, 279, playerLocation.getBlockZ()-1),
                    com.sk89q.worldedit.math.BlockVector3.at(playerLocation.getBlockX()+1, 290, playerLocation.getBlockZ()+1));
            sourceRegion.setPriority(10000); sourceRegion.setFlag(com.sk89q.worldguard.protection.flags.Flags.PVP,
                    com.sk89q.worldguard.protection.flags.StateFlag.State.DENY); manager.addRegion(sourceRegion);
            int ammo = owner.getInventory().getItem(0).getAmount();
            check(!plugin.vehicleCannons().fire(owner) && owner.getInventory().getItem(0).getAmount() == ammo,
                    "protected shooter cannot fire or spend ammunition");
            manager.removeRegion(sourceRegion.getId());
            // Let WorldGuard's location-query cache expire after removing the test safe zone.
            later(45, () -> {
            var wall = origin.clone().add(platform.direction().clone().multiply(3));
            for (int y = wall.getBlockY()-1; y <= wall.getBlockY()+2; y++) {
                var block = world.getBlockAt(wall.getBlockX(), y, wall.getBlockZ());
                blocks.add(block.getState()); block.setType(Material.STONE, false);
            }
            check(plugin.vehicleCannons().fire(owner), "wall collision fixture emitted");
            later(12, () -> {
                check(victim.getHealth() == 10 && hits == beforeHits, "swept bolt stops at thin wall and splash respects cover");
                manager.removeRegion("vehicle_cannon_probe"); manager.removeRegion("vehicle_cannon_source_probe");
                victim.remove(); configs.put("speederbike", old); torpedoes();
            });
            });
        });
    }

    private void verifyFacing() throws Exception {
        var field = plugin.vehicleCannons().getClass().getDeclaredField("bolts"); field.setAccessible(true);
        for (Object bolt : (List<?>) field.get(plugin.vehicleCannons())) {
            var displayMethod = bolt.getClass().getDeclaredMethod("display"); displayMethod.setAccessible(true);
            var motionMethod = bolt.getClass().getDeclaredMethod("motion"); motionMethod.setAccessible(true);
            var display = (ItemDisplay) displayMethod.invoke(bolt);
            var motion = ((org.bukkit.util.Vector) motionMethod.invoke(bolt)).clone().normalize();
            var settingsMethod=bolt.getClass().getDeclaredMethod("settings");settingsMethod.setAccessible(true);
            var settings=(VehicleWeaponSettings)settingsMethod.invoke(bolt);
            String expected="tibanna_gas_cells".equals(settings.ammo()) ? "mandomc:projectiles/greenlaser" : "mandomc:projectiles/redlaser";
            check(expected.equals(display.getItemStack().getItemMeta().getItemModel().toString()),"actual projectile model follows ammo " + settings.ammo());
            var visible = new org.joml.Vector3f(0, 0, 1).rotateY((float) Math.toRadians(45));
            display.getTransformation().getLeftRotation().transform(visible);
            check(Math.abs(visible.x - motion.getX()) < 1e-5 && Math.abs(visible.y - motion.getY()) < 1e-5
                    && Math.abs(visible.z - motion.getZ()) < 1e-5 && display.getYaw() == 0 && display.getPitch() == 0,
                    "visible vehicle laser axis follows motion without inherited bone rotation");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void torpedoExplosion(org.bukkit.event.entity.EntityExplodeEvent event) {
        if (torpedoShooter != null && torpedoShooter.equals(event.getEntity())) {
            torpedoBlasts++; blastCenter = event.getLocation().clone();
            if (cancelTorpedo) event.setCancelled(true);
        }
    }

    @SuppressWarnings("unchecked")
    private void torpedoes() throws Exception {
        torpedoShooter = players.get(1);
        var field = plugin.vehicleCannons().getClass().getDeclaredField("weapons"); field.setAccessible(true);
        var configs = (Map<String, VehicleWeaponSettings>) field.get(plugin.vehicleCannons());
        var old = configs.get("xwing");
        check(old.terrainExplosion(), "X-wing config enables the shared terrain explosion rules");
        configs.put("xwing", new VehicleWeaponSettings(10, 4, 0, "proton_torpedo", 1,
                List.of("weapon_one"), 100, 8, old.sound(), true));
        var platform = plugin.vehicles().firingPlatform(torpedoShooter, List.of("weapon_one"));
        var target = platform.muzzles().getFirst().clone().add(platform.direction().clone().multiply(8));
        var cube = new ArrayList<org.bukkit.block.Block>();
        for (int x = target.getBlockX()-4; x <= target.getBlockX()+4; x++)
            for (int y = target.getBlockY()-4; y <= target.getBlockY()+4; y++)
                for (int z = target.getBlockZ()-4; z <= target.getBlockZ()+4; z++) {
                    var block = world.getBlockAt(x,y,z); blocks.add(block.getState());
                    block.setType(Material.STONE,false); cube.add(block);
                }
        var ammo = plugin.items().create("proton_torpedo"); ammo.setAmount(64);
        torpedoShooter.getInventory().setItem(0, ammo);
        int pending = plugin.explosions().pendingCount();
        cancelTorpedo = true;
        check(plugin.vehicleCannons().fire(torpedoShooter), "proton torpedo emits toward terrain");
        later(15, () -> {
            check(torpedoBlasts == 1 && plugin.explosions().pendingCount() == pending
                    && cube.stream().noneMatch(block -> block.getType().isAir()),
                    "cancelled torpedo explosion changes no terrain or restoration journal");
            cancelTorpedo = false;
            check(plugin.vehicleCannons().fire(torpedoShooter), "uncancelled proton torpedo emits");
            later(15, () -> {
                int added = plugin.explosions().pendingCount() - pending;
                check(torpedoBlasts == 2 && added > 0 && added <= 64, "torpedo uses shared sixty-four-block blast cap");
                check(cube.stream().filter(block -> block.getType().isAir()).allMatch(block ->
                        block.getLocation().add(.5,.5,.5).distanceSquared(blastCenter) <= 16.0001),
                        "torpedo terrain changes stay inside shared four-block radius");
                check(plugin.explosions().settings().restoreSeconds() == 60
                        && plugin.explosions().settings().restoreBlocksPerStep() == 4
                        && plugin.explosions().settings().restoreIntervalTicks() == 2,
                        "torpedo damage uses delayed gradual restoration settings");
                plugin.explosions().restore(true);
                check(plugin.explosions().pendingCount() == 0 && cube.stream().allMatch(block -> block.getType() == Material.STONE),
                        "torpedo terrain journal restores original blocks");
                configs.put("xwing", old); torpedoShooter = null; repairs();
            });
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageByEntityEvent event) {
        if (target != null && target.equals(event.getEntity().getUniqueId())) {
            check(expectedShooter.equals(event.getDamageSource().getCausingEntity()), "cannon impact preserves player attribution");
            hits++;
        }
    }

    private void repairs() throws Exception {
        for (int i = 0; i < ids.size(); i++) {
            var entity = plugin.vehicles().entity(ids.get(i));
            var modeled = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(entity);
            var manager = modeled.getModels().values().iterator().next().getMountManager().orElseThrow();
            manager.dismountDriver();
            for (Entity rider : List.copyOf(manager.getPassengerSeatMap().keySet())) manager.dismountPassenger(rider);
        }
        var bikeOwner = players.getFirst(); plugin.combat().clear(bikeOwner);
        check(plugin.vehicles().pack(bikeOwner, ids.getFirst()), "fifty-HP bike packs with authoritative health");
        var bikeItem = Arrays.stream(bikeOwner.getInventory().getContents()).filter(Objects::nonNull)
                .filter(item -> "speederbike".equals(plugin.items().identify(item))).findFirst().orElseThrow();
        var bikeMeta = bikeItem.getItemMeta(); var bikeLore = new ArrayList<>(bikeMeta.lore());
        bikeLore.set(bikeLore.size()-1, net.kyori.adventure.text.Component.text("Health: 400 / 400"));
        bikeMeta.lore(bikeLore); bikeItem.setItemMeta(bikeMeta);
        var identity = bikeItem.getItemMeta().getPersistentDataContainer().serializeToBytes();
        check(plugin.vehicles().refreshPackedHealth(bikeItem)
                && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(bikeItem.getItemMeta().lore().getLast()).equals("Health: 50 / 50")
                && Arrays.equals(identity, bikeItem.getItemMeta().getPersistentDataContainer().serializeToBytes()),
                "older packed-bike tooltip refreshes to fifty without changing identity");
        var owner = players.get(3); var id = ids.get(3); var entity = plugin.vehicles().entity(id);
        plugin.combat().clear(owner);
        move(owner, entity.getLocation().clone().add(2, 0, 0)); owner.setSneaking(true);
        var wrench = plugin.items().create("durasteel_wrench"); owner.getInventory().setItemInMainHand(wrench);
        check(wrench != null && plugin.vehicleRepairs().isWrench(wrench), "combined durasteel wrench ID recognized");
        entity.setHealth(160);
        owner.setSneaking(false);
        check(!plugin.vehicleRepairs().start(owner, id), "repair requires crouching"); owner.setSneaking(true);
        plugin.combat().tag(owner);
        check(!plugin.vehicleRepairs().start(owner, id), "combat prevents repair start"); plugin.combat().clear(owner);
        entity.setVelocity(new org.bukkit.util.Vector(.3, 0, 0));
        check(!plugin.vehicleRepairs().start(owner, id), "moving vehicle cannot repair"); entity.setVelocity(new org.bukkit.util.Vector());
        check(plugin.vehicleRepairs().start(owner, id), "nearby held wrench starts repair");
        pulse();
        check(entity.getHealth() == 165, "repair pulse restores five authoritative health");
        check(((Damageable) owner.getInventory().getItemInMainHand().getItemMeta()).getDamage() == 25,
                "repair pulse spends 25 held-wrench durability");
        owner.getInventory().setHeldItemSlot(1); pulse();
        check(entity.getHealth() == 165 && plugin.vehicleRepairs().activeRepairs() == 0, "changing held slot stops repair");
        owner.getInventory().setHeldItemSlot(0);
        check(plugin.vehicleRepairs().start(owner, id), "repair can restart with actual worn wrench");
        move(owner, entity.getLocation().clone().add(30, 0, 0)); pulse();
        check(entity.getHealth() == 165 && plugin.vehicleRepairs().activeRepairs() == 0, "moving away stops repair");
        move(owner, entity.getLocation().clone().add(2, 0, 0)); entity.setHealth(180);
        int damage = ((Damageable) owner.getInventory().getItemInMainHand().getItemMeta()).getDamage();
        check(plugin.vehicleRepairs().start(owner, id), "full vehicle repair terminates safely"); pulse();
        check(((Damageable) owner.getInventory().getItemInMainHand().getItemMeta()).getDamage() == damage,
                "full vehicle spends no durability");
        entity.setHealth(178);
        var nearlyBroken = owner.getInventory().getItemInMainHand();
        var meta = (Damageable) nearlyBroken.getItemMeta(); meta.setDamage(nearlyBroken.getType().getMaxDurability() - 1);
        nearlyBroken.setItemMeta(meta); owner.getInventory().setItemInMainHand(nearlyBroken);
        check(plugin.vehicleRepairs().start(owner, id), "nearly broken wrench starts final pulse"); pulse();
        check(Math.abs(entity.getHealth() - 178.2) < .0001 && owner.getInventory().getItemInMainHand().isEmpty(),
                "final one durability restores proportional health and breaks the wrench");
        entity.setHealth(178);
        owner.getInventory().setItemInMainHand(plugin.items().create("durasteel_wrench"));
        check(plugin.vehicleRepairs().start(owner, id), "repair session can start before removal");
        check(plugin.vehicles().pack(owner, id), "packing retains authoritative health after repair");
        pulse();
        check(plugin.vehicleRepairs().activeRepairs() == 0
                && ((Damageable) owner.getInventory().getItemInMainHand().getItemMeta()).getDamage() == 0,
                "removed vehicle cancels repair without spending wrench durability");
        getLogger().info("VEHICLE WEAPONS REPAIR PROBE PASSED: " + checks + " assertions");
        cleanup(); Bukkit.shutdown();
    }

    private void pulse() throws Exception {
        var method = plugin.vehicleRepairs().getClass().getDeclaredMethod("tick"); method.setAccessible(true); method.invoke(plugin.vehicleRepairs());
    }

    private void move(Player player, Location location) throws Exception {
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        handle.getClass().getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
    }

    private interface Checked { void run() throws Exception; }
    private void later(long delay, Checked action) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { action.run(); } catch (Throwable error) {
            getLogger().log(java.util.logging.Level.SEVERE, "VEHICLE WEAPONS REPAIR PROBE FAILED", error); cleanup(); Bukkit.shutdown();
        } }, delay);
    }
    private void check(boolean ok, String why) {
        if (!ok) throw new IllegalStateException(why); checks++; getLogger().info("PASS " + why);
    }
    private void cleanup() {
        Bukkit.getScheduler().cancelTasks(this);
        fixtures.forEach(Entity::remove);
        if (plugin != null && plugin.explosions() != null) plugin.explosions().restore(true);
        if (plugin != null && plugin.vehicles() != null) for (int i = 0; i < ids.size(); i++) {
            var player = players.get(i); plugin.combat().clear(player); player.getInventory().clear();
            var entity = plugin.vehicles().entity(ids.get(i));
            if (entity != null) {
                var model = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(entity);
                model.getModels().values().iterator().next().getMountManager().orElseThrow().dismountDriver();
                plugin.vehicles().pack(player, ids.get(i));
            }
        }
        if (world != null) {
            var manager = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(world));
            manager.removeRegion("vehicle_cannon_probe"); manager.removeRegion("vehicle_cannon_source_probe");
        }
        if (online != null) players.forEach(player -> online.remove(player.getUniqueId()));
        Collections.reverse(blocks); blocks.forEach(block -> block.update(true, false)); blocks.clear();
    }
    @Override public void onDisable() { cleanup(); }

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
