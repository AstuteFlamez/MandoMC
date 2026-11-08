package net.mandomc.integrations.regions;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import net.mandomc.gameplay.arrival.ArrivalProtection;
import org.bukkit.Bukkit;
import org.bukkit.Location;

public final class WorldGuardArrivalProtection implements ArrivalProtection {
    @Override
    public void requireProtected(Location location) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            throw new IllegalStateException("WorldGuard must be enabled for arrival protection");
        }
        var platform = WorldGuard.getInstance().getPlatform();
        var world = BukkitAdapter.adapt(location.getWorld());
        if (!platform.getGlobalStateManager().get(world).useRegions) {
            throw new IllegalStateException("WorldGuard regions are disabled in " + location.getWorld().getName());
        }
        var manager = platform.getRegionContainer().get(world);
        if (manager == null) {
            throw new IllegalStateException("WorldGuard could not load regions in " + location.getWorld().getName());
        }
        var regions = manager.getApplicableRegions(BukkitAdapter.asBlockVector(location));
        for (var flag : new StateFlag[] {Flags.PVP, Flags.BLOCK_BREAK, Flags.BLOCK_PLACE}) {
            if (regions.isVirtual() || regions.queryState(null, flag) != StateFlag.State.DENY) {
                throw new IllegalStateException("Arrival in " + location.getWorld().getName()
                        + " requires WorldGuard " + flag.getName() + "=deny");
            }
        }
    }
}
