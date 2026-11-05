package net.mandomc.gameplay.vehicle.listener;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.BaseEntityInteractEvent.Action;
import com.ticxo.modelengine.api.model.ActiveModel;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.gameplay.vehicle.gui.VehicleInteractGUI;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.server.items.ItemUtils;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/**
 * Opens the vehicle interaction GUI when a player right-clicks a deployed vehicle.
 *
 * Any player can open the GUI regardless of ownership; access control for
 * pickup is enforced inside {@link VehicleInteractGUI}.
 */
public class MountListener implements Listener {

    private final GUIManager guiManager;

    public MountListener(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onInteract(BaseEntityInteractEvent event) {
        if (event.getAction() != Action.INTERACT) return;

        Player player = event.getPlayer();

        // Ignore interactions with special items in hand
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand != null && (ItemUtils.isItem(hand, "rhydonium_canister")
                || ItemUtils.isItem(hand, "wrench"))) {
            return;
        }

        ActiveModel clicked = event.getModel();

        for (Vehicle vehicle : VehicleModule.getActiveVehicles().values()) {
            VehicleData data = vehicle.getVehicleData();

            if (clicked != data.getActiveModel()) continue;

            // Open the seat-selection / pick-up GUI
            VehicleInteractGUI gui = new VehicleInteractGUI(vehicle, player, guiManager);
            guiManager.openGUI(gui, player);
            return;
        }
    }
}
