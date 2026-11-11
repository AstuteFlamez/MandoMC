package net.mandomc.gameplay.jawa;

import java.util.*;
import net.mandomc.gameplay.credits.CreditLedger;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable offers and bounded population settings; prices represent one explicitly sized bundle. */
public record JawaSettings(List<String> worlds, int interval, int cap, int minimum, int maximum,
        int minDistance, int maxDistance, int lifetime, int offers, List<Offer> supplies, List<Offer> purchases) {
    public record Offer(String id, String item, int amount, long price) { }
    public static JawaSettings read(ConfigurationSection config) {
        if (config.getInt("schema-version") != 1) throw new IllegalArgumentException("mobs/jawas.yml: expected schema 1");
        var worlds = config.getStringList("spawning.worlds");
        int interval=config.getInt("spawning.interval-seconds"), cap=config.getInt("spawning.world-cap"),
                minimum=config.getInt("spawning.group-min"), maximum=config.getInt("spawning.group-max"),
                near=config.getInt("spawning.min-distance"), far=config.getInt("spawning.max-distance"),
                lifetime=config.getInt("spawning.lifetime-seconds"), offers=config.getInt("shop.offers-per-jawa");
        if (worlds.isEmpty() || worlds.stream().anyMatch(w -> !w.matches("[a-z0-9_-]+:[a-z0-9_/-]+"))
                || interval<10 || interval>600 || minimum<1 || maximum<minimum || maximum>8 || cap<maximum || cap>64
                || near<16 || far<near+4 || far>128 || lifetime<60 || lifetime>7200 || offers<1 || offers>28)
            throw new IllegalArgumentException("mobs/jawas.yml: invalid population or shop bounds");
        var supplies=offers(config,"shop.supplies"); var purchases=offers(config,"shop.purchases");
        if (supplies.size()<offers || purchases.isEmpty() || purchases.size()>28) throw new IllegalArgumentException("mobs/jawas.yml: invalid offer count");
        return new JawaSettings(List.copyOf(worlds),interval,cap,minimum,maximum,near,far,lifetime,offers,supplies,purchases);
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
    public List<Offer> stock(long seed) {
        var pool=new ArrayList<>(supplies); Collections.shuffle(pool,new Random(seed)); return List.copyOf(pool.subList(0,offers));
    }
}
