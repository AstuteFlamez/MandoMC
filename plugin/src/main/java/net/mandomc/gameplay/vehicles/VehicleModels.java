package net.mandomc.gameplay.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

public interface VehicleModels extends AutoCloseable {
    interface Handle {
        boolean mount(Player player, boolean driver);

        List<Player> riders();

        Player driver();

        void eject(Player player);

        void remove();
    }

    interface Access {
        boolean mayPilot(Player player);

        boolean mayMove(Location from, Location to);

        void interact(Player player);
    }

    Handle attach(LivingEntity entity, VehicleDefinition definition, Access access);

    @Override
    void close();
}
