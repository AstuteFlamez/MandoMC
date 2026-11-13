import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import net.mandomc.gameplay.boba.BobaEquipmentSettings;
import net.mandomc.gameplay.mobs.JabbaDropsSettings;

/** Fast staged-release parsing and preservation checks. Does not start, stop or change any runtime. */
public final class BobaReleasePreflight {
    static YamlConfiguration yaml(ZipFile z,String name)throws Exception {
        var c=new YamlConfiguration();
        c.loadFromString(new String(z.getInputStream(z.getEntry(name)).readAllBytes(),StandardCharsets.UTF_8));return c;
    }
    public static void main(String[] args)throws Exception {
        try(var base=new ZipFile(args[0]);var candidate=new ZipFile(args[1])) {
            var drops=yaml(candidate,"mobs/drops/jabba.yml");var original=yaml(base,"mobs/drops/jabba.yml");
            for(var entry:original.getValues(true).entrySet())if(!(entry.getValue() instanceof ConfigurationSection))
                if(!Objects.equals(entry.getValue(),drops.get(entry.getKey())))throw new AssertionError("Old loot changed: "+entry.getKey());
            var settings=JabbaDropsSettings.read(drops);
            if(settings.mobs().get("BobaFett").extras().size()!=2)throw new AssertionError("Missing equipment loot");
            if(settings.mobs().containsKey("JawaBrown")&&settings.mobs().get("JawaBrown").salvage().pool().isEmpty())
                throw new AssertionError("Jawa salvage lost");
            var plugin=yaml(candidate,"plugin.yml");
            if(!plugin.getBoolean("permissions.mandomc.equipment.default"))throw new AssertionError("Missing player permission");
            if(!yaml(candidate,"language/messages.yml").isConfigurationSection("boba-equipment"))throw new AssertionError("Missing messages");
            var runtimeLanguage=new YamlConfiguration();runtimeLanguage.setDefaults(yaml(candidate,"language/messages.yml"));
            if(runtimeLanguage.getConfigurationSection("boba-equipment")==null)throw new AssertionError("Bundled message fallback missing");
            var items=yaml(candidate,"items/definitions/boba-equipment.yml");
            if(items.getConfigurationSection("items").getKeys(false).size()!=2)throw new AssertionError("Item definitions invalid");
            BobaEquipmentSettings.read(yaml(candidate,"equipment/boba.yml"));
            System.out.println("Release preflight passed: all old loot values/Jawa salvage retained; two equipment rolls, permission, messages, item definitions and settings parse.");
        }
    }
}
