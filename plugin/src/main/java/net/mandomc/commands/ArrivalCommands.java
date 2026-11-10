package net.mandomc.commands;

import java.util.Objects;
import java.util.function.Function;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import net.mandomc.gameplay.arrival.ArrivalListener;
import net.mandomc.gameplay.travel.TeleportService;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Feature registration lives here; handlers choose destinations, never implement their own timers. */
public final class ArrivalCommands {
    private ArrivalCommands() { }

    public static void register(JavaPlugin plugin, ArrivalListener tutorial, ArrivalDestinations destinations,
            TeleportService travel) {
        bind(plugin, "tutorial", tutorial::tutorial);
        bind(plugin, "spawn", sender -> teleport(sender, travel, destinations::spawn));
    }

    private static boolean teleport(CommandSender sender, TeleportService travel,
            java.util.function.Supplier<org.bukkit.Location> destination) {
        if (sender instanceof Player player) travel.request(player, destination);
        else sender.sendMessage(travel.message("players-only"));
        return true;
    }

    public static void bind(JavaPlugin plugin, String name, Function<CommandSender, Boolean> handler) {
        var command = Objects.requireNonNull(plugin.getCommand(name), "Missing plugin.yml command: " + name);
        command.setExecutor((sender, ignored, alias, arguments) -> {
            if (arguments.length != 0) return false;
            return handler.apply(sender);
        });
        command.setTabCompleter((sender, ignored, alias, arguments) -> java.util.List.of());
    }
}
