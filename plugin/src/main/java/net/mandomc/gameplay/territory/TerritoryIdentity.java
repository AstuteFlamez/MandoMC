package net.mandomc.gameplay.territory;

import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mandomc.gameplay.clans.ClanState;

/** Presentation identity only; zone, claim and raid state remain owned by their existing services. */
public record TerritoryIdentity(TerritoryPolicy.Zone zone, UUID owner, String name, Relation relation) {
    public enum Relation {
        NONE(NamedTextColor.DARK_GRAY), OWN(NamedTextColor.GREEN), OTHER(NamedTextColor.RED),
        ALLY(NamedTextColor.BLUE), RAID(NamedTextColor.RED);

        private final NamedTextColor color;
        Relation(NamedTextColor color) { this.color = color; }
        public NamedTextColor color() { return color; }
    }

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
        var relation = clanRelation(viewer == null ? null : viewer.id, owner.id,
                viewer != null && state != null && state.allied(viewer.id, owner.id));
        if (viewer != null && state != null && relation == Relation.OTHER) {
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

    public static Relation clanRelation(UUID viewer, UUID owner, boolean allied) {
        if (owner.equals(viewer)) return Relation.OWN;
        return allied ? Relation.ALLY : Relation.OTHER;
    }
}
