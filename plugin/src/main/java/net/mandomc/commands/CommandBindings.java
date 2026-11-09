package net.mandomc.commands;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.plugin.java.JavaPlugin;

/** Claims the requested bare labels while preserving every vendor's namespaced command. */
public final class CommandBindings implements AutoCloseable {
    private final Map<String, Command> known;
    private final Map<String, Command> previous = new HashMap<>();
    private final Map<String, Command> owned = new HashMap<>();
    public CommandBindings(JavaPlugin plugin) {
        known = plugin.getServer().getCommandMap().getKnownCommands();
        plugin.getDescription().getCommands().forEach((name, description) -> {
            var command = plugin.getCommand(name);
            claim(name, command);
            Object aliases = description.get("aliases");
            if (aliases instanceof java.util.List<?> list) {
                for (var alias : list) claim(alias.toString(), command);
            } else if (aliases instanceof String alias) claim(alias, command);
        });
    }
    private void claim(String label, Command command) {
        previous.put(label, known.put(label, command)); owned.put(label, command);
    }
    @Override public void close() {
        owned.forEach((label, command) -> {
            if (known.get(label) != command) return;
            var original = previous.get(label);
            if (original == null) known.remove(label); else known.put(label, original);
        });
        owned.clear(); previous.clear();
    }
}
