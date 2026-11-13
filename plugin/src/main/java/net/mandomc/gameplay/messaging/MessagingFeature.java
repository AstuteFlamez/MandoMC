package net.mandomc.gameplay.messaging;

import java.io.File;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Single lifecycle owner; a presentation failure retains existing gameplay and original messages. */
public final class MessagingFeature implements AutoCloseable {
    private final JavaPlugin plugin;
    private MessageService messages;
    public MessagingFeature(JavaPlugin plugin){
        this.plugin=plugin;
        Bukkit.getServicesManager().register(MessagingFeature.class,this,plugin,ServicePriority.Normal);
        if(!new File(plugin.getDataFolder(),"language/chat-style.yml").exists())plugin.saveResource("language/chat-style.yml",false);
        if(!YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(),"language/chat-style.yml")).getBoolean("enabled",true))return;
        try{
            messages=new MessageService(plugin);
            Bukkit.getServicesManager().register(MessageService.class,messages,plugin,ServicePriority.Normal);
        }catch(Exception|LinkageError error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Chat presentation unavailable; original gameplay messages retained",error);}
    }
    public static void start(JavaPlugin plugin){if(Bukkit.getServicesManager().load(MessagingFeature.class)==null)new MessagingFeature(plugin);}
    public static void stop(){var owner=Bukkit.getServicesManager().load(MessagingFeature.class);if(owner!=null)owner.close();}
    @Override public void close(){
        Bukkit.getServicesManager().unregister(MessagingFeature.class,this);
        if(messages!=null){Bukkit.getServicesManager().unregister(MessageService.class,messages);messages.close();messages=null;}
    }
}
