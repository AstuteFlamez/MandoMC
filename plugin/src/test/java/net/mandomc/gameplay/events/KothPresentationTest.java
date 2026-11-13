package net.mandomc.gameplay.events;

import java.lang.reflect.Proxy;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.gameplay.clans.ClanState;
import net.mandomc.gameplay.messaging.ChatStyle;
import org.bukkit.command.CommandSender;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.permissions.Permissible;
import org.junit.Test;
import static org.junit.Assert.*;

public class KothPresentationTest {
    private final ClanState state=new ClanState();
    private final ClanState.Clan own=state.create("CanyonClan",UUID.randomUUID(),0);
    private final ClanState.Clan enemy=state.create("RaidClan",UUID.randomUUID(),0);
    private final ClanState.Clan other=state.create("OtherClan",UUID.randomUUID(),0);
    private Component name(ClanState.Clan viewer,long now) {
        return KothPresentation.name(own,viewer,state,Component.text("Solo"),now);
    }
    private void raid() {
        for(int i=0;i<10;i++)state.kill(enemy.id,own.id,100);
        assertTrue(state.breach(enemy.id,own.id,100,1000,10));
    }
    private String plain(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
    private List<TextColor> colors(Component c,String text,TextColor inherited) {
        var color=c.color()==null?inherited:c.color();var out=new ArrayList<TextColor>();
        if(c instanceof TextComponent t && t.content().contains(text))out.add(color);
        for(var child:c.children())out.addAll(colors(child,text,color));
        return out;
    }
    @Test public void actualRenderedCaptureVictoryRewardAndProgressKeepIndependentNameColors() {
        raid();
        for(var template:List.of("<yellow><player> is capturing the canyon!", "<green><player> captured the canyon!",
                "<gold><clan> won KOTH! <yellow><recipient> received one KOTH Crate Key. <gray>/crates open koth",
                "<gold>KOTH <gray>— <yellow><player> <white><seconds>s remaining")) {
            boolean chat=!template.contains("remaining");
            var skeleton=KothPresentation.template(template,179,"Winner",chat);
            for(var viewer:List.of(own,enemy,other)) {
                var result=KothPresentation.render(skeleton,name(viewer,101));
                assertEquals(List.of(viewer==own?NamedTextColor.GREEN:viewer==enemy?NamedTextColor.RED:NamedTextColor.BLUE),
                        colors(result,"CanyonClan",null));
                assertFalse(plain(result).contains("faction"));assertFalse(plain(result).contains("koth-contestant"));
                if(chat){assertTrue(ChatStyle.formatted(result));assertTrue(plain(result).startsWith("ᴍᴀɴᴅᴏᴍᴄ » "));
                    assertEquals(List.of(NamedTextColor.YELLOW),colors(result,"ᴍᴀɴᴅᴏᴍᴄ",null));}
                else{assertEquals("KOTH — CanyonClan 179s remaining",plain(result));
                    assertEquals(List.of(NamedTextColor.WHITE),colors(result,"179",null));}
            }
        }
    }
    @Test public void scoreAloneExpiryReverseRaidAndNoClanRemainBlue() {
        for(int i=0;i<10;i++)state.kill(enemy.id,own.id,100);
        assertEquals(NamedTextColor.BLUE,name(enemy,101).color());
        assertTrue(state.breach(enemy.id,own.id,100,1000,10));
        assertEquals(NamedTextColor.RED,name(enemy,101).color());
        assertEquals(NamedTextColor.BLUE,name(enemy,1100).color());
        assertEquals(NamedTextColor.BLUE,name(null,101).color());
        assertEquals(NamedTextColor.BLUE,KothPresentation.name(enemy,own,state,Component.empty(),101).color());
    }
    @Test public void soloDisplayNameRetainsRichStyleAndDoesNotParseText() {
        var solo=Component.text("<red>Solo",NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD)
                .hoverEvent(Component.text("Player hover"));
        var name=KothPresentation.name(null,null,null,solo,0);assertEquals(solo,name);
        var result=KothPresentation.render(KothPresentation.template("<yellow><player> is capturing!",180,"",true),name);
        assertEquals("ᴍᴀɴᴅᴏᴍᴄ » <red>Solo is capturing!",plain(result));
        assertEquals(List.of(NamedTextColor.LIGHT_PURPLE),colors(result,"Solo",null));
        assertTrue(contains(result,solo));
    }
    private boolean contains(Component root,Component part){return root.equals(part)||root.children().stream().anyMatch(c->contains(c,part));}
    private CommandSender sender(Class<?> type,boolean permission,List<Component> messages) {
        return (CommandSender)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{type},(p,m,a)->switch(m.getName()) {
            case "hasPermission" -> permission;
            case "sendMessage" -> {if(a[0] instanceof Component c)messages.add(c);yield null;}
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p==a[0];
            case "toString" -> "KothAudience";
            default -> null;
        });
    }
    @Test public void oneAudiencePassHonorsPermissionsCancellationAndListenerRecipientEdits() {
        raid();var a=new ArrayList<Component>();var b=new ArrayList<Component>();var c=new ArrayList<Component>();
        var log=new ArrayList<Component>();var deniedLog=new ArrayList<Component>();
        var first=(Player)sender(Player.class,true,a);var second=(Player)sender(Player.class,true,b);
        var third=(Player)sender(Player.class,true,c);var console=sender(CommandSender.class,true,log);
        var denied=sender(Player.class,false,deniedLog);var block=sender(BlockCommandSender.class,true,deniedLog);
        var audience=KothPresentation.recipients(Set.of(first,second,third,console,denied,block),"bukkit.broadcast.user");
        assertEquals(Set.of(first,second,third,console),audience);
        var template=KothPresentation.template("<yellow><player> is capturing!",180,"",true);
        var neutral=name(null,101);
        // The marker is preserved in the real event component, including listener appended content.
        var marked=neutral.insertion("mandomc:koth:contestant");
        var event=new BroadcastMessageEvent(false,KothPresentation.render(template,marked),audience);
        event.message(event.message().append(Component.text(" Listener suffix")));
        KothPresentation.deliver(event,p->name(p==first?own:p==second?enemy:other,101),neutral);
        assertEquals(1,a.size());assertEquals(1,b.size());assertEquals(1,c.size());assertEquals(1,log.size());
        assertTrue(deniedLog.isEmpty());assertTrue(plain(a.getFirst()).endsWith("Listener suffix"));
        assertEquals(List.of(NamedTextColor.GREEN),colors(a.getFirst(),"CanyonClan",null));
        assertEquals(List.of(NamedTextColor.RED),colors(b.getFirst(),"CanyonClan",null));
        assertEquals(List.of(NamedTextColor.BLUE),colors(c.getFirst(),"CanyonClan",null));
        event.setCancelled(true);KothPresentation.deliver(event,p->name(own,101),neutral);assertEquals(1,a.size());
        event.setCancelled(false);event.getRecipients().remove(first);
        event.message(Component.text("Listener replacement"));
        KothPresentation.deliver(event,p->name(own,101),neutral);
        assertEquals(1,a.size());assertEquals("Listener replacement",plain(b.getLast()));
    }
}
