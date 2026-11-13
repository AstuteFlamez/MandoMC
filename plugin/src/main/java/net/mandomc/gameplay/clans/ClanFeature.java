package net.mandomc.gameplay.clans;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.crates.CrateService;
import net.mandomc.gameplay.territory.TerritoryService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

/** Command-owned lifecycle permits a scoped deployment without replacing the shared bootstrap. */
public final class ClanFeature implements AutoCloseable {
    private final MandoPlugin plugin;
    private final ClanService clans;
    private final FrozenLandPreparation preparation;
    private final ClanCommands commands;
    private final BukkitTask startup;
    private CrateService ownedCrates;
    private boolean closed;

    public ClanFeature(MandoPlugin plugin) throws Exception {
        this.plugin=plugin;
        // Ilum's deferred startup reads this merged file and can become the sole crate owner first.
        prepareCrates();
        var territory=Objects.requireNonNull(Bukkit.getServicesManager().load(TerritoryService.class),
                "Native territory service must start before clans");
        clans=new ClanService(plugin,territory,read("clans/settings.yml"),read("language/clans.yml"));
        preparation=new FrozenLandPreparation(plugin,clans);
        commands=new ClanCommands(plugin,clans,plugin.travel());
        startup=Bukkit.getScheduler().runTask(plugin,()->{
            try {
                plugin.items().reload();
                var crates=Bukkit.getServicesManager().load(CrateService.class);
                if(crates==null) {
                    ownedCrates=new CrateService(plugin,plugin.items(),read("crates/crates.yml"),
                            player->player.getPersistentDataContainer().has(new NamespacedKey("mandomc","ilum_inventory")),
                            plugin.combat()::tagged);
                    Bukkit.getServicesManager().register(CrateService.class,ownedCrates,plugin,ServicePriority.Normal);
                    crates=ownedCrates;
                }
                if(!crates.definitions().containsKey("koth"))throw new IllegalStateException("Shared crate service lacks KOTH");
                plugin.getLogger().info("Native clans ready: four members, four claims, one-hour raids and shared KOTH crate.");
            }catch(Exception error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Clan crate startup failed",error);}
        });
    }
    private YamlConfiguration read(String path)throws Exception {
        var file=new File(plugin.getDataFolder(),path);
        if(!file.isFile())plugin.saveResource(path,false);
        var yaml=new YamlConfiguration();yaml.load(file);
        try(var reader=new InputStreamReader(Objects.requireNonNull(plugin.getResource(path)),StandardCharsets.UTF_8)) {
            yaml.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        return yaml;
    }
    private void prepareCrates()throws Exception {
        if(!new File(plugin.getDataFolder(),"items/definitions/koth.yml").isFile())
            plugin.saveResource("items/definitions/koth.yml",false);
        var config=read("crates/crates.yml");
        // contains(...,true) checks the operator file, excluding the bundled fallback definition.
        if(!config.contains("crates.koth",true)) {
            try(var reader=new InputStreamReader(Objects.requireNonNull(plugin.getResource("crates/crates.yml")),StandardCharsets.UTF_8)) {
                var defaults=YamlConfiguration.loadConfiguration(reader);
                for(var entry:Objects.requireNonNull(defaults.getConfigurationSection("crates.koth")).getValues(true).entrySet())
                    if(!(entry.getValue() instanceof ConfigurationSection))config.set("crates.koth."+entry.getKey(),entry.getValue());
            }
            config.save(new File(plugin.getDataFolder(),"crates/crates.yml"));
        }
    }
    @Override public void close() {
        if(closed)return;closed=true;startup.cancel();commands.close();preparation.close();clans.close();
        if(ownedCrates!=null) {
            Bukkit.getServicesManager().unregister(CrateService.class,ownedCrates);ownedCrates.close();ownedCrates=null;
        }
    }
}
