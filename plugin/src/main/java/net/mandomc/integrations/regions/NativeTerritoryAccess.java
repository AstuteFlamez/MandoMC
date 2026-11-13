package net.mandomc.integrations.regions;

import net.mandomc.gameplay.territory.TerritoryService;
import org.bukkit.Bukkit;

/** Stateless compatibility boundary for existing gameplay adapters. Missing authority fails closed. */
public final class NativeTerritoryAccess {
    private NativeTerritoryAccess() {}
    public static TerritoryService current() {
        return Bukkit.getServicesManager().load(TerritoryService.class);
    }
}
