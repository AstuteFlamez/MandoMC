package net.mandomc.gameplay.mobs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Main-thread encounter ownership: every member resolves to one group; each player reserves at most one group. */
public final class PatrolState {
    private static final class Group {
        private final Set<UUID> members = new HashSet<>();
        private final Set<UUID> hostiles = new HashSet<>();
    }
    private record Claim(String group, long observedAt) { }
    private final Map<String, Group> groups = new HashMap<>();
    private final Map<UUID, String> byMember = new HashMap<>();
    private final Map<UUID, Claim> byPlayer = new HashMap<>();

    public void update(String id, Set<UUID> members, Set<UUID> hostiles) {
        if (!members.containsAll(hostiles)) throw new IllegalArgumentException("Patrol hostiles must be group members");
        if (hostiles.isEmpty()) { remove(id); return; }
        var group = groups.computeIfAbsent(id, ignored -> new Group());
        for (UUID old : group.members) byMember.remove(old, id);
        group.members.clear();
        group.hostiles.clear();
        group.members.addAll(members);
        group.hostiles.addAll(hostiles);
        members.forEach(member -> byMember.put(member, id));
    }

    /** A target event can identify a newly loaded group member before the next population pass. */
    public void track(String id, UUID member, boolean hostile) {
        var group = groups.get(id);
        if (group == null && !hostile) return;
        if (group == null) { group = new Group(); groups.put(id, group); }
        group.members.add(member);
        if (hostile) group.hostiles.add(member);
        byMember.put(member, id);
    }

    public boolean claim(UUID player, String id, long now) {
        if (!groups.containsKey(id)) return false;
        var previous = byPlayer.get(player);
        if (previous != null && !previous.group().equals(id)) return false;
        byPlayer.put(player, new Claim(id, now));
        return true;
    }

    public boolean claimMember(UUID player, UUID member, long now) {
        String id = byMember.get(member);
        return id == null || claim(player, id, now);
    }

    public String playerGroup(UUID player) {
        var claim = byPlayer.get(player);
        return claim == null ? null : claim.group();
    }

    public String memberGroup(UUID member) { return byMember.get(member); }
    public Set<String> groups() { return Set.copyOf(groups.keySet()); }
    public Set<UUID> members(String id) {
        var group = groups.get(id);
        return group == null ? Set.of() : Set.copyOf(group.members);
    }
    public Set<UUID> hostiles(String id) {
        var group = groups.get(id);
        return group == null ? Set.of() : Set.copyOf(group.hostiles);
    }

    public void touch(UUID player, long now) {
        var claim = byPlayer.get(player);
        if (claim != null) byPlayer.put(player, new Claim(claim.group(), now));
    }

    public void release(UUID player) { byPlayer.remove(player); }
    public void retainPlayers(Set<UUID> present) { byPlayer.keySet().retainAll(present); }
    public void expire(long now, long timeout) {
        byPlayer.entrySet().removeIf(entry -> now - entry.getValue().observedAt() >= timeout);
    }

    public void memberGone(UUID member) {
        String id = byMember.remove(member);
        var group = groups.get(id);
        if (group == null) return;
        group.members.remove(member);
        group.hostiles.remove(member);
        // Surviving passive banthas do not hold a defeated combat patrol's reservation forever.
        if (group.hostiles.isEmpty()) remove(id);
    }

    public void retainGroups(Set<String> present) {
        for (String id : groups()) if (!present.contains(id)) remove(id);
    }

    public void remove(String id) {
        var group = groups.remove(id);
        if (group != null) group.members.forEach(member -> byMember.remove(member, id));
        byPlayer.entrySet().removeIf(entry -> entry.getValue().group().equals(id));
    }
}
