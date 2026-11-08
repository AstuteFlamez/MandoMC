package net.mandomc.bootstrap;

import java.io.File;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import net.mandomc.gameplay.arrival.ArrivalListener;
import net.mandomc.gameplay.arrival.ArrivalSettings;
import net.mandomc.integrations.regions.WorldGuardArrivalProtection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class MandoPlugin extends JavaPlugin implements Listener {
    private ArrivalListener arrival;
    private ArrivalDestinations destinations;
    private boolean failed;
    private net.mandomc.gameplay.items.ItemCatalog items;
    private net.mandomc.commands.ItemCommand itemCommands;

    public net.mandomc.gameplay.items.ItemCatalog items() {
        return items;
    }
    private net.mandomc.gameplay.travel.TeleportService travel;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        for (var name : new String[] {"tutorial", "survival", "spawn"}) {
            Objects.requireNonNull(getCommand(name)).setExecutor((sender, command, label, arguments) -> {
                sender.sendMessage(Component.text("Travel is unavailable. Please contact a server administrator."));
                return true;
            });
        }
        try {
            if (getConfig().getInt("schema-version", -1) != 1) {
                throw new IllegalArgumentException("config.yml: unsupported schema-version; expected 1");
            }
            if (!new File(getDataFolder(), "messages.yml").isFile()) {
                saveResource("messages.yml", false);
            }
            var language = new YamlConfiguration();
            language.load(new File(getDataFolder(), "messages.yml"));
            // New message keys fall back to bundled defaults without overwriting operator translations.
            try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(getResource("messages.yml")),
                    java.nio.charset.StandardCharsets.UTF_8)) {
                language.setDefaults(YamlConfiguration.loadConfiguration(reader));
            }
            var root = new net.mandomc.commands.RootCommand(this, sender -> {
                try {
                    if (failed) {
                        throw new IllegalStateException("Arrival setup failed; see the server log and restart after repair.");
                    }
                    if (destinations != null) {
                        destinations.validate();
                        sender.sendMessage(Component.text("MandoMC arrival ready: " + destinations.spawn()
                                + " -> " + destinations.survival()));
                    } else {
                        sender.sendMessage(Component.text("MandoMC foundation enabled; arrival is disabled."));
                    }
                } catch (IllegalStateException error) {
                    sender.sendMessage(Component.text(error.getMessage()));
                }
            }, sender -> sender.sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
                    .deserialize(Objects.requireNonNull(language.getString("items.denied")))));
            for (var resource : new String[] {"items.yml", "items/robes.yml"}) {
                if (!new File(getDataFolder(), resource).isFile()) saveResource(resource, false);
            }
            items = new net.mandomc.gameplay.items.ItemCatalog(getDataFolder().toPath(),
                    new net.mandomc.integrations.weapons.WeaponMechanicsItems()::generate);
            itemCommands = new net.mandomc.commands.ItemCommand(this, items, language, root);
            try {
                items.reload();
                getLogger().info("Loaded " + items.ids().size() + " custom items.");
            } catch (Exception error) {
                getLogger().severe("Custom items unavailable; repair config and /mandomc items reload: " + error.getMessage());
            }
            if (!getConfig().getBoolean("arrival.enabled", false)) {
                getLogger().info("Foundation enabled; arrival is disabled.");
                return;
            }
            if (!getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
                throw new IllegalStateException("arrival.enabled requires WorldGuard");
            }
            var settings = new ArrivalSettings(getConfig().getString("arrival.spawn-world"),
                    getConfig().getString("arrival.survival-world"));
            destinations = new ArrivalDestinations(settings, new WorldGuardArrivalProtection());
            destinations.validate();
            arrival = new ArrivalListener(this, settings, destinations, language);
            getServer().getPluginManager().registerEvents(arrival, this);
            if (!new File(getDataFolder(), "teleport.yml").isFile()) saveResource("teleport.yml", false);
            var teleportConfig = new YamlConfiguration();
            teleportConfig.load(new File(getDataFolder(), "teleport.yml"));
            travel = new net.mandomc.gameplay.travel.TeleportService(this, teleportConfig, language);
            getServer().getPluginManager().registerEvents(travel, this);
            net.mandomc.commands.ArrivalCommands.register(this, arrival, destinations, travel);
            getLogger().info("Arrival enabled: protected SpawnWorld -> survival; /spawn, /survival and temporary /tutorial ready.");
        } catch (Exception | LinkageError error) {
            failed = true;
            getLogger().severe("Arrival unavailable; player logins blocked until repaired: " + error.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockUnconfiguredArrival(PlayerLoginEvent event) {
        if (failed) {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER,
                    Component.text("Arrival is unavailable. Please contact a server administrator."));
        }
    }

    @Override
    public void onDisable() {
        if (travel != null) travel.close();
        if (itemCommands != null) itemCommands.close();
    }
}
