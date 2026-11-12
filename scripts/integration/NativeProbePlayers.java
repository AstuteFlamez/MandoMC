package net.mandomc.verification;

import java.util.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;

/** Socket-free connected native actors; used only by disposable full-runtime fixtures. */
public final class NativeProbePlayers implements AutoCloseable {
    private final List<Player> players=new ArrayList<>();
    private final Map<UUID,Object> handles=new HashMap<>();
    private final Map<UUID,EmbeddedChannel> channels=new HashMap<>();
    private final Map<UUID,Object> online;
    private final Map<String,Object> names;
    private final List<Object> serverPlayers;
    private final String prefix;
    private final Map<UUID,List<String>> otherPackets=new HashMap<>();
    public NativeProbePlayers()throws Exception{this("ChatProbe");}
    @SuppressWarnings("unchecked") public NativeProbePlayers(String prefix)throws Exception{
        this.prefix=prefix;
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object list=server.getClass().getMethod("getPlayerList").invoke(server);
        var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);
        var nameField=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByName");nameField.setAccessible(true);names=(Map<String,Object>)nameField.get(list);
        serverPlayers=(List<Object>)list.getClass().getMethod("getPlayers").invoke(list);
    }
    public Player create(Location location)throws Exception{
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer()),level=location.getWorld().getClass().getMethod("getHandle").invoke(location.getWorld());
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),prefix+players.size());
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class.forName("net.minecraft.world.entity.Entity").getField("valid").setBoolean(handle,true);
        Class<?> flow=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionType=Class.forName("net.minecraft.network.Connection");
        Object connection=connectionType.getConstructor(flow).newInstance(flow.getField("SERVERBOUND").get(null));
        var channel=new EmbeddedChannel();var channelField=connectionType.getDeclaredField("channel");channelField.setAccessible(true);channelField.set(connection,channel);
        Class<?> cookieType=Class.forName("net.minecraft.server.network.CommonListenerCookie"),listenerType=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object cookie=cookieType.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Object listener=listenerType.getConstructor(serverClass,connectionType,playerClass,cookieType).newInstance(server,connection,handle,cookie);
        playerClass.getField("connection").set(handle,listener);
        for(String name:List.of("clientLoadedTimeoutTimer","waitingForRespawn")){
            var field=listenerType.getDeclaredField(name);field.setAccessible(true);if(field.getType()==int.class)field.setInt(listener,0);else field.setBoolean(listener,false);
        }
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,location.getX(),location.getY(),location.getZ());
        var player=(Player)playerClass.getMethod("getBukkitEntity").invoke(handle);players.add(player);handles.put(player.getUniqueId(),handle);channels.put(player.getUniqueId(),channel);
        online.put(player.getUniqueId(),handle);names.put(player.getName().toLowerCase(Locale.ROOT),handle);serverPlayers.add(handle);level.getClass().getMethod("addNewPlayer",playerClass).invoke(level,handle);
        player.setGravity(false);player.setGameMode(GameMode.ADVENTURE);return player;
    }
    public List<Component> drain(Player player)throws Exception{
        channels.get(player.getUniqueId()).runPendingTasks();
        var out=new ArrayList<Component>();Object packet;
        while((packet=channels.get(player.getUniqueId()).readOutbound())!=null){
            if(!packet.getClass().getName().equals("net.minecraft.network.protocol.game.ClientboundSystemChatPacket")){
                otherPackets.computeIfAbsent(player.getUniqueId(),id->new ArrayList<>()).add(packet.getClass().getName());continue;
            }
            if((boolean)packet.getClass().getMethod("overlay").invoke(packet))continue;
            Object content=packet.getClass().getMethod("content").invoke(packet);
            out.add((Component)Class.forName("io.papermc.paper.adventure.PaperAdventure").getMethod("asAdventure",Class.forName("net.minecraft.network.chat.Component")).invoke(null,content));
        }
        return out;
    }
    public List<String> otherPackets(Player player){return otherPackets.remove(player.getUniqueId());}
    @SuppressWarnings("unchecked") @Override public void close(){
        for(var player:players)try{
            Bukkit.getPluginManager().callEvent(new org.bukkit.event.player.PlayerQuitEvent(player,(Component)null));
            player.closeInventory();Object handle=handles.get(player.getUniqueId());online.remove(player.getUniqueId());names.remove(player.getName().toLowerCase(Locale.ROOT));serverPlayers.remove(handle);
            for(var world:Bukkit.getWorlds()){Object level=world.getClass().getMethod("getHandle").invoke(world);((List<Object>)level.getClass().getMethod("players").invoke(level)).remove(handle);}
            handle.getClass().getMethod("discard").invoke(handle);
            var channel=channels.get(player.getUniqueId());Object queued;
            while((queued=channel.readOutbound())!=null)io.netty.util.ReferenceCountUtil.release(queued);
            if(channel.isOpen())channel.close();
        }catch(Exception error){throw new IllegalStateException("Native actor cleanup failed",error);}
        players.clear();handles.clear();channels.clear();
    }
}
