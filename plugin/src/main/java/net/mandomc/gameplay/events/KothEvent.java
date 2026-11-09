package net.mandomc.gameplay.events;

import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.integrations.regions.SaberProtection;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Adapted legacy hill/bossbar/boundary; rewards intentionally disabled until selected by the user. */
public final class KothEvent implements ManagedEvent, Listener {
    private final JavaPlugin plugin;
    private final KothSettings settings;
    private final ConfigurationSection language;
    private final String name;
    private final CaptureState capture = new CaptureState();
    private BossBar bar;
    private World world;
    private boolean running;
    private int age;
    private UUID winner;
    public KothEvent(JavaPlugin plugin, ConfigurationSection config) {
        this.plugin=plugin;settings=KothSettings.read(config);name=config.getString("display-name", "King of the Hill");
        language=config.getConfigurationSection("messages");
        for (String key : new String[]{"start","capturing","winner","timeout","stopped","waiting","progress","contested"})
            if (language==null || !language.isString(key)) throw new IllegalArgumentException("events/koth.yml: missing messages."+key);
        if (config.getBoolean("rewards.enabled", false))
            throw new IllegalArgumentException("events/koth.yml: rewards are not implemented; keep rewards.enabled false");
    }
    public String id() { return "koth"; }
    public String displayName() { return name; }
    public boolean running() { return running; }
    public CaptureState capture() { return capture; }
    public UUID winner() { return winner; }
    public KothSettings settings() { return settings; }
    public void start() {
        if (running) throw new IllegalStateException("KOTH is already running");
        world=Bukkit.getWorld(NamespacedKey.fromString(settings.world()));
        if (world==null) throw new IllegalStateException("KOTH world is not loaded: "+settings.world());
        var center=new Location(world,settings.x(),settings.y(),settings.z());
        if (!world.getWorldBorder().isInside(center) || !new SaberProtection().allowed(center))
            throw new IllegalStateException("KOTH hill must be inside a loaded PvP-enabled region");
        running=true;age=0;winner=null;capture.reset();
        bar=Bukkit.createBossBar("",BarColor.BLUE,BarStyle.SOLID);bar.setProgress(0);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        Bukkit.broadcast(text("start", ""));
    }
    public void tick() { sample(world.getPlayers()); }
    /** Takes a main-thread player snapshot; event time advances only with server ticks. */
    public void sample(Collection<? extends Player> players) {
        if (!running) return;
        age+=10;
        if (age>=settings.maximumTicks()) {Bukkit.broadcast(text("timeout",""));stop(false);return;}
        Map<String,Player> present=new LinkedHashMap<>();
        var visible=new java.util.HashSet<Player>();
        for (Player player:players) {
            if (!eligible(player) || !player.getWorld().equals(world)) continue;
            visible.add(player);
            if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
            if (inside(player.getLocation())) present.put(player.getUniqueId().toString(),player);
        }
        for (Player viewer:List.copyOf(bar.getPlayers())) if (!visible.contains(viewer)) bar.removePlayer(viewer);
        updateCapture(present);
    }
    /** Applies an already eligible contestant snapshot; keeps group resolution outside the capture rule. */
    public void updateCapture(Map<String,Player> present) {
        if (!running) return;
        String previous=capture.holder();
        String completed=capture.update(present.keySet(),10,settings.captureTicks());
        String holder=capture.holder();
        if (holder!=null && !holder.equals(previous)) Bukkit.broadcast(text("capturing",present.get(holder).getName()));
        String key=holder!=null?"progress":present.size()>1?"contested":"waiting";
        String holderName=holder==null?"":present.get(holder).getName();
        bar.setTitle(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(text(key,holderName)));
        bar.setColor(holder!=null?BarColor.GREEN:present.size()>1?BarColor.RED:BarColor.BLUE);
        bar.setProgress(Math.min(1,(double)capture.ticks()/settings.captureTicks()));
        for (int i=0;i<20;i++) {
            double angle=Math.PI*2*i/20;
            world.spawnParticle(Particle.HAPPY_VILLAGER,settings.x()+Math.cos(angle)*settings.radius(),settings.y()+.15,
                    settings.z()+Math.sin(angle)*settings.radius(),1,0,0,0,0);
        }
        if (completed!=null) {
            winner=present.get(completed).getUniqueId();
            Bukkit.broadcast(text("winner",present.get(completed).getName()));
            world.spawnParticle(Particle.END_ROD,settings.x(),settings.y()+1,settings.z(),40,.5,1,.5,.05);
            // No chest/commands/economy mutation: reward selection is explicitly deferred.
            stop(false);
        }
    }
    public boolean eligible(Player p) {
        if (!p.isOnline() || p.isDead() || p.isInvulnerable()
                || (p.getGameMode()!=GameMode.SURVIVAL && p.getGameMode()!=GameMode.ADVENTURE)) return false;
        for (String key:new String[]{"vanished","essentials:vanished","PremiumVanish.IsVanished"})
            if (p.getMetadata(key).stream().anyMatch(value->value.asBoolean())) return false;
        return true;
    }
    private boolean inside(Location location) {
        return location.getWorld().equals(world) && settings.inside(location.getX(),location.getY(),location.getZ());
    }
    private Component text(String key,String player) {
        return MiniMessage.miniMessage().deserialize(language.getString(key),Placeholder.unparsed("player",player),
                Placeholder.unparsed("seconds",Integer.toString(Math.max(0,(settings.captureTicks()-capture.ticks()+19)/20))));
    }
    private void departed(Player p) {
        if (p.getUniqueId().toString().equals(capture.holder())) capture.reset();
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void move(PlayerMoveEvent e) {
        if (!inside(e.getTo())) departed(e.getPlayer());
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e) {
        // Even leaving and returning between samples must break continuous control.
        if (!inside(e.getTo())) departed(e.getPlayer());
    }
    @EventHandler public void quit(PlayerQuitEvent e) {departed(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e) {departed(e.getEntity());}
    public void stop(boolean announce) {
        boolean wasRunning=running;running=false;capture.reset();HandlerList.unregisterAll(this);
        if (bar!=null) {bar.removeAll();bar=null;}
        if (announce && wasRunning) Bukkit.broadcast(text("stopped",""));
    }
}
