package net.mandomc.gameplay.vehicles;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.List;

public interface VehicleModels extends AutoCloseable {
    /** Native onGround can lag after a zero-velocity hover; require actual nearby support. */
    static boolean grounded(LivingEntity entity) {
        var box = entity.getBoundingBox();
        var support = new org.bukkit.util.BoundingBox(
                box.getMinX() + .01, box.getMinY() - .08, box.getMinZ() + .01,
                box.getMaxX() - .01, box.getMinY() + .001, box.getMaxZ() - .01);
        return entity.getWorld().hasCollisionsIn(support);
    }

    enum Seat {
        PILOT,
        GUNNER,
        PASSENGER
    }

    record SeatStatus(String id, Seat role, Player occupant) {}

    interface Handle {
        boolean mount(Player player, Seat seat);

        boolean mount(Player player, Seat seat, String seatId);

        List<SeatStatus> seats();

        boolean mayShoot(Player player);

        Location muzzle(String bone);

        org.bukkit.util.Vector aim();

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
