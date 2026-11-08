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

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("mandomc")).setExecutor((sender, command, label, arguments) -> {
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
            return true;
        });
        for (var name : new String[] {"tutorial", "survival"}) {
            Objects.requireNonNull(getCommand(name)).setExecutor((sender, command, label, arguments) -> {
                if (arrival == null) {
                    sender.sendMessage(Component.text("Arrival is unavailable. Please contact a server administrator."));
                    return true;
                }
                return command.getName().equals("tutorial") ? arrival.tutorial(sender) : arrival.depart(sender);
            });
        }
        try {
            if (getConfig().getInt("schema-version", -1) != 1) {
                throw new IllegalArgumentException("config.yml: unsupported schema-version; expected 1");
            }
            if (!getConfig().getBoolean("arrival.enabled", false)) {
                getLogger().info("Foundation enabled; arrival is disabled.");
                return;
            }
            if (!getServer().getPluginManager().isPluginEnabled("WorldGuard")) {
                throw new IllegalStateException("arrival.enabled requires WorldGuard");
            }
            var settings = new ArrivalSettings(getConfig().getString("arrival.spawn-world"),
                    getConfig().getString("arrival.survival-world"), getConfig().getInt("arrival.warmup-seconds", -1));
            destinations = new ArrivalDestinations(settings, new WorldGuardArrivalProtection());
            destinations.validate();
            if (!new File(getDataFolder(), "messages.yml").isFile()) {
                saveResource("messages.yml", false);
            }
            var language = new YamlConfiguration();
            language.load(new File(getDataFolder(), "messages.yml"));
            arrival = new ArrivalListener(this, settings, destinations, language);
            getServer().getPluginManager().registerEvents(arrival, this);
            getLogger().info("Arrival enabled: protected SpawnWorld -> survival; /tutorial and /survival ready.");
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
        if (arrival != null) {
            arrival.close();
        }
    }
}
