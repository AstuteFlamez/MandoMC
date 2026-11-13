package net.mandomc.integrations.regions;
import org.bukkit.Location;
/** Shared no-bypass native zone check for attacks, throw paths and reflections. */
public final class SaberProtection {
    public boolean allowed(Location location) {
        var territories=NativeTerritoryAccess.current();
        return territories!=null && territories.combat(location);
    }
}
