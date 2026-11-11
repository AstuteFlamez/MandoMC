package net.mandomc.gameplay.territory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import net.mandomc.gameplay.clans.ClanService;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Native territory lifecycle and transition presentation. No per-tick scans or region vendor. */
public final class TerritoryService implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final TerritoryPolicy policy;
    private final ConfigurationSection language;
    private final Map<UUID,TerritoryIdentity> shown = new HashMap<>();
    private final BukkitTask transitions;
    public TerritoryService(JavaPlugin plugin, ConfigurationSection config, ConfigurationSection language) {
        this.plugin=plugin; this.policy=new TerritoryPolicy(config);
        try(var reader=new java.io.InputStreamReader(java.util.Objects.requireNonNull(plugin.getResource("language/territory.yml")),java.nio.charset.StandardCharsets.UTF_8)) {
            var defaults=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
            for(String key:defaults.getKeys(true))if(defaults.isString(key)&&!language.isString(key))language.set(key,defaults.getString(key));
        }catch(java.io.IOException error){throw new IllegalStateException("Cannot load territory language",error);}
        this.language=language;
        plugin.getServer().getServicesManager().register(TerritoryService.class,this,plugin,ServicePriority.Normal);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        plugin.getServer().getPluginManager().registerEvents(new TerritoryProtection(this),plugin);
        plugin.getServer().getOnlinePlayers().forEach(this::announce);
        transitions = plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> plugin.getServer().getOnlinePlayers().forEach(this::announce), 10, 10);
    }
    public TerritoryPolicy policy() { return policy; }
    public TerritoryPolicy.Zone zone(Location location) {
        if(location==null || location.getWorld()==null) throw new IllegalArgumentException("Territory requires a world");
        return policy.zone(location.getWorld().getKey().toString(),location.getBlockX(),location.getBlockY(),location.getBlockZ());
    }
    public boolean combat(Location location) { return location!=null && location.getWorld()!=null && zone(location)!=TerritoryPolicy.Zone.SAFEZONE; }
    public boolean edits(Location location) { return location!=null && location.getWorld()!=null && zone(location)==TerritoryPolicy.Zone.WILDERNESS; }
    public boolean spawning(Location l) { return l!=null && l.getWorld()!=null && policy.spawning(l.getWorld().getKey().toString(),l.getBlockX(),l.getBlockY(),l.getBlockZ()); }
    public Component message(String key) { return MiniMessage.miniMessage().deserialize(language.getString(key,key)); }
    public TerritoryIdentity identity(Player player, Location location) {
        var clans = plugin.getServer().getServicesManager().load(ClanService.class);
        return TerritoryIdentity.read(zone(location), clans == null ? null : clans.owner(location),
                clans == null ? null : clans.member(player), clans == null ? null : clans.state(),
                System.currentTimeMillis());
    }
    public Component label(Player player) { return label(identity(player,player.getLocation())); }
    private Component label(TerritoryIdentity identity) {
        String key = identity.relation() == TerritoryIdentity.Relation.NONE
                ? "titles." + identity.zone().name().toLowerCase(java.util.Locale.ROOT)
                : "titles.claim-" + identity.relation().name().toLowerCase(java.util.Locale.ROOT);
        return MiniMessage.miniMessage().deserialize(language.getString(key,key),
                Placeholder.unparsed("clan",identity.name()));
    }
    public void announce(Player player) {
        var identity=identity(player,player.getLocation());
        if(identity.equals(shown.put(player.getUniqueId(),identity)))return;
        var title = label(identity);
        player.showTitle(Title.title(title,Component.empty(),
                Title.Times.times(java.time.Duration.ofMillis(200),java.time.Duration.ofMillis(1500),java.time.Duration.ofMillis(400))));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) {
        // Use the final destination for cross-world teleports and exact legacy Hoth bounds.
        if(event.getTo()==null || (event.getFrom().getWorld()==event.getTo().getWorld()
                && event.getFrom().getBlockX()==event.getTo().getBlockX() && event.getFrom().getBlockY()==event.getTo().getBlockY()
                && event.getFrom().getBlockZ()==event.getTo().getBlockZ())) return;
        if(identity(event.getPlayer(),event.getTo()).equals(shown.get(event.getPlayer().getUniqueId())))return;
        plugin.getServer().getScheduler().runTask(plugin,()->{if(event.getPlayer().isOnline())announce(event.getPlayer());});
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) { move(event); }
    @EventHandler public void join(PlayerJoinEvent event) { announce(event.getPlayer()); }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) { announce(event.getPlayer()); }
    @EventHandler public void respawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin,()->{if(event.getPlayer().isOnline())announce(event.getPlayer());});
    }
    @EventHandler public void quit(PlayerQuitEvent event) { shown.remove(event.getPlayer().getUniqueId()); }
    @Override public void close() {
        transitions.cancel(); HandlerList.unregisterAll(this); plugin.getServer().getServicesManager().unregister(TerritoryService.class,this);shown.clear();
    }
}
