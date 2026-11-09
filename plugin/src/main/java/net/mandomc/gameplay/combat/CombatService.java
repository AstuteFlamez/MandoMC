package net.mandomc.gameplay.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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

/** Sole owner of combat deadlines, travel denial and logout death. PlayerHud renders remaining time. */
public final class CombatService implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final CombatTags tags;
    private final ConfigurationSection language;
    private final Consumer<UUID> returnSaber;
    private final Map<UUID, Player> taggedPlayers = new HashMap<>();
    private final Map<UUID, UUID> attackers = new HashMap<>();
    private final Map<UUID, List<ItemStack>> logoutDrops = new HashMap<>();
    private final BukkitTask ticker;
    private final java.util.Set<UUID> movementCorrections = new java.util.HashSet<>();
    private boolean closed;

    public CombatService(JavaPlugin plugin, ConfigurationSection config, ConfigurationSection language,
                         Consumer<UUID> returnSaber) {
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("combat.yml: expected schema-version 1");
        this.plugin = plugin;
        this.language = language;
        this.returnSaber = returnSaber;
        tags = new CombatTags(config.getInt("tag-seconds", -1), () -> System.nanoTime() / 1_000_000);
        for (String key : List.of("tagged", "safe", "teleport-denied")) {
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
        taggedPlayers.put(player.getUniqueId(), player);
        if (fresh) player.sendMessage(message("tagged"));
    }

    private Component message(String key) {
        return MiniMessage.miniMessage().deserialize(language.getString(key));
    }

    private void tick() {
        for (Player player : List.copyOf(taggedPlayers.values())) {
            if (!tagged(player)) {
                clear(player);
                if (player.isOnline()) player.sendMessage(message("safe"));
            }
        }
    }

    public void clear(Player player) {
        tags.clear(player.getUniqueId());
        attackers.remove(player.getUniqueId());
        taggedPlayers.remove(player.getUniqueId());
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
        if (tagged(event.getPlayer()) && !movementCorrections.contains(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(message("teleport-denied"));
        }
    }

    /** Narrow internal escape hatch for returning a vehicle rider to a verified outside position. */
    public boolean correctMovement(Player player, org.bukkit.Location outside) {
        movementCorrections.add(player.getUniqueId());
        try { return player.teleport(outside, PlayerTeleportEvent.TeleportCause.PLUGIN); }
        finally { movementCorrections.remove(player.getUniqueId()); }
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
        for (Player player : List.copyOf(taggedPlayers.values())) clear(player);
        org.bukkit.event.HandlerList.unregisterAll(this);
        tags.clear();
        attackers.clear();
        logoutDrops.clear();
    }
}
