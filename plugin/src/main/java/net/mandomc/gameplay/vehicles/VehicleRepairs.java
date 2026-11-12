package net.mandomc.gameplay.vehicles;

import java.util.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Repairs the authoritative deployed entity, spending current held-tool durability per pulse. */
public final class VehicleRepairs implements Listener, AutoCloseable {
    private record Repair(Player player, UUID vehicle, int slot, ItemStack tool) {}
    private final VehicleService vehicles;
    private final ItemCatalog items;
    private final ConfigurationSection language;
    private final double amount, reach;
    private final int durabilityCost;
    private final Map<UUID, Repair> repairs = new HashMap<>();
    private final BukkitTask task;

    public VehicleRepairs(JavaPlugin plugin, VehicleService vehicles, ItemCatalog items,
            ConfigurationSection config, ConfigurationSection language) {
        this.vehicles = vehicles; this.items = items; this.language = language;
        amount = config.getDouble("health-per-pulse"); reach = config.getDouble("reach");
        int interval = config.getInt("interval-ticks");
        durabilityCost = config.getInt("durability-per-pulse", 25);
        if (config.getInt("schema-version") != 1 || !Double.isFinite(amount) || amount <= 0 || amount > 20
                || !Double.isFinite(reach) || reach < 1 || reach > 6 || interval < 1 || interval > 100
                || durabilityCost < 1 || durabilityCost > 1000)
            throw new IllegalArgumentException("vehicles/repairs.yml: invalid repair parameters");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
        vehicles.repairs(this);
    }

    public boolean isWrench(ItemStack item) {
        return Set.of("durasteel_wrench", "wrench").contains(Objects.toString(items.identify(item), ""));
    }

    public boolean start(Player player, UUID vehicle) {
        var tool = player.getInventory().getItemInMainHand();
        if (!player.isSneaking() || !isWrench(tool) || !vehicles.repairable(player, vehicle, reach)) {
            message(player, "repair-denied", vehicle); return false;
        }
        if (!(tool.getItemMeta() instanceof Damageable damage) || damage.isUnbreakable()
                || durability(tool, damage) <= damage.getDamage()) return false;
        repairs.put(player.getUniqueId(), new Repair(player, vehicle, player.getInventory().getHeldItemSlot(), tool.clone()));
        return true;
    }

    public int activeRepairs() { return repairs.size(); }

    private void tick() {
        for (Repair repair : List.copyOf(repairs.values())) pulse(repair);
    }

    private void pulse(Repair repair) {
        var player = repair.player();
        var tool = player.getInventory().getItemInMainHand();
        if (!player.isOnline() || !player.isSneaking()
                || player.getInventory().getHeldItemSlot() != repair.slot()
                || !isWrench(tool) || !sameTool(repair.tool(), tool)
                || !vehicles.repairable(player, repair.vehicle(), reach)) {
            repairs.remove(player.getUniqueId()); return;
        }
        var meta = (Damageable) tool.getItemMeta();
        int limit = durability(tool, meta);
        if (meta.isUnbreakable() || limit <= meta.getDamage()) { repairs.remove(player.getUniqueId()); return; }
        int charged = Math.min(durabilityCost, limit - meta.getDamage());
        // A worn wrench cannot grant a full pulse with only a sliver of durability left.
        double restored = vehicles.repair(repair.vehicle(), amount * charged / durabilityCost);
        if (restored <= 0) {
            repairs.remove(player.getUniqueId()); message(player, "repair-complete", repair.vehicle()); return;
        }
        // Tool damage is charged only after an actual health increase, including the last partial pulse.
        meta.setDamage(meta.getDamage() + charged);
        if (meta.getDamage() >= limit) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1, 1);
            repairs.remove(player.getUniqueId());
        } else {
            tool.setItemMeta(meta);
            player.getInventory().setItemInMainHand(tool);
            repairs.put(player.getUniqueId(), new Repair(player, repair.vehicle(), repair.slot(), tool.clone()));
        }
        var entity = vehicles.entity(repair.vehicle());
        if (entity != null) {
            entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation(), 6, .3, .3, .3, .1);
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ANVIL_USE, .3f, 1.2f);
        }
        message(player, "repair-progress", repair.vehicle());
    }

    private static int durability(ItemStack tool, Damageable meta) {
        return meta.hasMaxDamage() ? meta.getMaxDamage() : tool.getType().getMaxDurability();
    }

    private static boolean sameTool(ItemStack expected, ItemStack actual) {
        return expected.equals(actual);
    }

    private void message(Player player, String key, UUID id) {
        var entity = vehicles.entity(id);
        player.sendActionBar(MiniMessage.miniMessage().deserialize(language.getString(key),
                Placeholder.unparsed("health", entity == null ? "0" : Integer.toString((int) Math.ceil(entity.getHealth()))),
                Placeholder.unparsed("max", Integer.toString((int) vehicles.maxHealth(id)))));
    }

    @EventHandler public void quit(PlayerQuitEvent event) { repairs.remove(event.getPlayer().getUniqueId()); }

    @Override public void close() {
        task.cancel(); repairs.clear(); vehicles.repairs(null); HandlerList.unregisterAll(this);
    }
}
