package net.mandomc.verification;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.mount.controller.MountController;
import com.ticxo.modelengine.api.mount.controller.MountControllerTypes;

import me.deecaad.weaponmechanics.weapon.weaponevents.WeaponPreShootEvent;

import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.ItemPresentationRefresh;
import net.mandomc.gameplay.vehicles.VehicleModels;

import org.bukkit.*;
import org.bukkit.block.BlockState;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Disposable mounted-step, seat-role, stale-seat and presentation regression checks. */
public final class VehiclePolishProbe extends JavaPlugin {
    private MandoPlugin plugin;
    private World world;
    private final List<BlockState> blocks = new ArrayList<>();
    private final List<Player> players = new ArrayList<>();
    private final List<UUID> ids = new ArrayList<>();
    private int checks;
    private double highest;

    @Override
    public void onEnable() {
        later(120, this::begin);
    }

    private void begin() throws Exception {
        plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        world = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        for (int x = 790; x <= 850; x++)
            for (int z = 790; z <= 850; z++) set(x, 280, z, Material.STONE);
        for (int x = 808; x <= 815; x++)
            for (int z = 800; z <= 808; z++) set(x, 281, z, Material.STONE);
        for (int x = 816; x <= 818; x++)
            for (int z = 800; z <= 808; z++)
                // Two blocks above the raised step, not a second climbable one-block step.
                for (int y = 281; y <= 283; y++) set(x, y, z, Material.STONE);
        int i = 0;
        for (String type : List.of("speederbike", "xwing", "tiefighter", "uwing")) {
            var location =
                    new Location(
                            world,
                            i == 0 ? 803.5 : 830.5,
                            281,
                            i == 0 ? 804.5 : 810.5 + i * 10,
                            -90,
                            0);
            var owner = nativeTestPlayer(world, location);
            players.add(owner);
            var item = plugin.items().create(type);
            owner.getInventory().setItemInMainHand(item);
            var id = plugin.vehicles().deploy(owner, item, location);
            check(id != null, "deploy " + type);
            ids.add(id);
            var seats =
                    Class.forName("net.mandomc.gameplay.vehicles.VehicleService$Seats")
                            .getDeclaredConstructor(
                                    net.mandomc.gameplay.vehicles.VehicleService.class, UUID.class);
            seats.setAccessible(true);
            var menu = ((InventoryHolder) seats.newInstance(plugin.vehicles(), id)).getInventory();
            check(
                    menu.getItem(13).getType() == Material.GRAY_STAINED_GLASS_PANE,
                    "no nonexistent passenger button: " + type);
            check(
                    menu.getItem(15).getType()
                            == (type.equals("uwing")
                                    ? Material.CROSSBOW
                                    : Material.GRAY_STAINED_GLASS_PANE),
                    "gunner button matches seats: " + type);
            check(
                    net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                            .plainText()
                            .serialize(plugin.items().create(type).getItemMeta().lore().getFirst())
                            .contains("ď"),
                    "legacy vehicle badge: " + type);
            i++;
        }
        later(15, this::drive);
    }

    private void drive() throws Exception {
        var owner = players.getFirst();
        var bike = plugin.vehicles().entity(ids.getFirst());
        check(
                ModelEngineAPI.getModeledEntity(bike).getBase().getMaxStepHeight() == 1,
                "legacy native one-block step height");
        check(plugin.vehicles().mount(owner, ids.getFirst(), true), "mount speeder");
        check(shoot(owner).isCancelled(), "pilot cannot fire held blaster");
        var controller = ModelEngineAPI.getMountPairManager().getController(owner.getUniqueId());
        var model = ModelEngineAPI.getModeledEntity(bike);
        var active = model.getModels().values().iterator().next();
        int[] ticks = {0};
        Bukkit.getScheduler()
                .runTaskTimer(
                        this,
                        task -> {
                            try {
                                // A real client keeps sending its look direction while riding.
                                owner.setRotation(-90, 0);
                                controller.setInput(
                                        new MountController.MountInput(0, 1, false, false));
                                controller.updateDriverMovement(
                                        model.getBase().getMoveController(), active);
                                highest = Math.max(highest, bike.getY());
                                if (++ticks[0] >= 45) {
                                    task.cancel();
                                    check(
                                            bike.getX() > 810 && highest >= 281.99,
                                            "mounted speeder physically climbs one block without"
                                                + " jump: "
                                                    + bike.getLocation());
                                    check(
                                            bike.getX() < 816 && highest < 282.5,
                                            "two-block wall still blocks movement");
                                    active.getMountManager().orElseThrow().dismountDriver();
                                    later(15, this::uwing);
                                }
                            } catch (Throwable e) {
                                task.cancel();
                                fail(e);
                            }
                        },
                        1,
                        1);
    }

    private void uwing() throws Exception {
        var owner = players.get(3);
        var id = ids.get(3);
        var entity = plugin.vehicles().entity(id);
        check(plugin.vehicles().mount(owner, id, true), "U-wing pilot mounts");
        check(
                !plugin.vehicles().mount(owner, id, VehicleModels.Seat.GUNNER),
                "one player cannot occupy pilot and gunner simultaneously");
        check(shoot(owner).isCancelled(), "U-wing pilot firing denied before shot");
        var gunner = nativeTestPlayer(world, entity.getLocation());
        players.add(gunner);
        check(
                plugin.vehicles().mount(gunner, id, VehicleModels.Seat.GUNNER),
                "unrelated gunner allowed");
        check(!shoot(gunner).isCancelled(), "U-wing gunner firing allowed");
        check(
                !plugin.vehicles().mount(players.get(1), id, VehicleModels.Seat.PASSENGER),
                "U-wing has no actual passenger seat");
        var manager =
                ModelEngineAPI.getModeledEntity(entity)
                        .getModels()
                        .values()
                        .iterator()
                        .next()
                        .getMountManager()
                        .orElseThrow();
        manager.dismountDriver();
        check(!plugin.vehicles().pack(owner, id), "real gunner occupancy prevents packing");
        manager.dismountPassenger(gunner);
        check(!shoot(gunner).isCancelled(), "dismounted player firing remains allowed");
        check(plugin.vehicles().mount(owner, id, true), "U-wing pilot can board again");
        check(
                manager.mountPassenger("seatone", owner, MountControllerTypes.WALKING),
                "reproduce old simultaneous vendor seat state");
        manager.dismountRider(owner);
        check(
                manager.getPassengerSeatMap().containsKey(owner),
                "old vendor dismount leaves stale passenger entry");
        later(
                20,
                () -> {
                    check(
                            manager.getDriver() == null && manager.getPassengerSeatMap().isEmpty(),
                            "completed dismount clears all stale seat entries");
                    check(
                            plugin.vehicles().pack(owner, id),
                            "U-wing packs after exit without being recreated");
                    check(
                            plugin.vehicles().pack(players.getFirst(), ids.getFirst()),
                            "stepped speeder packs after landing");
                    ItemStack packed =
                            Arrays.stream(owner.getInventory().getContents())
                                    .filter(Objects::nonNull)
                                    .filter(it -> "uwing".equals(plugin.items().identify(it)))
                                    .findFirst()
                                    .orElseThrow();
                    var meta = packed.getItemMeta();
                    var lore = new ArrayList<>(meta.lore());
                    lore.set(0, net.kyori.adventure.text.Component.text("VEHICLE"));
                    meta.lore(lore);
                    packed.setItemMeta(meta);
                    var pdc = packed.getItemMeta().getPersistentDataContainer().serializeToBytes();
                    var health = lore.getLast();
                    check(
                            ItemPresentationRefresh.refresh(plugin.items(), packed),
                            "existing item badge refreshes");
                    check(
                            Arrays.equals(
                                            pdc,
                                            packed.getItemMeta()
                                                    .getPersistentDataContainer()
                                                    .serializeToBytes())
                                    && health.equals(packed.getItemMeta().lore().getLast()),
                            "badge refresh preserves packed identity and health");
                    check(
                            !ItemPresentationRefresh.refresh(plugin.items(), packed),
                            "badge refresh is idempotent");
                    getLogger().info("VEHICLE POLISH PROBE PASSED: " + checks + " assertions");
                    cleanup();
                    Bukkit.shutdown();
                });
    }

    private WeaponPreShootEvent shoot(Player p) {
        var e = new WeaponPreShootEvent("E11", plugin.items().create("e11"), p, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(e);
        return e;
    }

    private void set(int x, int y, int z, Material material) {
        var b = world.getBlockAt(x, y, z);
        blocks.add(b.getState());
        b.setType(material, false);
    }

    private interface Checked {
        void run() throws Exception;
    }

    private void later(long delay, Checked c) {
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            try {
                                c.run();
                            } catch (Throwable e) {
                                fail(e);
                            }
                        },
                        delay);
    }

    private void check(boolean ok, String why) {
        if (!ok) throw new IllegalStateException(why);
        checks++;
        getLogger().info("PASS " + why);
    }

    private void fail(Throwable e) {
        getLogger().log(java.util.logging.Level.SEVERE, "VEHICLE POLISH PROBE FAILED", e);
        cleanup();
        Bukkit.shutdown();
    }

    private void cleanup() {
        Bukkit.getScheduler().cancelTasks(this);
        Collections.reverse(blocks);
        blocks.forEach(b -> b.update(true, false));
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
