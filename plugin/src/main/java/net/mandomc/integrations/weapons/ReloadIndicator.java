package net.mandomc.integrations.weapons;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.hud.ActionBars;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Observes the pinned WeaponMechanics lifecycle; never writes weapon, ammunition, cooldown or task state. */
public final class ReloadIndicator implements Listener, AutoCloseable {
    private record Key(UUID player, EquipmentSlot hand) { }
    private record Reload(Player player, String title, Object hand, Object event, int startTick) { }
    private final JavaPlugin plugin;
    private final Map<Key, Reload> reloads = new HashMap<>();
    private final Method shooter, hand, title, handData, duration, reloading, elapsed, weaponTitle;
    private final BukkitTask task;
    private final Object boba;
    private final Method bobaOwner;
    private final String message;
    private boolean failed;

    @SuppressWarnings("unchecked")
    public ReloadIndicator(JavaPlugin plugin) throws ReflectiveOperationException {
        this.plugin = plugin;
        var vendor = Bukkit.getPluginManager().getPlugin("WeaponMechanics");
        if (vendor == null || !vendor.isEnabled()) throw new IllegalStateException("WeaponMechanics is unavailable");
        var loader = vendor.getClass().getClassLoader();
        var base = Class.forName("me.deecaad.weaponmechanics.weapon.weaponevents.WeaponEvent", true, loader);
        var start = (Class<? extends Event>) Class.forName(base.getPackageName() + ".WeaponReloadEvent", true, loader);
        var data = Class.forName("me.deecaad.weaponmechanics.wrappers.HandData", true, loader);
        shooter = base.getMethod("getShooter"); hand = base.getMethod("getHand");
        title = base.getMethod("getWeaponTitle"); handData = base.getMethod("getHandData", boolean.class);
        duration = start.getMethod("getReloadCompleteTime");
        reloading = data.getMethod("isReloading"); elapsed = data.getMethod("getReloadElapsedTime");
        weaponTitle = Class.forName("me.deecaad.weaponmechanics.WeaponMechanicsAPI", true, loader)
                .getMethod("getWeaponTitle", ItemStack.class);
        if (!new java.io.File(plugin.getDataFolder(), "language/reload-indicator.yml").isFile())
            plugin.saveResource("language/reload-indicator.yml", false);
        var language = YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), "language/reload-indicator.yml"));
        message = java.util.Objects.requireNonNull(language.getString("progress"), "reload-indicator.yml: progress");
        // The post-Boba release owns powered thrust and the full critical-fuel notice interval.
        var featureType = Class.forName("net.mandomc.gameplay.boba.BobaEquipmentFeature", true, plugin.getClass().getClassLoader());
        var feature = java.util.Objects.requireNonNull(Bukkit.getServicesManager().load((Class) featureType), "Boba UI owner is unavailable");
        boba = featureType.getMethod("equipment").invoke(feature);
        bobaOwner = boba.getClass().getMethod("ownsActionBar", Player.class);
        Bukkit.getPluginManager().registerEvent(start, this, EventPriority.MONITOR,
                (listener, event) -> observe(event, true), plugin, true);
        for (String end : new String[]{"WeaponReloadCompleteEvent", "WeaponReloadCancelEvent"}) {
            var type = (Class<? extends Event>) Class.forName(base.getPackageName() + "." + end, true, loader);
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.MONITOR,
                    (listener, event) -> observe(event, false), plugin);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void observe(Event event, boolean start) {
        if (failed) return;
        try {
            if (!(shooter.invoke(event) instanceof Player player)) return;
            var key = new Key(player.getUniqueId(), (EquipmentSlot) hand.invoke(event));
            String name = (String) title.invoke(event);
            if (start) {
                // Capture the final event object, read only after all listeners and vendor acceptance have run.
                reloads.put(key, new Reload(player, name, handData.invoke(event, false), event, Bukkit.getCurrentTick()));
            } else {
                var current = reloads.get(key);
                if (current != null && current.title().equals(name)) remove(key, current);
            }
        } catch (ReflectiveOperationException error) { fail(error); }
    }

    private void tick() {
        if (failed) return;
        try {
            for (var entry : java.util.List.copyOf(reloads.entrySet())) {
                var key = entry.getKey(); var state = entry.getValue(); var player = state.player();
                var stack = key.hand() == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand()
                        : player.getInventory().getItemInOffHand();
                if (!player.isOnline() || player.isDead() || state.hand() == null
                        || ((Cancellable) state.event()).isCancelled()
                        || !Boolean.TRUE.equals(reloading.invoke(state.hand()))
                        || !state.title().equals(weaponTitle.invoke(null, stack))) {
                    remove(key, state); continue;
                }
                if (Bukkit.getCurrentTick() % 2 != 0) continue;
                int total = (int) duration.invoke(state.event());
                if (total <= 0) { remove(key, state); continue; }
                // Vendor elapsed time uses wall milliseconds, while its tasks execute in server ticks.
                // Cap presentation by observed ticks so lag cannot run the countdown ahead of the real task.
                int passed = Math.min((int) elapsed.invoke(state.hand()), Math.max(0, Bukkit.getCurrentTick() - state.startTick()));
                int remaining = Math.max(0, total - passed);
                int filled = Math.min(10, Math.max(0, (int) Math.floor(10.0 * (total - remaining) / total)));
                var bar = MiniMessage.miniMessage().deserialize("<yellow>" + "|".repeat(filled)
                        + "<dark_gray>" + "|".repeat(10 - filled));
                // One writer when dual wielding: prefer the main hand if both are actually reloading.
                if (key.hand() == EquipmentSlot.OFF_HAND && reloads.containsKey(new Key(key.player(), EquipmentSlot.HAND))) continue;
                if (Boolean.TRUE.equals(bobaOwner.invoke(boba, player))) continue;
                ActionBars.reload(player, MiniMessage.miniMessage().deserialize(message,
                        Placeholder.component("bar", bar), Placeholder.unparsed("time", String.format(Locale.ROOT, "%.1f", remaining / 20.0))));
            }
        } catch (ReflectiveOperationException | RuntimeException error) { fail(error); }
    }

    private void remove(Key key, Reload state) {
        reloads.remove(key, state);
        if (reloads.keySet().stream().noneMatch(k -> k.player().equals(key.player()))) ActionBars.clearReload(state.player());
    }
    private void clear(Player player) {
        reloads.keySet().removeIf(key -> key.player().equals(player.getUniqueId()));
        ActionBars.clearReload(player); ActionBars.forget(player);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { clear(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) { clear(event.getEntity()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { clear(event.getPlayer()); }
    private void fail(Exception error) {
        failed = true;
        for (var state : reloads.values()) ActionBars.clearReload(state.player());
        reloads.clear();
        plugin.getLogger().log(Level.SEVERE, "Reload indicator disabled; WeaponMechanics gameplay is unchanged", error);
    }
    @Override public void close() {
        task.cancel(); HandlerList.unregisterAll(this);
        for (var state : reloads.values()) ActionBars.clearReload(state.player());
        reloads.clear(); ActionBars.close();
    }
    public int activeReloads() { return reloads.size(); }
    public static void enable(JavaPlugin plugin) {
        try {
            var indicator = new ReloadIndicator(plugin);
            Bukkit.getServicesManager().register(ReloadIndicator.class, indicator, plugin, org.bukkit.plugin.ServicePriority.Normal);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot bind reload indicator", error); }
    }
    public static void disable(JavaPlugin plugin) {
        var indicator = Bukkit.getServicesManager().load(ReloadIndicator.class);
        if (indicator != null) { indicator.close(); Bukkit.getServicesManager().unregister(indicator); }
    }
}
