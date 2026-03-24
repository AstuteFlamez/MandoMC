package net.mandomc.system.vehicles.managers;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ModeledEntity;

import net.mandomc.modules.system.VehicleModule;
import net.mandomc.system.items.ItemUtils;
import net.mandomc.system.vehicles.Vehicle;
import net.mandomc.system.vehicles.VehicleData;
import net.mandomc.system.vehicles.VehicleRegistry;
import net.mandomc.system.vehicles.config.VehiclesConfig;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class VehicleManager {

    public static HashMap<UUID, Integer> sound = new HashMap<>();

    /* =========================
       CONFIG ACCESS (NEW)
    ========================= */

    private static FileConfiguration getVehicleConfig(ItemStack item) {

        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) return null;

        String vehicleId = VehicleRegistry.getVehicleId(itemId);
        if (vehicleId == null) return null;

        return VehiclesConfig.get(vehicleId);
    }

    private static ConfigurationSection getStats(ItemStack item) {

        FileConfiguration config = getVehicleConfig(item);
        if (config == null) return null;

        return config.getConfigurationSection("vehicle.stats");
    }

    private static ConfigurationSection getSystems(ItemStack item) {

        FileConfiguration config = getVehicleConfig(item);
        if (config == null) return null;

        return config.getConfigurationSection("vehicle.systems");
    }

    /* =========================
       STATS
    ========================= */

    public static double getSpeed(ItemStack item) {

        ConfigurationSection stats = getStats(item);
        if (stats == null) return 40;

        return stats.getDouble("speed", 40);
    }

    public static double getScale(ItemStack item) {

        ConfigurationSection stats = getStats(item);
        if (stats == null) return 2;

        return stats.getDouble("scale", 2);
    }

    public static String getModelKey(ItemStack item) {

        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) return "";

        // 🔥 model_key stays in item config
        var section = net.mandomc.system.items.config.ItemsConfig.getItemSection(itemId);
        if (section == null) return "";

        return section.getString("model_key", "");
    }

    /* =========================
       SYSTEMS
    ========================= */

    public static String getMovementSound(ItemStack item) {

        ConfigurationSection systems = getSystems(item);
        if (systems == null) return null;

        return systems.getString("movement_sound");
    }

    public static int getMovementSoundLength(ItemStack item) {

        ConfigurationSection systems = getSystems(item);
        if (systems == null) return 0;

        return systems.getInt("movement_sound_time_in_ticks");
    }

    /* =========================
       VEHICLE LIFECYCLE
    ========================= */

    public static void pickupVehicle(Player player) {

        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);

        if (vehicle == null) return;

        VehicleData data = vehicle.getVehicleData();
        ItemStack item = data.getItem();

        destroyVehicle(player);

        item.setAmount(1);
        player.getInventory().addItem(item);

        player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §aVehicle picked up.");
    }

    public static void destroyVehicle(Player player) {

        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);

        if (vehicle == null) return;

        VehicleModule.getActiveVehicles().remove(vehicle.getOwnerUUID());

        VehicleData data = vehicle.getVehicleData();
        Entity entity = data.getEntity();

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(entity);

        if (modeledEntity != null) {
            modeledEntity.destroy();
            entity.remove();
        }
    }

    public static void explodeVehicle(Player player) {

        UUID uuid = player.getUniqueId();
        Vehicle vehicle = VehicleModule.getActiveVehicles().get(uuid);

        if (vehicle == null) return;

        VehicleModule.getActiveVehicles().remove(vehicle.getOwnerUUID());

        VehicleData data = vehicle.getVehicleData();
        Entity entity = data.getEntity();

        Location loc = entity.getLocation();
        World world = entity.getWorld();

        /* Sounds */
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.0f);
        world.playSound(loc, Sound.BLOCK_ANVIL_LAND, 1.2f, 0.6f);
        world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.5f);

        /* Particles */
        world.spawnParticle(Particle.EXPLOSION, loc, 1);

        world.spawnParticle(Particle.FLAME, loc, 60, 1.5, 1.5, 1.5, 0.05);
        world.spawnParticle(Particle.SMOKE, loc, 80, 2, 2, 2, 0.02);
        world.spawnParticle(Particle.CRIT, loc, 40, 1.5, 1.5, 1.5, 0.2);

        /* Knockback */
        for (Entity nearby : world.getNearbyEntities(loc, 4, 3, 4)) {

            if (nearby instanceof Player nearbyPlayer) {

                Vector knockback = nearbyPlayer.getLocation()
                        .toVector()
                        .subtract(loc.toVector())
                        .normalize()
                        .multiply(0.6)
                        .setY(0.4);

                nearbyPlayer.setVelocity(knockback);
            }
        }

        ModeledEntity modeledEntity = ModelEngineAPI.getModeledEntity(entity);

        if (modeledEntity != null) {
            modeledEntity.destroy();
            entity.remove();
        }
    }
}