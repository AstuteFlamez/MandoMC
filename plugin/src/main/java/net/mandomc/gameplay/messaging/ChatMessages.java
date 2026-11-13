package net.mandomc.gameplay.messaging;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Explicit chat-only boundary for send helpers whose templates are also used in GUI components. */
public final class ChatMessages {
    private ChatMessages() {}
    public static void send(CommandSender audience,String source,String path,Component component){
        var owner=Bukkit.getServicesManager().load(MessageService.class);
        audience.sendMessage(owner!=null&&audience instanceof Player?owner.known(source,path,component):component);
    }
}
