package net.mandomc.verification;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.gameplay.clans.*;
import net.mandomc.gameplay.events.*;
import net.mandomc.gameplay.territory.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** A standalone disposable Paper fixture. Never install on the shared runtime. */
public final class KothPresentationProbe extends JavaPlugin implements Listener {
    private net.mandomc.gameplay.crates.CrateService crates;
    private ClanService clans;private TerritoryService territory;private KothEvent event;
    private final List<Actor> actors=new ArrayList<>();private int checks,broadcasts;
    private boolean cancel,removeThird;private World world;private Location hill;
    private ClanState.Clan own,enemy,third;
    private void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private String plain(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
    private TextColor color(Component c,String word,TextColor inherited){
        var actual=c.color()==null?inherited:c.color();
        if(c instanceof TextComponent t&&t.content().contains(word))return actual;
        for(var child:c.children()){var found=color(child,word,actual);if(found!=null)return found;}return null;
    }
    private YamlConfiguration resource(String path)throws Exception {
        try(var reader=new java.io.InputStreamReader(Objects.requireNonNull(getResource(path)),java.nio.charset.StandardCharsets.UTF_8)){
            return YamlConfiguration.loadConfiguration(reader);
        }
    }
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{verify();Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),"KOTH PRESENTATION PASSED: "+checks+" native assertions");
            getLogger().info("KOTH PRESENTATION PASSED: "+checks+" native assertions");}
        catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"KOTH PRESENTATION FAILED",e);
            try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),e.toString());}catch(Exception ignored){}}
        finally{cleanup();Bukkit.shutdown();}
    },20);}
    @EventHandler(priority=EventPriority.HIGHEST) public void broadcast(BroadcastMessageEvent e){
        broadcasts++;check(!plain(e.message()).contains("koth-contestant"),"Broadcast event sees real neutral name");
        if(cancel)e.setCancelled(true);
        if(removeThird)e.getRecipients().remove(actors.get(2).player);
        e.message(e.message().append(Component.text(" [listener]")));
    }
    private KothEvent fixture(boolean rewards)throws Exception {
        var config=resource("events/koth.yml");config.set("settings.world",world.getKey().toString());
        config.set("settings.x",hill.getX());config.set("settings.y",hill.getY());config.set("settings.z",hill.getZ());
        config.set("settings.capture-seconds",2);config.set("rewards.enabled",rewards);
        return new KothEvent(this,config);
    }
    private void verify()throws Exception {
        check(getServer().getIp().equals("127.0.0.1")&&getServer().getPort()==25591,"Disposable loopback server only");
        Files.createDirectories(getDataFolder().toPath());world=Bukkit.getWorlds().getFirst();hill=world.getSpawnLocation().clone();
        territory=new TerritoryService(this,resource("territory/maps.yml"),resource("language/territory.yml"));
        clans=new ClanService(this,territory,resource("clans/settings.yml"),resource("language/clans.yml"));
        var a=new Actor("Holder",true,hill);var b=new Actor("Raider",true,hill.clone().add(30,0,0));
        var c=new Actor("Other",true,hill.clone().add(30,0,0));var mate=new Actor("Clanmate",true,hill.clone().add(1,0,0));
        var denied=new Actor("Denied",false,hill.clone().add(30,0,0));
        own=clans.state().create("CanyonClan",a.id,System.currentTimeMillis());own.members.add(mate.id);
        enemy=clans.state().create("RaidClan",b.id,System.currentTimeMillis());third=clans.state().create("OtherClan",c.id,System.currentTimeMillis());
        for(int i=0;i<10;i++)clans.state().kill(enemy.id,own.id,System.currentTimeMillis());
        check(clans.state().breach(enemy.id,own.id,System.currentTimeMillis(),60000,10),"Real raid authority starts directional relationship");
        Bukkit.getPluginManager().registerEvents(this,this);
        event=fixture(false);event.start();clear();int before=broadcasts;
        event.sample(actors.stream().map(x->x.player).toList());
        check(broadcasts==before+1,"Single capture event regardless of viewer count");
        check(event.capture().ticks()==0&&event.capture().holder().equals(own.id.toString()),"Capture keeps stable clan identity and initial zero timer");
        for(var actor:actors)check(actor.shown==1&&actor.bar!=null,"Eligible viewers receive one bossbar");
        verifyNames(a,b,c,mate);
        check(a.messages.size()==1&&b.messages.size()==1&&c.messages.size()==1&&mate.messages.size()==1,"One chat per authorized viewer");
        check(denied.messages.isEmpty(),"Denied broadcast subscription receives no chat");
        check(plain(a.messages.getFirst()).equals("ᴍᴀɴᴅᴏᴍᴄ » CanyonClan is capturing the canyon! [listener]"),"Native capture text and listener suffix");
        check(NamedTextColor.YELLOW.equals(color(a.messages.getFirst(),"ᴍᴀɴᴅᴏᴍᴄ",null)),"Event prefix stays yellow");
        for(var actor:List.of(a,b,c,mate))check(NamedTextColor.GRAY.equals(color(actor.messages.getFirst(),"is capturing",null)),"Body stays gray independent of relationship");
        check(NamedTextColor.GREEN.equals(color(a.messages.getFirst(),"CanyonClan",null)),"Own chat name green");
        check(NamedTextColor.RED.equals(color(b.messages.getFirst(),"CanyonClan",null)),"Active attacker chat name red");
        check(NamedTextColor.BLUE.equals(color(c.messages.getFirst(),"CanyonClan",null)),"Other chat name blue");
        // Membership/raid updates are recomputed while a stationary holder continues capture.
        var pair=enemy.id.compareTo(own.id)<0?new ClanState.Pair(enemy.id,own.id):new ClanState.Pair(own.id,enemy.id);
        clans.state().rivalries.put(pair,new ClanState.Rivalry(0,enemy.id,System.currentTimeMillis()-1));
        a.at=hill.clone().add(30,0,0);event.sample(actors.stream().map(x->x.player).toList());
        check(event.capture().ticks()==10&&event.capture().holder().equals(own.id.toString()),"Clanmate retains capture after initial player leaves");
        check(NamedTextColor.BLUE.equals(color(b.bar.name(),"CanyonClan",null)),"Raid expiry updates stationary viewer label");
        own.members.add(c.id);third.members.remove(c.id);event.sample(actors.stream().map(x->x.player).toList());
        check(NamedTextColor.GREEN.equals(color(c.bar.name(),"CanyonClan",null)),"Membership change updates stationary viewer label");
        event.sample(actors.stream().map(x->x.player).toList());check(event.capture().ticks()==30,"Timer unchanged before completion");
        clear();before=broadcasts;event.sample(actors.stream().map(x->x.player).toList());
        check(!event.running()&&event.winner().equals(own.id),"Exact configured capture completes with stable clan ID");
        check(broadcasts==before+1&&a.messages.size()==1,"Exactly one winner announcement");
        check(plain(a.messages.getFirst()).equals("ᴍᴀɴᴅᴏᴍᴄ » CanyonClan captured the canyon! [listener]"),"Native victory text");
        for(var actor:actors)check(actor.hidden==1,"Completion hides every per-viewer bar");
        // The real crate service and payout path must still deliver exactly one saved key.
        for(String path:List.of("items/catalog.yml","items/definitions/legacy.yml","items/definitions/koth.yml"))saveResource(path,true);
        var items=new net.mandomc.gameplay.items.ItemCatalog(getDataFolder().toPath());items.reload();
        var crateConfig=resource("verification/koth-crates.yml");
        for(String id:List.copyOf(crateConfig.getConfigurationSection("crates").getKeys(false)))
            if(!id.equals("koth"))crateConfig.set("crates."+id,null);
        crateConfig.set("crates.koth.blocks",List.of());
        crates=new net.mandomc.gameplay.crates.CrateService(this,items,crateConfig,p->false,p->false);
        Bukkit.getServicesManager().register(net.mandomc.gameplay.crates.CrateService.class,crates,this,org.bukkit.plugin.ServicePriority.Normal);
        own.members.remove(c.id);third.members.add(c.id);
        clans.state().rivalries.put(pair,new ClanState.Rivalry(0,enemy.id,System.currentTimeMillis()+60000));
        a.at=hill;b.at=hill.clone().add(30,0,0);c.at=hill.clone().add(30,0,0);
        event=fixture(true);event.start();event.sample(actors.stream().map(x->x.player).toList());clear();before=broadcasts;
        for(int i=0;i<4;i++)event.sample(actors.stream().map(x->x.player).toList());
        check(!event.running()&&event.winner().equals(own.id),"Rewarded capture ends normally");
        check(broadcasts==before+1,"One public reward event after payout");
        check(a.messages.size()==1&&b.messages.size()==1&&c.messages.size()==1,"Exactly one reward chat per audience");
        int keys=0,saves=0;
        for(var actor:actors){keys+=crates.keys(actor.player,crates.definitions().get("koth"));saves+=actor.saves;}
        check(keys==1&&saves==1,"Exactly one virtual key and one player save");
        check(crates.keys(a.player,crates.definitions().get("koth"))+crates.keys(mate.player,crates.definitions().get("koth"))==1,
                "Key belongs to one eligible present winning member");
        check(NamedTextColor.GREEN.equals(color(a.messages.getFirst(),"CanyonClan",null)),"Reward own clan name green");
        check(NamedTextColor.RED.equals(color(b.messages.getFirst(),"CanyonClan",null)),"Reward raid viewer clan name red");
        check(NamedTextColor.BLUE.equals(color(c.messages.getFirst(),"CanyonClan",null)),"Reward other viewer clan name blue");
        check(plain(a.messages.getFirst()).matches("ᴍᴀɴᴅᴏᴍᴄ » CanyonClan won KOTH! (Holder|Clanmate) received one KOTH Crate Key. /crates open koth \\[listener\\]"),
                "Actual reward wording, clan and recipient");
        before=broadcasts;event.sample(actors.stream().map(x->x.player).toList());
        check(broadcasts==before,"Stopped event never repeats payout or broadcast");
        // Empty and contested snapshots, cancellation and audience edits use the actual KOTH path.
        event=fixture(false);event.start();clear();event.sample(List.of(b.player,c.player));
        check(plain(b.bar.name()).equals("KOTH — Hold the canyon!"),"Waiting bossbar stays in original channel style");
        b.at=hill;c.at=hill;own.members.remove(c.id);third.members.add(c.id);
        event.sample(List.of(b.player,c.player));
        check(plain(b.bar.name()).equals("KOTH — Clear the canyon to take control!"),"Contested label preserved without invented faction");
        cancel=true;event.updateCapture(Map.of(enemy.id.toString(),b.player));cancel=false;
        check(a.messages.isEmpty()&&b.messages.isEmpty(),"Actual BroadcastMessageEvent cancellation suppresses delivery");
        event.updateCapture(Map.of());removeThird=true;clear();event.updateCapture(Map.of(enemy.id.toString(),b.player));removeThird=false;
        check(c.messages.isEmpty()&&b.messages.size()==1,"Actual broadcast listener recipient removal honored");
        event.stop(false);
        // Unclanned holder uses its rich display name for both progress and capture.
        clans.state().remove(own);clans.state().remove(enemy);clans.state().remove(third);
        a.at=hill;b.at=hill.clone().add(30,0,0);event=fixture(false);event.start();clear();event.sample(List.of(a.player,b.player));
        check(plain(a.bar.name()).equals("KOTH — <Solo> 2s remaining"),"Solo display-name text used literally in native bar");
        check(NamedTextColor.LIGHT_PURPLE.equals(color(a.bar.name(),"<Solo>",null)),"Solo display-name style preserved in native bar");
        check(NamedTextColor.LIGHT_PURPLE.equals(color(a.messages.getFirst(),"<Solo>",null)),"Solo display-name style preserved in native chat");
        event.stop(false);
    }
    private void verifyNames(Actor a,Actor b,Actor c,Actor mate){
        for(var actor:List.of(a,b,c,mate)) {
            check(plain(actor.bar.name()).equals("KOTH — CanyonClan 2s remaining"),"Native progress label");
            check(actor.bar.color()==BossBar.Color.GREEN&&actor.bar.progress()==0,"Original bar style and progress retained");
            check(NamedTextColor.GOLD.equals(color(actor.bar.name(),"KOTH",null)),"Bossbar title retains gold");
        }
        check(a.bar!=b.bar&&b.bar!=c.bar,"Different native bar objects per viewer");
        check(NamedTextColor.GREEN.equals(color(a.bar.name(),"CanyonClan",null)),"Own bar name green");
        check(NamedTextColor.RED.equals(color(b.bar.name(),"CanyonClan",null)),"Raiding viewer bar name red");
        check(NamedTextColor.BLUE.equals(color(c.bar.name(),"CanyonClan",null)),"Other viewer bar name blue");
        check(NamedTextColor.GREEN.equals(color(mate.bar.name(),"CanyonClan",null)),"Clanmate bar name green");
    }
    private void clear(){actors.forEach(a->a.messages.clear());}
    private final class Actor {
        final UUID id=UUID.randomUUID();final Player player;final List<Component> messages=new ArrayList<>();
        final org.bukkit.persistence.PersistentDataContainer data=new org.bukkit.inventory.ItemStack(Material.STONE).getItemMeta().getPersistentDataContainer();
        Location at;BossBar bar;int shown,hidden,saves;
        Actor(String name,boolean permission,Location at){this.at=at;actors.add(this);
            player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(p,m,args)->switch(m.getName()){
                case "getUniqueId" -> id;case "getName" -> name;
                case "displayName" -> Component.text("<Solo>",NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD);
                case "getWorld" -> world;case "getLocation" -> this.at;
                case "isOnline" -> true;case "isDead","isInvulnerable" -> false;
                case "getMetadata" -> List.of();case "getGameMode" -> GameMode.SURVIVAL;
                case "showBossBar" -> {bar=(BossBar)args[0];shown++;yield null;}
                case "hideBossBar" -> {hidden++;yield null;}
                case "getPersistentDataContainer" -> data;case "saveData" -> {saves++;yield null;}
                case "hasPermission" -> permission;
                case "sendMessage" -> {if(args[0] instanceof Component component)messages.add(component);yield null;}
                case "hashCode" -> id.hashCode();case "equals" -> p==args[0];case "toString" -> name;
                default -> null;
            });
            Bukkit.getPluginManager().subscribeToPermission("bukkit.broadcast.user",player);
        }
    }
    private void cleanup(){
        if(event!=null)event.stop(false);
        for(var actor:actors)Bukkit.getPluginManager().unsubscribeFromPermission("bukkit.broadcast.user",actor.player);
        if(clans!=null){for(var clan:new ClanState.Clan[]{own,enemy,third})if(clan!=null)clans.state().remove(clan);clans.close();clans=null;}
        if(crates!=null){crates.close();crates=null;}
        if(territory!=null){territory.close();territory=null;}
    }
    @Override public void onDisable(){cleanup();}
}
