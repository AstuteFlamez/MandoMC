package net.mandomc.gameplay.events;

import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerInteractEvent;

/** Active event ownership permits claiming its caches, never opening arbitrary protected containers. */
public interface EventCaches {
    boolean mayClaim(PlayerInteractEvent event);

    static boolean allowed(PlayerInteractEvent event) {
        var owner = Bukkit.getServicesManager().load(EventCaches.class);
        return owner != null && owner.mayClaim(event);
    }
}
