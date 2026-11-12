package net.mandomc.verification;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.mandomc.integrations.packs.SaberPack;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Pack API exercise on an isolated server; no real players or terrain are modified. */
public final class PackStackProbe extends JavaPlugin {
    private int checks;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify();getLogger().info("PACK STACK PROBE PASSED " + checks + " assertions"); }
            catch (Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"PACK STACK PROBE FAILED",error);}
            Bukkit.shutdown();
        }, 80);
    }
    private void verify() throws Exception {
        var plugin=(JavaPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        check(plugin.isEnabled(),"candidate plugin enabled");
        var config=YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("combat/sabers.yml").toFile());
        var owner=new SaberPack(plugin,config.getConfigurationSection("upstream-pack"));
        List<ResourcePackRequest> requests=new ArrayList<>();UUID id=UUID.randomUUID();
        Player player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(proxy,method,args)->switch(method.getName()) {
            case "isOnline" -> true;case "getUniqueId" -> id;case "getName" -> "PackStackFixture";
            case "sendResourcePacks" -> {if(args[0] instanceof ResourcePackRequest r)requests.add(r);yield null;}
            case "toString" -> "PackStackFixture";case "hashCode" -> id.hashCode();case "equals" -> proxy==args[0];
            default -> method.getReturnType()==boolean.class ? false : method.getReturnType()==int.class ? 0 : null;
        });
        owner.request(player);check(requests.size()==1,"one atomic stack request");var request=requests.getFirst();
        check(request.replace()&&request.required(),"replace stale server order and require every pack");
        check(request.packs().size()==3,"three distinct packs");
        var packs=request.packs();
        check(packs.get(0).uri().getHost().equals("cdn.modrinth.com"),"Faithful uses official Modrinth CDN");
        check(packs.get(0).hash().equals("9447e84ee7121c86304e467a0f8d5b095872e1e6"),"Faithful exact matching 26.1 hash");
        check(packs.get(1).hash().equals("0f1a11ab321d5d4ac39ed2659ddbdf207a82f5dc"),"JustTimm retained above Faithful");
        check(packs.get(2).id().equals(Bukkit.getServer().getServerResourcePack().getId()),"MandoMC inserted last and highest");
        check(packs.get(2).hash().equals(Bukkit.getServer().getServerResourcePack().getHash()),"MandoMC current published hash");
        check(packs.stream().map(p->p.id()).distinct().count()==3,"unique UUIDs cannot overwrite dependencies");
        owner.request(player);check(requests.size()==1,"resource status/join deduplicate requests");
        owner.quit(new PlayerQuitEvent(player,net.kyori.adventure.text.Component.empty()));owner.request(player);
        check(requests.size()==2,"a new session receives a fresh ordered stack");
        var bad=YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath().resolve("combat/sabers.yml").toFile());
        bad.set("upstream-pack.base-pack.sha1","broken");
        try {new SaberPack(plugin,bad.getConfigurationSection("upstream-pack"));throw new AssertionError("invalid pin accepted");}
        catch(IllegalArgumentException expected){check(true,"malformed dependency hash is rejected");}
    }
    private void check(boolean condition,String message){if(!condition)throw new IllegalStateException(message);checks++;getLogger().info("PASS "+message);}
}
