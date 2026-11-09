package net.mandomc.commands;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.mandomc.gameplay.admin.AdminService;
import net.mandomc.gameplay.worlds.WorldService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Operator commands use exact player names, explicit permissions and normal cancellable Paper actions. */
public final class AdminCommands implements CommandExecutor, TabCompleter {
    private final AdminService service;
    private final WorldService worlds;
    private final CommandMessages messages;
    public AdminCommands(JavaPlugin plugin, AdminService service, WorldService worlds, CommandMessages messages) {
        this.service = service; this.worlds = worlds; this.messages = messages;
        for (String name : List.of("flyspeed", "fly", "speed", "gm", "gm0", "gm1", "gm2", "gm3", "heal",
                "feed", "god", "vanish", "invsee", "enderchest", "time", "weather", "tp", "tppos", "tphere",
                "tpall", "mvtp")) {
            var command = Objects.requireNonNull(plugin.getCommand(name));
            command.setExecutor(this); command.setTabCompleter(this);
        }
    }

    private Player self(CommandSender sender) {
        if (!(sender instanceof Player player)) throw new IllegalArgumentException("players-only");
        return player;
    }
    private Player player(CommandSender sender, String name) {
        var target = Bukkit.getPlayerExact(name);
        if (target == null || sender instanceof Player viewer && !viewer.canSee(target)) {
            throw new IllegalArgumentException("player-missing");
        }
        return target;
    }
    private Player target(CommandSender sender, String name) {
        var target = name == null ? self(sender) : player(sender, name);
        if (!target.equals(sender) && !sender.hasPermission("mandomc.admin.others")) {
            throw new IllegalArgumentException("denied");
        }
        return target;
    }
    public static GameMode mode(String input) {
        return switch (input.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> throw new IllegalArgumentException("gamemode");
        };
    }
    public static float speed(String input, boolean fly) {
        double value;
        try { value = Double.parseDouble(input); } catch (NumberFormatException error) { throw new IllegalArgumentException("speed"); }
        if (!Double.isFinite(value) || value < 0 || value > 10) throw new IllegalArgumentException("speed");
        return (float) (fly ? value / 10 : value <= 1 ? value * .2 : .2 + (value - 1) * .8 / 9);
    }
    public static double coordinate(String input, double origin) {
        boolean relative = input.startsWith("~");
        String number = relative ? input.substring(1) : input;
        double value = relative && number.isEmpty() ? 0 : Double.parseDouble(number);
        value += relative ? origin : 0;
        if (!Double.isFinite(value) || Math.abs(value) > 29999984) throw new IllegalArgumentException("coordinates");
        return value;
    }
    private void teleported(CommandSender sender, Player target, Location location) {
        if (service.teleport(target, location)) messages.send(sender, "teleported", target.getName());
        else messages.send(sender, "unavailable");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.testPermissionSilent(sender)) { messages.send(sender, "denied"); return true; }
        String name = command.getName();
        try {
            switch (name) {
                case "heal", "feed" -> {
                    if (args.length > 1) return false;
                    var target = target(sender, args.length == 0 ? null : args[0]);
                    if (name.equals("heal")) AdminService.heal(target); else AdminService.feed(target);
                    messages.send(sender, name + "-done", target.getName());
                }
                case "gm", "gm0", "gm1", "gm2", "gm3" -> {
                    boolean shortcut = !name.equals("gm");
                    if (args.length < (shortcut ? 0 : 1) || args.length > (shortcut ? 1 : 2)) return false;
                    var mode = mode(shortcut ? name.substring(2) : args[0]);
                    var target = target(sender, args.length == (shortcut ? 1 : 2) ? args[args.length - 1] : null);
                    target.setGameMode(mode);
                    messages.send(sender, target.getGameMode() == mode ? "gamemode-done" : "unavailable", target.getName());
                }
                case "flyspeed", "speed" -> {
                    boolean explicitType = name.equals("speed") && args.length > 0 && List.of("fly", "walk").contains(args[0]);
                    int offset = explicitType ? 1 : 0;
                    if (args.length < offset + 1 || args.length > offset + 2) return false;
                    var target = target(sender, args.length == offset + 2 ? args[offset + 1] : null);
                    boolean fly = name.equals("flyspeed") || (explicitType ? args[0].equals("fly") : target.isFlying());
                    float speed = speed(args[offset], fly);
                    if (fly) target.setFlySpeed(speed); else target.setWalkSpeed(speed);
                    messages.send(sender, "speed-done", target.getName());
                }
                case "fly", "god", "vanish" -> {
                    boolean explicitState = args.length > 0 && List.of("on", "off").contains(args[0].toLowerCase(Locale.ROOT));
                    if (args.length > (explicitState ? 2 : 1)) return false;
                    var target = target(sender, args.length == (explicitState ? 2 : 1) ? args[args.length - 1] : null);
                    boolean previous = switch (name) {
                        case "fly" -> target.getAllowFlight(); case "god" -> service.god(target); default -> service.vanished(target);
                    };
                    boolean enabled = explicitState ? args[0].equalsIgnoreCase("on") : !previous;
                    switch (name) {
                        case "fly" -> {
                            if (!enabled && target.getGameMode() == GameMode.SPECTATOR) throw new IllegalArgumentException("spectator-flight");
                            if (!enabled) target.setFlying(false);
                            target.setAllowFlight(enabled);
                            if (enabled) target.setFlying(true);
                        }
                        case "god" -> service.god(target, enabled);
                        default -> service.vanish(target, enabled);
                    }
                    messages.send(sender, name + (enabled ? "-on" : "-off"), target.getName());
                }
                case "invsee", "enderchest" -> {
                    if (args.length > 1 || name.equals("invsee") && args.length != 1) return false;
                    var viewer = self(sender);
                    service.inspect(viewer, target(sender, args.length == 0 ? null : args[0]), name.equals("enderchest"));
                }
                case "mvtp" -> {
                    if (args.length < 1 || args.length > 2) return false;
                    var target = target(sender, args.length == 2 ? args[0] : null);
                    String world = args[args.length - 1];
                    teleported(sender, target, worlds.spawn(world.startsWith("w:") ? world.substring(2) : world));
                }
                case "tp", "tppos" -> {
                    if (name.equals("tp") && args.length == 1) {
                        teleported(sender, self(sender), player(sender, args[0]).getLocation());
                    } else if (name.equals("tp") && args.length == 2) {
                        teleported(sender, target(sender, args[0]), player(sender, args[1]).getLocation());
                    } else {
                        boolean other = args.length == 4 || args.length == 6;
                        int offset = other ? 1 : 0;
                        if (args.length != offset + 3 && args.length != offset + 5) return false;
                        var target = target(sender, other ? args[0] : null);
                        var origin = target.getLocation();
                        var location = new Location(origin.getWorld(), coordinate(args[offset], origin.getX()),
                                coordinate(args[offset + 1], origin.getY()), coordinate(args[offset + 2], origin.getZ()),
                                origin.getYaw(), origin.getPitch());
                        if (args.length == offset + 5) {
                            location.setYaw((float) coordinate(args[offset + 3], origin.getYaw()));
                            location.setPitch((float) coordinate(args[offset + 4], origin.getPitch()));
                            if (Math.abs(location.getPitch()) > 90) throw new IllegalArgumentException("coordinates");
                        }
                        teleported(sender, target, location);
                    }
                }
                case "tphere" -> {
                    if (args.length != 1) return false;
                    teleported(sender, target(sender, args[0]), self(sender).getLocation());
                }
                case "tpall" -> {
                    if (args.length > 1) return false;
                    if (!sender.hasPermission("mandomc.admin.others")) throw new IllegalArgumentException("denied");
                    var anchor = args.length == 0 ? self(sender) : player(sender, args[0]);
                    int count = 0;
                    for (var target : Bukkit.getOnlinePlayers()) {
                        if (!target.equals(anchor) && service.teleport(target, anchor.getLocation())) count++;
                    }
                    messages.send(sender, "tpall-done", Integer.toString(count));
                }
                case "time" -> {
                    boolean operation = args.length > 0 && List.of("set", "add", "query").contains(args[0]);
                    int offset = operation ? 1 : 0;
                    if (args.length < offset + 1 || args.length > offset + 2) return false;
                    World world = args.length == offset + 2 ? worlds.resolve(args[offset + 1]) : self(sender).getWorld();
                    if (operation && args[0].equals("query")) {
                        messages.send(sender, "time-value", Long.toString(world.getTime())); return true;
                    }
                    long ticks = switch (args[offset].toLowerCase(Locale.ROOT)) {
                        case "day" -> 1000; case "noon" -> 6000; case "night" -> 13000; case "midnight" -> 18000;
                        default -> Long.parseLong(args[offset]);
                    };
                    if (ticks < 0 || ticks > Integer.MAX_VALUE) throw new IllegalArgumentException("time");
                    world.setTime(operation && args[0].equals("add") ? world.getTime() + ticks : ticks);
                    messages.send(sender, "time-value", Long.toString(world.getTime()));
                }
                case "weather" -> {
                    if (args.length < 1 || args.length > 3) return false;
                    boolean duration = args.length > 1 && args[1].matches("[0-9]+");
                    if (args.length == 3 && !duration) return false;
                    int seconds = duration ? Integer.parseInt(args[1]) : 300;
                    if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException("weather");
                    World world = args.length > (duration ? 2 : 1) ? worlds.resolve(args[args.length - 1]) : self(sender).getWorld();
                    boolean storm = switch (args[0].toLowerCase(Locale.ROOT)) {
                        case "clear", "sun" -> false; case "rain", "storm", "thunder" -> true;
                        default -> throw new IllegalArgumentException("weather");
                    };
                    world.setStorm(storm); world.setThundering(storm && !args[0].equalsIgnoreCase("rain"));
                    world.setWeatherDuration(seconds * 20); world.setThunderDuration(seconds * 20);
                    if (!storm) world.setClearWeatherDuration(seconds * 20);
                    messages.send(sender, world.hasStorm() == storm ? "weather-done" : "weather-locked", world.getName());
                }
                default -> { return false; }
            }
        } catch (IllegalArgumentException error) {
            String key = error.getMessage();
            if (key == null || !List.of("players-only", "player-missing", "player-dead", "denied", "gamemode", "speed",
                    "coordinates", "spectator-flight", "time", "weather").contains(key)) key = "invalid";
            messages.send(sender, key);
        } catch (IllegalStateException error) { messages.send(sender, "unavailable"); }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.testPermissionSilent(sender) || args.length == 0) return List.of();
        var choices = new java.util.ArrayList<String>();
        if (command.getName().equals("mvtp") || command.getName().equals("time") || command.getName().equals("weather")) choices.addAll(worlds.names());
        if (args.length == 1) {
            switch (command.getName()) {
                case "gm" -> choices.addAll(List.of("0", "1", "2", "3", "survival", "creative", "adventure", "spectator"));
                case "speed" -> choices.addAll(List.of("fly", "walk", "0", "1", "2", "5", "10"));
                case "flyspeed" -> choices.addAll(List.of("0", "1", "2", "5", "10"));
                case "fly", "god", "vanish" -> choices.addAll(List.of("on", "off"));
                case "time" -> choices.addAll(List.of("day", "night", "noon", "midnight", "set", "add", "query"));
                case "weather" -> choices.addAll(List.of("clear", "rain", "thunder"));
                default -> { }
            }
        }
        if (args.length <= 2) Bukkit.getOnlinePlayers().stream()
                .filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p)).map(Player::getName).forEach(choices::add);
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().distinct().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
