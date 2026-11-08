package net.mandomc.integrations.regions;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/** Explicit no-bypass zone check for saber attacks, throw paths and reflections. */
public final class SaberProtection {
    public boolean allowed(Location location) {
        if (location.getWorld()==null || !Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) return false;
        var regions=WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(location));
        return !regions.isVirtual() && regions.queryState(null, Flags.PVP)!=StateFlag.State.DENY
                && regions.queryState(null, Flags.INVINCIBILITY)!=StateFlag.State.ALLOW;
    }
}
