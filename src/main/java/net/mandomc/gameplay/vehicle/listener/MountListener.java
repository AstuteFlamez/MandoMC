package net.mandomc.gameplay.vehicle.listener;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action;
import com.ticxo.modelengine.api.model.ActiveModel;

import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.gameplay.vehicle.manager.VehicleManager;
import net.mandomc.core.LangManager;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.gameplay.fuel.FuelManager;
import net.mandomc.server.items.ItemUtils;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class MountListener implements Listener {

    @EventHandler
    public void onInteract(BaseEntityInteractEvent event) {

        if (event.getAction() != Action.INTERACT) return;

        Player player = event.getPlayer();

        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand != null && (ItemUtils.isItem(hand, "rhydonium_canister")
                || ItemUtils.isItem(hand, "wrench"))) {
            return;
        }

        ActiveModel clicked = event.getModel();
        UUID uuid = player.getUniqueId();

        for (Vehicle vehicle : VehicleModule.getActiveVehicles().values()) {

            VehicleData data = vehicle.getVehicleData();

            if (clicked != data.getActiveModel()) continue;
            if (!uuid.equals(vehicle.getOwnerUUID())) continue;

            int fuel = FuelManager.getCurrentFuel(data.getItem());

            if (fuel <= 0) {
                player.sendMessage(LangManager.get("vehicles.no-fuel"));
                return;
            }

            VehicleManager.mountVehicle(player, vehicle);
        }
    }
}