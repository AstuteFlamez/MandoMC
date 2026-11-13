package net.mandomc.access;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.permissions.PermissionDefault;

public final class AccessPlugin extends JavaPlugin implements Listener {
    private static final String PERMISSION = "mandomc.admin.maintenance";
    private volatile AccessState state;
    private Path path;
    private Component denied = Component.text("MandoMC: Maintenance is in progress. Please try again later.");
    private Component unavailable = Component.text("MandoMC: Access is temporarily unavailable.");
    private int pending;

    @Override public void onEnable() {
        // Stay registered and fail closed if configuration/state initialization fails.
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("maintenance")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("adminhelp")).setTabCompleter(this);
        try {
            saveDefaultConfig();
            if (getConfig().getInt("schema-version", -1) != 1) throw new IllegalArgumentException("Invalid config schema");
            if (!getServer().getOnlineMode()) throw new IllegalArgumentException("Maintenance requires verified online-mode UUIDs");
            denied = MiniMessage.miniMessage().deserialize(getConfig().getString("denied", "MandoMC: Maintenance"));
            unavailable = MiniMessage.miniMessage().deserialize(getConfig().getString("unavailable", "MandoMC: Access unavailable"));
            path = getDataFolder().toPath().resolve("maintenance.yml");
            if (Files.exists(path)) state = AccessState.load(path);
            else {
                String name = getConfig().getString("initial-owner", "AstuteFlamez");
                var owner = getServer().getOperators().stream().filter(player -> name.equalsIgnoreCase(player.getName()))
                        .findFirst().orElseThrow(() -> new IllegalStateException("Initial owner must already be in online-mode ops.json"));
                var initial = new AccessState(true, owner.getUniqueId(), Map.of(owner.getUniqueId(), Objects.requireNonNull(owner.getName())));
                initial.save(path);
                state = initial;
            }
            getLogger().info("Maintenance " + (state.enabled() ? "ON" : "OFF") + "; " + state.players().size() + " listed account(s). Vanilla whitelist remains independent.");
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Access initialization failed; all joins blocked. Repair state/config and restart.", error);
        }
    }

    public boolean allows(UUID id) { var current = state; return current != null && current.allows(id); }

    @EventHandler
    public void pluginEnabled(PluginEnableEvent event) {
        if (!event.getPlugin().getName().equals("CoreProtect")) return;
        // CoreProtect exposes its root help command publicly by default. Keep staff tools private.
        for (String name : List.of("coreprotect.co", "coreprotect.core", "coreprotect.coreprotect")) {
            var permission = getServer().getPluginManager().getPermission(name);
            if (permission != null) permission.setDefault(PermissionDefault.OP);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void preLogin(AsyncPlayerPreLoginEvent event) {
        if (!allows(event.getUniqueId())) event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, state == null ? unavailable : denied);
    }

    @SuppressWarnings("removal")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void login(PlayerLoginEvent event) {
        // Recheck after async authentication in case an admin changed the list meanwhile.
        if (!allows(event.getPlayer().getUniqueId())) event.disallow(PlayerLoginEvent.Result.KICK_OTHER, state == null ? unavailable : denied);
        // Never allow() here: vanilla whitelist, bans and capacity keep their own denials.
    }

    private boolean permitted(CommandSender sender) { return sender instanceof ConsoleCommandSender || sender.hasPermission(PERMISSION); }
    private void reply(CommandSender sender, String text) { sender.sendMessage(Component.text("MandoMC » " + text)); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equals("adminhelp")) {
            if (!sender.hasPermission("mandomc.admin.help") && !(sender instanceof ConsoleCommandSender)) return true;
            for (String entry : new String[] {"maintenance", "gm", "fly", "speed", "heal", "feed", "god", "vanish", "tp", "tphere", "invsee", "enderchest", "event", "co"}) {
                var registered = getServer().getPluginCommand(entry);
                if (registered != null && registered.testPermissionSilent(sender)) reply(sender, "/" + entry + " — " + registered.getDescription());
            }
            return true;
        }
        if (!permitted(sender)) { reply(sender, "You do not have permission."); return true; }
        if (state == null) { reply(sender, "Access state unavailable; repair server files and restart. Joins are blocked."); return true; }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        try {
            if (args.length == 1 && (action.equals("on") || action.equals("off"))) {
                change(state.withEnabled(action.equals("on")));
                reply(sender, "Maintenance " + (state.enabled() ? "ON. Only listed accounts may join. Online players were not kicked." : "OFF. Normal whitelist and bans still apply."));
            } else if (args.length <= 1 && action.equals("status")) {
                reply(sender, "Maintenance " + (state.enabled() ? "ON" : "OFF") + "; " + state.players().size() + " listed; vanilla whitelist " + (getServer().hasWhitelist() ? "ON" : "OFF") + ".");
            } else if (args.length == 1 && action.equals("list")) {
                reply(sender, "Maintenance list: " + String.join(", ", state.players().values().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList()));
            } else if (args.length == 2 && action.equals("remove")) {
                UUID id = state.players().entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(args[1]) || entry.getKey().toString().equalsIgnoreCase(args[1]))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                if (id == null) reply(sender, "That account is not listed.");
                else { change(state.remove(id)); reply(sender, "Removed " + args[1] + " from maintenance access."); }
            } else if (args.length == 2 && action.equals("add")) add(sender, args[1]);
            else reply(sender, "Use /maintenance on|off|status|list|add <player>|remove <player>.");
        } catch (Exception error) {
            reply(sender, "Change refused: " + error.getMessage());
        }
        return true;
    }

    private void change(AccessState next) throws java.io.IOException { next.save(path); state = next; }

    private void add(CommandSender sender, String name) {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) { reply(sender, "Use a Java account name (1–16 letters, digits or underscore)."); return; }
        if (pending >= 4) { reply(sender, "Account lookups are busy. Please try again shortly."); return; }
        pending++;
        reply(sender, "Verifying account " + name + "…");
        // Paper resolves a real online UUID asynchronously; never synthesize offline UUIDs.
        getServer().createPlayerProfile(name).update().orTimeout(20, java.util.concurrent.TimeUnit.SECONDS).whenComplete((profile, error) -> {
            if (!isEnabled()) return;
            getServer().getScheduler().runTask(this, () -> {
                pending--;
                if (!permitted(sender) || state == null) return;
                if (error != null || profile == null || profile.getUniqueId() == null || profile.getName() == null
                        || !name.equalsIgnoreCase(profile.getName())) { reply(sender, "Could not verify that account. No access changed."); return; }
                try {
                    change(state.add(profile.getUniqueId(), profile.getName()));
                    reply(sender, "Added " + profile.getName() + ". Normal whitelist and bans still apply; no admin permissions granted.");
                } catch (Exception failure) { reply(sender, "Could not persist access; no access changed."); getLogger().log(java.util.logging.Level.SEVERE, "Maintenance save failed", failure); }
            });
        });
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equals("maintenance") || !permitted(sender) || state == null) return List.of();
        List<String> values = args.length == 1 ? List.of("on", "off", "status", "list", "add", "remove")
                : args.length == 2 && args[0].equalsIgnoreCase("remove") ? state.players().values().stream().sorted().toList() : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
