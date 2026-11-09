package net.mandomc.gameplay.admin;

import java.util.List;
import net.mandomc.commands.CommandMessages;
import net.mandomc.gameplay.combat.CombatService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdminService implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final CombatService combat;
    private final CommandMessages messages;
    private final NamespacedKey godKey;
    private final NamespacedKey vanishKey;
    private final InventoryInspection inventories;
    public AdminService(JavaPlugin plugin, CombatService combat, CommandMessages messages) {
        this.plugin = plugin; this.combat = combat; this.messages = messages;
        godKey = new NamespacedKey(plugin, "god_v1"); vanishKey = new NamespacedKey(plugin, "vanish_v1");
        inventories = new InventoryInspection(plugin);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    public static void heal(Player target) {
        if (target.isDead()) throw new IllegalArgumentException("player-dead");
        target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
        feed(target); target.setFireTicks(0); target.setFreezeTicks(0);
        for (var effect : List.copyOf(target.getActivePotionEffects())) target.removePotionEffect(effect.getType());
    }
    public static void feed(Player target) {
        if (target.isDead()) throw new IllegalArgumentException("player-dead");
        target.setFoodLevel(20); target.setSaturation(20); target.setExhaustion(0);
    }
    public boolean teleport(Player player, Location target) {
        if (!player.isOnline() || player.isDead() || player.isInsideVehicle() || !player.getPassengers().isEmpty()) {
            messages.send(player, "request-unavailable"); return false;
        }
        if (combat.tagged(player)) { messages.send(player, "combat"); return false; }
        if (target.getWorld() == null || !target.getWorld().getWorldBorder().isInside(target)
                || target.getY() < target.getWorld().getMinHeight() || target.getY() >= target.getWorld().getMaxHeight()) {
            messages.send(player, "unavailable"); return false;
        }
        return player.teleport(target, PlayerTeleportEvent.TeleportCause.COMMAND);
    }
    public boolean god(Player player) { return player.getPersistentDataContainer().has(godKey); }
    public void god(Player player, boolean enabled) {
        if (enabled) player.getPersistentDataContainer().set(godKey, PersistentDataType.BYTE, (byte) 1);
        else player.getPersistentDataContainer().remove(godKey);
        player.setInvulnerable(enabled);
    }
    public boolean vanished(Player player) { return player.getPersistentDataContainer().has(vanishKey); }
    public void vanish(Player player, boolean enabled) {
        if (enabled) {
            player.getPersistentDataContainer().set(vanishKey, PersistentDataType.BYTE, (byte) 1);
            player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
        } else {
            player.getPersistentDataContainer().remove(vanishKey); player.removeMetadata("vanished", plugin);
        }
        for (var viewer : Bukkit.getOnlinePlayers()) visibility(viewer, player);
    }
    private void visibility(Player viewer, Player target) {
        if (!viewer.equals(target) && vanished(target) && !viewer.hasPermission("mandomc.admin.vanish.see")) {
            viewer.hidePlayer(plugin, target);
        } else viewer.showPlayer(plugin, target);
    }
    public void inspect(Player viewer, Player target, boolean ender) { inventories.open(viewer, target, ender, messages); }
    @EventHandler public void join(PlayerJoinEvent event) {
        var player = event.getPlayer();
        if (god(player)) {
            if (player.hasPermission("mandomc.admin.god")) player.setInvulnerable(true); else god(player, false);
        }
        if (vanished(player)) {
            if (player.hasPermission("mandomc.admin.vanish")) { vanish(player, true); event.joinMessage(null); }
            else vanish(player, false);
        }
        for (var target : Bukkit.getOnlinePlayers()) visibility(player, target);
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        if (vanished(event.getPlayer())) event.quitMessage(null);
    }
    @Override public void close() {
        inventories.close();
        for (var player : Bukkit.getOnlinePlayers()) {
            player.removeMetadata("vanished", plugin);
            for (var viewer : Bukkit.getOnlinePlayers()) viewer.showPlayer(plugin, player);
        }
        HandlerList.unregisterAll(this);
    }
}
