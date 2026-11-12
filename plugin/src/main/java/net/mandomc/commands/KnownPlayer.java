package net.mandomc.commands;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Exact online/cached names or explicit UUIDs; never performs a blocking profile lookup. */
public record KnownPlayer(UUID id, String name) {
    public static KnownPlayer find(CommandSender sender, String input) {
        var online = Bukkit.getPlayerExact(input);
        if (online != null) {
            if (sender instanceof Player viewer && !viewer.canSee(online)) throw new IllegalArgumentException("player-missing");
            return new KnownPlayer(online.getUniqueId(), online.getName());
        }
        try { var id = UUID.fromString(input); return new KnownPlayer(id, input); }
        catch (IllegalArgumentException ignored) { }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(input);
        if (cached == null) throw new IllegalArgumentException("player-missing");
        return new KnownPlayer(cached.getUniqueId(), cached.getName() == null ? input : cached.getName());
    }
}
