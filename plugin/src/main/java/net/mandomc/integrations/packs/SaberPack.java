package net.mandomc.integrations.packs;

import java.util.HexFormat;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Stack the author's unchanged pack from its official host after the server pack loads. */
public final class SaberPack implements Listener {
    private final UUID id;
    private final UUID mainId;
    private final String url;
    private final byte[] hash;
    public SaberPack(JavaPlugin plugin, ConfigurationSection c) {
        mainId=UUID.fromString(c.getString("main-pack-id"));
        id=UUID.fromString(c.getString("id")); url=c.getString("url"); hash=HexFormat.of().parseHex(c.getString("sha1"));
        if(hash.length!=20 || url==null || !url.startsWith("https://cdn.modrinth.com/")) throw new IllegalArgumentException("Invalid official saber pack pin");
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    @EventHandler public void loaded(PlayerResourcePackStatusEvent event) {
        if (!event.getID().equals(mainId)) return;
        if (event.getStatus()==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            event.getPlayer().addResourcePack(id,url,hash,"Vader's saber models by JustTimm — thank you!",true);
        }
    }
}
