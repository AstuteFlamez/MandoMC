package net.mandomc.verification;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import net.mandomc.gameplay.arrival.ArrivalListener;
import net.mandomc.gameplay.arrival.ArrivalSettings;
import net.mandomc.integrations.regions.WorldGuardArrivalProtection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;

/** Run only in the local development server. Player proxies never connect or write player files. */
public final class ArrivalProbe extends JavaPlugin {
    private final List<ArrivalListener> listeners = new ArrayList<>();
    private int checks;

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                verify();
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "ARRIVAL PROBE FAILED", error);
                listeners.forEach(ArrivalListener::close);
            }
        }, 20L);
    }

    private void verify() throws Exception {
        var settings = new ArrivalSettings("minecraft:spawnworld", "minecraft:overworld", 1);
        var protection = new WorldGuardArrivalProtection();
        var destinations = new ArrivalDestinations(settings, protection);
        destinations.validate();
        check(true, "both real arrivals have safe footing and loaded protection");
        check(Bukkit.getPluginCommand("survival").getPlugin().getName().equals("MandoMC"),
                "public /survival resolves to MandoMC, not a vendor alias");
        var spawn = destinations.spawn();
        var landing = destinations.survival();
        var nonop = new TestPlayer(spawn);
        var actor = WorldGuardPlugin.inst().wrapPlayer(nonop.player);
        var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        for (var location : List.of(spawn, landing,
                new Location(landing.getWorld(), -176, 73, -144),
                new Location(landing.getWorld(), -112, 73, -144),
                new Location(landing.getWorld(), -144, 73, -176),
                new Location(landing.getWorld(), -144, 73, -112))) {
            var point = BukkitAdapter.adapt(location);
            check(!query.testBuild(point, actor, Flags.BLOCK_BREAK), "non-op cannot break inside protected boundary");
            check(!query.testBuild(point, actor, Flags.BLOCK_PLACE), "non-op cannot place inside protected boundary");
            check(query.queryState(point, actor, Flags.PVP) == StateFlag.State.DENY, "PvP denied inside protected boundary");
        }
        for (var location : List.of(new Location(landing.getWorld(), -177, 73, -144),
                new Location(landing.getWorld(), -111, 73, -144),
                new Location(landing.getWorld(), -144, 73, -177),
                new Location(landing.getWorld(), -144, 73, -111))) {
            var point = BukkitAdapter.adapt(location);
            check(query.testBuild(point, actor, Flags.BLOCK_BREAK), "WorldGuard allows building outside boundary");
            check(query.queryState(point, actor, Flags.PVP) != StateFlag.State.DENY, "WorldGuard does not deny PvP outside boundary");
            expectFailure(() -> protection.requireProtected(location), "unsafe arrival outside region rejected");
        }
        expectFailure(() -> new ArrivalDestinations(new ArrivalSettings("minecraft:missing_world",
                "minecraft:overworld", 1), protection).validate(), "missing destination rejected");

        var plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        var language = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "messages.yml"));
        var listener = new ArrivalListener(this, settings, destinations, language);
        listeners.add(listener);
        var learner = new TestPlayer(spawn);
        listener.onPack(new PlayerResourcePackStatusEvent(learner.player, UUID.randomUUID(),
                PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD));
        check(learner.messages == 0, "failed pack does not mark or display tutorial");
        listener.onPack(new PlayerResourcePackStatusEvent(learner.player, UUID.randomUUID(),
                PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        int firstMessages = learner.messages;
        check(firstMessages == language.getStringList("tutorial").size(), "successful pack displays tutorial");
        var returning = new TestPlayer(spawn);
        returning.data.readFromBytes(learner.data.serializeToBytes());
        listener.onPack(new PlayerResourcePackStatusEvent(returning.player, UUID.randomUUID(),
                PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        check(returning.messages == 0, "serialized tutorial marker survives reconnect/restart data roundtrip");
        listener.tutorial(returning.player);
        check(returning.messages == firstMessages, "tutorial can be replayed manually");

        var completed = new TestPlayer(spawn);
        listener.depart(completed.player);
        listener.depart(completed.player);
        var moved = new TestPlayer(spawn);
        listener.depart(moved.player);
        listener.onMove(new PlayerMoveEvent(moved.player, spawn, spawn.clone().add(1, 0, 0)));
        var quit = new TestPlayer(spawn);
        listener.depart(quit.player);
        listener.onQuit(new PlayerQuitEvent(quit.player, (net.kyori.adventure.text.Component) null));
        var outside = new TestPlayer(landing);
        listener.depart(outside.player);
        var mounted = new TestPlayer(spawn);
        mounted.mounted = true;
        listener.depart(mounted.player);
        var changedWorld = new TestPlayer(spawn);
        listener.depart(changedWorld.player);
        changedWorld.location = landing;

        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                check(completed.teleports == 1 && completed.location.getWorld().equals(landing.getWorld()),
                        "one warmup produces one survival teleport, even after duplicate command");
                check(moved.teleports == 0, "movement cancels departure");
                check(quit.teleports == 0, "disconnect cancels departure");
                check(outside.teleports == 0, "cannot depart from survival or bypass future combat travel rules");
                check(mounted.teleports == 0, "mounted departure refused");
                check(changedWorld.teleports == 0, "world change during warmup cancels departure");
                getLogger().info("ARRIVAL PROBE PASSED: " + checks
                        + " assertions against live worlds/WorldGuard plus simulated player callbacks.");
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "ARRIVAL PROBE FAILED", error);
            } finally {
                listener.close();
            }
        }, 25L);
    }

    private void expectFailure(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            check(true, label);
            return;
        }
        throw new AssertionError(label);
    }

    private void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
        checks++;
        getLogger().info("PASS: " + label);
    }

    @Override
    public void onDisable() {
        listeners.forEach(ArrivalListener::close);
    }

    private static final class TestPlayer {
        private final UUID id = UUID.randomUUID();
        private final PersistentDataContainer data = new ItemStack(Material.STONE).getItemMeta()
                .getPersistentDataContainer();
        private final Player player;
        private Location location;
        private boolean mounted;
        private int teleports;
        private int messages;

        private TestPlayer(Location location) {
            this.location = location.clone();
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getName" -> "ArrivalProbe";
                        case "getUniqueId" -> id;
                        case "getWorld" -> this.location.getWorld();
                        case "getLocation" -> this.location.clone();
                        case "getPersistentDataContainer" -> data;
                        case "isOnline" -> true;
                        case "isInsideVehicle" -> mounted;
                        case "getPassengers" -> List.of();
                        case "getEffectivePermissions" -> java.util.Set.of();
                        case "getGameMode" -> org.bukkit.GameMode.SURVIVAL;
                        case "sendMessage" -> { messages++; yield null; }
                        case "teleport" -> { this.location = ((Location) args[0]).clone(); teleports++; yield true; }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> id.hashCode();
                        case "toString" -> "ArrivalProbe " + id;
                        default -> {
                            if (method.getReturnType() == boolean.class) yield false;
                            if (method.getReturnType() == int.class) yield 0;
                            throw new UnsupportedOperationException("Unmocked player method: " + method);
                        }
                    });
        }
    }
}
