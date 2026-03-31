package net.mandomc.gameplay.vehicle.model;

/**
 * Immutable configuration for a single seat defined in a vehicle YAML file.
 *
 * @param name     display name shown on the seat button in the GUI
 * @param slot     inventory slot the button occupies in the vehicle interact GUI
 * @param type     role this seat grants its occupant (DRIVER / PASSENGER)
 * @param skullUrl base texture URL used to build the skull item for this seat's button;
 *                 must point to a valid Minecraft texture (textures.minecraft.net)
 * @param gunner   tag that allows the seat occupant to fire vehicle weapons
 */
public record SeatConfig(String name, int slot, SeatType type, String skullUrl, boolean gunner) {

    /**
     * Returns whether the occupant of this seat is allowed to fire the vehicle's weapon.
     *
     * DRIVER seats always permit shooting. PASSENGER seats may permit shooting
     * when explicitly tagged as gunner.
     */
    public boolean canShoot() {
        return type == SeatType.DRIVER || gunner;
    }
}
