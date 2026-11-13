package net.mandomc.gameplay.boba;

import java.io.File;
import net.mandomc.commands.RootCommand;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.integrations.regions.SaberProtection;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** Small additive bootstrap boundary, also used by the exact-live-JAR release overlay. */
public final class BobaEquipmentFeature implements AutoCloseable {
    private final BobaEquipment equipment;
    private final JetpackEquipmentMenu menu;
    private BobaEquipmentFeature(BobaEquipment equipment, JetpackEquipmentMenu menu) {
        this.equipment=equipment;this.menu=menu;
    }
    /** Must run before the existing catalog reload. Existing operator item definitions are preserved. */
    public static void installDefinitions(JavaPlugin plugin) {
        String path="items/definitions/boba-equipment.yml";
        if(!new File(plugin.getDataFolder(),path).isFile())plugin.saveResource(path,false);
    }
    /** Must run before the existing Jabba config load; preserves every unrelated runtime setting. */
    public static void enable(JavaPlugin plugin,ItemCatalog items,RootCommand root,ConfigurationSection language)
            throws Exception {
        if(plugin.getServer().getServicesManager().getRegistration(BobaEquipmentFeature.class)!=null)
            throw new IllegalStateException("Boba equipment already initialized");
        if(!items.ids().containsAll(java.util.Set.of(BobaEquipment.JETPACK,BobaEquipment.FLAMETHROWER)))
            throw new IllegalStateException("Boba equipment item definitions did not load");
        String path="equipment/boba.yml";
        var file=new File(plugin.getDataFolder(),path);
        if(!file.isFile())plugin.saveResource(path,false);
        var config=new YamlConfiguration();config.load(file);
        var protection=new SaberProtection();
        var equipment=new BobaEquipment(plugin,items,BobaEquipmentSettings.read(config),
                language.getConfigurationSection("boba-equipment"),protection::allowed);
        JetpackEquipmentMenu menu=null;
        try {
            menu=new JetpackEquipmentMenu(plugin,items,equipment,language.getConfigurationSection("boba-equipment"));
            menu.register(root);
            var dropsFile=new File(plugin.getDataFolder(),"mobs/drops/jabba.yml");
            if(dropsFile.isFile()) {
                var drops=new YamlConfiguration();drops.load(dropsFile);
                if(!drops.isConfigurationSection("drops.BobaFett"))throw new IllegalStateException("Missing Boba loot section");
                boolean changed=false;
                for(String id:new String[]{BobaEquipment.JETPACK,BobaEquipment.FLAMETHROWER}) {
                    String key="drops.BobaFett.equipment."+id;
                    if(!drops.contains(key)) {drops.set(key,id.equals(BobaEquipment.JETPACK)?.10:.15);changed=true;}
                }
                if(changed)drops.save(dropsFile);
            }
            plugin.getServer().getServicesManager().register(BobaEquipmentFeature.class,
                    new BobaEquipmentFeature(equipment,menu),plugin,ServicePriority.Normal);
        } catch(Exception | LinkageError error) {
            if(menu!=null)menu.close();equipment.close();throw error;
        }
    }
    public static void disable(JavaPlugin plugin) {
        var service=plugin.getServer().getServicesManager().getRegistration(BobaEquipmentFeature.class);
        if(service!=null&&service.getPlugin()==plugin) {
            service.getProvider().close();
            plugin.getServer().getServicesManager().unregister(BobaEquipmentFeature.class,service.getProvider());
        }
    }
    public BobaEquipment equipment() {return equipment;}
    @Override public void close() {menu.close();equipment.close();}
}
