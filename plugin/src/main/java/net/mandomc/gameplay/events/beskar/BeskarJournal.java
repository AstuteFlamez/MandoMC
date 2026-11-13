package net.mandomc.gameplay.events.beskar;

import java.nio.file.*;
import java.util.*;
import net.mandomc.config.AtomicData;
import org.bukkit.configuration.file.YamlConfiguration;
import net.mandomc.gameplay.events.beskar.MineSurvey.Pos;

/** Durable intent before world changes. Chunk generation markers make cleanup crash-idempotent. */
public final class BeskarJournal {
    public record Node(Pos position,String original,boolean mined) {
        public Node mined(boolean value){return new Node(position,original,value);}
    }
    public record State(String world,String generation,List<Node> nodes) {
        public State {nodes=List.copyOf(nodes);}
    }
    private final Path path;
    public BeskarJournal(Path path){this.path=path;}
    public State load()throws Exception {
        if(!Files.exists(path))return null;
        var c=new YamlConfiguration();c.load(path.toFile());
        if(c.getInt("schema-version")!=1)throw new IllegalArgumentException("Invalid Beskar recovery journal");
        var nodes=new ArrayList<Node>();
        for(var line:c.getStringList("nodes")){
            var a=line.split("\\|",3);var xyz=a[0].split(",");
            nodes.add(new Node(new Pos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2])),a[1],Boolean.parseBoolean(a[2])));
        }
        if(nodes.size()!=25||nodes.stream().map(Node::position).distinct().count()!=25)throw new IllegalArgumentException("Beskar journal must contain100 distinct hosts");
        return new State(Objects.requireNonNull(c.getString("world")),Objects.requireNonNull(c.getString("generation")),nodes);
    }
    public void save(State state)throws Exception {
        var c=new YamlConfiguration();c.set("schema-version",1);c.set("world",state.world);c.set("generation",state.generation);
        c.set("nodes",state.nodes.stream().map(n->n.position.x()+","+n.position.y()+","+n.position.z()+"|"+n.original+"|"+n.mined).toList());
        AtomicData.write(path,c.saveToString());
    }
}
