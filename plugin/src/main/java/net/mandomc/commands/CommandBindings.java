package net.mandomc.commands;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.plugin.java.JavaPlugin;

/** Claims the requested bare labels while preserving every vendor's namespaced command. */
public final class CommandBindings implements AutoCloseable {
    private final AutoCloseable clans;
    private final AutoCloseable decor;
    private final net.mandomc.gameplay.messaging.MessagingFeature messaging;
    private final AutoCloseable ilum;
    private final Map<String, Command> known;
    private final Map<String, Command> previous = new HashMap<>();
    private final Map<String, Command> owned = new HashMap<>();
    public CommandBindings(JavaPlugin plugin) {
        new net.mandomc.gameplay.territory.ClanMapCommand(plugin);
        known = plugin.getServer().getCommandMap().getKnownCommands();
        plugin.getDescription().getCommands().forEach((name, description) -> {
            var command = plugin.getCommand(name);
            claim(name, command);
            Object aliases = description.get("aliases");
            if (aliases instanceof java.util.List<?> list) {
                for (var alias : list) claim(alias.toString(), command);
            } else if (aliases instanceof String alias) claim(alias, command);
        });
        // Start Ilum's deferred crate owner first when its source has been integrated into this build.
        ilum=optionalIlum(plugin);
        clans=optionalFeature(plugin,"net.mandomc.gameplay.clans.ClanFeature");
        messaging=new net.mandomc.gameplay.messaging.MessagingFeature(plugin);
        decor=optionalFeature(plugin,"net.mandomc.gameplay.decor.DecorFeature");
    }
    private AutoCloseable optionalFeature(JavaPlugin plugin,String name) {
        try {
            var type=Class.forName(name,true,plugin.getClass().getClassLoader());
            return (AutoCloseable)type.getConstructor(net.mandomc.bootstrap.MandoPlugin.class).newInstance(plugin);
        }catch(ClassNotFoundException absent){return null;}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot initialize "+name,error);}
    }
    private AutoCloseable optionalIlum(JavaPlugin plugin) {
        if(plugin.getCommand("parkour")==null)return null;
        try {
            var type=Class.forName("net.mandomc.gameplay.ilum.IlumFeature",true,plugin.getClass().getClassLoader());
            return (AutoCloseable)type.getConstructor(net.mandomc.bootstrap.MandoPlugin.class).newInstance(plugin);
        }catch(ClassNotFoundException absent){return null;}
        catch(ReflectiveOperationException error){throw new IllegalStateException("Cannot retain Ilum lifecycle",error);}
    }
    private void claim(String label, Command command) {
        previous.put(label, known.put(label, command)); owned.put(label, command);
    }
    @Override public void close() {
        if(decor!=null)try{decor.close();}catch(Exception error){throw new IllegalStateException("Cannot close spawn decor",error);}
        messaging.close();
        if(clans!=null)try{clans.close();}catch(Exception error){throw new IllegalStateException("Cannot close clans",error);}
        if(ilum!=null)try{ilum.close();}catch(Exception error){throw new IllegalStateException("Cannot close Ilum lifecycle",error);}
        owned.forEach((label, command) -> {
            if (known.get(label) != command) return;
            var original = previous.get(label);
            if (original == null) known.remove(label); else known.put(label, original);
        });
        owned.clear(); previous.clear();
    }
}
