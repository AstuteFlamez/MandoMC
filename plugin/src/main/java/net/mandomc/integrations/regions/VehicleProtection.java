package net.mandomc.integrations.regions;
import org.bukkit.Location;
/** Native static protection applies even to operators and unattributed explosions. */
public final class VehicleProtection {
    public boolean damage(Location location) {
        var territories=NativeTerritoryAccess.current();return territories!=null&&territories.combat(location);
    }
    public boolean blocks(Location location) {
        var territories=NativeTerritoryAccess.current();return territories!=null&&territories.edits(location);
    }
}
