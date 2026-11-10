package net.mandomc.gameplay.territory;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Full-map chat grid, deliberately independent of world/chunk loading and vendor claims. */
public final class ClanMapCommand implements CommandExecutor, TabCompleter {
    private final ChunkAtlas atlas;
    private final YamlConfiguration language;

    public ClanMapCommand(JavaPlugin plugin) {
        for (String resource : List.of("territory/maps.yml", "language/territory.yml")) {
            if (!new File(plugin.getDataFolder(), resource).exists()) plugin.saveResource(resource, false);
        }
        atlas = ChunkAtlas.read(YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "territory/maps.yml")));
        language = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "language/territory.yml"));
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource("language/territory.yml")),
                StandardCharsets.UTF_8)) {
            language.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Could not load language/territory.yml", error);
        }
        var command = Objects.requireNonNull(plugin.getCommand("c"));
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    private Component message(String key) {
        return MiniMessage.miniMessage().deserialize(Objects.requireNonNull(language.getString(key)));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (!sender.hasPermission("mandomc.clan.map")) { sender.sendMessage(message("denied")); return true; }
        if (arguments.length != 1 || !arguments[0].equalsIgnoreCase("map")) {
            sender.sendMessage(message("usage")); return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage(message("players-only")); return true; }
        if (!player.getWorld().getKey().toString().equals(atlas.world())) {
            sender.sendMessage(message("unmapped")); return true;
        }
        int playerX = Math.floorDiv(player.getLocation().getBlockX(), 16);
        int playerZ = Math.floorDiv(player.getLocation().getBlockZ(), 16);
        player.sendMessage(message("title").append(Component.text(" " + atlas.name(), NamedTextColor.WHITE)));
        player.sendMessage(message("orientation"));
        for (int z = atlas.minZ(); z <= atlas.maxZ(); z++) {
            Component row = Component.text(String.format(java.util.Locale.ROOT, "%3d ", z), NamedTextColor.GRAY);
            for (int x = atlas.minX(); x <= atlas.maxX(); x++) {
                char zone = atlas.zone(x, z);
                boolean here = x == playerX && z == playerZ;
                var color = here ? NamedTextColor.YELLOW : switch (zone) {
                    case 'S' -> NamedTextColor.GREEN;
                    case 'W' -> NamedTextColor.RED;
                    default -> NamedTextColor.DARK_GRAY;
                };
                String zoneKey = switch (zone) { case 'S' -> "safezone"; case 'W' -> "warzone"; default -> "wilderness"; };
                Component hover = message(zoneKey)
                        .append(Component.text(" | " + x + ", " + z + "\nX " + (x * 16) + ".." + (x * 16 + 15)
                                + " | Z " + (z * 16) + ".." + (z * 16 + 15), NamedTextColor.WHITE));
                row = row.append(Component.text(here ? "+" : "■", color).hoverEvent(HoverEvent.showText(hover)));
            }
            player.sendMessage(row);
        }
        player.sendMessage(message("legend"));
        player.sendMessage(message("position").append(Component.text(" " + playerX + ", " + playerZ
                + " | X " + atlas.minX() + ".." + atlas.maxX() + " | Z " + atlas.minZ() + ".." + atlas.maxZ(), NamedTextColor.WHITE)));
        if (!atlas.contains(playerX, playerZ)) player.sendMessage(message("outside"));
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] arguments) {
        return sender.hasPermission("mandomc.clan.map") && arguments.length == 1
                && "map".startsWith(arguments[0].toLowerCase(java.util.Locale.ROOT)) ? List.of("map") : List.of();
    }
}
