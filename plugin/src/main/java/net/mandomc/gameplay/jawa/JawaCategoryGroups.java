package net.mandomc.gameplay.jawa;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Durable group reservations include unloaded members. Mutations run on the shared serialized IO writer. */
public final class JawaCategoryGroups {
    private final Path file;
    private volatile Map<String,Map<UUID,String>> groups=Map.of();
    public JawaCategoryGroups(Path file) throws Exception {
        this.file=file;if(!Files.exists(file))return;
        var yaml=new YamlConfiguration();yaml.load(file.toFile());
        if(yaml.getInt("schema-version")!=1)throw new IllegalArgumentException("jawa-category-groups.yml: unsupported schema");
        var saved=yaml.getConfigurationSection("groups");var loaded=new HashMap<String,Map<UUID,String>>();
        if(saved!=null)for(String id:saved.getKeys(false)) {
            UUID.fromString(id);var members=Objects.requireNonNull(saved.getConfigurationSection(id));var rows=new HashMap<UUID,String>();
            for(String key:members.getKeys(false)){String category=members.getString(key,"");if(!category.matches("[a-z0-9_]+"))throw new IllegalArgumentException("Invalid saved Jawa category");rows.put(UUID.fromString(key),category);}
            if(new HashSet<>(rows.values()).size()!=rows.size())throw new IllegalArgumentException("Duplicate saved Jawa group category: "+id);
            loaded.put(id,Map.copyOf(rows));
        }
        groups=Map.copyOf(loaded);
    }
    public String category(String group,UUID member){return groups.getOrDefault(group,Map.of()).get(member);}
    /** Keep valid reservations and preferred legacy categories, then fill remaining slots without replacement. */
    public static Map<UUID,String> plan(String group,Map<UUID,String> existing,Map<UUID,String> observed,List<String> categories) {
        UUID id=UUID.fromString(group);
        if(categories.isEmpty() || new HashSet<>(categories).size()!=categories.size())throw new IllegalArgumentException("Invalid category catalog");
        var result=new HashMap<UUID,String>();var used=new HashSet<String>();
        existing.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            if(categories.contains(entry.getValue())){if(!used.add(entry.getValue()))throw new IllegalArgumentException("Duplicate category reservation");result.put(entry.getKey(),entry.getValue());}
        });
        var members=observed.keySet().stream().sorted().toList();
        for(var member:members)if(!result.containsKey(member)) {
            String preferred=observed.get(member);
            if(preferred!=null && categories.contains(preferred) && used.add(preferred))result.put(member,preferred);
        }
        var available=new ArrayList<>(categories);Collections.shuffle(available,new Random(id.getMostSignificantBits()^id.getLeastSignificantBits()));available.removeAll(used);
        for(var member:members)if(!result.containsKey(member)) {
            if(available.isEmpty())throw new IllegalArgumentException("Jawa group has more members than available categories: "+group);
            result.put(member,available.removeFirst());
        }
        return Map.copyOf(result);
    }
    public synchronized void assign(String id,Map<UUID,String> observed,List<String> categories) throws Exception {
        var next=plan(id,groups.getOrDefault(id,Map.of()),observed,categories);
        if(next.equals(groups.get(id)))return;
        var updated=new HashMap<>(groups);updated.put(id,next);
        var yaml=new YamlConfiguration();yaml.set("schema-version",1);yaml.createSection("groups");
        for(var group:updated.entrySet())for(var member:group.getValue().entrySet())yaml.set("groups."+group.getKey()+"."+member.getKey(),member.getValue());
        net.mandomc.config.AtomicData.write(file,yaml.saveToString());groups=Map.copyOf(updated);
    }
}
