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
import net.mandomc.gameplay.travel.TeleportService;
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
    private final List<TeleportService> listeners = new ArrayList<>();
    private int checks;

    @Override
    public void onEnable() {
        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                verify();
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "ARRIVAL PROBE FAILED", error);
                listeners.forEach(TeleportService::close);
            }
        }, 20L);
    }

    private void verify() throws Exception {
        var settings = new ArrivalSettings("minecraft:spawnworld", "minecraft:overworld");
        var protection = new WorldGuardArrivalProtection();
        var destinations = new ArrivalDestinations(settings, protection,
                ((net.mandomc.bootstrap.MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC")).worlds()::spawn);
        destinations.validate();
        check(true, "both real arrivals have safe footing and loaded protection");
        check(Bukkit.getPluginCommand("survival").getPlugin().getName().equals("MandoMC"),
                "public /survival resolves to MandoMC, not a vendor alias");
        var spawn = destinations.spawn();
        var landing = destinations.survival();
        check(landing.getX() == -135.5 && landing.getZ() == -135.5 && landing.getY() == 73,
                "survival destination centered on middle block of chunk (-9,-9), with safe footing");
        var nonop = new TestPlayer(spawn);
        var actor = WorldGuardPlugin.inst().wrapPlayer(nonop.player);
        var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        for (var location : List.of(spawn, landing,
                new Location(landing.getWorld(), -144, 73, -136),
                new Location(landing.getWorld(), -129, 73, -136),
                new Location(landing.getWorld(), -136, 73, -144),
                new Location(landing.getWorld(), -136, 73, -129))) {
            var point = BukkitAdapter.adapt(location);
            check(!query.testBuild(point, actor, Flags.BLOCK_BREAK), "non-op cannot break inside protected boundary");
            check(!query.testBuild(point, actor, Flags.BLOCK_PLACE), "non-op cannot place inside protected boundary");
            check(query.queryState(point, actor, Flags.PVP) == StateFlag.State.DENY, "PvP denied inside protected boundary");
        }
        for (var location : List.of(new Location(landing.getWorld(), -145, 73, -136),
                new Location(landing.getWorld(), -128, 73, -136),
                new Location(landing.getWorld(), -136, 73, -145),
                new Location(landing.getWorld(), -136, 73, -128))) {
            var point = BukkitAdapter.adapt(location);
            check(query.testBuild(point, actor, Flags.BLOCK_BREAK), "WorldGuard allows building outside boundary");
            check(query.queryState(point, actor, Flags.PVP) != StateFlag.State.DENY, "WorldGuard does not deny PvP outside boundary");
            expectFailure(() -> protection.requireProtected(location), "unsafe arrival outside region rejected");
        }
        expectFailure(() -> new ArrivalDestinations(new ArrivalSettings("minecraft:missing_world",
                "minecraft:overworld"), protection).validate(), "missing destination rejected");

        var plugin = (JavaPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        var language = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "language/messages.yml"));
        var listener = new ArrivalListener(this, settings, destinations, language);
        var travelConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "travel/teleport.yml"));
        travelConfig.set("warmup-seconds", 3);
        var travel = new TeleportService(this, travelConfig, language, ((net.mandomc.bootstrap.MandoPlugin) plugin).combat());
        listeners.add(travel);
        check(Bukkit.getPluginCommand("spawn").getPlugin().getName().equals("MandoMC"), "spawn owned by MandoMC");
        var looking = new TestPlayer(landing);
        travel.request(looking.player, destinations::spawn);
        var rotated = landing.clone(); rotated.setYaw(140); rotated.setPitch(-75);
        travel.onMove(new PlayerMoveEvent(looking.player, landing, rotated));
        var tagged = new TestPlayer(landing);
        travel.tagCombat(tagged.player);
        travel.request(tagged.player, destinations::spawn);
        var failed = new TestPlayer(landing);
        travel.request(failed.player, () -> { throw new IllegalStateException("Unavailable test world"); });
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

        var denied = new TestPlayer(landing);
        denied.allowTeleport = false;
        travel.request(denied.player, destinations::spawn);
        var stale = new TestPlayer(landing);
        int[] resolutions = {0};
        travel.request(stale.player, () -> {
            if (++resolutions[0] > 1) throw new IllegalStateException("Destination removed during countdown");
            return destinations.spawn();
        });
        int startedTick = Bukkit.getCurrentTick();
        var completed = new TestPlayer(spawn);
        travel.request(completed.player, destinations::survival);
        travel.request(completed.player, destinations::survival);
        var moved = new TestPlayer(spawn);
        travel.request(moved.player, destinations::survival);
        travel.onMove(new PlayerMoveEvent(moved.player, spawn, spawn.clone().add(1, 0, 0)));
        var quit = new TestPlayer(spawn);
        travel.request(quit.player, destinations::survival);
        travel.onQuit(new PlayerQuitEvent(quit.player, (net.kyori.adventure.text.Component) null));
        var outside = new TestPlayer(landing);
        travel.request(outside.player, destinations::spawn);
        var mounted = new TestPlayer(spawn);
        mounted.mounted = true;
        travel.request(mounted.player, destinations::survival);
        var changedWorld = new TestPlayer(spawn);
        travel.request(changedWorld.player, destinations::survival);
        changedWorld.location = landing;

        getServer().getScheduler().runTaskLater(this, () -> {
            check(completed.teleports == 0, "does not teleport before the full three seconds");
        }, 59L);
        getServer().getScheduler().runTaskLater(this, () -> {
            try {
                check(completed.titleText.subList(0, 3).equals(List.of("3", "2", "1")), "countdown titles are exactly 3, 2, 1");
                check(completed.teleportTick - startedTick >= 60, "teleport waits at least 60 server ticks");
                check(completed.teleports == 1 && completed.location.getWorld().equals(landing.getWorld()),
                        "one warmup produces one survival teleport, even after duplicate command");
                check(moved.teleports == 0, "movement cancels departure");
                check(quit.teleports == 0, "disconnect cancels departure");
                check(outside.teleports == 1 && outside.location.getWorld().equals(spawn.getWorld()), "spawn returns to SpawnWorld");
                check(looking.teleports == 1, "looking around does not cancel teleport");
                check(tagged.teleports == 0, "combat-tagged travel blocked");
                check(failed.teleports == 0, "missing destination never teleports");
                check(denied.teleports == 0 && denied.titles == 3, "cancelled teleport has no success title/effect");
                check(stale.teleports == 0, "destination revalidated at completion");
                check(completed.titles >= 2 && completed.sounds >= 1, "countdown and success titles plus sound emitted");
                check(mounted.teleports == 0, "mounted departure refused");
                check(changedWorld.teleports == 0, "world change during warmup cancels departure");
                getLogger().info("ARRIVAL PROBE PASSED: " + checks
                        + " assertions against live worlds/WorldGuard plus simulated player callbacks.");
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "ARRIVAL PROBE FAILED", error);
            } finally {
                travel.close();
            }
        }, 65L);
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
        listeners.forEach(TeleportService::close);
    }

    private static final class TestPlayer {
        private org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        private final UUID id = UUID.randomUUID();
        private final PersistentDataContainer data = new ItemStack(Material.STONE).getItemMeta()
                .getPersistentDataContainer();
        private final Player player;
        private Location location;
        private boolean mounted;
        private boolean allowTeleport = true;
        private int teleports;
        private int messages;
        private int titles;
        private final List<String> titleText = new ArrayList<>();
        private int teleportTick;
        private int sounds;

        private TestPlayer(Location location) {
            this.location = location.clone();
            player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getScoreboard" -> scoreboard;
                        case "setScoreboard" -> { scoreboard = (org.bukkit.scoreboard.Scoreboard) args[0]; yield null; }
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
                        case "showTitle" -> {
                            titles++;
                            titleText.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                                    .serialize(((net.kyori.adventure.title.Title) args[0]).title()));
                            yield null;
                        }
                        case "clearTitle" -> null;
                        case "playSound" -> { sounds++; yield null; }
                        case "sendMessage" -> { messages++; yield null; }
                        case "teleport" -> { if (!allowTeleport) yield false; this.location = ((Location) args[0]).clone(); teleports++; teleportTick = Bukkit.getCurrentTick(); yield true; }
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
