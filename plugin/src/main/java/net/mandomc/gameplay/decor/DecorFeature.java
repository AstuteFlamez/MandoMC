package net.mandomc.gameplay.decor;

import java.io.File;
import net.mandomc.bootstrap.MandoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

/** Starts after the existing world/territory/model owners; supplies one typed scenery service. */
public final class DecorFeature implements AutoCloseable {
    private final MandoPlugin plugin;
    private final BukkitTask startup;
    private DecorService service;
    public DecorFeature(MandoPlugin plugin){
        this.plugin=plugin;
        startup=Bukkit.getScheduler().runTask(plugin,()->{
            try{
                if(Bukkit.getServicesManager().load(DecorService.class)!=null){plugin.getLogger().warning("Spawn decor already has an owner; duplicate startup ignored.");return;}
                var file=new File(plugin.getDataFolder(),"decor/placements.yml");if(!file.exists())plugin.saveResource("decor/placements.yml",false);
                var config=new YamlConfiguration();config.load(file);if(!config.getBoolean("enabled",false))return;
                service=new DecorService(plugin,config);Bukkit.getServicesManager().register(DecorService.class,service,plugin,ServicePriority.Normal);
            }catch(Exception error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Spawn decoration unavailable; existing gameplay owners retained",error);}
        });
        if(Bukkit.getServicesManager().load(DecorFeature.class)==null)Bukkit.getServicesManager().register(DecorFeature.class,this,plugin,ServicePriority.Normal);
    }
    public static void start(MandoPlugin plugin){if(Bukkit.getServicesManager().load(DecorFeature.class)==null)new DecorFeature(plugin);}
    public static void stop(){var owner=Bukkit.getServicesManager().load(DecorFeature.class);if(owner!=null)owner.close();}
    @Override public void close(){startup.cancel();if(service!=null){Bukkit.getServicesManager().unregister(DecorService.class,service);service.close();service=null;}Bukkit.getServicesManager().unregister(DecorFeature.class,this);}
}
