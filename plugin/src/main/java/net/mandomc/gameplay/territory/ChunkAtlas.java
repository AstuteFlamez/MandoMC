package net.mandomc.gameplay.territory;

import java.util.List;
import org.bukkit.configuration.ConfigurationSection;

/** A bounded, immutable chunk projection. This is presentation data, not protection or ownership. */
public record ChunkAtlas(String world, String name, int minX, int minZ, int maxX, int maxZ, List<String> rows) {
    public ChunkAtlas {
        rows = List.copyOf(rows);
        if (world == null || world.isBlank() || name == null || name.isBlank()
                || minX > maxX || minZ > maxZ || (long) maxX - minX >= 64 || (long) maxZ - minZ >= 64) {
            throw new IllegalArgumentException("territory/maps.yml: invalid world, name or bounds (maximum 64 chunks)");
        }
        int width = maxX - minX + 1;
        int height = maxZ - minZ + 1;
        if (rows.size() != height || rows.stream().anyMatch(row -> row.length() != width || !row.matches("[SW.]+"))) {
            throw new IllegalArgumentException("territory/maps.yml: rows must exactly cover bounds using S, W or .");
        }
    }

    public static ChunkAtlas read(ConfigurationSection config) {
        if (config == null || config.getInt("schema-version", -1) != 1) {
            throw new IllegalArgumentException("territory/maps.yml: expected schema-version 1");
        }
        return new ChunkAtlas(config.getString("world"), config.getString("name"),
                requiredInt(config, "min-chunk-x"), requiredInt(config, "min-chunk-z"),
                requiredInt(config, "max-chunk-x"), requiredInt(config, "max-chunk-z"), config.getStringList("rows"));
    }

    private static int requiredInt(ConfigurationSection config, String key) {
        if (!config.isInt(key)) throw new IllegalArgumentException("territory/maps.yml: missing integer " + key);
        return config.getInt(key);
    }

    public boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; }

    /** A finite world-border projection; protection outside that view remains authoritative. */
    public static ChunkAtlas borderView(TerritoryPolicy policy,String world,String name,
                                       double centerX,double centerZ,double size,int y) {
        if(!Double.isFinite(size)||size<=0||size>1024||!Double.isFinite(centerX)||!Double.isFinite(centerZ))return null;
        int minX=(int)Math.floor((centerX-size/2)/16),maxX=(int)Math.ceil((centerX+size/2)/16)-1;
        int minZ=(int)Math.floor((centerZ-size/2)/16),maxZ=(int)Math.ceil((centerZ+size/2)/16)-1;
        if((long)maxX-minX>=64||(long)maxZ-minZ>=64)return null;
        var rows=new java.util.ArrayList<String>();
        for(int z=minZ;z<=maxZ;z++) {
            var row=new StringBuilder();
            for(int x=minX;x<=maxX;x++)row.append(switch(policy.zone(world,x*16+8,y,z*16+8)) {
                case SAFEZONE->'S';case WARZONE->'W';case WILDERNESS->'.';
            });
            rows.add(row.toString());
        }
        return new ChunkAtlas(world,name,minX,minZ,maxX,maxZ,rows);
    }
    public char zone(int x, int z) { return contains(x, z) ? rows.get(z - minZ).charAt(x - minX) : '.'; }
}
