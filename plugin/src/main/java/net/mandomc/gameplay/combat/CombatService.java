package net.mandomc.gameplay.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

/** Sole owner of combat tags, sidebar, travel denial and logout death. */
public final class CombatService implements Listener, AutoCloseable {
    private record Display(Player player, Scoreboard previous, Scoreboard board, Score timer) { }
    private final JavaPlugin plugin;
    private final CombatTags tags;
    private final ConfigurationSection language;
    private final Consumer<UUID> returnSaber;
    private final Map<UUID, Display> displays = new HashMap<>();
    private final Map<UUID, UUID> attackers = new HashMap<>();
    private final Map<UUID, List<ItemStack>> logoutDrops = new HashMap<>();
    private final BukkitTask ticker;
    private boolean closed;

    public CombatService(JavaPlugin plugin, ConfigurationSection config, ConfigurationSection language,
                         Consumer<UUID> returnSaber) {
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("combat.yml: expected schema-version 1");
        this.plugin = plugin;
        this.language = language;
        this.returnSaber = returnSaber;
        tags = new CombatTags(config.getInt("tag-seconds", -1), () -> System.nanoTime() / 1_000_000);
        for (String key : List.of("title", "timer", "tagged", "safe", "teleport-denied")) {
            if (language == null || !language.isString(key)) throw new IllegalArgumentException("messages.yml: missing combat." + key);
        }
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean tagged(Player player) { return tags.active(player.getUniqueId()); }
    public int remainingTenths(Player player) { return tags.tenths(player.getUniqueId()); }

    public void tag(Player player) {
        if (closed || player.isDead()) return;
        boolean fresh = !tagged(player);
        tags.tag(player.getUniqueId());
        Display display = displays.computeIfAbsent(player.getUniqueId(), ignored -> {
            var previous = player.getScoreboard();
            var board = plugin.getServer().getScoreboardManager().getNewScoreboard();
            var objective = board.registerNewObjective("mandomc_combat", Criteria.DUMMY, message("title"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
            var score = objective.getScore("timer");
            score.setScore(1);
            player.setScoreboard(board);
            return new Display(player, previous, board, score);
        });
        update(display);
        if (fresh) player.sendMessage(message("tagged"));
    }

    private Component message(String key) {
        return MiniMessage.miniMessage().deserialize(language.getString(key));
    }

    private void update(Display display) {
        preserveTeams(display);
        int tenths = remainingTenths(display.player());
        String seconds = (tenths / 10) + "." + (tenths % 10);
        display.timer().customName(MiniMessage.miniMessage().deserialize(language.getString("timer"),
                Placeholder.unparsed("seconds", seconds)));
    }

    private void preserveTeams(Display display) {
        // A sidebar must not erase scoreboard friendly-fire, collision or nameplate policy.
        var source = display.previous();
        var target = display.board();
        for (var team : java.util.Set.copyOf(target.getTeams())) {
            if (source.getTeam(team.getName()) == null) team.unregister();
        }
        for (var original : source.getTeams()) {
            var copy = target.getTeam(original.getName());
            if (copy == null) copy = target.registerNewTeam(original.getName());
            if (!copy.displayName().equals(original.displayName())) copy.displayName(original.displayName());
            if (!copy.prefix().equals(original.prefix())) copy.prefix(original.prefix());
            if (!copy.suffix().equals(original.suffix())) copy.suffix(original.suffix());
            if (copy.getColor() != original.getColor()) copy.setColor(original.getColor());
            if (copy.allowFriendlyFire() != original.allowFriendlyFire()) copy.setAllowFriendlyFire(original.allowFriendlyFire());
            if (copy.canSeeFriendlyInvisibles() != original.canSeeFriendlyInvisibles())
                copy.setCanSeeFriendlyInvisibles(original.canSeeFriendlyInvisibles());
            for (var option : org.bukkit.scoreboard.Team.Option.values()) {
                if (copy.getOption(option) != original.getOption(option)) copy.setOption(option, original.getOption(option));
            }
            for (String entry : java.util.Set.copyOf(copy.getEntries())) {
                if (!original.hasEntry(entry)) copy.removeEntry(entry);
            }
            for (String entry : original.getEntries()) if (!copy.hasEntry(entry)) copy.addEntry(entry);
        }
    }

    private void tick() {
        for (Display display : List.copyOf(displays.values())) {
            if (!tagged(display.player())) {
                clear(display.player());
                if (display.player().isOnline()) display.player().sendMessage(message("safe"));
            } else {
                update(display);
            }
        }
    }

    public void clear(Player player) {
        tags.clear(player.getUniqueId());
        attackers.remove(player.getUniqueId());
        Display display = displays.remove(player.getUniqueId());
        if (display != null && player.getScoreboard() == display.board()) player.setScoreboard(display.previous());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        if (event.getFinalDamage() <= 0 || !(event.getEntity() instanceof LivingEntity victim)
                || victim instanceof ArmorStand) return;
        Entity source = event.getDamageSource().getCausingEntity();
        if (source == null) source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
        if (!(source instanceof LivingEntity attacker) || attacker instanceof ArmorStand
                || attacker.getUniqueId().equals(victim.getUniqueId())) return;
        if (victim instanceof Player player) {
            tag(player);
            // An intervening mob hit must not credit an old PvP opponent for a later logout.
            attackers.remove(player.getUniqueId());
            if (attacker instanceof Player opponent) attackers.put(player.getUniqueId(), opponent.getUniqueId());
        }
        if (attacker instanceof Player player) {
            tag(player);
            attackers.remove(player.getUniqueId());
            if (victim instanceof Player opponent) attackers.put(player.getUniqueId(), opponent.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        if (tagged(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(message("teleport-denied"));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void quit(PlayerQuitEvent event) {
        var player = event.getPlayer();
        if (!closed && !plugin.getServer().isStopping() && tagged(player) && !player.isDead()) {
            returnSaber.accept(player.getUniqueId());
            // Closing returns the cursor stack before snapshotting inventory, armor and offhand.
            player.closeInventory();
            var drops = new ArrayList<ItemStack>();
            for (var item : player.getInventory().getContents()) {
                if (item != null && !item.isEmpty()) drops.add(item.clone());
            }
            logoutDrops.put(player.getUniqueId(), drops);
            UUID killer = attackers.get(player.getUniqueId());
            if (killer != null) player.setKiller(plugin.getServer().getPlayer(killer));
            try {
                // Let Paper perform a single real death, with its ordinary drops and persisted dead state.
                player.setHealth(0);
            } finally {
                logoutDrops.remove(player.getUniqueId());
            }
        }
        clear(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(PlayerDeathEvent event) {
        var drops = logoutDrops.get(event.getEntity().getUniqueId());
        if (drops != null) {
            event.setKeepInventory(false);
            event.setKeepLevel(false);
            event.getItemsToKeep().clear();
            event.getDrops().clear();
            event.getDrops().addAll(drops);
        }
        clear(event.getEntity());
    }

    @Override public void close() {
        closed = true;
        ticker.cancel();
        for (Display display : List.copyOf(displays.values())) clear(display.player());
        tags.clear();
        attackers.clear();
        logoutDrops.clear();
    }
}
