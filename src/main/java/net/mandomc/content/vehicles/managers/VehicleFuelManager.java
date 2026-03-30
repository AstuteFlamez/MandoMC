package net.mandomc.content.vehicles.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.ticxo.modelengine.api.model.ActiveModel;

import net.mandomc.content.vehicles.Vehicle;
import net.mandomc.content.vehicles.VehicleData;
import net.mandomc.core.LangManager;
import net.mandomc.mechanics.fuel.FuelManager;

public class VehicleFuelManager {

    private static final Map<UUID, Long> lastFuelTick = new HashMap<>();

    public static void handleFuel(Player player, Vehicle vehicle, ActiveModel model) {

        UUID uuid = player.getUniqueId();
        long tick = Bukkit.getCurrentTick();

        long last = lastFuelTick.getOrDefault(uuid, 0L);

        // Only burn fuel once per second (20 ticks)
        if (tick - last < 20) return;

        lastFuelTick.put(uuid, tick);

        VehicleData data = vehicle.getVehicleData();

        int fuel = FuelManager.getCurrentFuel(data.getItem());

        fuel--;

        FuelManager.updateFuel(data.getItem(), fuel);

        if (fuel <= 0) {

            player.sendActionBar(LangManager.get("vehicles.out-of-fuel"));

            model.getMountManager().ifPresent(m -> m.dismountDriver());

            VehicleManager.explodeVehicle(player);

            lastFuelTick.remove(uuid);
        }
    }
}