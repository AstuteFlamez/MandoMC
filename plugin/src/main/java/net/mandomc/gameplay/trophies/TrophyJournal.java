package net.mandomc.gameplay.trophies;

import java.nio.file.*;
import java.util.*;
import net.mandomc.config.AtomicData;
import org.bukkit.configuration.file.YamlConfiguration;

/** Serialized IO owner. Decisions are durable before their corresponding inventory/world mutation. */
public final class TrophyJournal {
    public enum Phase { PREPARED, PLACED, RETURNING }
    public enum Face { UP, DOWN, NORTH, SOUTH, EAST, WEST }
    public record Cell(String world,int x,int y,int z) {
        public Cell { if(world==null || !world.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))throw new IllegalArgumentException("Invalid trophy world"); }
    }
    public record Entry(UUID id,UUID owner,Cell cell,int yaw,Face face,String item,Phase phase) {
        public Entry {
            Objects.requireNonNull(id);Objects.requireNonNull(owner);Objects.requireNonNull(cell);Objects.requireNonNull(face);Objects.requireNonNull(phase);
            if(yaw<0 || yaw>=360 || yaw%90!=0 || item==null || item.isBlank() || item.length()>1_400_000)
                throw new IllegalArgumentException("Invalid trophy entry");
            if(Base64.getDecoder().decode(item).length==0)throw new IllegalArgumentException("Empty trophy item");
        }
        public Entry(UUID id,UUID owner,Cell cell,int yaw,String item,Phase phase){this(id,owner,cell,yaw,Face.UP,item,phase);}
        public Entry placed(){return new Entry(id,owner,cell,yaw,face,item,Phase.PLACED);}
        public Entry returning(UUID recipient){return new Entry(id,recipient,cell,yaw,face,item,Phase.RETURNING);}
        public String placeReceipt(){return "place:"+id;}
        public String returnReceipt(){return "return:"+id;}
    }
    private final Path file;
    private final int limit;
    private volatile Map<UUID,Entry> entries=Map.of();
    public TrophyJournal(Path file,int limit)throws Exception {
        this.file=file;this.limit=limit;if(limit<1 || limit>100000)throw new IllegalArgumentException("Invalid trophy limit");
        if(!Files.exists(file))return;
        var yaml=new YamlConfiguration();yaml.load(file.toFile());
        if(yaml.getInt("schema-version")!=1 && yaml.getInt("schema-version")!=2)throw new IllegalArgumentException("trophies.yml: unsupported journal schema");
        var rows=yaml.getConfigurationSection("entries");var loaded=new HashMap<UUID,Entry>();
        if(rows!=null)for(String key:rows.getKeys(false)) {
            String p=key+".";var row=rows;
            var entry=new Entry(UUID.fromString(key),UUID.fromString(row.getString(p+"owner","")),
                    new Cell(row.getString(p+"world"),row.getInt(p+"x"),row.getInt(p+"y"),row.getInt(p+"z")),
                    row.getInt(p+"yaw"),Face.valueOf(row.getString(p+"face","UP")),row.getString(p+"item"),Phase.valueOf(row.getString(p+"phase","")));
            loaded.put(entry.id(),entry);
        }
        validate(loaded);entries=Map.copyOf(loaded);
    }
    public Map<UUID,Entry> entries(){return entries;}
    public Entry at(Cell cell){return entries.values().stream().filter(e->e.phase()!=Phase.RETURNING && e.cell().equals(cell)).findFirst().orElse(null);}
    private void validate(Map<UUID,Entry> values) {
        if(values.size()>limit)throw new IllegalStateException("Trophy journal limit reached");
        var occupied=new HashSet<Cell>();
        for(var e:values.values())if(e.phase()!=Phase.RETURNING && !occupied.add(e.cell()))throw new IllegalStateException("Overlapping trophy reservations");
    }
    public synchronized void reserve(Entry entry)throws Exception {
        if(entry.phase()!=Phase.PREPARED || entries.containsKey(entry.id()))throw new IllegalStateException("Invalid trophy reservation");
        var next=new HashMap<>(entries);next.put(entry.id(),entry);save(next);
    }
    public synchronized void replace(Entry expected,Entry next)throws Exception {
        if(!Objects.equals(entries.get(expected.id()),expected) || !expected.id().equals(next.id()) || !expected.cell().equals(next.cell())
                || !expected.item().equals(next.item()) || expected.yaw()!=next.yaw() || expected.face()!=next.face()
                || next.phase()==Phase.PREPARED || expected.phase()==Phase.RETURNING
                || next.phase()==Phase.PLACED && !expected.owner().equals(next.owner()))throw new IllegalStateException("Stale or invalid trophy transfer");
        var updated=new HashMap<>(entries);updated.put(next.id(),next);save(updated);
    }
    public synchronized void remove(Entry expected)throws Exception {
        if(!Objects.equals(entries.get(expected.id()),expected) || expected.phase()==Phase.PLACED)throw new IllegalStateException("Cannot remove active/stale trophy");
        var updated=new HashMap<>(entries);updated.remove(expected.id());save(updated);
    }
    private void save(Map<UUID,Entry> updated)throws Exception {
        validate(updated);var yaml=new YamlConfiguration();yaml.set("schema-version",2);yaml.createSection("entries");
        for(var e:updated.values()) {
            String p="entries."+e.id()+".";yaml.set(p+"owner",e.owner().toString());yaml.set(p+"world",e.cell().world());
            yaml.set(p+"x",e.cell().x());yaml.set(p+"y",e.cell().y());yaml.set(p+"z",e.cell().z());yaml.set(p+"yaw",e.yaw());
            yaml.set(p+"face",e.face().name());yaml.set(p+"item",e.item());yaml.set(p+"phase",e.phase().name());
        }
        AtomicData.write(file,yaml.saveToString());entries=Map.copyOf(updated);
    }
}
