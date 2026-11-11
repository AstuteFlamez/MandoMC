package net.mandomc.gameplay.hud;

import java.time.Instant;
import java.util.*;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.*;
import net.mandomc.gameplay.combat.CombatService;
import net.mandomc.gameplay.territory.TerritoryService;
import net.mandomc.gameplay.credits.*;
import net.mandomc.gameplay.ranks.*;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

/** Sole sidebar/tab owner. Combat supplies a deadline; ranks and credits retain their own state. */
public final class PlayerHud implements Listener, AutoCloseable {
    private static final String TEAM_PREFIX = "mmc_rank_";
    private static final class View {
        final Player player;
        final Scoreboard previous;
        final Scoreboard board;
        final Objective objective;
        final Component oldName, oldHeader, oldFooter;
        final int oldOrder;
        Component name, header, footer;
        int order;
        boolean hidden;
        int ping, kills, deaths, visible;
        View(Player player, Scoreboard board, Objective objective) {
            this.player = player; previous = player.getScoreboard(); this.board = board; this.objective = objective;
            oldName = player.playerListName(); oldHeader = player.playerListHeader(); oldFooter = player.playerListFooter();
            oldOrder = player.getPlayerListOrder();
        }
    }
    private final Map<UUID, View> views = new HashMap<>();
    private final HudSettings settings;
    private final CombatService combat;
    private final RankService ranks;
    private final CreditService credits;
    private final BukkitTask ticker;
    private final long start = System.nanoTime();
    private long ticks;
    public PlayerHud(JavaPlugin plugin, ConfigurationSection config, CombatService combat, RankService ranks, CreditService credits) {
        settings = HudSettings.read(config); this.combat = combat; this.ranks = ranks; this.credits = credits;
        var binding = Objects.requireNonNull(plugin.getCommand("scoreboard"));
        binding.setExecutor((sender, command, label, args) -> {
            if (!command.testPermissionSilent(sender)) return true;
            String key = "players-only";
            if (sender instanceof Player player && views.containsKey(player.getUniqueId())) {
                var view = views.get(player.getUniqueId()); view.hidden = !view.hidden;
                view.objective.setDisplaySlot(view.hidden ? null : DisplaySlot.SIDEBAR);
                key = view.hidden ? "hidden" : "shown";
            }
            sender.sendMessage(MiniMessage.miniMessage().deserialize(config.getString("messages." + key)));
            return true;
        });
        binding.setTabCompleter((sender, command, label, args) -> List.of());
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (settings.enabled()) Bukkit.getOnlinePlayers().forEach(this::attach);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }
    public void attach(Player player) {
        if (!settings.enabled() || views.containsKey(player.getUniqueId())) return;
        var board = Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
        var objective = board.registerNewObjective("mandomc_hud", Criteria.DUMMY, Component.empty());
        objective.numberFormat(NumberFormat.blank()); objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        var view = new View(player, board, objective); views.put(player.getUniqueId(), view);
        // Copy protection/collision teams before assigning the new board, avoiding a policy gap on join.
        mirrorTeams(view, List.copyOf(Bukkit.getOnlinePlayers()));
        player.setScoreboard(board);
        updateStats(view); render(view);
    }
    @EventHandler(priority = EventPriority.MONITOR) public void join(PlayerJoinEvent event) { attach(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) { detach(event.getPlayer()); }
    private void tick() {
        ticks++;
        if (ticks % 10 == 1) {
            var online = new ArrayList<>(Bukkit.getOnlinePlayers());
            online.sort(Comparator.comparingInt((Player player) -> ranks.rank(player).order()).thenComparing(Player::getName));
            // The client puts larger list-order values first.
            int order = online.size();
            for (Player player : online) {
                var view = views.get(player.getUniqueId());
                if (view == null) continue;
                Component name = ranks.rank(player).playerName(player.getName());
                if (!name.equals(view.name)) { player.playerListName(name); view.name = name; }
                if (view.order != order) { player.setPlayerListOrder(order); view.order = order; }
                order--;

            }
            for (var view : views.values()) { updateStats(view); mirrorTeams(view, online); }
        }
        for (var view : views.values()) render(view);
    }
    private void updateStats(View view) {
        view.ping = view.player.getPing();
        view.kills = view.player.getStatistic(Statistic.PLAYER_KILLS);
        view.deaths = view.player.getStatistic(Statistic.DEATHS);
        view.visible = (int) Bukkit.getOnlinePlayers().stream().filter(view.player::canSee).count();
    }
    private void render(View view) {
        long millis = (System.nanoTime() - start) / 1_000_000;
        var logo = settings.animations().get("logo").at(millis);
        int tenths = combat.remainingTenths(view.player);
        var timer = MiniMessage.miniMessage().deserialize(tenths > 0 ? settings.combat() : settings.kills(),
                Placeholder.unparsed("seconds", HudSettings.seconds(tenths)), Placeholder.unparsed("kills", String.valueOf(view.kills)),
                Placeholder.unparsed("deaths", String.valueOf(view.deaths)));
        var placeholders = TagResolver.resolver(
                Placeholder.component("logo", logo), Placeholder.component("bar", settings.animations().get("bar").at(millis)),
                Placeholder.component("serverinfo", settings.animations().get("serverinfo").at(millis)),
                Placeholder.unparsed("online", String.valueOf(view.visible)), Placeholder.unparsed("player", view.player.getName()),
                Placeholder.unparsed("date", settings.date().format(Instant.now())), Placeholder.unparsed("ping", String.valueOf(view.ping)),
                Placeholder.component("rank", ranks.rank(view.player).badge(false)), Placeholder.component("combat-or-kills", timer),
                Placeholder.unparsed("deaths", String.valueOf(view.deaths)),
                Placeholder.component("territory", territory(view.player)),
                Placeholder.unparsed("balance", CreditLedger.format(credits.ledger().balance(view.player.getUniqueId()))));
        if (!view.objective.displayName().equals(logo)) view.objective.displayName(logo);
        var lines = tenths > 0 ? settings.combatLines() : settings.lines();
        if (!view.hidden) for (int i = 0; i < lines.size(); i++) {
            var line = MiniMessage.miniMessage().deserialize(lines.get(i), placeholders);
            var score = view.objective.getScore("mmc_line_" + i);
            if (!score.isScoreSet()) score.setScore(settings.lines().size() - i);
            if (!line.equals(score.customName())) score.customName(line);
        }
        var header = MiniMessage.miniMessage().deserialize(settings.header(), placeholders);
        var footer = MiniMessage.miniMessage().deserialize(settings.footer(), placeholders);
        if (!header.equals(view.header) || !footer.equals(view.footer)) {
            view.player.sendPlayerListHeaderAndFooter(header, footer); view.header = header; view.footer = footer;
        }
    }
    private Component territory(Player player) {
        // Services retain authority; resolving at render also handles same-chunk claim/disband changes.
        var zones = Bukkit.getServicesManager().load(TerritoryService.class);
        return zones == null ? Component.text("Wilderness",net.kyori.adventure.text.format.NamedTextColor.GRAY)
                : zones.label(player);
    }

    private void mirrorTeams(View view, List<? extends Player> online) {
        var source = view.previous; var target = view.board;
        for (var team : Set.copyOf(target.getTeams())) {
            if (!team.getName().startsWith(TEAM_PREFIX) && source.getTeam(team.getName()) == null) team.unregister();
        }
        for (var original : source.getTeams()) {
            var copy = target.getTeam(original.getName());
            if (copy == null) copy = target.registerNewTeam(original.getName());
            if (!copy.displayName().equals(original.displayName())) copy.displayName(original.displayName());
            if (!copy.prefix().equals(original.prefix())) copy.prefix(original.prefix());
            if (!copy.suffix().equals(original.suffix())) copy.suffix(original.suffix());
            if (copy.getColor() != original.getColor()) copy.setColor(original.getColor());
            if (copy.allowFriendlyFire() != original.allowFriendlyFire()) copy.setAllowFriendlyFire(original.allowFriendlyFire());
            if (copy.canSeeFriendlyInvisibles() != original.canSeeFriendlyInvisibles()) copy.setCanSeeFriendlyInvisibles(original.canSeeFriendlyInvisibles());
            for (var option : Team.Option.values()) if (copy.getOption(option) != original.getOption(option)) copy.setOption(option, original.getOption(option));
            for (String entry : Set.copyOf(copy.getEntries())) if (!original.hasEntry(entry)) copy.removeEntry(entry);
            for (String entry : original.getEntries()) if (!copy.hasEntry(entry)) copy.addEntry(entry);
        }
        // Existing gameplay teams keep their names and policies. Only otherwise unteamed names get badges.
        var eligible = new HashSet<String>();
        for (Player player : online) {
            if (source.getEntryTeam(player.getName()) != null) continue;
            eligible.add(player.getName());
            var rank = ranks.rank(player); String id = TEAM_PREFIX + rank.order();
            var team = target.getTeam(id);
            if (team == null) {
                team = target.registerNewTeam(id); team.setAllowFriendlyFire(true); team.setCanSeeFriendlyInvisibles(false);
                team.prefix(rank.badge(false).append(Component.text(" ")));
                team.color(net.kyori.adventure.text.format.NamedTextColor.nearestTo(rank.color()));
            }
            if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
        }
        for (var team : target.getTeams()) if (team.getName().startsWith(TEAM_PREFIX) && source.getTeam(team.getName()) == null)
            for (String entry : Set.copyOf(team.getEntries())) if (!eligible.contains(entry)) team.removeEntry(entry);
    }
    public void detach(Player player) {
        var view = views.remove(player.getUniqueId()); if (view == null) return;
        if (player.getScoreboard() == view.board) player.setScoreboard(view.previous);
        if (Objects.equals(player.playerListName(), view.name)) player.playerListName(view.oldName);
        if (player.getPlayerListOrder() == view.order) player.setPlayerListOrder(view.oldOrder);
        if (Objects.equals(player.playerListHeader(), view.header) && Objects.equals(player.playerListFooter(), view.footer))
            player.sendPlayerListHeaderAndFooter(view.oldHeader == null ? Component.empty() : view.oldHeader,
                    view.oldFooter == null ? Component.empty() : view.oldFooter);
        view.objective.unregister();
    }
    @Override public void close() {
        ticker.cancel(); HandlerList.unregisterAll(this);
        for (var view : List.copyOf(views.values())) detach(view.player);
    }
}
