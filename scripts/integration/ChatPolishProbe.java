package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.packettype.*;
import com.github.retrooper.packetevents.protocol.player.*;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerResourcePackSend;
import io.netty.buffer.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.*;
import net.kyori.adventure.text.format.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mandomc.gameplay.messaging.*;
import net.mandomc.integrations.messaging.ChatPackets;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable native protocol fixture. No live players, inventories or gameplay records are used. */
public final class ChatPolishProbe extends JavaPlugin {
    private int checks;
    private MessageService messages;private ChatPackets packets;
    private final UUID profileId=UUID.randomUUID();
    private final List<String> samples=new ArrayList<>();
    private NativeProbePlayers actors;
    private org.bukkit.entity.Player alice,bob;
    private net.mandomc.bootstrap.MandoPlugin mando;
    private net.mandomc.gameplay.crates.CrateService crates;
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();if(new java.io.File(getDataFolder(),"live-only").exists())success();else beginNative();}catch(Throwable error){fail(error);}},100);}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;}
    private Component mm(String value){return MiniMessage.miniMessage().deserialize(value);}
    private void run()throws Exception{
        messages=Bukkit.getServicesManager().load(MessageService.class);check(messages!=null,"One live message owner initialized");
        var field=messages.getClass().getDeclaredField("packets");field.setAccessible(true);packets=(ChatPackets)field.get(messages);
        var combat=system(mm("<red>In combat. Logging out now drops your items.</red>"),false);
        colored(combat,ChatCategory.COMBAT,false);samples.add(ChatStyle.plain(combat));
        var denial=system(mm("<red>You do not have permission to do that."),false);colored(denial,ChatCategory.ERROR,false);
        var admin=system(mm("<green>Game mode changed for AstuteFlamez."),false);colored(admin,ChatCategory.ADMIN,true);samples.add(ChatStyle.plain(admin));
        var click=ClickEvent.runCommand("/warp");var hover=HoverEvent.showText(Component.text("Select a planet"));
        var rich=mm("<gray>Make room in your inventory first.").clickEvent(click).hoverEvent(hover);
        var full=system(rich,false);colored(full,ChatCategory.ERROR,false);check(events(full,click,hover),"Serialized hover and click actions preserved");
        var unchanged=system(Component.text("A third-party combat and credits message."),false);
        check(ChatStyle.plain(unchanged).equals("A third-party combat and credits message."),"Unknown messages stay unchanged despite keywords");
        var overlay=system(mm("<red>In combat. Logging out now drops your items.</red>"),true);
        check(ChatStyle.plain(overlay).equals("In combat. Logging out now drops your items."),"Known combat actionbar remains untouched");
        var oldAmmo=mm("<red><bold>ᴍᴀɴᴅᴏᴍᴄ</bold></red> <dark_gray>» <red>Out of plasma cells!");
        var ammo=event(new WrapperPlayServerSystemChatMessage(false,oldAmmo),ConnectionState.PLAY);
        packets.onPacketSend(ammo);check(!ammo.isCancelled(),"First genuine vendor ammo notice retained");
        var ammoMessage=((WrapperPlayServerSystemChatMessage)ammo.getLastUsedWrapper()).getMessage();
        colored(ammoMessage,ChatCategory.ERROR,false);check(ChatStyle.plain(ammoMessage).equals(ChatStyle.PREFIX+"Out of plasma cells!"),"Vendor prefix replaced exactly once");
        var repeated=event(new WrapperPlayServerSystemChatMessage(false,oldAmmo),ConnectionState.PLAY);packets.onPacketSend(repeated);check(repeated.isCancelled(),"Repeated empty-ammo notice throttled");
        var crate=system(mm("<aqua>Crate reward » </aqua><blue>Blue Kyber Crystal"),false);colored(crate,ChatCategory.SUCCESS,false);samples.add(ChatStyle.plain(crate));
        for(UUID id:messages.packIds()){
            var push=new WrapperPlayServerResourcePackSend(id,"https://example.test/pinned.zip","0123456789012345678901234567890123456789",true,Component.text("Original prompt"));
            var event=event(push,ConnectionState.PLAY);packets.onPacketSend(event);
            var changed=(WrapperPlayServerResourcePackSend)event.getLastUsedWrapper();
            check(changed.getPackId().equals(id)&&changed.isRequired()&&changed.getHash().equals(push.getHash())&&changed.getUrl().equals(push.getUrl()),"Play pack identity/hash/URL/required policy retained");
            colored(changed.getPrompt(),ChatCategory.INFO,false);
        }
        var initial=new WrapperConfigServerResourcePackSend(messages.packIds().iterator().next(),"https://example.test/pack.zip","0123456789012345678901234567890123456789",true,null);
        var configEvent=event(initial,ConnectionState.CONFIGURATION);packets.onPacketSend(configEvent);
        var configPush=(WrapperConfigServerResourcePackSend)configEvent.getLastUsedWrapper();
        check(configPush.isRequired()&&configPush.getUrl().equals(initial.getUrl())&&configPush.getHash().equals(initial.getHash()),"Initial configuration pack policy retained");colored(configPush.getPrompt(),ChatCategory.INFO,false);
        var other=new WrapperPlayServerResourcePackSend(UUID.randomUUID(),"https://example.test/other.zip","0123456789012345678901234567890123456789",false,Component.text("Third-party prompt"));
        var otherEvent=event(other,ConnectionState.PLAY);packets.onPacketSend(otherEvent);
        check(((WrapperPlayServerResourcePackSend)otherEvent.getLastUsedWrapper()).getPrompt().equals(other.getPrompt()),"Unowned resource packs untouched");
        var nativeAdmin=system(Component.translatable("chat.type.admin",Component.text("Console"),Component.text("Internal result")),false);colored(nativeAdmin,ChatCategory.ADMIN,true);
        check(nativeAdmin.children().get(1) instanceof TranslatableComponent,"Native staff translation and arguments retained");
    }
    private void beginNative()throws Exception{
        mando=(net.mandomc.bootstrap.MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        actors=new NativeProbePlayers();alice=actors.create(mando.warps().destination("spawn"));bob=actors.create(mando.warps().destination("spawn").add(1,0,0));
        var permissions=alice.addAttachment(this);
        for(String permission:List.of("mandomc.admin.gamemode","mandomc.admin.others","mandomc.credits.admin","mandomc.credits.balance","mandomc.credits.pay"))permissions.setPermission(permission,true);
        check(!bob.hasPermission("mandomc.admin.gamemode"),"Nonstaff actor lacks admin command permission");
        var welcome=new org.bukkit.event.player.PlayerJoinEvent(alice,Component.text("Original join"));messages.joined(welcome);
        check(ChatStyle.plain(welcome.joinMessage()).contains("Welcome "+alice.getName()+" to MandoMC!"),"First join replaces the native join with one welcome");
        colored(welcome.joinMessage(),ChatCategory.SUCCESS,false);
        var hidden=new org.bukkit.event.player.PlayerJoinEvent(bob,(Component)null);messages.joined(hidden);check(hidden.joinMessage()==null,"Suppressed join stays suppressed");
        actors.drain(alice);actors.drain(bob);
        var ids=new ArrayList<>(messages.packIds());
        for(int i=0;i<ids.size()-1;i++)messages.packs(new org.bukkit.event.player.PlayerResourcePackStatusEvent(alice,ids.get(i),org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        check(actors.drain(alice).isEmpty(),"No pack success spam before all three packs load");
        messages.packs(new org.bukkit.event.player.PlayerResourcePackStatusEvent(alice,ids.getLast(),org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        var ready=actors.drain(alice);check(ready.size()==1,"Exactly one pack-ready message");colored(ready.getFirst(),ChatCategory.SUCCESS,false);
        messages.packs(new org.bukkit.event.player.PlayerResourcePackStatusEvent(alice,ids.getLast(),org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));check(actors.drain(alice).isEmpty(),"Duplicate success status stays quiet");
        messages.packs(new org.bukkit.event.player.PlayerResourcePackStatusEvent(bob,ids.getFirst(),org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD));
        var failed=actors.drain(bob);check(failed.size()==1,"One pack failure notice for its owner");colored(failed.getFirst(),ChatCategory.ERROR,false);
        messages.packs(new org.bukkit.event.player.PlayerResourcePackStatusEvent(bob,ids.getLast(),org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD));check(actors.drain(bob).isEmpty(),"Repeated pack failures stay quiet");
        mando.warps().open(alice);var gui=alice.getOpenInventory().getTopInventory();
        check(!ChatStyle.plain(gui.getItem(20).getItemMeta().displayName()).contains(ChatStyle.PREFIX),"Planet GUI label remains unprefixed");alice.closeInventory();
        Bukkit.dispatchCommand(alice,"gm1 "+bob.getName());
        var admin=actors.drain(alice);check(admin.size()==1,"Admin response remains private to staff sender");check(ChatStyle.plain(admin.getFirst()).contains("Game mode changed"),"Native admin command succeeded: "+ChatStyle.plain(admin.getFirst()));colored(admin.getFirst(),ChatCategory.ADMIN,true);check(actors.drain(bob).isEmpty(),"Target is not sent admin-only feedback");
        var mode=bob.getGameMode();Bukkit.dispatchCommand(bob,"gm0");var denied=actors.drain(bob);
        check(bob.getGameMode()==mode,"Native command tree does not grant nonstaff a mode change");
        if(denied.isEmpty()){
            var command=mando.getCommand("gm0");command.getExecutor().onCommand(bob,command,"gm0",new String[0]);denied=actors.drain(bob);
        }
        check(denied.size()==1,"Existing plugin permission branch returns one denial");colored(denied.getFirst(),ChatCategory.ERROR,false);
        samples.add("Native denied: "+ChatStyle.plain(denied.getFirst()));
        Bukkit.dispatchCommand(alice,"credits give "+alice.getName()+" 10.00");later(10,this::afterCredits);
    }
    private void afterCredits()throws Exception{
        var adjusted=actors.drain(alice);check(adjusted.size()==1,"Credit adjustment response sent only to administrator");colored(adjusted.getFirst(),ChatCategory.ADMIN,true);check(actors.drain(bob).isEmpty(),"No administrative economy leak");
        Bukkit.dispatchCommand(alice,"balance");later(10,this::afterBalance);
    }
    private void afterBalance()throws Exception{
        var balance=actors.drain(alice);check(balance.size()==1,"Native balance response");colored(balance.getFirst(),ChatCategory.ECONOMY,false);check(ChatStyle.plain(balance.getFirst()).contains("$10.00"),"Currency text and value retained");
        Bukkit.dispatchCommand(alice,"pay "+bob.getName()+" 2.00");later(10,this::afterPayment);
    }
    private void afterPayment()throws Exception{
        var payer=actors.drain(alice);var recipient=actors.drain(bob);check(payer.size()==1&&recipient.size()==1,"Payment keeps its exact sender and recipient audiences");colored(payer.getFirst(),ChatCategory.ECONOMY,false);colored(recipient.getFirst(),ChatCategory.ECONOMY,false);
        crates=Bukkit.getServicesManager().load(net.mandomc.gameplay.crates.CrateService.class);crates.enqueueKey(alice,"kyber",2);alice.saveData();crates.claim(alice);actors.drain(alice);
        var key=alice.getInventory().getContents();org.bukkit.inventory.ItemStack actual=null;
        for(var item:key)if("ilum_kyber_crate_key".equals(mando.items().identify(item)))actual=item.clone();
        check(actual!=null,"Native crate key delivered");
        alice.getInventory().clear();for(int slot=0;slot<36;slot++)alice.getInventory().setItem(slot,new org.bukkit.inventory.ItemStack(Material.STONE,64));alice.getInventory().setItem(0,actual);
        var menuField=crates.getClass().getDeclaredField("menus");menuField.setAccessible(true);
        getLogger().info("Full-inventory fixture state: menu="+((Map<?,?>)menuField.get(crates)).get(alice.getUniqueId())+" pending="+alice.getPersistentDataContainer().has(net.mandomc.gameplay.crates.CrateService.PENDING)+" keys="+alice.getPersistentDataContainer().getKeys()+" title="+ChatStyle.plain(alice.getOpenInventory().title()));
        crates.open(alice,crates.definitions().get("kyber"));later(3,this::afterFullInventory);
    }
    private void afterFullInventory()throws Exception{
        var full=actors.drain(alice).stream().filter(c->ChatStyle.plain(c).contains("Free an inventory slot")).toList();check(full.size()==1,"Full inventory emits one denial; count="+full.size()+" messages="+full.stream().map(ChatStyle::plain).toList()+" online="+alice.isOnline()+" keys="+crates.keys(alice,crates.definitions().get("kyber"))+" other="+actors.otherPackets(alice));colored(system(full.getFirst(),false),ChatCategory.ERROR,false);
        check(crates.keys(alice,crates.definitions().get("kyber"))==2,"Inventory denial does not spend key");
        alice.getInventory().setItem(1,null);crates.open(alice,crates.definitions().get("kyber"));
        var event=new org.bukkit.event.inventory.InventoryClickEvent(alice.getOpenInventory(),org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,22,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"Native crate skip click protected");later(4,this::afterCrate);
    }
    private void afterCrate()throws Exception{
        var rewards=actors.drain(alice);var reward=rewards.stream().filter(c->ChatStyle.plain(c).contains("Crate reward")).findFirst().orElseThrow();colored(system(reward,false),ChatCategory.SUCCESS,false);
        check(actors.drain(bob).isEmpty(),"Crate reward remains private to its recipient");
        var original=Component.translatable("death.attack.player",Component.text(bob.getName()),Component.text(alice.getName()));
        var death=new org.bukkit.event.entity.PlayerDeathEvent(bob,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withCausingEntity(alice).withDirectEntity(alice).build(),new ArrayList<>(),0,original,true);
        messages.death(death);check(death.deathMessage()!=null&&ChatStyle.formatted(death.deathMessage()),"Native death event replaced in place");colored(death.deathMessage(),ChatCategory.COMBAT,false);
        check(death.deathMessage().children().get(1) instanceof TranslatableComponent,"Death cause and named arguments retained");check(actors.drain(alice).isEmpty()&&actors.drain(bob).isEmpty(),"Death handler adds no duplicate broadcast");
        death.deathMessage(null);messages.death(death);check(death.deathMessage()==null,"Suppressed death remains suppressed");
        playerChat();success();
    }
    private void playerChat(){
        var body=Component.text("<red><Name> $5");
        var signed=net.kyori.adventure.chat.SignedMessage.system("<red><Name> $5",body);
        var viewers=new HashSet<net.kyori.adventure.audience.Audience>();viewers.add(bob);
        var event=new io.papermc.paper.event.player.AsyncChatEvent(false,bob,viewers,io.papermc.paper.chat.ChatRenderer.defaultRenderer(),body,body,signed);
        Bukkit.getPluginManager().callEvent(event);
        var name=Component.text("Nickname",NamedTextColor.GOLD).clickEvent(ClickEvent.suggestCommand("/msg Nickname "));
        var result=event.renderer().render(bob,name,event.message(),bob);
        check(ChatStyle.plain(result).equals("Nickname » <red><Name> $5"),"Actual native unranked renderer removes brackets and keeps literal body");
        check(event.viewers().equals(Set.of(bob))&&event.signedMessage()==signed&&event.message()==body,"Recipients, signed-message identity and message components unchanged");
        check(result.children().getFirst().equals(name),"Nickname color and click action retained");
        var cancelled=new io.papermc.paper.event.player.AsyncChatEvent(false,bob,viewers,io.papermc.paper.chat.ChatRenderer.defaultRenderer(),body,body,signed);cancelled.setCancelled(true);
        var renderer=cancelled.renderer();Bukkit.getPluginManager().callEvent(cancelled);check(cancelled.isCancelled()&&cancelled.renderer()==renderer,"Cancelled chat retains prior owner and no duplicate send");
    }
    private interface Check{void run()throws Exception;}
    private void later(long ticks,Check action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){fail(error);}},ticks);}
    private Component system(Component input,boolean overlay)throws Exception{
        var event=event(new WrapperPlayServerSystemChatMessage(overlay,input),ConnectionState.PLAY);packets.onPacketSend(event);
        check(!event.isCancelled(),"Non-ammo system chat was not suppressed");
        return ((WrapperPlayServerSystemChatMessage)event.getLastUsedWrapper()).getMessage();
    }
    private PacketSendEvent event(PacketWrapper<?> wrapper,ConnectionState state)throws Exception{
        var version=PacketEvents.getAPI().getServerManager().getVersion();
        wrapper.setServerVersion(version);wrapper.setClientVersion(version.toClientVersion());wrapper.setBuffer(Unpooled.buffer());wrapper.write();
        ((ByteBuf)wrapper.getBuffer()).readerIndex(0);
        var channel=new EmbeddedChannel();var user=new User(channel,state,version.toClientVersion(),new UserProfile(profileId,"ChatProbe"));
        var type=wrapper instanceof WrapperPlayServerSystemChatMessage?PacketType.Play.Server.SYSTEM_CHAT_MESSAGE
                :state==ConnectionState.CONFIGURATION?PacketType.Configuration.Server.RESOURCE_PACK_SEND:PacketType.Play.Server.RESOURCE_PACK_SEND;
        return new TestSendEvent(type,user,channel,wrapper.getBuffer());
    }
    private static final class TestSendEvent extends PacketSendEvent{
        TestSendEvent(PacketTypeCommon type,User user,Object channel,Object buffer){
            super(-1,type,PacketEvents.getAPI().getServerManager().getVersion(),channel,user,null,buffer);markForReEncode(false);
        }
    }
    private void colored(Component message,ChatCategory category,boolean admin){
        check(ChatStyle.formatted(message),"Formatted component has one owned prefix");
        check(message.children().getFirst().color().equals(category.color()),"Category color "+category);
        var brand=message.children().getFirst();var separator=brand.children().getFirst();
        check(brand.font().equals(net.kyori.adventure.key.Key.key("minecraft:default"))&&brand.decoration(TextDecoration.BOLD)==TextDecoration.State.TRUE,"Scoreboard smallcap bold brand uses default font");
        check(separator instanceof TextComponent text&&text.content().equals(" » ")&&separator.color().equals(NamedTextColor.DARK_GRAY)
                &&separator.decoration(TextDecoration.BOLD)==TextDecoration.State.FALSE,"Special double-chevron separator independently dark gray");
        if(admin)effective(message,null,TextDecoration.State.NOT_SET);
        else check(message.children().getFirst().decoration(TextDecoration.ITALIC)==TextDecoration.State.FALSE,"Ordinary message prefix non-italic");
    }
    private void effective(Component node,TextColor inherited,TextDecoration.State inheritedItalic){
        var color=node.color()==null?inherited:node.color();
        var italic=node.decoration(TextDecoration.ITALIC)==TextDecoration.State.NOT_SET?inheritedItalic:node.decoration(TextDecoration.ITALIC);
        if(node instanceof TextComponent text&&!text.content().isEmpty())check(text.content().equals(" » ")?color.equals(NamedTextColor.DARK_GRAY)&&italic==TextDecoration.State.FALSE:color.equals(NamedTextColor.RED)&&italic==TextDecoration.State.TRUE,"Staff body/brand red italic; separator dark gray");
        for(var child:node.children())effective(child,color,italic);
        if(node instanceof TranslatableComponent translated)for(var arg:translated.arguments())if(arg.value() instanceof Component child)effective(child,color,italic);
    }
    private boolean events(Component node,ClickEvent click,HoverEvent<?> hover){return click.equals(node.clickEvent())&&hover.equals(node.hoverEvent())||node.children().stream().anyMatch(c->events(c,click,hover));}
    private void success()throws Exception{
        if(actors!=null){actors.close();actors=null;}
        Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),"CHAT POLISH PASSED: "+checks+" native assertions\n");
        Files.write(getDataFolder().toPath().resolve("samples.txt"),samples);getLogger().info("CHAT POLISH PASSED: "+checks+" native assertions");
        if(!new java.io.File(getDataFolder(),"live-only").exists()&&!new java.io.File(getDataFolder(),"keep-running").exists())Bukkit.shutdown();
    }
    private void fail(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"CHAT POLISH FAILED",error);try{if(actors!=null){actors.close();actors=null;}Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());}catch(Exception cleanup){getLogger().warning("Probe cleanup: "+cleanup);}if(!new java.io.File(getDataFolder(),"live-only").exists()&&!new java.io.File(getDataFolder(),"keep-running").exists())Bukkit.shutdown();}
}
