package net.mandomc.gameplay.jawa;

import java.util.*;
import net.mandomc.gameplay.credits.CreditLedger;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable offers and bounded population settings; prices represent one explicitly sized bundle. */
public record JawaSettings(List<String> worlds, int interval, int cap, int minimum, int maximum,
        int minDistance, int maxDistance, int lifetime, int offers, List<Offer> supplies, List<Offer> purchases, List<Category> categories) {
    public record Offer(String id, String item, int amount, long price) { }
    public record Category(String id,String name,List<Offer> offers) {
        public Category { offers=List.copyOf(offers); }
    }
    public static JawaSettings read(ConfigurationSection config) {
        if (config.getInt("schema-version") != 1) throw new IllegalArgumentException("mobs/jawas.yml: expected schema 1");
        var worlds = config.getStringList("spawning.worlds");
        int interval=config.getInt("spawning.interval-seconds"), cap=config.getInt("spawning.world-cap"),
                minimum=config.getInt("spawning.group-min"), maximum=config.getInt("spawning.group-max"),
                near=config.getInt("spawning.min-distance"), far=config.getInt("spawning.max-distance"),
                lifetime=config.getInt("spawning.lifetime-seconds");
        if (worlds.isEmpty() || worlds.stream().anyMatch(w -> !w.matches("[a-z0-9_-]+:[a-z0-9_/-]+"))
                || interval<10 || interval>600 || minimum<1 || maximum<minimum || maximum>8 || cap<maximum || cap>64
                || near<16 || far<near+4 || far>128 || lifetime<60 || lifetime>7200)
            throw new IllegalArgumentException("mobs/jawas.yml: invalid population or shop bounds");
        var supplies=offers(config,"shop.supplies"); var purchases=offers(config,"shop.purchases");
        if (supplies.isEmpty() || purchases.isEmpty() || purchases.size()>28) throw new IllegalArgumentException("mobs/jawas.yml: invalid offer count");
        var definitions=Objects.requireNonNull(config.getConfigurationSection("shop.categories"),"mobs/jawas.yml: categories required");
        var byId=new HashMap<String,Offer>();for(var offer:supplies)byId.put(offer.id(),offer);
        var assigned=new HashSet<String>();var categories=new ArrayList<Category>();
        for(String id:definitions.getKeys(false)) {
            var row=Objects.requireNonNull(definitions.getConfigurationSection(id));var ids=row.getStringList("items");
            String name=row.getString("name","");
            if(!id.matches("[a-z0-9_]+") || name.isBlank() || name.length()>32 || ids.isEmpty() || ids.size()>28)
                throw new IllegalArgumentException("mobs/jawas.yml: invalid category "+id);
            var members=new ArrayList<Offer>();
            for(String item:ids) {
                if(!byId.containsKey(item) || !assigned.add(item))throw new IllegalArgumentException("mobs/jawas.yml: unknown or duplicate category offer "+item);
                members.add(byId.get(item));
            }
            categories.add(new Category(id,name,members));
        }
        if(categories.isEmpty() || !assigned.equals(byId.keySet()))throw new IllegalArgumentException("mobs/jawas.yml: every supply must belong to exactly one category");
        if(maximum>categories.size())throw new IllegalArgumentException("mobs/jawas.yml: group-max exceeds category count; each group member needs a different category");
        int offers=categories.stream().mapToInt(c->c.offers().size()).max().orElseThrow();
        return new JawaSettings(List.copyOf(worlds),interval,cap,minimum,maximum,near,far,lifetime,offers,supplies,purchases,List.copyOf(categories));
    }
    private static List<Offer> offers(ConfigurationSection root,String path) {
        var section=Objects.requireNonNull(root.getConfigurationSection(path),path); var result=new ArrayList<Offer>();
        for(String key:section.getKeys(false)) {
            var row=Objects.requireNonNull(section.getConfigurationSection(key)); String item=row.getString("item","");
            int count=row.getInt("amount"); long price=CreditLedger.amount(row.getString("price",""));
            if(!key.matches("[a-z0-9_]+") || !item.matches("[a-z0-9_:]+") || count<1 || count>64 || price<1)
                throw new IllegalArgumentException(path+"."+key+": invalid offer");
            result.add(new Offer(key,item,count,price));
        }
        return List.copyOf(result);
    }
    public Category category(long seed) { return categories.get(new Random(seed).nextInt(categories.size())); }
    public Category category(String id) { return categories.stream().filter(c->c.id().equals(id)).findFirst().orElse(null); }
    public List<Offer> stock(long seed) {
        return category(seed).offers();
    }
}
