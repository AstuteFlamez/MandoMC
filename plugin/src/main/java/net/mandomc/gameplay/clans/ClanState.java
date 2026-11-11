package net.mandomc.gameplay.clans;

import java.util.*;

/** Main-thread clan authority. Stable clan IDs also identify pairwise rivalries. */
public final class ClanState {
    public record Chunk(String world, int x, int z) {
        public boolean near(Chunk other) {
            return world.equals(other.world) && Math.abs((long)x - other.x) <= 1
                    && Math.abs((long)z - other.z) <= 1;
        }
    }
    public record Home(String world, double x, double y, double z, float yaw, float pitch) {}
    public static final class Clan {
        public final UUID id;
        public final String name;
        public UUID leader;
        public final Set<UUID> members = new LinkedHashSet<>();
        public long lastActive;
        public long abandonedAt;
        public Home home;
        public Clan(UUID id, String name, UUID leader, long now) {
            this.id = id; this.name = name; this.leader = leader; lastActive = now; members.add(leader);
        }
    }
    public record Pair(UUID first, UUID second) {
        static Pair of(UUID a, UUID b) { return a.compareTo(b) < 0 ? new Pair(a,b) : new Pair(b,a); }
    }
    public record Rivalry(int score, UUID activeAttacker, long activeUntil) {}
    public final Map<UUID, Clan> clans = new LinkedHashMap<>();
    public final Map<Chunk, UUID> claims = new LinkedHashMap<>();
    public final Map<Pair, Rivalry> rivalries = new LinkedHashMap<>();
    public Clan member(UUID player) {
        return clans.values().stream().filter(c -> c.members.contains(player)).findFirst().orElse(null);
    }
    public Clan named(String name) {
        return clans.values().stream().filter(c -> c.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public Clan owner(Chunk chunk) { return clans.get(claims.get(chunk)); }
    public Clan create(String name, UUID player, long now) {
        if (!name.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("invalid-name");
        if (member(player) != null) throw new IllegalArgumentException("already-member");
        if (named(name) != null) throw new IllegalArgumentException("name-taken");
        var clan = new Clan(UUID.randomUUID(), name, player, now); clans.put(clan.id, clan); return clan;
    }
    public void join(Clan clan, UUID player, int limit, long now) {
        if (member(player) != null) throw new IllegalArgumentException("already-member");
        if (clan.abandonedAt != 0) throw new IllegalArgumentException("abandoned");
        if (clan.members.size() >= limit) throw new IllegalArgumentException("member-limit");
        clan.members.add(player); clan.lastActive = now;
    }
    public void claim(Clan clan, Chunk chunk, int limit, java.util.function.Predicate<Chunk> wilderness) {
        if (clan.abandonedAt != 0) throw new IllegalArgumentException("abandoned");
        if (claims.containsKey(chunk)) throw new IllegalArgumentException("already-claimed");
        if (claims.values().stream().filter(clan.id::equals).count() >= limit)
            throw new IllegalArgumentException("claim-limit");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var nearby = new Chunk(chunk.world, chunk.x + dx, chunk.z + dz);
            if (!wilderness.test(nearby)) throw new IllegalArgumentException("zone-buffer");
            UUID owner = claims.get(nearby);
            if (owner != null && !owner.equals(clan.id)) throw new IllegalArgumentException("claim-buffer");
        }
        claims.put(chunk, clan.id);
    }
    public int score(UUID a, UUID b) {
        if (a.equals(b)) return 0;
        var pair = Pair.of(a,b); var rivalry = rivalries.get(pair);
        return rivalry == null ? 0 : a.equals(pair.first) ? rivalry.score : -rivalry.score;
    }
    public void kill(UUID attacker, UUID defender, long now) {
        if (attacker.equals(defender)) return;
        var pair = Pair.of(attacker, defender);
        var old = rivalries.getOrDefault(pair, new Rivalry(0,null,0));
        if (old.activeUntil > now) return;
        int next = Math.addExact(old.score, attacker.equals(pair.first) ? 1 : -1);
        rivalries.put(pair, new Rivalry(next, null, 0));
    }
    public boolean canRaid(UUID attacker, UUID defender, long now, int threshold) {
        if (attacker == null || attacker.equals(defender)) return false;
        var rivalry = rivalries.get(Pair.of(attacker, defender));
        if (rivalry != null && rivalry.activeUntil > now) return attacker.equals(rivalry.activeAttacker);
        return score(attacker, defender) >= threshold;
    }
    /** Only the first successful breach starts the hour. Further blasts cannot extend it. */
    public boolean breach(UUID attacker, UUID defender, long now, long activeMillis, int threshold) {
        if (!canRaid(attacker, defender, now, threshold)) return false;
        var pair = Pair.of(attacker, defender);
        var old = rivalries.get(pair);
        if (old != null && old.activeUntil > now) return false;
        rivalries.put(pair, new Rivalry(0, attacker, now + activeMillis));
        return true;
    }
    public void remove(Clan clan) {
        clans.remove(clan.id); claims.values().removeIf(clan.id::equals);
        rivalries.keySet().removeIf(p -> p.first.equals(clan.id) || p.second.equals(clan.id));
    }
}
