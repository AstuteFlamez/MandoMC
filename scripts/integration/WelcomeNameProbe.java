package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.*;
import net.mandomc.gameplay.messaging.*;
import net.mandomc.gameplay.ranks.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable native check; never changes persisted rank membership or real players. */
public final class WelcomeNameProbe extends JavaPlugin {
    private int checks;
    private NativeProbePlayers actors;
    private void check(boolean condition,String label){if(!condition)throw new AssertionError(label);checks++;}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{run();Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),"WELCOME PASSED: "+checks+" native assertions\n");getLogger().info("WELCOME PASSED: "+checks+" native assertions");}
        catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"WELCOME FAILED",error);}
        finally{if(actors!=null)actors.close();if(!Files.exists(getDataFolder().toPath().resolve("keep-running")))Bukkit.shutdown();}
    },100);}
    @SuppressWarnings("unchecked") private void run()throws Exception{
        var plugin=(net.mandomc.bootstrap.MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        var service=Bukkit.getServicesManager().load(MessageService.class);check(service!=null,"Message owner initialized");
        actors=new NativeProbePlayers("WelcomeProbe");
        var ranked=actors.create(plugin.warps().destination("spawn"));var normal=actors.create(plugin.warps().destination("spawn"));
        var ranks=plugin.ranks();var field=RankService.class.getDeclaredField("users");field.setAccessible(true);
        var original=(Map<UUID,Set<String>>)field.get(ranks);var users=new HashMap<>(original);users.put(ranked.getUniqueId(),Set.of("mandostudios"));field.set(ranks,Map.copyOf(users));
        try{
            verify(service,ranks,ranked,true);verify(service,ranks,normal,false);
            var click=ClickEvent.suggestCommand("/msg Nick ");var hover=HoverEvent.showText(Component.text("Player details"));
            var nickname=Component.text("<red>Nick <player>",NamedTextColor.GOLD).clickEvent(click).hoverEvent(hover);
            ranked.displayName(nickname);
            var event=new PlayerJoinEvent(ranked,Component.text("Original join"));service.joined(event);
            check(ChatStyle.plain(event.joinMessage()).contains("<red>Nick <player>"),"Nickname markup stays literal, including placeholder-like text");
            check(contains(event.joinMessage(),nickname),"Nickname color/click/hover component preserved exactly");
            var hidden=new PlayerJoinEvent(normal,(Component)null);service.joined(hidden);check(hidden.joinMessage()==null,"Suppressed join retained");
            for(var player:List.of(ranked,normal)){
                var played=player.getClass().getDeclaredField("hasPlayedBefore");played.setAccessible(true);played.setBoolean(player,true);check(player.hasPlayedBefore(),"Native returning-player flag set");
                var reconnect=new PlayerJoinEvent(player,Component.text("Reconnect "+player.getName()));service.joined(reconnect);
                check(!ChatStyle.plain(reconnect.joinMessage()).contains("Welcome "),"Reconnect has no repeated welcome");
                check(ChatStyle.plain(reconnect.joinMessage()).equals(ChatStyle.PREFIX+"Reconnect "+player.getName()),"Returning join formatted once");
            }
            var body=Component.text("<red>literal");var signed=net.kyori.adventure.chat.SignedMessage.system("<red>literal",body);
            var viewers=new HashSet<net.kyori.adventure.audience.Audience>();viewers.add(normal);
            var chat=new io.papermc.paper.event.player.AsyncChatEvent(false,ranked,viewers,io.papermc.paper.chat.ChatRenderer.defaultRenderer(),body,body,signed);
            ranks.chat(chat);var rendered=chat.renderer().render(ranked,nickname,chat.message(),normal);
            check(chat.signedMessage()==signed&&chat.message()==body&&chat.viewers().equals(Set.of(normal)),"Signed message, original component and recipients unchanged");
            check(contains(rendered,nickname),"Normal chat rich nickname unchanged");
            check(count(ChatStyle.plain(rendered),ranks.rank(ranked).icon())==1&&!ChatStyle.plain(rendered).contains(ChatStyle.PREFIX),"Normal ranked chat has one icon and no system prefix");
        }finally{field.set(ranks,original);}
    }
    private void verify(MessageService service,RankService ranks,Player player,boolean ranked)throws Exception{
        check(!player.hasPlayedBefore(),"Fresh native player detected");
        actors.drain(player);var event=new PlayerJoinEvent(player,Component.text("Original join"));service.joined(event);
        var result=event.joinMessage();var expected=PlayerChat.displayName(ranks.rank(player),player.displayName());
        check(ChatStyle.formatted(result)&&count(ChatStyle.plain(result),ChatStyle.PREFIX)==1,"Single MandoMC system prefix");
        check(count(ChatStyle.plain(result),"Welcome ")==1,"One in-place welcome");
        check(contains(result,expected),"Authoritative display-name component survives styling exactly");
        check(contains(result,player.displayName().colorIfAbsent(ranks.rank(player).color())),"Existing rank name color retained");
        check(count(ChatStyle.plain(result),ranks.rank(player).icon())==(ranked?1:0),"Ranked icon once; default omits icon");
        check(ChatStyle.plain(result).contains("/tutorial"),"Existing welcome guidance retained");
        check(actors.drain(player).isEmpty(),"Handler sends no extra broadcast/private message");
    }
    private boolean contains(Component root,Component target){return root.equals(target)||root.children().stream().anyMatch(child->contains(child,target));}
    private int count(String text,String needle){return (text.length()-text.replace(needle,"").length())/needle.length();}
}
