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
        public final Map<UUID,ClanRole> roles = new LinkedHashMap<>();
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
        clan.members.add(player);clan.roles.put(player,ClanRole.RECRUIT); clan.lastActive = now;
    }
    public ClanRole role(Clan clan, UUID player) {
        if(clan==null||!clan.members.contains(player))return null;
        return clan.leader.equals(player)?ClanRole.LEADER:clan.roles.getOrDefault(player,ClanRole.MEMBER);
    }
    public void kick(Clan clan, UUID actor, UUID target) {
        var rank=role(clan,actor);var lower=role(clan,target);
        if(rank==null||!rank.manages())throw new IllegalArgumentException("not-manager");
        if(lower==null)throw new IllegalArgumentException("not-clanmate");
        if(actor.equals(target)||rank.ordinal()<=lower.ordinal())throw new IllegalArgumentException("rank-protected");
        clan.members.remove(target);clan.roles.remove(target);
    }
    public void changeRole(Clan clan, UUID actor, UUID target, boolean promote) {
        if(!clan.leader.equals(actor))throw new IllegalArgumentException("not-leader");
        var rank=role(clan,target);
        if(rank==null)throw new IllegalArgumentException("not-clanmate");
        if(rank==ClanRole.LEADER)throw new IllegalArgumentException("rank-protected");
        int next=rank.ordinal()+(promote?1:-1);
        if(next<ClanRole.RECRUIT.ordinal()||next>ClanRole.OFFICER.ordinal())throw new IllegalArgumentException("role-limit");
        clan.roles.put(target,ClanRole.values()[next]);
    }
    public void transfer(Clan clan, UUID actor, UUID target) {
        if(!clan.leader.equals(actor))throw new IllegalArgumentException("not-leader");
        if(!clan.members.contains(target)||actor.equals(target))throw new IllegalArgumentException("not-clanmate");
        clan.roles.put(actor,ClanRole.MEMBER);clan.roles.remove(target);clan.leader=target;
    }
    public void claim(Clan clan, Chunk chunk, int limit, java.util.function.Predicate<Chunk> wilderness) {
        if (clan.abandonedAt != 0) throw new IllegalArgumentException("abandoned");
        if (claims.containsKey(chunk)) throw new IllegalArgumentException("already-claimed");
        if (claims.values().stream().filter(clan.id::equals).count() >= limit)
            throw new IllegalArgumentException("claim-limit");
        var owned = claims.entrySet().stream().filter(entry -> entry.getValue().equals(clan.id))
                .map(Map.Entry::getKey).toList();
        if (!owned.isEmpty() && owned.stream().noneMatch(existing -> adjacent(existing,chunk)))
            throw new IllegalArgumentException("claim-adjacent");
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var nearby = new Chunk(chunk.world, chunk.x + dx, chunk.z + dz);
            if (!wilderness.test(nearby)) throw new IllegalArgumentException("zone-buffer");
            UUID owner = claims.get(nearby);
            if (owner != null && !owner.equals(clan.id)) throw new IllegalArgumentException("claim-buffer");
        }
        claims.put(chunk, clan.id);
    }
    private static boolean adjacent(Chunk left, Chunk right) {
        return left.world.equals(right.world)
                && Math.abs((long)left.x-right.x)+Math.abs((long)left.z-right.z)==1;
    }
    private static int components(Set<Chunk> chunks) {
        var remaining=new HashSet<>(chunks);int count=0;
        while(!remaining.isEmpty()) {
            count++;var queue=new ArrayDeque<Chunk>();var first=remaining.iterator().next();
            remaining.remove(first);queue.add(first);
            while(!queue.isEmpty()) {
                var current=queue.removeFirst();
                var neighbors=remaining.stream().filter(other->adjacent(current,other)).toList();
                remaining.removeAll(neighbors);queue.addAll(neighbors);
            }
        }
        return count;
    }
    /** Legacy islands remain; ordinary removal must not create additional disconnected components. */
    public void checkUnclaim(Clan clan, Chunk chunk) {
        if(owner(chunk)!=clan)throw new IllegalArgumentException("not-owner");
        var owned=new HashSet<Chunk>();
        claims.forEach((key,id)->{if(id.equals(clan.id))owned.add(key);});
        int before=components(owned);owned.remove(chunk);
        if(components(owned)>before)throw new IllegalArgumentException("unclaim-splits");
    }
    public void unclaim(Clan clan, Chunk chunk) { checkUnclaim(clan,chunk);claims.remove(chunk); }
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
