package net.mandomc.integrations.mobs;

import net.mandomc.integrations.models.HerdModels;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerLeashEntityEvent;
import org.bukkit.event.entity.EntityUnleashEvent;
import org.bukkit.event.player.PlayerUnleashEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Routes model clicks into ordinary native leash state, events, physics and fence knots. */
public final class HerdLeads implements Listener {
    private final HerdAnimals animals;
    public HerdLeads(JavaPlugin plugin, HerdAnimals animals) {
        this.animals = animals; Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    public boolean interact(Player player, Horse horse, EquipmentSlot hand) {
        if (!animals.leadAccess(player, horse) || HerdModels.ridden(horse)) return false;
        if (horse.isLeashed()) {
            if (horse.getLeashHolder() instanceof Player holder && !holder.getUniqueId().equals(player.getUniqueId())) return false;
            var event = new PlayerUnleashEntityEvent(horse, player, hand == null ? EquipmentSlot.HAND : hand,
                    player.getGameMode() != GameMode.CREATIVE);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || !horse.setLeashHolder(null)) return false;
            if (event.isDropLeash()) horse.getWorld().dropItemNaturally(horse.getLocation(), new ItemStack(Material.LEAD));
            return true;
        }
        if (hand == null || player.getInventory().getItem(hand).getType() != Material.LEAD) return false;
        var event = new PlayerLeashEntityEvent(horse, player, player, hand);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || !horse.setLeashHolder(player)) return false;
        if (player.getGameMode() != GameMode.CREATIVE) player.getInventory().getItem(hand).subtract(1);
        return true;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void leash(PlayerLeashEntityEvent event) {
        if (!animals.herd(event.getEntity())) return;
        if (!(event.getEntity() instanceof LivingEntity entity) || !animals.leadAccess(event.getPlayer(), entity)
                || HerdModels.ridden(entity) || !animals.leadLocation(event.getLeashHolder().getLocation())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void unleash(PlayerUnleashEntityEvent event) {
        if (animals.herd(event.getEntity()) && (!(event.getEntity() instanceof LivingEntity entity)
                || !animals.leadAccess(event.getPlayer(), entity))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void deathLead(EntityUnleashEvent event) {
        if (event.getEntity() instanceof LivingEntity entity
                && (animals.herd(entity) || net.mandomc.gameplay.mobs.HerdAge.herd(entity.getPersistentDataContainer().get(
                    net.mandomc.gameplay.mobs.HerdAge.KIND, org.bukkit.persistence.PersistentDataType.STRING)))
                && net.mandomc.gameplay.mobs.HerdAge.baby(entity) && (entity.isDead() || entity.getHealth() <= 0)) event.setDropLeash(false);
    }
}
