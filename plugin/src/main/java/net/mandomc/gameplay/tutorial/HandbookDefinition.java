package net.mandomc.gameplay.tutorial;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;

public record HandbookDefinition(String title,String author,List<Page> pages,List<String> lore) {
    public record Page(String id,String topic,String heading,String body) {}
    public HandbookDefinition {pages=List.copyOf(pages);lore=List.copyOf(lore);}
    public static HandbookDefinition read(ConfigurationSection config) {
        if(config.getInt("schema-version",-1)!=1)throw new IllegalArgumentException("language/handbook.yml: expected schema-version 1");
        String title=config.getString("title","");String author=config.getString("author","");
        if(title.isBlank()||title.length()>32||author.isBlank()||author.length()>16)
            throw new IllegalArgumentException("language/handbook.yml: invalid book title/author");
        var pages=new ArrayList<Page>();var ids=new HashSet<String>();
        for(var entry:config.getMapList("pages")) {
            String id=required(entry,"id"),topic=required(entry,"topic"),heading=required(entry,"heading"),body=required(entry,"body");
            if(!id.matches("[a-z0-9_-]+")||!ids.add(id))throw new IllegalArgumentException("language/handbook.yml: duplicate/invalid feature "+id);
            BookPageLayout.validate(heading,body);pages.add(new Page(id,topic,heading,body));
        }
        int expected=config.getInt("page-count",-1);
        if(expected<1||expected>100||pages.size()!=expected)
            throw new IllegalArgumentException("language/handbook.yml: page-count must match 1..100 feature pages");
        return new HandbookDefinition(title,author,pages,config.getStringList("lore"));
    }
    private static String required(Map<?,?> map,String key) {
        if(!(map.get(key) instanceof String value)||value.isBlank())
            throw new IllegalArgumentException("language/handbook.yml: missing page "+key);
        return value.strip();
    }
}
