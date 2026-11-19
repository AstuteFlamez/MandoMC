package net.mandomc.gameplay.messaging;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Tracks the latest successfully delivered one-to-one message for each online player. */
final class DirectMessages implements TabExecutor, Listener, AutoCloseable {
    private final Map<UUID, UUID> correspondents = new HashMap<>();
    private final Component noRecentTarget;

    DirectMessages(JavaPlugin plugin) {
        var language = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "language/messages.yml"));
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource("language/messages.yml")),
                StandardCharsets.UTF_8)) {
            language.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot load private-message language", error);
        }
        noRecentTarget = MiniMessage.miniMessage().deserialize(
                Objects.requireNonNull(language.getString("private-messages.no-recent-target")));
        for (String label : List.of("msg", "reply")) {
            var command = Objects.requireNonNull(plugin.getCommand(label));
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean reply = command.getName().equals("reply");
        if (!reply && (!(sender instanceof Player) || (args.length >= 1 && args[0].startsWith("@")))) {
            return Bukkit.dispatchCommand(sender, "minecraft:msg " + String.join(" ", args));
        }
        if (!(sender instanceof Player player) || args.length < (reply ? 1 : 2)) return false;
        UUID previous = reply ? correspondents.get(player.getUniqueId()) : null;
        if (reply && previous == null) {
            player.sendMessage(noRecentTarget);
            return true;
        }
        var recipient = reply ? Bukkit.getPlayer(previous) : Bukkit.getPlayerExact(args[0]);
        if (recipient == null || !player.canSee(recipient)) {
            player.sendMessage(Component.translatable("argument.entity.notfound.player", NamedTextColor.RED));
            return true;
        }

        var message = Component.text(String.join(" ", List.of(args).subList(reply ? 0 : 1, args.length)));
        player.sendMessage(display("outgoing", recipient, message));
        recipient.sendMessage(display("incoming", player, message));
        if (!recipient.equals(player)) {
            correspondents.put(player.getUniqueId(), recipient.getUniqueId());
            correspondents.put(recipient.getUniqueId(), player.getUniqueId());
        }
        return true;
    }

    private static Component display(String direction, Player other, Component message) {
        return Component.translatable("commands.message.display." + direction, NamedTextColor.GRAY,
                Component.text(other.getName()), message).decorate(TextDecoration.ITALIC);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equals("msg") || args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .filter(target -> !(sender instanceof Player player) || player.canSee(target))
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .toList();
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        UUID player = event.getPlayer().getUniqueId();
        correspondents.remove(player);
        correspondents.values().removeIf(player::equals);
    }

    @Override
    public void close() {
        HandlerList.unregisterAll(this);
        correspondents.clear();
    }
}
