package net.mandomc.verification;

import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.*;
import net.mandomc.access.*;
import net.coreprotect.CoreProtect;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable isolated Paper fixture only. No production worlds or players. */
public final class AccessProbe extends JavaPlugin {
    private int checks;
    private AccessPlugin access;
    private final UUID owner = UUID.fromString("e7075e0d-f7fb-479b-9bb9-1c30059a43d2");
    private final UUID guest = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
    private Path stateFile;
    private void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
        checks++; getLogger().info("PASS " + why);
    }
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { run(); } catch (Throwable error) { fail(error); }
        }, 60);
    }
    private void fail(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "ACCESS PROBE FAILED", error); Bukkit.shutdown(); }
    private void command(String... args) { access.onCommand(Bukkit.getConsoleSender(), access.getCommand("maintenance"), "maintenance", args); }
    private CommandSender sender(boolean admin) {
        return (CommandSender) Proxy.newProxyInstance(getClassLoader(), new Class<?>[] {CommandSender.class}, (p,m,a) -> {
            if (m.getName().equals("hasPermission") || m.getName().equals("isOp")) return admin;
            if (m.getName().equals("getName")) return "Probe";
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == int.class) return 0;
            return null;
        });
    }
    private Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(getClassLoader(), new Class<?>[] {Player.class}, (p,m,a) -> {
            if (m.getName().equals("getUniqueId")) return id;
            if (m.getName().equals("getName")) return "Probe";
            if (m.getReturnType() == boolean.class) return false;
            if (m.getReturnType() == int.class) return 0;
            return null;
        });
    }
    private AsyncPlayerPreLoginEvent pre(UUID id) throws Exception {
        var event = new AsyncPlayerPreLoginEvent("Probe", InetAddress.getLoopbackAddress(), id);
        access.preLogin(event); return event;
    }
    @SuppressWarnings("removal") private void run() throws Exception {
        if (!Bukkit.getWorlds().getFirst().getName().equals("access_probe")) throw new IllegalStateException("Only run in disposable access_probe runtime");
        access = (AccessPlugin) Bukkit.getPluginManager().getPlugin("MandoAccess");
        stateFile = access.getDataFolder().toPath().resolve("maintenance.yml");
        check(access.isEnabled(), "independent gate enabled without gameplay plugin");
        var persisted = AccessState.load(stateFile);
        check(persisted.enabled() && persisted.players().size() == 1 && persisted.owner().equals(owner), "boot/restart retains maintenance enabled with verified owner only");
        check(pre(owner).getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED, "owner passes prelogin");
        check(pre(guest).getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED, "unlisted UUID denied before entry");
        var banned = pre(owner); banned.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, net.kyori.adventure.text.Component.text("ban")); access.preLogin(banned);
        check(banned.getLoginResult() == AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "listed owner cannot override another denial");
        command("off");
        check(!AccessState.load(stateFile).enabled() && access.allows(guest), "off persists and opens only maintenance layer");
        var whiteDenied = new PlayerLoginEvent(player(guest), "localhost", InetAddress.getLoopbackAddress());
        whiteDenied.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, net.kyori.adventure.text.Component.text("whitelist")); access.login(whiteDenied);
        check(whiteDenied.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST, "maintenance off preserves vanilla whitelist denial");
        access.onCommand(sender(false), access.getCommand("maintenance"), "maintenance", new String[] {"on"});
        check(!AccessState.load(stateFile).enabled(), "nonadmin cannot enable maintenance");
        check(access.onTabComplete(sender(false), access.getCommand("maintenance"), "maintenance", new String[] {""}).isEmpty(), "no private completions for nonadmin");
        command("on"); command("remove", "AstuteFlamez");
        check(access.allows(owner) && !access.allows(guest), "owner removal refused and on persists");
        var login = new PlayerLoginEvent(player(guest), "localhost", InetAddress.getLoopbackAddress()); access.login(login);
        check(login.getResult() != PlayerLoginEvent.Result.ALLOWED, "sync gate closes authentication race");
        check(Bukkit.hasWhitelist() && Bukkit.getOfflinePlayer(owner).isWhitelisted(), "vanilla whitelist enabled and owner listed");
        command("add", "Notch");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                var saved = AccessState.load(stateFile);
                UUID resolved = saved.players().entrySet().stream().filter(entry -> entry.getValue().equals("Notch")).map(Map.Entry::getKey).findFirst().orElseThrow();
                check(!resolved.equals(UUID.nameUUIDFromBytes("OfflinePlayer:Notch".getBytes(java.nio.charset.StandardCharsets.UTF_8))), "async name lookup resolves online UUID");
                check(access.allows(resolved) && pre(resolved).getLoginResult() == AsyncPlayerPreLoginEvent.Result.ALLOWED, "listed additional account passes prelogin");
                check(!Bukkit.getOfflinePlayer(resolved).isOp() && !Bukkit.getOfflinePlayer(resolved).isWhitelisted(), "maintenance addition grants neither OP nor whitelist");
                var independent = new PlayerLoginEvent(player(resolved), "localhost", InetAddress.getLoopbackAddress());
                independent.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, net.kyori.adventure.text.Component.text("whitelist")); access.login(independent);
                check(independent.getResult() == PlayerLoginEvent.Result.KICK_WHITELIST, "maintenance listed account still requires vanilla whitelist");
                command("remove", "Notch");
                check(!access.allows(resolved) && AccessState.load(stateFile).players().size() == 1, "removal persists and immediately restricts listed guest");
            } catch (Throwable error) { fail(error); }
        }, 440);
        for (String permission : List.of("mandomc.admin.maintenance", "mandomc.admin.help", "coreprotect.co", "coreprotect.lookup", "coreprotect.inspect", "coreprotect.rollback", "coreprotect.restore")) {
            var perm = Bukkit.getPluginManager().getPermission(permission);
            check(perm != null && !perm.getDefault().getValue(false) && perm.getDefault().getValue(true), permission + " default is staff only");
        }
        var core = (CoreProtect) Bukkit.getPluginManager().getPlugin("CoreProtect");
        var api = core.getAPI();
        check(core.isEnabled() && api.isEnabled(), "official CoreProtect 24.1/API active on Paper 26.1.2 Java25");
        var config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(core.getDataFolder().toPath().resolve("config.yml").toFile());
        for (String key : List.of("block-place", "block-break", "item-transactions", "explosions")) check(config.getBoolean(key), key + " audit enabled");
        check(!config.getBoolean("use-mysql"), "private embedded SQLite database selected");
        var block = Bukkit.getWorlds().getFirst().getBlockAt(8, 100, 8);
        block.setType(Material.DIAMOND_BLOCK, false);
        check(api.logRemoval("AccessProbe", block.getState()), "isolated block removal queued");
        block.setType(Material.AIR, false);
        var placed = block.getWorld().getBlockAt(20, 100, 8);
        placed.setType(Material.GOLD_BLOCK, false);
        check(api.logPlacement("AccessProbe", placed.getState()), "isolated placement queued");
        var chestBlock = block.getWorld().getBlockAt(30, 100, 8);
        chestBlock.setType(Material.CHEST, false);
        var chest = (org.bukkit.block.Chest) chestBlock.getState();
        chest.getBlockInventory().setItem(0, new org.bukkit.inventory.ItemStack(Material.DIAMOND, 3));
        check(api.logContainerTransaction("AccessProbe", chestBlock.getLocation()), "container before-state queued");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            chest.getBlockInventory().clear();
            api.logContainerTransaction("AccessProbe", chestBlock.getLocation());
        }, 10);
        var blast = block.getWorld().getBlockAt(40, 100, 8);
        blast.setType(Material.GLASS, false);
        var tnt = blast.getWorld().spawn(blast.getLocation().add(.5, .5, .5), org.bukkit.entity.TNTPrimed.class,
                entity -> { entity.setFuseTicks(Integer.MAX_VALUE); entity.setGravity(false); });
        blast.getWorld().createExplosion(blast.getLocation().add(.5, .5, .5), 2, false, true, tnt);
        tnt.remove();
        check(blast.getType().isAir(), "isolated vanilla explosion destroyed test glass");
        Bukkit.getScheduler().runTaskLaterAsynchronously(this, () -> {
            try {
                check(!api.blockLookup(block, 300).isEmpty(), "removed block persisted and readable through lookup");
                check(!api.blockLookup(placed, 300).isEmpty(), "placement persisted and readable");
                check(!api.blockLookup(blast, 300).isEmpty(), "real explosion audit persisted");
                check(!api.containerLookup(chestBlock.getLocation(), 300).isEmpty(), "container item removal audit persisted");
                var rows = api.performRollback(300, new ArrayList<>(List.of("AccessProbe")), null, null, null, new ArrayList<>(List.of(0)), 2, block.getLocation());
                check(rows != null && !rows.isEmpty(), "scoped isolated rollback returned rows");
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    try {
                        check(block.getType() == Material.DIAMOND_BLOCK, "isolated rollback restored exact test block");
                        block.setType(Material.AIR, false);
                        placed.setType(Material.AIR, false);
                        chest.getBlockInventory().clear(); chestBlock.setType(Material.AIR, false);
                        getLogger().info("ACCESS PROBE PASSED: " + checks + " assertions");
                        Bukkit.shutdown();
                    } catch (Throwable error) { fail(error); }
                }, 30);
            } catch (Throwable error) { Bukkit.getScheduler().runTask(this, () -> fail(error)); }
        }, 480);
    }
}
