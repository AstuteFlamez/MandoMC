package net.mandomc.gameplay.vehicle.weapon;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.bone.ModelBone;
import net.mandomc.MandoMC;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;
import net.mandomc.gameplay.vehicle.util.AmmoUtil;
import net.mandomc.core.LangManager;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class TieFighter implements WeaponSystem {

    private final Random rng = new Random();

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

        debug("TieFighter shoot requested by " + player.getName() + " vehicle=" + vehicleId);
        fireBurst(vehicle, player, weaponSection);
    }

    private void fireBurst(Vehicle vehicle, Player player, ConfigurationSection config) {

        String gun = config.getString("gun");
        String ammo = config.getString("ammo");
        int ammoPerShot = config.getInt("ammo_per_shot", 1);

        int burst = config.getInt("burst", 1);
        int burstInterval = config.getInt("burst_interval", 0);
        double spread = config.getDouble("spread", 0);

        String sound = config.getString("sound");

        debug("TieFighter fire config gun=" + gun + " ammo=" + ammo + " burst=" + burst + " ammo_per_shot=" + ammoPerShot);

        if (!isValidWeaponTitle(gun)) {
            debug("TieFighter fire blocked: invalid WeaponMechanics gun '" + gun + "'");
            return;
        }

        ActiveModel activeModel = vehicle.getVehicleData().getActiveModel();

        for (int i = 0; i < burst; i++) {

            Bukkit.getScheduler().runTaskLater(
                    MandoMC.getInstance(),
                    () -> {

                        if (!AmmoUtil.hasAmmo(player, ammo, ammoPerShot)) {
                            debug("TieFighter burst blocked: missing ammo " + ammo);
                            player.sendMessage(LangManager.get("vehicles.weapon.out-of-ammo", "%ammo%", ammo.replace("_", " ")));
                            return;
                        }

                        AmmoUtil.consumeAmmo(player, ammo, ammoPerShot);
                        List<Location> spawnLocations = resolveSpawnLocations(config, activeModel, player);

                        for (Location spawn : spawnLocations) {
                            Location shootLoc = aimedFromPlayerYawPitch(spawn, player, spread);
                            debug("TieFighter shoot invoke gun=" + gun
                                    + " loc=" + formatLocation(shootLoc)
                                    + " dir=" + formatVector(shootLoc.getDirection()));
                            try {
                                WeaponMechanicsAPI.shoot(player, gun, shootLoc);
                            } catch (IllegalArgumentException ex) {
                                debug("TieFighter shoot failed for gun='" + gun + "' reason=" + ex.getMessage());
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

                    },
                    i * burstInterval
            );
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
                debug("TieFighter resolved spawn bone '" + boneName + "' at " + formatLocation(resolved));
            } else {
                debug("TieFighter missing spawn bone '" + boneName + "'");
                debug("TieFighter available model bones: " + summarizeBoneKeys(activeModel.getBones()));
            }
        }

        if (result.isEmpty()) {
            debug("TieFighter using fallback spawn location (player eye)");
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
                debug("TieFighter matched spawn bone by case-insensitive key: '" + entry.getKey() + "'");
                return entry.getValue().getLocation().clone();
            }
        }

        for (Map.Entry<String, ModelBone> entry : bones.entrySet()) {
            String normalizedKey = normalize(entry.getKey());
            if (normalizedKey.endsWith(normalizedWanted)) {
                debug("TieFighter matched spawn bone by suffix key: '" + entry.getKey() + "'");
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

    private Location aimedFromPlayerYawPitch(Location origin, Player player, double spreadDegrees) {
        Location eye = player.getEyeLocation();
        float yaw = eye.getYaw();
        float pitch = eye.getPitch();

        if (spreadDegrees > 0) {
            yaw += (float) (rng.nextGaussian() * spreadDegrees);
            pitch += (float) (rng.nextGaussian() * spreadDegrees);
        }

        return new Location(
                origin.getWorld(),
                origin.getX(),
                origin.getY(),
                origin.getZ(),
                yaw,
                pitch
        );
    }
}