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
        var values=definitions();assertEquals(12,values.size());assertEquals(12,values.stream().map(DecorDefinition::id).distinct().count());
        assertEquals(9,values.stream().filter(d->!d.id().startsWith("brown_jawa_")).count());
        var uwing=values.stream().filter(d->d.type().equals("u_wing")).findFirst().orElseThrow();
        assertTrue(uwing.openDoors());assertEquals("landing",uwing.mode());assertEquals(11.695954251385313,uwing.x(),0);assertEquals(18.749952f,uwing.yaw(),0);
        assertEquals(8,values.stream().filter(DecorDefinition::npc).count());
        for(var value:values)if(value.npc()){assertTrue(value.dialogue().size()>=3);assertTrue(value.voices().isEmpty());assertEquals(List.of("idle"),value.animations());}
    }
    @Test public void brownTrioRetainsSavedCenterAndSceneryOnlyContract(){
        var trio=definitions().stream().filter(d->d.id().startsWith("brown_jawa_")).toList();assertEquals(3,trio.size());
        for(var d:trio){assertEquals("minecraft:spawnworld",d.world());assertEquals(93,d.y(),0);assertEquals("jawa_brown",d.model());
            assertEquals(1.5,Math.hypot(d.x()+4.216087537153815,d.z()-33.4909486110643),1e-10);assertTrue(d.voices().isEmpty());assertTrue(d.npc());}
        for(int i=0;i<3;i++)for(int j=i+1;j<3;j++)assertEquals(Math.sqrt(6.75),Math.hypot(trio.get(i).x()-trio.get(j).x(),trio.get(i).z()-trio.get(j).z()),1e-10);
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
