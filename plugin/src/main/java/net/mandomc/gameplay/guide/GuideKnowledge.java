package net.mandomc.gameplay.guide;

import java.util.*;
import net.mandomc.gameplay.items.RecipeSearch;
import org.bukkit.configuration.ConfigurationSection;

/** Only explicitly maintained public server facts are eligible context; arbitrary files are never retrieved. */
public final class GuideKnowledge {
    public record Note(String id,String tags,String facts,String fallback) { }
    private final List<Note> notes;
    public GuideKnowledge(ConfigurationSection config){
        var rows=new ArrayList<Note>();for(String id:config.getKeys(false)){
            var row=Objects.requireNonNull(config.getConfigurationSection(id));
            var note=new Note(id,row.getString("tags",""),row.getString("facts",""),row.getString("answer",""));
            if(note.tags().isBlank() || note.facts().isBlank() || note.fallback().isBlank())throw new IllegalArgumentException("Empty droid knowledge "+id);rows.add(note);
        }notes=List.copyOf(rows);
    }
    public List<Note> find(String question){
        var terms=Arrays.stream(RecipeSearch.normalize(question).split(" +"))
                .filter(t->t.length()>2 && !Set.of("how","what","where","when","does","can","the","and","for","with","that","this","have","are","get","please","tell","about","your").contains(t)).distinct().toList();
        return notes.stream().filter(n->score(n,terms)>0).sorted(Comparator.comparingInt((Note n)->score(n,terms)).reversed()).limit(3).toList();
    }
    private int score(Note note,List<String> terms){
        var tags=Set.of(RecipeSearch.normalize(note.tags()).split(" +"));return (int)terms.stream().filter(tags::contains).count();
    }
}
