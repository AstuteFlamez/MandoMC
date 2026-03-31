package net.mandomc.gameplay.vehicle.weapon;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;
import net.mandomc.gameplay.vehicle.util.AmmoUtil;
import net.mandomc.core.LangManager;
import net.mandomc.MandoMC;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.bone.ModelBone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class SpeederBike implements WeaponSystem {

    private final Map<UUID, Long> cooldownMap = new HashMap<>();

    @Override
    public void shoot(Vehicle vehicle) {

        Player player = Bukkit.getPlayer(vehicle.getOwnerUUID());
        if (player == null) return;

        String vehicleId = VehicleRegistry.getVehicleId(vehicle.getItemId());
        if (vehicleId == null) return;

        FileConfiguration config = VehicleConfig.get(vehicleId);
        if (config == null) return;

        ConfigurationSection weaponSection =
                config.getConfigurationSection("vehicle.systems.weapon");

        if (weaponSection == null) return;

        debug("SpeederBike shoot requested by " + player.getName() + " vehicle=" + vehicleId);
        fireWeapon(vehicle, player, weaponSection);
    }

    private void fireWeapon(Vehicle vehicle, Player player, ConfigurationSection config) {

        String gun = config.getString("gun");
        String ammo = config.getString("ammo");
        int ammoPerShot = config.getInt("ammo_per_shot", 1);
        long cooldown = config.getLong("cooldown", 0);
        String sound = config.getString("sound");

        debug("SpeederBike fire config gun=" + gun + " ammo=" + ammo + " ammo_per_shot=" + ammoPerShot);

        if (!isValidWeaponTitle(gun)) {
            debug("SpeederBike fire blocked: invalid WeaponMechanics gun '" + gun + "'");
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        long cooldownUntil = cooldownMap.getOrDefault(uuid, 0L);

        if (now < cooldownUntil) {
            long msLeft = cooldownUntil - now;
            double secondsLeft = Math.ceil(msLeft / 100.0) / 10.0;
            debug("SpeederBike fire blocked by cooldown: " + secondsLeft + "s left");
            player.sendMessage(LangManager.get("vehicles.weapon.recharging", "%seconds%", String.valueOf(secondsLeft)));
            return;
        }

        if (!AmmoUtil.hasAmmo(player, ammo, ammoPerShot)) {
            debug("SpeederBike fire blocked: missing ammo " + ammo);
            player.sendMessage(LangManager.get("vehicles.weapon.out-of-ammo", "%ammo%", (ammo != null ? ammo.replace("_", " ") : "ammo")));
            return;
        }

        ActiveModel activeModel = vehicle.getVehicleData().getActiveModel();
        List<Location> spawnLocations = resolveSpawnLocations(config, activeModel, player);

        AmmoUtil.consumeAmmo(player, ammo, ammoPerShot);

        for (Location spawn : spawnLocations) {
            for (int i = 0; i < ammoPerShot; i++) {
                Location shootLoc = aimedFromPlayerYawPitch(spawn, player);
                debug("SpeederBike shoot invoke gun=" + gun
                        + " loc=" + formatLocation(shootLoc)
                        + " dir=" + formatVector(shootLoc.getDirection()));
                try {
                    WeaponMechanicsAPI.shoot(player, gun, shootLoc);
                } catch (IllegalArgumentException ex) {
                    debug("SpeederBike shoot failed for gun='" + gun + "' reason=" + ex.getMessage());
                }
            }
        }

        if (sound != null) {
            Location soundLoc = spawnLocations.get(0);
            soundLoc.getWorld().playSound(
                soundLoc,
                    sound,
                    SoundCategory.MASTER,
                    1f,
                    1f
            );
        }

        if (cooldown > 0) {
            cooldownMap.put(uuid, now + cooldown);
        }
    }

    private List<Location> resolveSpawnLocations(ConfigurationSection config, ActiveModel activeModel, Player player) {
        List<String> bones = config.getStringList("spawn_bones");
        List<Location> result = new ArrayList<>();

        for (String boneName : bones) {
            if (boneName == null || boneName.isBlank()) continue;
            Location resolved = resolveBoneLocation(activeModel, boneName);
            if (resolved != null) {
                result.add(resolved);
                debug("SpeederBike resolved spawn bone '" + boneName + "' at " + formatLocation(resolved));
            } else {
                debug("SpeederBike missing spawn bone '" + boneName + "'");
                debug("SpeederBike available model bones: " + summarizeBoneKeys(activeModel.getBones()));
            }
        }

        if (result.isEmpty()) {
            debug("SpeederBike using fallback spawn location (player eye)");
            result.add(player.getEyeLocation());
        }

        return result;
    }

    private static void debug(String message) {
        MandoMC.getInstance().getLogger().info("[VehicleDebug] " + message);
    }

    private static String formatLocation(Location location) {
        return String.format("%s(%.2f, %.2f, %.2f)",
                location.getWorld() != null ? location.getWorld().getName() : "null",
                location.getX(),
                location.getY(),
                location.getZ());
    }

    private static String formatVector(Vector vector) {
        return String.format("(%.3f, %.3f, %.3f)", vector.getX(), vector.getY(), vector.getZ());
    }

    private static Location aimedFromPlayerYawPitch(Location origin, Player player) {
        Location eye = player.getEyeLocation();
        return new Location(
                origin.getWorld(),
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                eye.getYaw(),
                eye.getPitch()
        );
    }

    private static boolean isValidWeaponTitle(String gun) {
        if (gun == null || gun.isBlank()) return false;
        ItemStack generated = WeaponMechanicsAPI.generateWeapon(gun);
        if (generated == null) return false;
        String resolved = WeaponMechanicsAPI.getWeaponTitle(generated);
        return gun.equalsIgnoreCase(resolved);
    }

    private static Location resolveBoneLocation(ActiveModel activeModel, String configuredName) {
        var direct = activeModel.getBone(configuredName).map(b -> b.getLocation().clone()).orElse(null);
        if (direct != null) return direct;

        Map<String, ModelBone> bones = activeModel.getBones();
        String normalizedWanted = normalize(configuredName);

        for (Map.Entry<String, ModelBone> entry : bones.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(configuredName)) {
                debug("SpeederBike matched spawn bone by case-insensitive key: '" + entry.getKey() + "'");
                return entry.getValue().getLocation().clone();
            }
        }

        for (Map.Entry<String, ModelBone> entry : bones.entrySet()) {
            String normalizedKey = normalize(entry.getKey());
            if (normalizedKey.endsWith(normalizedWanted)) {
                debug("SpeederBike matched spawn bone by suffix key: '" + entry.getKey() + "'");
                return entry.getValue().getLocation().clone();
            }
        }

        return null;
    }

    private static String summarizeBoneKeys(Map<String, ModelBone> bones) {
        if (bones.isEmpty()) return "<none>";
        return bones.keySet().stream().sorted().limit(20).collect(Collectors.joining(", "))
                + (bones.size() > 20 ? " ... (" + bones.size() + " total)" : "");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
    }
}