package net.mandomc.integrations.regions;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;

import org.bukkit.Bukkit;
import org.bukkit.Location;

/** Static map protections apply even to operators and unattributed explosions. */
public final class VehicleProtection {
    public boolean damage(Location location) {
        return new SaberProtection().allowed(location);
    }

    public boolean blocks(Location location) {
        if (location.getWorld() == null || !Bukkit.getPluginManager().isPluginEnabled("WorldGuard"))
            return false;
        var regions =
                WorldGuard.getInstance()
                        .getPlatform()
                        .getRegionContainer()
                        .createQuery()
                        .getApplicableRegions(BukkitAdapter.adapt(location));
        if (regions.isVirtual()) return false;
        for (var flag :
                new StateFlag[] {
                    Flags.BLOCK_BREAK,
                    Flags.TNT,
                    Flags.OTHER_EXPLOSION,
                    Flags.CREEPER_EXPLOSION,
                    Flags.GHAST_FIREBALL,
                    Flags.WITHER_DAMAGE
                }) {
            if (regions.queryState(null, flag) == StateFlag.State.DENY) return false;
        }
        return regions.queryState(
                        ignored -> com.sk89q.worldguard.domains.Association.NON_MEMBER, Flags.BUILD)
                != StateFlag.State.DENY;
    }
}
