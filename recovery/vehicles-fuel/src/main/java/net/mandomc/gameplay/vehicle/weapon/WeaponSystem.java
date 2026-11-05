package net.mandomc.gameplay.vehicle.weapon;

import net.mandomc.gameplay.vehicle.model.Vehicle;
import org.bukkit.entity.Player;

public interface WeaponSystem {

    void shoot(Vehicle vehicle, Player shooter);

}