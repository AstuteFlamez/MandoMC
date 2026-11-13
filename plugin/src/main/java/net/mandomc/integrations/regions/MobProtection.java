package net.mandomc.integrations.regions;
import org.bukkit.Location;
public final class MobProtection {
    public boolean allowed(Location location) {
        var territories=NativeTerritoryAccess.current();return territories!=null&&territories.spawning(location);
    }
}
