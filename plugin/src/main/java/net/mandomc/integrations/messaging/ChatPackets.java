package net.mandomc.integrations.messaging;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResourcePackSend;
import com.github.retrooper.packetevents.wrapper.configuration.server.WrapperConfigServerResourcePackSend;
import net.kyori.adventure.text.Component;
import net.mandomc.gameplay.messaging.ChatStyle;
import net.mandomc.gameplay.messaging.MessageCatalog;
import net.mandomc.gameplay.messaging.ChatCategory;

/** Opt-in presentation of known server system-chat packets. No Bukkit state is read on Netty threads. */
public final class ChatPackets extends PacketListenerAbstract implements AutoCloseable {
    private final ChatStyle style;
    private final MessageCatalog catalog;
    private final Set<UUID> packs;
    private final Component prompt;
    private final Map<UUID,Long> ammoNotices=new ConcurrentHashMap<>();
    public ChatPackets(ChatStyle style,MessageCatalog catalog,Set<UUID> packs,Component prompt){
        super(PacketListenerPriority.HIGH);this.style=style;this.catalog=catalog;this.packs=Set.copyOf(packs);this.prompt=prompt;
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }
    @Override public void onPacketSend(PacketSendEvent event){
        if(event.isCancelled())return;
        if(event.getPacketType()==PacketType.Play.Server.SYSTEM_CHAT_MESSAGE){
            var packet=new WrapperPlayServerSystemChatMessage(event);
            if(packet.isOverlay()||ChatStyle.formatted(packet.getMessage()))return;
            if(packet.getMessage() instanceof net.kyori.adventure.text.TranslatableComponent nativeMessage
                    &&nativeMessage.key().equals("chat.type.admin")){
                packet.setMessage(style.format(packet.getMessage(),ChatCategory.ADMIN));event.markForReEncode(true);return;
            }
            var match=catalog.match(packet.getMessage());
            if(match==null)return;
            if(match.ammo()&&event.getUser()!=null&&event.getUser().getUUID()!=null){
                long now=System.nanoTime();Long before=ammoNotices.get(event.getUser().getUUID());
                if(before!=null&&now-before<2_000_000_000L){event.setCancelled(true);return;}
                ammoNotices.put(event.getUser().getUUID(),now);
            }
            packet.setMessage(style.format(packet.getMessage(),match.category()));event.markForReEncode(true);
        }else if(event.getPacketType()==PacketType.Play.Server.RESOURCE_PACK_SEND){
            var packet=new WrapperPlayServerResourcePackSend(event);
            if(packs.contains(packet.getPackId())){packet.setPrompt(prompt);event.markForReEncode(true);}
        }else if(event.getPacketType()==PacketType.Configuration.Server.RESOURCE_PACK_SEND){
            var packet=new WrapperConfigServerResourcePackSend(event);
            if(packs.contains(packet.getPackId())){packet.setPrompt(prompt);event.markForReEncode(true);}
        }
    }
    public void forget(UUID player){ammoNotices.remove(player);}
    @Override public void close(){PacketEvents.getAPI().getEventManager().unregisterListener(this);ammoNotices.clear();}
}
