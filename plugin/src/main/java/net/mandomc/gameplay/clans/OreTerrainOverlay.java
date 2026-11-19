package net.mandomc.gameplay.clans;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Audited ore changes layered onto immutable wilderness originals. */
final class OreTerrainOverlay {
    private static final Set<String> COAL = Set.of("minecraft:coal_ore", "minecraft:deepslate_coal_ore");
    private static final Set<String> METALS = Set.of(
            "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
            "minecraft:redstone_ore[lit=false]", "minecraft:deepslate_redstone_ore[lit=false]",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore");
    private record Change(int x,int y,int z,String before,String after) { }
    private final Map<ClanState.Chunk,List<Change>> changes;
    OreTerrainOverlay(Path path) throws Exception {
        var out=new HashMap<ClanState.Chunk,List<Change>>();
        if(Files.exists(path)) {
            var yaml=new YamlConfiguration();yaml.load(path.toFile());
            int version=yaml.getInt("schema-version");
            if(version!=1&&version!=2)throw new IllegalArgumentException("Invalid ore terrain overlay schema");
            var rows=yaml.getStringList("changes");
            if(rows.size()>200000)throw new IllegalArgumentException("Ore terrain overlay is oversized");
            var unique=new HashSet<String>();
            for(String row:rows) {
                var a=row.split("\\|",-1);
                if(a.length!=4 || !a[0].matches("minecraft:(hoth|tatooine_review|mandalore_review|naboo)")
                        || !(COAL.contains(a[3]) || version==2 && METALS.contains(a[3]))
                        || !unique.add(a[0]+"|"+a[1]))
                    throw new IllegalArgumentException("Invalid ore terrain overlay row");
                var xyz=a[1].split(",");if(xyz.length!=3)throw new IllegalArgumentException("Invalid ore coordinate");
                int x=Integer.parseInt(xyz[0]),y=Integer.parseInt(xyz[1]),z=Integer.parseInt(xyz[2]);
                var key=new ClanState.Chunk(a[0],x>>4,z>>4);
                out.computeIfAbsent(key,k->new ArrayList<>()).add(new Change(x,y,z,a[2],a[3]));
            }
        }
        changes=Map.copyOf(out);
    }
    void apply(ClanState.Chunk chunk,int minY,String[] blocks,BitSet staff) {
        for(var change:changes.getOrDefault(chunk,List.of())) {
            int i=(change.y-minY)*256+(change.z&15)*16+(change.x&15);
            if(i<0||i>=blocks.length)throw new IllegalStateException("Ore overlay outside baseline heights");
            if(staff.get(i))continue;
            if(!blocks[i].equals(change.before)&&!blocks[i].equals(change.after))
                throw new IllegalStateException("Ore overlay conflicts with frozen baseline at "+chunk+" index "+i);
            blocks[i]=change.after;
        }
    }
}
