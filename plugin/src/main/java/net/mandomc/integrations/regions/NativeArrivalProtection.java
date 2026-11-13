package net.mandomc.integrations.regions;
import net.mandomc.gameplay.arrival.ArrivalProtection;
import org.bukkit.Location;
public final class NativeArrivalProtection implements ArrivalProtection {
    @Override public void requireProtected(Location location) {
        var territories=NativeTerritoryAccess.current();
        if(territories==null||location==null||location.getWorld()==null||territories.combat(location)||territories.edits(location))
            throw new IllegalStateException("Arrival requires a native safezone with combat and building disabled");
    }
}
