package net.mandomc.integrations.regions;
import org.bukkit.Location;
public final class MobProtection {
    /** Ambient desert encounters spawn only in wilderness; event mobs retain warzone permission. */
    public boolean encounter(Location location) {
        var territories = NativeTerritoryAccess.current();
        return territories != null && territories.edits(location) && territories.spawning(location);
    }
    public boolean allowed(Location location) {
        var territories=NativeTerritoryAccess.current();return territories!=null&&territories.spawning(location);
    }
}
