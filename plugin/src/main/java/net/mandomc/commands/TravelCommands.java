package net.mandomc.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import net.mandomc.gameplay.travel.PersonalTravel;
import net.mandomc.gameplay.travel.TeleportRequests;
import net.mandomc.gameplay.travel.TeleportService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class TravelCommands implements CommandExecutor, TabCompleter {
    private final TeleportService travel;
    private final PersonalTravel personal;
    private final TeleportRequests requests;
    private final CommandMessages messages;
    public TravelCommands(JavaPlugin plugin, TeleportService travel, PersonalTravel personal,
                          TeleportRequests requests, CommandMessages messages) {
        this.travel = travel; this.personal = personal; this.requests = requests; this.messages = messages;
        for (String name : List.of("home", "homes", "sethome", "delhome", "back", "tpa", "tpahere", "tpaccept",
                "tpdeny", "tpacancel", "tpcancel")) {
            var command = Objects.requireNonNull(plugin.getCommand(name));
            command.setExecutor(this); command.setTabCompleter(this);
        }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { messages.send(sender, "players-only"); return true; }
        if (!command.testPermissionSilent(sender)) { messages.send(sender, "denied"); return true; }
        String name = command.getName();
        try {
            switch (name) {
                case "homes" -> {
                    if (args.length != 0) return false;
                    messages.send(player, "homes", String.join(", ", personal.names(player)));
                }
                case "home", "sethome", "delhome" -> {
                    if (args.length > 1) return false;
                    String home = PersonalTravel.homeName(args.length == 0 ? "home" : args[0]);
                    if (name.equals("home")) {
                        personal.home(player, home); // Report missing home before starting a countdown.
                        travel.request(player, () -> {
                            if (!player.hasPermission("mandomc.travel.home")) throw new IllegalStateException("Home permission changed");
                            try { return ArrivalDestinations.safeSpawn(personal.home(player, home).resolve()); }
                            catch (IllegalArgumentException error) { throw new IllegalStateException("Home changed during countdown", error); }
                        });
                    } else if (name.equals("sethome")) {
                        ArrivalDestinations.safeSpawn(player.getLocation());
                        personal.set(player, home); messages.send(player, "home-set", home);
                    } else { personal.delete(player, home); messages.send(player, "home-deleted", home); }
                }
                case "back" -> {
                    if (args.length != 0) return false;
                    var destination = personal.back(player);
                    travel.request(player, () -> {
                        if (!player.hasPermission("mandomc.travel.back")) throw new IllegalStateException("Back permission changed");
                        return ArrivalDestinations.safeSpawn(destination.resolve());
                    });
                }
                case "tpa", "tpahere" -> {
                    if (args.length != 1) return false;
                    var target = Bukkit.getPlayerExact(args[0]);
                    if (target == null || !player.canSee(target)) { messages.send(player, "player-missing"); return true; }
                    requests.send(player, target, name.equals("tpahere"));
                }
                case "tpaccept", "tpdeny" -> {
                    if (args.length > 1) return false;
                    String from = args.length == 0 ? null : args[0];
                    if (name.equals("tpaccept")) requests.accept(player, from); else requests.deny(player, from);
                }
                case "tpacancel" -> { if (args.length != 0) return false; requests.cancel(player); }
                case "tpcancel" -> { if (args.length != 0) return false; travel.cancelRequest(player); }
                default -> { return false; }
            }
        } catch (IllegalArgumentException error) {
            String key = error.getMessage();
            if (!List.of("home-name", "home-limit", "home-missing", "back-missing", "request-missing", "request-choose").contains(key)) {
                key = "unavailable";
            }
            messages.send(player, key);
        } catch (IllegalStateException error) { messages.send(player, "unavailable"); }
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || !command.testPermissionSilent(sender) || args.length != 1) return List.of();
        List<String> choices = switch (command.getName()) {
            case "home", "delhome", "sethome" -> personal.names(player);
            case "tpaccept", "tpdeny" -> requests.incoming(player).stream().map(r -> Bukkit.getPlayer(r.sender()))
                    .filter(Objects::nonNull).filter(player::canSee).map(Player::getName).toList();
            case "tpa", "tpahere" -> Bukkit.getOnlinePlayers().stream().filter(player::canSee)
                    .filter(p -> !p.equals(player)).map(Player::getName).toList();
            default -> List.of();
        };
        return choices.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
