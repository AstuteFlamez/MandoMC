package net.mandomc.gameplay.decor;

import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class DecorDefinitionTest {
    private List<DecorDefinition> definitions(){
        var config=YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(getClass().getResourceAsStream("/decor/placements.yml"),java.nio.charset.StandardCharsets.UTF_8));
        return config.getMapList("placements").stream().map(DecorDefinition::read).toList();
    }
    @Test public void requestedModesAndExactCapturedFacingAreRetained(){
        var values=definitions();assertEquals(9,values.size());assertEquals(9,values.stream().map(DecorDefinition::id).distinct().count());
        var uwing=values.stream().filter(d->d.type().equals("u_wing")).findFirst().orElseThrow();
        assertTrue(uwing.openDoors());assertEquals("landing",uwing.mode());assertEquals(11.695954251385313,uwing.x(),0);assertEquals(18.749952f,uwing.yaw(),0);
        assertEquals(5,values.stream().filter(DecorDefinition::npc).count());
        for(var value:values)if(value.npc()){assertTrue(value.dialogue().size()>=3);assertTrue(value.voices().size()>=2);assertEquals(List.of("idle"),value.animations());}
    }
    @Test public void nonfiniteOrOutOfContractDataIsRejected(){
        var d=definitions().getFirst();
        assertThrows(IllegalArgumentException.class,()->copy(d,Double.NaN,1,List.of()));
        assertThrows(IllegalArgumentException.class,()->copy(d,1,-1,List.of()));
        assertThrows(IllegalArgumentException.class,()->copy(d,1,1,List.of("not a key")));
    }
    private DecorDefinition copy(DecorDefinition d,double x,double scale,List<String> sounds){return new DecorDefinition(d.id(),d.name(),d.type(),d.model(),scale,d.mode(),d.openDoors(),d.world(),x,d.y(),d.z(),d.yaw(),d.pitch(),d.animations(),sounds,d.dialogue());}
}
