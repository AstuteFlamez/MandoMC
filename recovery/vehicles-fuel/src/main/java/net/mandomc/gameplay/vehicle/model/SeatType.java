package net.mandomc.gameplay.vehicle.model;

/**
 * Defines the role a seat plays within a vehicle.
 *
 * DRIVER   — controls vehicle movement; there can only be one driver per vehicle.
 * PASSENGER — rides without movement control.
 */
public enum SeatType {
    DRIVER,
    PASSENGER
}
