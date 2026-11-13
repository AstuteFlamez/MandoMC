package net.mandomc.gameplay.clans;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Versioned native state; snapshots are serialized on the server thread and written on one IO worker. */
final class ClanStorage {
    static String encode(ClanState state) {
        var yaml = new YamlConfiguration(); yaml.set("schema-version", 1);
        yaml.set("clans", state.clans.values().stream().map(c -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id", c.id.toString()); row.put("name", c.name); row.put("leader", c.leader.toString());
            row.put("members", c.members.stream().map(UUID::toString).toList());
            Map<String,String> roles=new LinkedHashMap<>();
            c.roles.forEach((id,role)->{if(c.members.contains(id)&&!c.leader.equals(id))roles.put(id.toString(),role.name().toLowerCase(Locale.ROOT));});
            row.put("roles",roles);
            row.put("last-active", c.lastActive); row.put("abandoned-at", c.abandonedAt);
            if (c.home != null) row.put("home", Map.of("world",c.home.world(),"x",c.home.x(),"y",c.home.y(),
                    "z",c.home.z(),"yaw",c.home.yaw(),"pitch",c.home.pitch()));
            return row;
        }).toList());
        yaml.set("claims", state.claims.entrySet().stream().map(e -> Map.of("world",e.getKey().world(),
                "x",e.getKey().x(),"z",e.getKey().z(),"owner",e.getValue().toString())).toList());
        yaml.set("rivalries", state.rivalries.entrySet().stream().map(e -> {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("first",e.getKey().first().toString()); row.put("second",e.getKey().second().toString());
            row.put("score",e.getValue().score()); row.put("until",e.getValue().activeUntil());
            if (e.getValue().activeAttacker()!=null) row.put("attacker",e.getValue().activeAttacker().toString());
            return row;
        }).toList());
        return yaml.saveToString();
    }
    static ClanState load(Path path) throws Exception {
        if (!Files.exists(path)) return new ClanState();
        var yaml = new YamlConfiguration(); yaml.load(path.toFile());
        if (yaml.getInt("schema-version") != 1) throw new IllegalArgumentException("Invalid clan state schema");
        var state = new ClanState(); var members = new HashSet<UUID>();
        for (var row : yaml.getMapList("clans")) {
            var c = new ClanState.Clan(id(row,"id"),row.get("name").toString(),id(row,"leader"),number(row,"last-active"));
            c.members.clear();
            for (Object member : (List<?>)row.get("members")) {
                UUID uuid = UUID.fromString(member.toString());
                if (!members.add(uuid)) throw new IllegalArgumentException("Duplicate clan membership");
                c.members.add(uuid);
            }
            if (!c.members.contains(c.leader) || state.named(c.name)!=null || state.clans.containsKey(c.id))
                throw new IllegalArgumentException("Invalid clan identity");
            if(row.containsKey("roles")&&!(row.get("roles") instanceof Map<?,?>))throw new IllegalArgumentException("Invalid clan roles");
            if(row.get("roles") instanceof Map<?,?> roles)for(var entry:roles.entrySet()) {
                UUID player=UUID.fromString(entry.getKey().toString());var role=ClanRole.read(entry.getValue().toString());
                if(!c.members.contains(player))throw new IllegalArgumentException("Role assigned to nonmember");
                if(c.leader.equals(player))continue;
                if(role==ClanRole.LEADER)throw new IllegalArgumentException("Multiple clan leaders");
                c.roles.put(player,role);
            }
            c.abandonedAt = number(row,"abandoned-at");
            if (row.get("home") instanceof Map<?,?> h) c.home = new ClanState.Home(h.get("world").toString(),
                    decimal(h,"x"),decimal(h,"y"),decimal(h,"z"),(float)decimal(h,"yaw"),(float)decimal(h,"pitch"));
            state.clans.put(c.id,c);
        }
        for (var row : yaml.getMapList("claims")) {
            UUID owner = id(row,"owner");
            var chunk = new ClanState.Chunk(row.get("world").toString(),(int)number(row,"x"),(int)number(row,"z"));
            if (!state.clans.containsKey(owner) || state.claims.putIfAbsent(chunk,owner)!=null)
                throw new IllegalArgumentException("Invalid claim owner");
        }
        for (var row : yaml.getMapList("rivalries")) {
            UUID first=id(row,"first"), second=id(row,"second");
            if (!state.clans.containsKey(first)||!state.clans.containsKey(second)||first.compareTo(second)>=0)
                throw new IllegalArgumentException("Invalid rivalry");
            state.rivalries.put(new ClanState.Pair(first,second), new ClanState.Rivalry((int)number(row,"score"),
                    row.containsKey("attacker")?id(row,"attacker"):null,number(row,"until")));
        }
        return state;
    }
    private static UUID id(Map<?,?> row,String key) { return UUID.fromString(row.get(key).toString()); }
    private static long number(Map<?,?> row,String key) { return ((Number)row.get(key)).longValue(); }
    private static double decimal(Map<?,?> row,String key) { return ((Number)row.get(key)).doubleValue(); }
}
