package net.mandomc.commands;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Small subcommand registry: feature handlers own behavior, permissions are checked at the boundary. */
public final class RootCommand {
    private record Route(String permission, BiConsumer<CommandSender, String[]> run,
            BiFunction<CommandSender, String[], List<String>> complete) { }
    private final Map<String, Route> routes = new LinkedHashMap<>();

    public RootCommand(JavaPlugin plugin, Consumer<CommandSender> status, Consumer<CommandSender> denied) {
        var command = Objects.requireNonNull(plugin.getCommand("mandomc"));
        command.setExecutor((sender, ignored, label, args) -> {
            if (args.length == 0) {
                if (sender.hasPermission("mandomc.admin.status")) status.accept(sender);
                else denied.accept(sender);
                return true;
            }
            var route = routes.get(args[0].toLowerCase(Locale.ROOT));
            if (route == null) return false;
            if (!sender.hasPermission(route.permission())) denied.accept(sender);
            else route.run().accept(sender, Arrays.copyOfRange(args, 1, args.length));
            return true;
        });
        command.setTabCompleter((sender, ignored, label, args) -> {
            if (args.length <= 1) {
                return routes.entrySet().stream().filter(e -> sender.hasPermission(e.getValue().permission()))
                        .map(Map.Entry::getKey).filter(s -> s.startsWith(args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT))).toList();
            }
            var route = routes.get(args[0].toLowerCase(Locale.ROOT));
            return route != null && sender.hasPermission(route.permission())
                    ? route.complete().apply(sender, Arrays.copyOfRange(args, 1, args.length)) : List.of();
        });
    }

    public void register(String name, String permission, BiConsumer<CommandSender, String[]> run,
            BiFunction<CommandSender, String[], List<String>> complete) {
        if (routes.putIfAbsent(name, new Route(permission, run, complete)) != null) {
            throw new IllegalArgumentException("Duplicate subcommand " + name);
        }
    }
}
