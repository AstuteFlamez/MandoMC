package net.mandomc.gameplay.territory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.bukkit.configuration.ConfigurationSection;

/** Sole authority for zone classification and protected terrain, shared by every action and map. */
public final class TerritoryPolicy {
    public enum Zone { SAFEZONE, WARZONE, WILDERNESS }
    private final ChunkAtlas mandalore;
    private final Map<String, ChunkAtlas> additionalMaps;
    private final Map<String, String> safeWorlds;
    private final String hoth;
    private final String landing;
    private final List<Integer> hothMin, hothMax, landingMin, landingMax;

    public TerritoryPolicy(ConfigurationSection config) {
        mandalore = ChunkAtlas.read(config);
        Map<String, ChunkAtlas> maps = new HashMap<>();
        var additional = config.getConfigurationSection("additional-maps");
        if (additional != null) for (String key : additional.getKeys(false)) {
            var atlas = ChunkAtlas.read(additional.getConfigurationSection(key));
            if (atlas.world().equals(mandalore.world()) || maps.putIfAbsent(atlas.world(), atlas) != null)
                throw new IllegalArgumentException("territory/maps.yml: duplicate atlas world " + atlas.world());
        }
        additionalMaps = Map.copyOf(maps);
        Map<String,String> safe = new HashMap<>();
        var section = config.getConfigurationSection("safezone-worlds");
        if (section == null) throw new IllegalArgumentException("territory/maps.yml: safezone-worlds required");
        for (String world : section.getKeys(false)) {
            if (!world.matches("[a-z0-9_-]+:[a-z0-9_/-]+") || !section.isString(world)
                    || section.getString(world).isBlank()) {
                throw new IllegalArgumentException("territory/maps.yml: invalid safezone world " + world);
            }
            safe.put(world, section.getString(world));
        }
        safeWorlds = Map.copyOf(safe);
        hoth = requiredWorld(config, "hoth.world");
        landing = requiredWorld(config, "legacy-landing.world");
        hothMin = point(config, "hoth.warzone-min"); hothMax = point(config, "hoth.warzone-max");
        landingMin = point(config, "legacy-landing.min"); landingMax = point(config, "legacy-landing.max");
        for (int i=0; i<3; i++) if (hothMin.get(i)>hothMax.get(i) || landingMin.get(i)>landingMax.get(i))
            throw new IllegalArgumentException("territory/maps.yml: reversed cuboid bounds");
    }
    private static String requiredWorld(ConfigurationSection config, String key) {
        String world = config.getString(key, "");
        if (!world.matches("[a-z0-9_-]+:[a-z0-9_/-]+"))
            throw new IllegalArgumentException("territory/maps.yml: invalid world at " + key);
        return world;
    }
    private static List<Integer> point(ConfigurationSection config, String key) {
        List<?> raw = config.getList(key);
        if (raw == null || raw.size()!=3 || raw.stream().anyMatch(v -> !(v instanceof Integer)))
            throw new IllegalArgumentException("territory/maps.yml: expected three integers at " + key);
        return raw.stream().map(v -> (Integer)v).toList();
    }
    private boolean inside(int x,int y,int z,List<Integer> min,List<Integer> max) {
        return x>=min.get(0) && x<=max.get(0) && y>=min.get(1) && y<=max.get(1) && z>=min.get(2) && z<=max.get(2);
    }
    public Zone zone(String world,int x,int y,int z) {
        if (safeWorlds.containsKey(world)) return Zone.SAFEZONE;
        if (world.equals(mandalore.world())) return switch(mandalore.zone(Math.floorDiv(x,16),Math.floorDiv(z,16))) {
            case 'S' -> Zone.SAFEZONE; case 'W' -> Zone.WARZONE; default -> Zone.WILDERNESS;
        };
        var atlas = additionalMaps.get(world);
        if (atlas != null) return switch (atlas.zone(Math.floorDiv(x,16), Math.floorDiv(z,16))) {
            case 'S' -> Zone.SAFEZONE; case 'W' -> Zone.WARZONE; default -> Zone.WILDERNESS;
        };
        if (world.equals(hoth)) return inside(x,y,z,hothMin,hothMax) ? Zone.WARZONE : Zone.SAFEZONE;
        if (world.equals(landing) && inside(x,y,z,landingMin,landingMax)) return Zone.SAFEZONE;
        return Zone.WILDERNESS;
    }
    public boolean edits(String world,int x,int y,int z) { return zone(world,x,y,z)==Zone.WILDERNESS; }
    public boolean combat(String world,int x,int y,int z) { return zone(world,x,y,z)!=Zone.SAFEZONE; }
    public boolean spawning(String world,int x,int y,int z) { return combat(world,x,y,z) && !world.equals(hoth); }
    public boolean wildernessChunk(String world,int x,int z) {
        if (safeWorlds.containsKey(world) || world.equals(hoth)) return false;
        var atlas = world.equals(mandalore.world()) ? mandalore : additionalMaps.get(world);
        if (atlas != null) return atlas.zone(x,z)=='.';
        if (world.equals(landing)) return (long)x*16+15<landingMin.get(0) || (long)x*16>landingMax.get(0)
                || (long)z*16+15<landingMin.get(2) || (long)z*16>landingMax.get(2);
        return true;
    }
    public ChunkAtlas map(String world,int chunkX,int chunkZ) {
        if (world.equals(mandalore.world())) return mandalore;
        if (additionalMaps.containsKey(world)) return additionalMaps.get(world);
        String name=safeWorlds.get(world);
        if (name!=null) return new ChunkAtlas(world,name,chunkX-16,chunkZ-16,chunkX+15,chunkZ+15,
                java.util.Collections.nCopies(32,"S".repeat(32)));
        return null;
    }
    public boolean wholeWorldSafe(String world) { return safeWorlds.containsKey(world); }
}
