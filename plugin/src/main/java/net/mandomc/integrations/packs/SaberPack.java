package net.mandomc.integrations.packs;

import java.util.HexFormat;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Stack the author's unchanged pack from its official host after the server pack loads. */
public final class SaberPack implements Listener {
    private final JavaPlugin plugin;
    private final Set<UUID> requested = new HashSet<>();
    private final UUID id;
    private final UUID mainId;
    private final String url;
    private final byte[] hash;
    public SaberPack(JavaPlugin plugin, ConfigurationSection c) {
        this.plugin=plugin;
        mainId=UUID.fromString(c.getString("main-pack-id"));
        id=UUID.fromString(c.getString("id")); url=c.getString("url"); hash=HexFormat.of().parseHex(c.getString("sha1"));
        if(hash.length!=20 || url==null || !url.startsWith("https://cdn.modrinth.com/")) throw new IllegalArgumentException("Invalid official saber pack pin");
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    @EventHandler public void joined(PlayerJoinEvent event) {
        // The server pack can complete in configuration, before play-phase status events.
        plugin.getServer().getScheduler().runTask(plugin, () -> request(event.getPlayer()));
    }
    @EventHandler public void quit(PlayerQuitEvent event) { requested.remove(event.getPlayer().getUniqueId()); }
    public void request(Player player) {
        if (!player.isOnline() || !requested.add(player.getUniqueId())) return;
        try {
            player.addResourcePack(id,url,hash,"Vader's saber models by JustTimm — thank you!",true);
            plugin.getLogger().info("Requested JustTimm saber pack for " + player.getName());
        } catch (RuntimeException error) {
            requested.remove(player.getUniqueId());
            throw error;
        }
    }
    @EventHandler public void loaded(PlayerResourcePackStatusEvent event) {
        if (event.getID().equals(id)) {
            plugin.getLogger().info("JustTimm saber pack for " + event.getPlayer().getName() + ": " + event.getStatus());
        } else if (event.getID().equals(mainId)
                && event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            request(event.getPlayer());
        }
    }
}
