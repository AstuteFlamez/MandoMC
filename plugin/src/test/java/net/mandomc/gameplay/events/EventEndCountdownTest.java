package net.mandomc.gameplay.events;

import java.lang.reflect.Proxy;
import java.time.ZonedDateTime;
import java.util.*;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventEndCountdownTest {
    private static final class Event implements ManagedEvent {
        boolean running=true;long ticks=Long.MAX_VALUE;Set<UUID> audience=new HashSet<>();
        public String id(){return "test";}public String displayName(){return "Jabba's Palace";}
        public void start(){}public void tick(){}public void stop(boolean announce){running=false;}
        public boolean running(){return running;}public boolean relevant(Player p){return audience.contains(p.getUniqueId());}
        public long endingInTicks(){return ticks;}
    }
    private record Viewer(UUID id,Player player,List<Title> messages){}
    private Viewer viewer(){
        var id=UUID.randomUUID();var out=new ArrayList<Title>();
        var player=(Player)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){
            case "getUniqueId"->id;case "isOnline"->true;case "isDead"->false;
            case "showTitle"->{out.add((Title)a[0]);yield null;}
            default->throw new UnsupportedOperationException(m.getName());
        });
        return new Viewer(id,player,out);
    }
    private EventEndCountdown countdown(){return new EventEndCountdown(EventEndCountdown.DEFAULT_TITLE,EventEndCountdown.DEFAULT_SUBTITLE);}
    @Test public void scheduledDeadlineRoundsUpAndDoesNotWrapAtExactStop(){
        var t=ZonedDateTime.parse("2026-11-09T14:54:50-06:00[America/Chicago]");
        assertEquals(10,EventEndCountdown.secondsUntilStop(t,55));
        assertEquals(1,EventEndCountdown.secondsUntilStop(t.plusSeconds(9).plusNanos(900000000),55));
        assertEquals(0,EventEndCountdown.secondsUntilStop(t.plusSeconds(10),55));
        assertEquals(3599,EventEndCountdown.secondsUntilStop(t.plusSeconds(11),55));
        var fold=ZonedDateTime.parse("2026-11-01T01:55:01-05:00[America/Chicago]");
        assertEquals(3599,EventEndCountdown.secondsUntilStop(fold,55));
    }
    @Test public void earliestKnownDeadlineWins(){
        assertEquals(5,EventEndCountdown.number(5,200));assertEquals(10,EventEndCountdown.number(100,200));
        assertEquals(0,EventEndCountdown.number(Long.MAX_VALUE,201));assertEquals(1,EventEndCountdown.number(Long.MAX_VALUE,1));
        assertEquals(0,EventEndCountdown.number(0,200));assertEquals(0,EventEndCountdown.number(Long.MAX_VALUE,Long.MAX_VALUE));
    }
    @Test public void sendsEveryNumberOnceOnlyToRelevantPlayers(){
        var e=new Event();var a=viewer();var other=viewer();e.audience.add(a.id);var c=countdown();
        for(int n=10;n>=1;n--){assertEquals(1,c.send(e,n,List.of(a.player,other.player)));assertEquals(0,c.send(e,n,List.of(a.player,other.player)));assertEquals("Event ending in "+n,PlainTextComponentSerializer.plainText().serialize(a.messages.getLast().title()));}
        assertEquals(10,a.messages.size());assertTrue(other.messages.isEmpty());assertEquals(0,c.send(e,0,List.of(a.player)));
        e.running=false;assertEquals(0,c.send(e,10,List.of(a.player)));e.running=true;assertEquals(1,c.send(e,10,List.of(a.player)));
    }
    @Test public void joiningLateGetsCurrentNumberWithoutReplayingMissedSeconds(){
        var e=new Event();var a=viewer();var b=viewer();e.audience.add(a.id);var c=countdown();
        c.send(e,10,List.of(a.player));c.send(e,6,List.of(a.player));e.audience.add(b.id);
        assertEquals(1,c.send(e,6,List.of(a.player,b.player)));assertEquals(1,b.messages.size());
        assertEquals("Event ending in 6",PlainTextComponentSerializer.plainText().serialize(b.messages.getFirst().title()));
        assertEquals(2,a.messages.size());
    }
    @Test public void kothExposesItsOwnTimeoutEvenWithoutScheduler() throws Exception {
        var config=YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(getClass().getResourceAsStream("/events/koth.yml")));
        var koth=new KothEvent(null,config);var running=KothEvent.class.getDeclaredField("running");running.setAccessible(true);running.set(koth,true);
        var age=KothEvent.class.getDeclaredField("age");age.setAccessible(true);age.set(koth,koth.settings().maximumTicks()-200);
        assertEquals(200,koth.endingInTicks());assertEquals(10,EventEndCountdown.number(Long.MAX_VALUE,koth.endingInTicks()));
    }
}
