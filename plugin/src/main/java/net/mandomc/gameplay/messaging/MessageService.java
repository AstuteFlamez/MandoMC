package net.mandomc.gameplay.messaging;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Owns chat presentation and coalesced lifecycle notices, never permissions or gameplay outcomes. */
public final class MessageService implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final YamlConfiguration settings;
    private final ChatStyle style = new ChatStyle();
    private final MessageCatalog catalog;
    private final Set<UUID> packIds;
    private final Map<UUID,Set<UUID>> loaded = new HashMap<>();
    private final Set<UUID> packNotice = new HashSet<>();
    private final net.mandomc.integrations.messaging.ChatPackets packets;

    public MessageService(JavaPlugin plugin) throws Exception {
        this.plugin=plugin;
        var file=new File(plugin.getDataFolder(),"language/chat-style.yml");
        if(!file.exists())plugin.saveResource("language/chat-style.yml",false);
        settings=new YamlConfiguration();settings.load(file);
        if(settings.getInt("schema-version")!=1)throw new IllegalArgumentException("chat-style.yml: schema-version1 required");
        var saber=YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"combat/sabers.yml"));
        packIds=new HashSet<>();
        for(String path:new String[]{"upstream-pack.main-pack-id","upstream-pack.id","upstream-pack.base-pack.id"})
            packIds.add(UUID.fromString(java.util.Objects.requireNonNull(saber.getString(path),path)));
        catalog=new MessageCatalog(plugin,settings);
        packets=new net.mandomc.integrations.messaging.ChatPackets(style,catalog,packIds,notice("pack-prompt",ChatCategory.INFO));
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        plugin.getLogger().info("Chat presentation ready: "+catalog.size()+" explicit template rules; unknown messages retained.");
    }
    public ChatStyle style(){return style;}
    public Component known(String source,String path,Component input){return style.format(input,catalog.category(source,path));}
    public Set<UUID> packIds(){return Set.copyOf(packIds);}
    public Component notice(String key,ChatCategory category){
        return style.format(MiniMessage.miniMessage().deserialize(java.util.Objects.requireNonNull(settings.getString("messages."+key))),category);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void death(PlayerDeathEvent event){
        if(event.deathMessage()!=null)event.deathMessage(style.format(event.deathMessage(),ChatCategory.COMBAT));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void joined(PlayerJoinEvent event){
        if(!event.getPlayer().hasPlayedBefore()&&event.joinMessage()!=null){
            var player=event.getPlayer();
            var ranks=((net.mandomc.bootstrap.MandoPlugin)plugin).ranks();
            var name=PlayerChat.displayName(ranks.rank(player),player.displayName());
            // Style first; the neutral wrapper absorbs template style without changing the rich name.
            var welcome=MiniMessage.miniMessage().deserialize(settings.getString("messages.first-join"),
                    Placeholder.unparsed("player","<player>"));
            event.joinMessage(style.format(welcome,ChatCategory.SUCCESS)
                    .replaceText(replacement->replacement.matchLiteral("<player>").replacement(Component.empty().append(name))));
        }else if(event.joinMessage()!=null)event.joinMessage(lifecycle(event.joinMessage(),event.getPlayer()));
    }
    private Component lifecycle(Component original,org.bukkit.entity.Player player){
        var ranks=((net.mandomc.bootstrap.MandoPlugin)plugin).ranks();
        var name=PlayerChat.displayName(ranks.rank(player),player.displayName());
        return PlayerChat.lifecycleName(style.format(original,ChatCategory.STATUS),player.getName(),player.displayName(),name);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void packs(PlayerResourcePackStatusEvent event){
        if(!packIds.contains(event.getID())||packNotice.contains(event.getPlayer().getUniqueId()))return;
        UUID player=event.getPlayer().getUniqueId();
        switch(event.getStatus()){
            case SUCCESSFULLY_LOADED -> {
                loaded.computeIfAbsent(player,id->new HashSet<>()).add(event.getID());
                if(loaded.get(player).containsAll(packIds)){
                    packNotice.add(player);event.getPlayer().sendMessage(notice("pack-ready",ChatCategory.SUCCESS));
                }
            }
            case DECLINED, FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                packNotice.add(player);event.getPlayer().sendMessage(notice("pack-failed",ChatCategory.ERROR));
            }
            default -> { }
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event){
        if(event.quitMessage()!=null)event.quitMessage(lifecycle(event.quitMessage(),event.getPlayer()));
        loaded.remove(event.getPlayer().getUniqueId());packNotice.remove(event.getPlayer().getUniqueId());packets.forget(event.getPlayer().getUniqueId());
    }
    @Override public void close(){packets.close();HandlerList.unregisterAll(this);loaded.clear();packNotice.clear();}
}
