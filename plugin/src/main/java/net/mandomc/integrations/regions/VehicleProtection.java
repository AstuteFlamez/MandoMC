package net.mandomc.integrations.regions;
import org.bukkit.Location;
/** Native static protection applies even to operators and unattributed explosions. */
public final class VehicleProtection {
    public boolean damage(Location location) {
        var territories=NativeTerritoryAccess.current();return territories!=null&&territories.combat(location);
    }
    /** Vehicle weapons follow vehicle placement zones, independently of ordinary PvP. */
    public boolean use(Location location) {
        var territories = NativeTerritoryAccess.current();
        return territories != null && territories.edits(location);
    }
    public boolean blocks(Location location) {
        var territories=NativeTerritoryAccess.current();return territories!=null&&territories.edits(location);
    }
}
