package net.mandomc.integrations.packs;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns the ordered official base/model packs and the overriding MandoMC server pack. */
public final class SaberPack implements Listener {
    private final JavaPlugin plugin;
    private final Set<UUID> requested = new HashSet<>();
    private final UUID mainId;
    private final ResourcePackInfo saber;
    private final ResourcePackInfo faithful;

    public SaberPack(JavaPlugin plugin, ConfigurationSection config) {
        this.plugin = plugin;
        mainId = UUID.fromString(config.getString("main-pack-id"));
        saber = pin(config);
        faithful = pin(Objects.requireNonNull(config.getConfigurationSection("base-pack"),
                "combat/sabers.yml: upstream-pack.base-pack is required"));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static ResourcePackInfo pin(ConfigurationSection config) {
        UUID id = UUID.fromString(config.getString("id"));
        URI uri = URI.create(Objects.requireNonNull(config.getString("url")));
        String hash = config.getString("sha1", "");
        if (!"https".equals(uri.getScheme()) || !"cdn.modrinth.com".equals(uri.getHost())
                || !hash.matches("[a-f0-9]{40}")) {
            throw new IllegalArgumentException("Invalid official Modrinth resource-pack pin");
        }
        return ResourcePackInfo.resourcePackInfo(id, uri, hash);
    }

    /** Client insertion order is bottom to top: Faithful, JustTimm, then MandoMC. */
    public static ResourcePackRequest stack(ResourcePackInfo main, ResourcePackInfo saber, ResourcePackInfo base) {
        if (Set.of(main.id(), saber.id(), base.id()).size() != 3) {
            throw new IllegalArgumentException("Resource pack IDs must be distinct");
        }
        return ResourcePackRequest.resourcePackRequest().packs(List.of(base, saber, main))
                .replace(true).required(true)
                .prompt(Component.text("MandoMC, JustTimm's Lightsabers and Faithful 32x"))
                .build();
    }

    @EventHandler public void joined(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> request(event.getPlayer()));
    }

    @EventHandler public void quit(PlayerQuitEvent event) {
        requested.remove(event.getPlayer().getUniqueId());
    }

    public void request(Player player) {
        if (!player.isOnline() || !requested.add(player.getUniqueId())) return;
        try {
            var serverPack = Objects.requireNonNull(plugin.getServer().getServerResourcePack(),
                    "The required MandoMC server pack is not configured");
            if (!mainId.equals(serverPack.getId())) throw new IllegalStateException("MandoMC pack UUID differs from its pin");
            var main = ResourcePackInfo.resourcePackInfo(mainId, URI.create(serverPack.getUrl()), serverPack.getHash());
            player.sendResourcePacks(stack(main, saber, faithful));
            plugin.getLogger().info("Requested packs (top to bottom): MandoMC > JustTimm > Faithful 32x for " + player.getName());
        } catch (RuntimeException error) {
            requested.remove(player.getUniqueId());
            throw error;
        }
    }

    @EventHandler public void loaded(PlayerResourcePackStatusEvent event) {
        UUID id = event.getID();
        String name = id.equals(mainId) ? "MandoMC" : id.equals(saber.id()) ? "JustTimm"
                : id.equals(faithful.id()) ? "Faithful 32x" : null;
        if (name == null) return;
        plugin.getLogger().info(name + " pack for " + event.getPlayer().getName() + ": " + event.getStatus());
        if (id.equals(mainId) && event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            request(event.getPlayer());
        }
    }
}
