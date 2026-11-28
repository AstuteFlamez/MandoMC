package net.mandomc.verification;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.clans.ClanService;
import net.mandomc.gameplay.clans.ClanState;
import net.mandomc.gameplay.trophies.RancorTrophies;
import net.mandomc.gameplay.trophies.TrophyJournal;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** One-shot operator-run test at an empty high-altitude location on loopback development Paper. */
public final class BarrelPreviewProbe extends JavaPlugin {
    private NativeProbePlayers actors;
    private Player owner, observer;
    private Block floor, base, upper;
    private ItemStack barrel;
    private World world;
    private RancorTrophies placements;
    private Location bluePose;
    private org.bukkit.util.Transformation blueTransform;
    private ItemDisplay firstGhost;
    private boolean supportInstalled, wallInstalled;
    private int checks;
    private ClanState claims;
    private ClanState.Clan fixtureClan;
    private ClanState.Chunk fixtureChunk;
    private final java.util.Set<Chunk> tickets = new java.util.HashSet<>();
    private interface Step { void run() throws Exception; }

    @Override public void onEnable() {
        var flag = getDataFolder().toPath().resolve("run-once");
        try {
            if (!java.nio.file.Files.deleteIfExists(flag)) return;
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
        later(100, this::begin);
    }

    private void later(long ticks, Step step) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { step.run(); }
            catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "BARREL PREVIEW FAILED", error);
                Bukkit.getPluginManager().disablePlugin(this);
            }
        }, ticks);
    }

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    private void begin() throws Exception {
        check("127.0.0.1".equals(Bukkit.getIp()), "loopback development only");
        world = Bukkit.getWorld(NamespacedKey.minecraft("devworld"));
        floor = world.getBlockAt(47, world.getMaxHeight() - 16, 47);
        base = floor.getRelative(BlockFace.UP);
        upper = base.getRelative(BlockFace.UP);
        for (int x = 2; x <= 3; x++) for (int z = 2; z <= 3; z++) {
            var chunk = world.getChunkAt(x,z);
            chunk.addPluginChunkTicket(this);
            tickets.add(chunk);
        }
        check(floor.isEmpty() && base.isEmpty() && upper.isEmpty(), "test column must be empty");
        check(world.getNearbyEntities(floor.getLocation(), 10, 10, 10).isEmpty(), "test site is unused");
        placements = Bukkit.getServicesManager().load(RancorTrophies.class);
        check(placements != null && entry() == null, "placement service and unused anchor");
        actors = new NativeProbePlayers("BarrelProbe");
        owner = actors.createTracked(floor.getLocation().add(.5, -.8, -2.5));
        observer = actors.createTracked(floor.getLocation().add(2.5, -.8, 2.5));
        for (var player : List.of(owner, observer)) {
            player.setViewDistance(2);
            player.setSendViewDistance(2);
        }
        owner.setGameMode(GameMode.SURVIVAL);
        observer.setGameMode(GameMode.SURVIVAL);
        floor.setType(Material.STONE, false);
        supportInstalled = true;
        var plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        barrel = plugin.items().create("rhydonium_barrel");
        owner.getInventory().setHeldItemSlot(4);
        setFuel(250);
        face(owner, floor.getLocation().add(.5, .6, .5));
        later(2, () -> face(owner, floor.getLocation().add(.5,.6,.5)));
        awaitChunk(0);
    }

    private void awaitChunk(int ticks) {
        if (owner.isChunkSent(floor.getChunk().getChunkKey()) && observer.isChunkSent(floor.getChunk().getChunkKey())) {
            later(5, this::blue);
        } else {
            if (ticks >= 200) throw new AssertionError("both players must subscribe to the test chunk");
            later(5, () -> awaitChunk(ticks + 5));
        }
    }

    private void setFuel(int units) {
        barrel.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("mandomc", "current_fuel"),
                PersistentDataType.INTEGER, units));
        owner.getInventory().setItemInMainHand(barrel.clone());
    }

    private void face(Player player, Location target) throws Exception {
        var rotation = player.getEyeLocation().setDirection(target.toVector().subtract(player.getEyeLocation().toVector()));
        player.setRotation(rotation.getYaw(), rotation.getPitch());
        var handle = player.getClass().getMethod("getHandle").invoke(player);
        handle.getClass().getMethod("setYHeadRot", float.class).invoke(handle, rotation.getYaw());
        handle.getClass().getMethod("setYBodyRot", float.class).invoke(handle, rotation.getYaw());
    }

    private List<ItemDisplay> displays() {
        return world.getNearbyEntities(base.getLocation().add(.5,.5,.5), 2, 3, 2).stream()
                .filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).toList();
    }

    private ItemDisplay only(String model) {
        var list = displays();
        if (list.isEmpty()) {
            var trace = owner.rayTraceBlocks(4.5);
            getLogger().info("Test actor eye=" + owner.getEyeLocation() + " mode=" + owner.getGameMode()
                    + " online=" + owner.isOnline() + " dead=" + owner.isDead()
                    + " hit=" + (trace == null ? "miss" : trace.getHitBlock()) + " hand="
                    + ((MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC")).items().identify(owner.getInventory().getItemInMainHand()));
        }
        check(list.size() == 1, "expected one " + model + " display, found " + list.size());
        var display = list.getFirst();
        check(("mandomc:" + model).equals(String.valueOf(display.getItemStack().getItemMeta().getItemModel())),
                "expected " + model + ", got " + display.getItemStack().getItemMeta().getItemModel());
        return display;
    }

    private boolean sawSpawn(Player player, ItemDisplay entity) throws Exception {
        for (var packet : actors.drainPackets(player)) {
            if (containsSpawn(packet, entity.getEntityId())) return true;
        }
        return false;
    }

    private boolean containsSpawn(Object packet, int id) throws Exception {
        if (packet.getClass().getSimpleName().equals("ClientboundBundlePacket")) {
            for (var element : (Iterable<?>)packet.getClass().getMethod("subPackets").invoke(packet)) {
                if (containsSpawn(element, id)) return true;
            }
        }
        return packet.getClass().getSimpleName().equals("ClientboundAddEntityPacket")
                && (int)packet.getClass().getMethod("getId").invoke(packet) == id;
    }

    private void blue() throws Exception {
        var ghost = only("placement/blue/legacy/rhydonium_barrel_half");
        firstGhost = ghost;
        bluePose = ghost.getLocation();
        blueTransform = ghost.getTransformation();
        check(bluePose.toVector().equals(base.getLocation().add(.5,.5,.5).toVector()), "barrel centered above support");
        panelToward(ghost, BlockFace.NORTH);
        check(owner.canSee(ghost) && !observer.canSee(ghost), "owner-only Paper visibility");
        check(sawSpawn(owner, ghost), "owner received preview spawn packet");
        check(!sawSpawn(observer, ghost), "observer received no preview spawn packet");
        check(!ghost.isPersistent() && !ghost.isVisibleByDefault(), "hidden before tracking and transient");
        var nativeGhost = ghost.getClass().getMethod("getHandle").invoke(ghost);
        check(!(boolean)nativeGhost.getClass().getMethod("isPickable").invoke(nativeGhost), "observer cannot target display");
        check(!(boolean)nativeGhost.getClass().getMethod("isPushable").invoke(nativeGhost), "display has no push collision");
        check(!(boolean)nativeGhost.getClass().getMethod("canBeCollidedWith", Class.forName("net.minecraft.world.entity.Entity"))
                .invoke(nativeGhost,observer.getClass().getMethod("getHandle").invoke(observer)), "observer cannot collide with preview");
        check(base.isEmpty() && upper.isEmpty() && entry() == null, "preview wrote no collision or custody");
        if (Bukkit.getPort() == 25589) testClaims();
        else obstruct();
    }

    // Claims are exercised only in the disposable fixture. The shared-server run never edits clan state.
    private void testClaims() throws Exception {
        var service = Bukkit.getServicesManager().load(ClanService.class);
        claims = service.state();
        fixtureClan = claims.create("PreviewFixture", observer.getUniqueId(), System.currentTimeMillis());
        claimChunk(48,47);
        check(service.canEdit(owner, base.getLocation()) && !service.canEdit(owner, base.getLocation().add(1,0,0)),
                "only the far cell of the three-wide fixture lies in the foreign claim");
        var items = ((MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC")).items();
        owner.getInventory().setItemInMainHand(items.create("crates_sign"));
        actors.drain(owner);
        placements.place(owner, base, org.bukkit.inventory.EquipmentSlot.HAND, TrophyJournal.Face.SOUTH, floor);
        later(4, this::confirmEdgeDenied);
    }

    private void confirmEdgeDenied() throws Exception {
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        var replies = actors.drain(owner).stream().map(plain::serialize).toList();
        check(replies.stream().anyMatch(text -> text.contains("cannot place")) && entry() == null,
                "shared footprint validator vetoed a claimed outer cell on the placement click: " + replies);
        owner.getInventory().setItemInMainHand(barrel.clone());
        unclaim();
        claimChunk(47,47);
        later(6, () -> {
            only("placement/red/legacy/rhydonium_barrel_half");
            click();
            check(owner.getInventory().getItemInMainHand().equals(barrel) && entry() == null,
                    "foreign claim keeps exact barrel in hand");
            unclaim();
            later(6, () -> { only("placement/blue/legacy/rhydonium_barrel_half"); obstruct(); });
        });
    }

    private void claimChunk(int x, int z) {
        var key = new ClanState.Chunk(world.getKey().toString(), x >> 4, z >> 4);
        check(claims.claims.putIfAbsent(key, fixtureClan.id) == null, "fixture chunk unclaimed");
        fixtureChunk = key;
    }

    private void unclaim() {
        if (fixtureChunk != null) claims.claims.remove(fixtureChunk, fixtureClan.id);
        fixtureChunk = null;
    }

    private void obstruct() {
        upper.setType(Material.STONE, false);
        wallInstalled = true;
        later(4, this::red);
    }

    private void red() throws Exception {
        var ghost = only("placement/red/legacy/rhydonium_barrel_half");
        check(ghost.getLocation().equals(bluePose), "red pose matches blue pose");
        if (Bukkit.getPort() != 25589) check(ghost.equals(firstGhost), "stationary tint update reused display");
        click();
        later(8, () -> {
            check(entry() == null && base.isEmpty() && upper.getType() == Material.STONE, "partial obstruction blocked placement");
            check(owner.getInventory().getItemInMainHand().equals(barrel), "blocked click retained exact hand stack");
            upper.setType(Material.AIR, false);
            wallInstalled = false;
            setFuel(375);
            later(4, this::reaim);
        });
    }

    private void reaim() throws Exception {
        only("placement/blue/legacy/rhydonium_barrel_three");
        face(owner, owner.getEyeLocation().add(0, 5, 0));
        later(4, () -> {
            check(displays().isEmpty(), "cleared out of range");
            face(owner, floor.getLocation().add(.5,.6,.5));
            later(4, () -> {
                only("placement/blue/legacy/rhydonium_barrel_three");
                owner.getInventory().setHeldItemSlot(5);
                later(4, () -> {
                    check(displays().isEmpty(), "cleared on hand change");
                    owner.getInventory().setHeldItemSlot(4);
                    later(4, () -> {
                        only("placement/blue/legacy/rhydonium_barrel_three");
                        click();
                        later(30, this::placed);
                    });
                });
            });
        });
    }

    private void placed() throws Exception {
        var display = only("legacy/rhydonium_barrel_three");
        check(display.getLocation().equals(bluePose) && display.getTransformation().equals(blueTransform)
                && display.getItemDisplayTransform() == ItemDisplay.ItemDisplayTransform.NONE, "preview and installed model have identical poses");
        check(entry() != null && entry().cells().size() == 2 && base.getType() == Material.BARRIER
                && upper.getType() == Material.BARRIER, "journal installed entire two-block footprint");
        check(owner.getInventory().getItemInMainHand().isEmpty() && observer.canSee(display), "one consumed and real barrel public");
        panelToward(display, BlockFace.NORTH);
        check(sawSpawn(observer, display), "same observer received the permanent barrel spawn");
        placements.breakTrophy(owner, entry());
        later(30, () -> {
            check(entry() == null && base.isEmpty() && upper.isEmpty(), "both cells released");
            check(Arrays.stream(owner.getInventory().getContents()).anyMatch(barrel::equals), "recovered exact fuel state");
            verifyNextFacing(0);
        });
    }

    private void panelToward(ItemDisplay display, BlockFace side) {
        // The north UV is the orange panel. The pinned item renderer rotates it 180 degrees
        // before applying entity yaw, so the outward panel normal equals the Bukkit look vector.
        check(display.getLocation().getDirection().dot(side.getDirection()) > .9999,
                "orange panel must face " + side + " (display yaw " + display.getYaw() + ")");
    }

    private void verifyNextFacing(int index) throws Exception {
        var sides = List.of(BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
        owner.getInventory().clear();
        owner.getInventory().setItemInMainHand(barrel.clone());
        if (index == sides.size()) {
            face(owner, owner.getEyeLocation().add(0, 5, 0));
            later(4, () -> {
                face(owner, floor.getLocation().add(.5,.6,.5));
                later(4, () -> {
                    var ghost = only("placement/blue/legacy/rhydonium_barrel_three");
                    actors.close();
                    actors = null;
                    check(!ghost.isValid() && displays().isEmpty(), "quit cleared preview immediately");
                    getLogger().info("BARREL PREVIEW PASSED " + checks + " checks including all four panel facings");
                    Bukkit.getPluginManager().disablePlugin(this);
                });
            });
            return;
        }
        var side = sides.get(index);
        var position = floor.getLocation().add(.5,-.8,.5).add(side.getDirection().multiply(3));
        var handle = owner.getClass().getMethod("getHandle").invoke(owner);
        handle.getClass().getMethod("setPos", double.class,double.class,double.class)
                .invoke(handle,position.getX(),position.getY(),position.getZ());
        face(owner, floor.getLocation().add(.5,.6,.5));
        later(5, () -> {
            panelToward(only("placement/blue/legacy/rhydonium_barrel_three"), side);
            click();
            later(25, () -> {
                panelToward(only("legacy/rhydonium_barrel_three"), side);
                placements.breakTrophy(owner, entry());
                later(25, () -> {
                    check(entry() == null && base.isEmpty() && upper.isEmpty(), "test barrel recovered");
                    verifyNextFacing(index + 1);
                });
            });
        });
    }

    // Drive the normal Paper use-item-on-block route, including the other plugins' interaction checks.
    private void click() throws Exception {
        var hit = owner.rayTraceBlocks(4.5);
        check(hit != null && floor.equals(hit.getHitBlock()), "native crosshair points at support");
        var handle = owner.getClass().getMethod("getHandle").invoke(owner);
        var mode = handle.getClass().getField("gameMode").get(handle);
        var pos = Class.forName("net.minecraft.core.BlockPos").getConstructor(int.class,int.class,int.class)
                .newInstance(floor.getX(), floor.getY(), floor.getZ());
        var handType = Class.forName("net.minecraft.world.InteractionHand");
        var hand = handType.getField("MAIN_HAND").get(null);
        var dir = Class.forName("net.minecraft.core.Direction").getField(hit.getHitBlockFace().name()).get(null);
        var v = hit.getHitPosition();
        var vec = Class.forName("net.minecraft.world.phys.Vec3").getConstructor(double.class,double.class,double.class)
                .newInstance(v.getX(),v.getY(),v.getZ());
        var nativeHit = Class.forName("net.minecraft.world.phys.BlockHitResult")
                .getConstructor(vec.getClass(),dir.getClass(),pos.getClass(),boolean.class).newInstance(vec,dir,pos,false);
        var level = world.getClass().getMethod("getHandle").invoke(world);
        var stack = handle.getClass().getMethod("getItemInHand",handType).invoke(handle,hand);
        Arrays.stream(mode.getClass().getMethods()).filter(m -> m.getName().equals("useItemOn") && m.getParameterCount()==5)
                .findFirst().orElseThrow().invoke(mode, handle, level, stack, hand, nativeHit);
    }

    private TrophyJournal.Entry entry() {
        return placements.journal().at(new TrophyJournal.Cell(world.getKey().toString(), base.getX(),base.getY(),base.getZ()));
    }

    @Override public void onDisable() {
        unclaim();
        if (fixtureClan != null) claims.clans.remove(fixtureClan.id, fixtureClan);
        if (actors != null) actors.close();
        for (var chunk : tickets) chunk.removePluginChunkTicket(this);
        if (wallInstalled && upper.getType() == Material.STONE) upper.setType(Material.AIR,false);
        if (supportInstalled && floor.getType() == Material.STONE) floor.setType(Material.AIR,false);
    }
}
