package net.mandomc.gameplay.ilum;

import java.io.File;
import java.util.List;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.crates.CrateService;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

/** Owned by command binding lifecycle; leaves the shared deployment bootstrap byte-identical. */
public final class IlumFeature implements AutoCloseable {
    private final MandoPlugin plugin;
    private final BukkitTask startup;
    private CrateService crates;
    private ParkourService parkour;

    public IlumFeature(MandoPlugin plugin) {
        this.plugin=plugin;
        // Wait for the existing bootstrap's worlds, catalog, arrival and public travel to finish.
        startup=Bukkit.getScheduler().runTask(plugin,()->{
            try {
                for(String resource:List.of("crates/crates.yml","parkour/ilum.yml","items/definitions/crates.yml"))
                    if(!new File(plugin.getDataFolder(),resource).isFile())plugin.saveResource(resource,false);
                plugin.items().reload();
                crates=new CrateService(plugin,plugin.items(),read("crates/crates.yml"),
                        player->ParkourInventory.stored(player),plugin.combat()::tagged);
                Bukkit.getServicesManager().register(CrateService.class,crates,plugin,ServicePriority.Normal);
                var config=read("parkour/ilum.yml");
                if(config.getBoolean("enabled",false)){
                    parkour=new ParkourService(plugin,crates,config);
                    Bukkit.getServicesManager().register(ParkourService.class,parkour,plugin,ServicePriority.Normal);
                }
                plugin.getLogger().info("Native crates ready; original Ilum parkour "+(parkour==null?"disabled":"ready"));
            }catch(Exception error){
                plugin.getLogger().log(java.util.logging.Level.SEVERE,"Ilum/crates unavailable; existing gameplay retained",error);
                close();
            }
        });
    }
    private YamlConfiguration read(String path)throws Exception{
        var config=new YamlConfiguration();config.load(new File(plugin.getDataFolder(),path));
        try(var reader=new java.io.InputStreamReader(java.util.Objects.requireNonNull(plugin.getResource(path)),java.nio.charset.StandardCharsets.UTF_8)){
            config.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        return config;
    }
    @Override public void close(){
        startup.cancel();
        if(parkour!=null){Bukkit.getServicesManager().unregister(ParkourService.class,parkour);parkour.close();parkour=null;}
        if(crates!=null){Bukkit.getServicesManager().unregister(CrateService.class,crates);crates.close();crates=null;}
    }
}
