package net.mandomc.gameplay.hud;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Main-thread actionbar ownership. Existing notices retain priority over background reload progress. */
public final class ActionBars {
    private record Owner(int until, boolean reload) { }
    private static final Map<UUID, Owner> OWNERS = new HashMap<>();
    private ActionBars() { }

    public static void send(Player player, Component message) {
        OWNERS.put(player.getUniqueId(), new Owner(Bukkit.getCurrentTick() + 30, false));
        player.sendActionBar(message);
    }

    public static boolean reload(Player player, Component message) {
        var owner = OWNERS.get(player.getUniqueId());
        if (owner != null && !owner.reload() && owner.until() > Bukkit.getCurrentTick()) return false;
        OWNERS.put(player.getUniqueId(), new Owner(0, true));
        player.sendActionBar(message);
        return true;
    }

    public static void clearReload(Player player) {
        var owner = OWNERS.get(player.getUniqueId());
        if (owner != null && owner.reload()) {
            OWNERS.remove(player.getUniqueId());
            player.sendActionBar(Component.empty());
        }
    }

    public static void forget(Player player) { OWNERS.remove(player.getUniqueId()); }
    public static void close() { OWNERS.clear(); }
}
