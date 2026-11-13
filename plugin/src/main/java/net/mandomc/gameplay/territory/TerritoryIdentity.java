package net.mandomc.gameplay.territory;

import java.util.UUID;
import net.mandomc.gameplay.clans.ClanState;

/** Presentation identity only; zone, claim and raid state remain owned by their existing services. */
public record TerritoryIdentity(TerritoryPolicy.Zone zone, UUID owner, String name, Relation relation) {
    public enum Relation { NONE, OWN, OTHER, RAID }

    public static TerritoryIdentity read(TerritoryPolicy.Zone zone, ClanState.Clan owner,
                                         ClanState.Clan viewer, ClanState state, long now) {
        if (zone != TerritoryPolicy.Zone.WILDERNESS || owner == null) {
            String name = switch (zone) {
                case SAFEZONE -> "Safezone";
                case WARZONE -> "Warzone";
                case WILDERNESS -> "Wilderness";
            };
            return new TerritoryIdentity(zone, null, name, Relation.NONE);
        }
        var relation = viewer != null && viewer.id.equals(owner.id) ? Relation.OWN : Relation.OTHER;
        if (viewer != null && relation == Relation.OTHER) {
            var pair = viewer.id.compareTo(owner.id) < 0
                    ? new ClanState.Pair(viewer.id, owner.id) : new ClanState.Pair(owner.id, viewer.id);
            var raid = state.rivalries.get(pair);
            // A raid opens only the defender's claims to its recorded attacking clan.
            if (raid != null && raid.activeUntil() > now && viewer.id.equals(raid.activeAttacker())) {
                relation = Relation.RAID;
            }
        }
        return new TerritoryIdentity(zone, owner.id, owner.name, relation);
    }
}
