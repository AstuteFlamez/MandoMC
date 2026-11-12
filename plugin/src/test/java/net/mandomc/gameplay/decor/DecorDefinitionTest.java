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
    @Test public void forwardOffsetUsesImmutableCaptureOnce(){
        var yaml=YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(getClass().getResourceAsStream("/decor/placements.yml"),java.nio.charset.StandardCharsets.UTF_8));
        var data=yaml.getMapList("placements").stream().filter(m->"point3_boba_fett".equals(m.get("id"))).findFirst().orElseThrow();
        var first=DecorDefinition.read(data);var second=DecorDefinition.read(data);
        assertEquals(first,second);
        assertEquals(13.699999988079071-Math.sin(Math.toRadians(92.54987))*.1875,first.x(),1e-12);
        assertEquals(20.836308565903398+Math.cos(Math.toRadians(92.54987))*.1875,first.z(),1e-12);
        assertEquals(93,first.y(),0);assertEquals(2.1386168f,first.pitch(),0);
        assertEquals(13.699999988079071,((Number)((Map<?,?>)data.get("location")).get("x")).doubleValue(),0);
    }
    @Test public void nonfiniteOrOutOfContractDataIsRejected(){
        var d=definitions().getFirst();
        assertThrows(IllegalArgumentException.class,()->copy(d,Double.NaN,1,List.of()));
        assertThrows(IllegalArgumentException.class,()->copy(d,1,-1,List.of()));
        assertThrows(IllegalArgumentException.class,()->copy(d,1,1,List.of("not a key")));
    }
    private DecorDefinition copy(DecorDefinition d,double x,double scale,List<String> sounds){return new DecorDefinition(d.id(),d.name(),d.type(),d.model(),scale,d.mode(),d.openDoors(),d.world(),x,d.y(),d.z(),d.yaw(),d.pitch(),d.animations(),sounds,d.dialogue());}
}
