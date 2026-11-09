package net.mandomc.verification;

import java.lang.reflect.Proxy;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.combat.CombatBarrier;
import net.mandomc.gameplay.combat.CombatBoundary.Cell;
import net.mandomc.gameplay.combat.CombatService;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.bukkit.damage.*;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;

/** Temporary local probe: packet recipients are isolated proxies; worlds and region queries are real. */
public final class BarrierProbe extends JavaPlugin {
    private int checks;
    private CombatService combat;
    private CombatBarrier barrier;
    private World world;
    private final List<Actor> actors = new ArrayList<>();
    private final List<Chunk> chunks = new ArrayList<>();
    private Player rider;
    private Boat boat;
    private Horse horse;
    private Actor expiring;
    private String zoneId;
    private String holeId;

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {verify();} catch (Throwable error) {fail(error);}
        }, 60);
    }
    private void verify() throws Exception {
        var plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        combat = plugin.combat();barrier = plugin.barrier();
        world = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        for (int x : new int[] {-146,-144,-128}) for (int z : new int[] {-146,-136,-128}) {
            var chunk = world.getChunkAt(x >> 4,z >> 4);chunk.addPluginChunkTicket(this);chunks.add(chunk);
        }
        var outside = new Location(world,-146.5,300,-136.5);
        var inside = new Location(world,-143.5,300,-136.5);
        var tagged = actor(outside);
        check(!barrier.blocks(tagged.player,outside,inside),"untagged player can enter safe zone");
        combat.tag(tagged.player);
        check(barrier.blocks(tagged.player,outside,inside),"tagged player blocked at west face");
        check(barrier.blocks(tagged.player,new Location(world,-127,300,-136),new Location(world,-129,300,-136)),"tagged player blocked at east face");
        check(barrier.blocks(tagged.player,new Location(world,-136,300,-145),new Location(world,-136,300,-143)),"tagged player blocked at north face");
        check(barrier.blocks(tagged.player,new Location(world,-136,300,-127),new Location(world,-136,300,-129)),"tagged player blocked at south face");
        check(barrier.blocks(tagged.player,outside,new Location(world,-120,300,-136.5)),"fast movement cannot tunnel through safe zone");
        check(barrier.blocks(tagged.player,new Location(world,-136,330,-136),new Location(world,-136,310,-136)),"vertical entry from above is blocked");
        check(!barrier.blocks(tagged.player,inside,outside),"tagged player already inside can leave");
        check(!barrier.blocks(tagged.player,outside,outside.clone().add(0,0,2)),"movement parallel to boundary is allowed");
        var move = new PlayerMoveEvent(tagged.player,outside,inside);barrier.move(move);
        check(move.isCancelled(),"movement event denies entry on server");
        check(tagged.velocity.isZero(),"entry correction stops incoming velocity");
        check(tagged.warnings == 1,"entry denial shows action-bar message");
        barrier.move(new PlayerMoveEvent(tagged.player,outside,inside));
        check(tagged.warnings == 1,"repeated contact does not spam messages");
        check(!tagged.red().isEmpty(),"tagged player receives red glass packet blocks");
        check(tagged.red().stream().allMatch(p -> p.x() == -144),"glass follows safe side of actual WorldGuard edge");
        check(tagged.red().stream().allMatch(p -> world.getBlockAt(p.x(),p.y(),p.z()).getType().isAir()),"glass never changes real blocks");
        var attempted = actor(inside);combat.tag(attempted.player);
        barrier.move(new PlayerMoveEvent(attempted.player,outside,inside));
        check(!attempted.red().isEmpty(),"denied move renders glass at retained outside position");
        attempted.location = outside.clone();
        var observer = actor(outside);barrier.refresh(observer.player);
        check(observer.packets.isEmpty(),"untagged observer receives no wall packets");
        var distant = actor(new Location(world,-200,300,-200));combat.tag(distant.player);barrier.refresh(distant.player);
        check(distant.red().isEmpty(),"no wall appears away from boundaries");
        var unseen = actor(outside);unseen.chunksVisible = false;combat.tag(unseen.player);barrier.refresh(unseen.player);
        check(unseen.packets.isEmpty(),"no fake blocks sent into unsent chunks");
        var blockPoint = tagged.red().iterator().next();
        var block = world.getBlockAt(blockPoint.x(),blockPoint.y(),blockPoint.z());
        var original = block.getBlockData();
        try {
            var breaking = new BlockBreakEvent(block,tagged.player);barrier.breakGlass(breaking);
            check(breaking.isCancelled(),"fake glass cannot be broken");
            block.setType(Material.STONE,false);
            combat.clear(tagged.player);barrier.refresh(tagged.player);
            check(tagged.packets.get(blockPoint).getMaterial() == Material.STONE,"wall removal sends current terrain instead of stale snapshot");
            check(tagged.red().isEmpty(),"clearing combat removes all glass");
            check(!barrier.blocks(tagged.player,outside,inside),"entry opens after combat clears");
        } finally {block.setBlockData(original,false);}
        var walking = actor(outside);combat.tag(walking.player);barrier.refresh(walking.player);
        check(!walking.red().isEmpty(),"wall present before walking away");
        walking.location = new Location(world,-170,300,-136);barrier.refresh(walking.player);
        check(walking.red().isEmpty(),"moving away restores all previous fake blocks");
        var quitting = actor(outside);combat.tag(quitting.player);barrier.refresh(quitting.player);
        barrier.quit(new PlayerQuitEvent(quitting.player,Component.empty()));
        check(quitting.red().isEmpty(),"quit clears client wall state");
        var dying = actor(outside);combat.tag(dying.player);barrier.refresh(dying.player);
        barrier.death(new PlayerDeathEvent(dying.player,DamageSource.builder(DamageType.GENERIC).build(),new ArrayList<>(),0,Component.empty(),false));
        check(dying.red().isEmpty(),"death restores fake blocks");
        var changing = actor(outside);combat.tag(changing.player);barrier.refresh(changing.player);
        int oldPackets = changing.sends;
        changing.location = Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")).getSpawnLocation();
        barrier.worldChanged(new PlayerChangedWorldEvent(changing.player,world));
        check(changing.sends == oldPackets,"world change does not send old-world blocks into new world");
        verifyPriority();
        verifyVehicle(outside,inside);
        expiring = actor(outside);combat.tag(expiring.player);barrier.refresh(expiring.player);
        check(!expiring.red().isEmpty(),"wall present for real expiry test");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                check(!combat.tagged(expiring.player),"normal fifteen-second timer expires");
                check(expiring.red().isEmpty(),"expiry automatically removes wall without movement");
                check(!barrier.blocks(expiring.player,outside,inside),"expiry automatically permits entry");
                getLogger().info("BARRIER PROBE PASSED: " + checks + " assertions");cleanup();
            } catch (Throwable error) {fail(error);}
        },330);
    }
    private void verifyPriority() {
        var manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
        zoneId = "barrier_probe_" + UUID.randomUUID().toString().replace("-","");holeId = zoneId + "_hole";
        var zone = new ProtectedCuboidRegion(zoneId,BlockVector3.at(-300,295,-300),BlockVector3.at(-290,305,-290));
        zone.setFlag(Flags.PVP,StateFlag.State.DENY);zone.setPriority(150);
        var hole = new ProtectedCuboidRegion(holeId,BlockVector3.at(-300,295,-296),BlockVector3.at(-290,305,-294));
        hole.setFlag(Flags.PVP,StateFlag.State.ALLOW);hole.setPriority(160);
        manager.addRegion(zone);manager.addRegion(hole);
        var player = actor(new Location(world,-302,300,-295));combat.tag(player.player);
        check(!barrier.blocks(player.player,player.location,new Location(world,-298,300,-295)),"higher-priority PvP-allowed corridor stays open");
        check(barrier.blocks(player.player,new Location(world,-302,300,-298),new Location(world,-298,300,-298)),"neighboring denied area still blocks entry");
    }
    private void verifyVehicle(Location outside, Location inside) throws Exception {
        boat = (Boat) world.spawnEntity(outside,EntityType.OAK_BOAT);boat.setPersistent(false);boat.setGravity(false);
        rider = nativeTestPlayer(world,outside);
        check(boat.addPassenger(rider),"native test rider mounts boat");combat.tag(rider);
        check(boat.teleport(inside),"test boat reaches simulated attempted entry");
        barrier.vehicle(new VehicleMoveEvent(boat,outside,inside));
        check(boat.getLocation().distanceSquared(outside) < .01,"tagged boat is returned outside safe zone");
        check(boat.getPassengers().contains(rider),"boat correction keeps passenger mounted");
        check(!rider.isInvulnerable(),"boat correction does not grant teleport invulnerability");
        rider.leaveVehicle();boat.remove();boat=null;
        horse = world.spawn(outside,Horse.class,h -> {h.setPersistent(false);h.setAI(false);h.setGravity(false);});
        check(horse.addPassenger(rider),"native test rider mounts horse");
        var movement = new io.papermc.paper.event.entity.EntityMoveEvent(horse,outside,inside);
        barrier.mount(movement);
        check(movement.isCancelled(),"tagged living mount cannot enter safe zone");
        combat.clear(rider);rider.leaveVehicle();horse.remove();horse=null;
    }
    private Actor actor(Location location) { var actor = new Actor(location);actors.add(actor);return actor; }
    private void check(boolean ok,String message) {if(!ok)throw new IllegalStateException(message);checks++;getLogger().info("PASS " + message);}
    private void fail(Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"BARRIER PROBE FAILED",error);Bukkit.getScheduler().cancelTasks(this);cleanup();}
    private void cleanup() {
        for (Actor actor : actors) {if(barrier!=null)barrier.clear(actor.player);if(combat!=null)combat.clear(actor.player);}
        if(rider!=null) {rider.leaveVehicle();if(combat!=null)combat.clear(rider);}
        if(boat!=null)boat.remove();
        if(horse!=null)horse.remove();
        chunks.forEach(chunk -> chunk.removePluginChunkTicket(this));
        if(world!=null) {
            var manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
            if(manager!=null) {if(zoneId!=null)manager.removeRegion(zoneId);if(holeId!=null)manager.removeRegion(holeId);}
        }
    }
    @Override public void onDisable() {cleanup();}
    private final class Actor {
        private final UUID id = UUID.randomUUID();
        private Location location;
        private org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        private final Map<Cell,BlockData> packets = new HashMap<>();
        private boolean chunksVisible = true;
        private int sends;
        private int warnings;
        private Vector velocity = new Vector(1,0,0);
        private final Player player;
        private Actor(Location location) {
            this.location = location.clone();
            player = (Player) Proxy.newProxyInstance(getClassLoader(),new Class<?>[] {Player.class},(proxy,method,args) -> {
                switch(method.getName()) {
                    case "getUniqueId": return id;
                    case "getName": return "BarrierProbe";
                    case "getLocation": return this.location.clone();
                    case "getWorld": return this.location.getWorld();
                    case "isOnline": return true;
                    case "isDead": return false;
                    case "isChunkSent": return chunksVisible;
                    case "getScoreboard": return scoreboard;
                    case "setScoreboard": scoreboard = (org.bukkit.scoreboard.Scoreboard) args[0];return null;
                    case "setVelocity": velocity = ((Vector)args[0]).clone();return null;
                    case "getVelocity": return velocity.clone();
                    case "sendActionBar": warnings++;return null;
                    case "sendMessage": return null;
                    case "sendMultiBlockChange":
                        sends++;
                        ((Map<?,?>)args[0]).forEach((key,value) -> {
                            var pos = (io.papermc.paper.math.Position)key;
                            packets.put(new Cell(pos.blockX(),pos.blockY(),pos.blockZ()),((BlockData)value).clone());
                        });return null;
                    case "sendBlockChange":
                        sends++;var where = (Location)args[0];
                        packets.put(new Cell(where.getBlockX(),where.getBlockY(),where.getBlockZ()),((BlockData)args[1]).clone());return null;
                    case "getPassengers": return List.of();
                    case "getGameMode": return GameMode.SURVIVAL;
                    case "getEffectivePermissions": return Set.of();
                    case "equals": return proxy == args[0];
                    case "hashCode": return id.hashCode();
                    case "toString": return "BarrierProbe " + id;
                    default:
                        if(method.getReturnType()==boolean.class)return false;
                        if(method.getReturnType()==int.class)return 0;
                        if(method.getReturnType()==double.class)return 0d;
                        if(method.getReturnType()==float.class)return 0f;
                        return null;
                }
            });
        }
        private Set<Cell> red() {
            Set<Cell> result = new HashSet<>();
            packets.forEach((point,data) -> {if(data.getMaterial()==Material.RED_STAINED_GLASS)result.add(point);});
            return result;
        }
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
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "BorderTest");
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
