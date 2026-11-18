package net.mandomc.gameplay.clans;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Additive, audited coal migration layer. Pristine frozen baseline files remain immutable. */
final class CoalTerrainOverlay {
    private record Change(int x,int y,int z,String before,String after) { }
    private final Map<ClanState.Chunk,List<Change>> changes;
    CoalTerrainOverlay(Path path) throws Exception {
        var out=new HashMap<ClanState.Chunk,List<Change>>();
        if(Files.exists(path)) {
            var yaml=new YamlConfiguration();yaml.load(path.toFile());
            if(yaml.getInt("schema-version")!=1)throw new IllegalArgumentException("Invalid coal terrain overlay schema");
            var rows=yaml.getStringList("changes");
            if(rows.size()>200000)throw new IllegalArgumentException("Coal terrain overlay is oversized");
            var unique=new HashSet<String>();
            for(String row:rows) {
                var a=row.split("\\|",-1);
                if(a.length!=4 || !a[0].matches("minecraft:(hoth|tatooine_review|mandalore_review|naboo)")
                        || !Set.of("minecraft:coal_ore","minecraft:deepslate_coal_ore").contains(a[3]) || !unique.add(a[0]+"|"+a[1]))
                    throw new IllegalArgumentException("Invalid coal terrain overlay row");
                var xyz=a[1].split(",");if(xyz.length!=3)throw new IllegalArgumentException("Invalid coal coordinate");
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
            if(i<0||i>=blocks.length)throw new IllegalStateException("Coal overlay outside baseline heights");
            if(staff.get(i))continue;
            if(!blocks[i].equals(change.before)&&!blocks[i].equals(change.after))
                throw new IllegalStateException("Coal overlay conflicts with frozen baseline at "+chunk+" index "+i);
            blocks[i]=change.after;
        }
    }
}
