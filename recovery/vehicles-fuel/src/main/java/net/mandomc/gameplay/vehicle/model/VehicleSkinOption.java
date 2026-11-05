package net.mandomc.gameplay.vehicle.model;

/**
 * Immutable vehicle skin definition loaded from config.
 */
public record VehicleSkinOption(
        String id,
        String modelKey,
        String permission,
        String itemName,
        int customModelData
) {
    public boolean hasPermissionNode() {
        return permission != null && !permission.isBlank();
    }
}
