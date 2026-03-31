package net.mandomc.server.events;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import net.mandomc.server.events.model.GameEvent;
import net.mandomc.server.events.model.EventDefinition;
import net.mandomc.server.events.model.EventPhase;
import net.mandomc.server.events.model.EventState;

/**
 * Manages all active and queued game events.
 *
 * Loads event definitions from the /events folder, handles scheduling phases,
 * tracks cooldowns between events, and coordinates event start and end lifecycle.
 */
public class EventManager {

    private final MandoMC plugin;
    private final EventRegistry registry;

    private YamlConfiguration config;

    private final Map<String, EventDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();

    private GameEvent activeEvent;
    private GameEvent queuedEvent;

    private EventState state = EventState.IDLE;
    private String lastEventId;

    public EventManager(MandoMC plugin) {
        this.plugin = plugin;
        this.registry = new EventRegistry();
    }

    public void load() {
        saveDefaultConfigIfNeeded();
        saveDefaultEventsIfNeeded();

        // Load main config (scheduler/gui)
        File file = new File(plugin.getDataFolder(), "events.yml");
        this.config = YamlConfiguration.loadConfiguration(file);

        definitions.clear();

        File folder = new File(plugin.getDataFolder(), "events");
        if (!folder.exists()) {
            folder.mkdirs();
            Bukkit.getLogger().warning("[MandoMC] Created /events folder. Add event yml files there.");
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File eventFile : files) {

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(eventFile);

            String id = yaml.getString("id");
            if (id == null) {
                Bukkit.getLogger().warning("[MandoMC] Missing 'id' in " + eventFile.getName());
                continue;
            }

            id = id.toLowerCase();

            boolean enabled = yaml.getBoolean("enabled", true);
            String displayName = color(yaml.getString("display-name", id));
            int weight = Math.max(0, yaml.getInt("weight", 0));

            Material icon = Material.matchMaterial(yaml.getString("icon", "PAPER"));
            if (icon == null) icon = Material.PAPER;

            List<String> description = color(yaml.getStringList("description"));
            int cooldownCycles = Math.max(0, yaml.getInt("cooldown-cycles", 0));

            Map<String, Object> settings = new HashMap<>();
            ConfigurationSection settingsSec = yaml.getConfigurationSection("settings");
            if (settingsSec != null) {
                for (String key : settingsSec.getKeys(false)) {
                    settings.put(key, settingsSec.get(key));
                }
            }

            EventDefinition definition = new EventDefinition(
                    id,
                    enabled,
                    displayName,
                    weight,
                    icon,
                    description,
                    cooldownCycles,
                    settings
            );

            definitions.put(id, definition);
        }
    }

    private void saveDefaultEventsIfNeeded() {
        File folder = new File(plugin.getDataFolder(), "events");

        if (!folder.exists()) {
            folder.mkdirs();
        }

        // List of default event files you ship
        String[] defaults = {
            "events/koth.yml",
            "events/beskar.yml",
            "events/jabba.yml"
        };

        for (String path : defaults) {
            File outFile = new File(plugin.getDataFolder(), path);

            if (!outFile.exists()) {
                plugin.saveResource(path, false);
                Bukkit.getLogger().info("[MandoMC] Created default " + path);
            }
        }
}

    public void reload() {
        load();
    }

    private void saveDefaultConfigIfNeeded() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists()) {
            plugin.saveResource("events.yml", false);
        }
    }

    public File getEventFile(String id) {
        return new File(plugin.getDataFolder(), "events/" + id.toLowerCase() + ".yml");
    }

    public boolean setEnabled(String id, boolean enabled) {
        File file = getEventFile(id);

        if (!file.exists()) return false;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("enabled", enabled);

        try {
            yaml.save(file);
            reload();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void tickSchedulerPhase(EventPhase phase) {
        switch (phase) {
            case END_WARNING -> {
                if (activeEvent != null && activeEvent.isRunning()) {
                    state = EventState.ENDING_SOON;
                    broadcastConfiguredMessage("scheduler.messages.current-event-ending", activeEvent.getDisplayName());
                }
            }
            case FORCE_END -> {
                if (activeEvent != null && activeEvent.isRunning()) {
                    forceEndActiveEvent(true);
                }
                queueNextRandomEventIfNeeded();
                if (queuedEvent != null) {
                    state = EventState.STARTING_SOON;
                    broadcastConfiguredMessage("scheduler.messages.next-event-warning", queuedEvent.getDisplayName());
                } else {
                    state = EventState.IDLE;
                }
            }
            case START_WARNING -> {
                if (queuedEvent == null) {
                    queueNextRandomEventIfNeeded();
                }
                if (queuedEvent != null) {
                    state = EventState.STARTING_SOON;
                    broadcastConfiguredMessage("scheduler.messages.next-event-warning", queuedEvent.getDisplayName());
                }
            }
            case START -> {
                if (queuedEvent != null) {
                    startQueuedEvent(true);
                }
            }
            default -> {}
        }
    }

    public boolean startEvent(String id, boolean broadcast) {
        EventDefinition definition = definitions.get(id.toLowerCase());
        if (definition == null) return false;

        GameEvent event = registry.create(definition);
        if (event == null) return false;

        if (activeEvent != null && activeEvent.isRunning()) {
            forceEndActiveEvent(false);
        }

        activeEvent = event;
        queuedEvent = null;
        state = EventState.RUNNING;
        activeEvent.start(this);
        lastEventId = activeEvent.getId();

        setCooldownsAfterStart(activeEvent.getId());

        if (broadcast) {
            broadcastTitle(LangManager.get("events.broadcast.started-title"), activeEvent.getDisplayName());
            broadcastConfiguredMessage("scheduler.messages.event-start", activeEvent.getDisplayName());
        }

        return true;
    }

    public boolean queueEvent(String id) {
        EventDefinition definition = definitions.get(id.toLowerCase());
        if (definition == null) return false;

        GameEvent event = registry.create(definition);
        if (event == null) return false;

        queuedEvent = event;
        state = EventState.STARTING_SOON;
        return true;
    }

    public void rerollNextEvent() {
        queuedEvent = pickRandomEvent();
        state = queuedEvent == null ? EventState.IDLE : EventState.STARTING_SOON;
    }

    public void queueNextRandomEventIfNeeded() {
        if (queuedEvent == null) {
            queuedEvent = pickRandomEvent();
        }
    }

    public GameEvent pickRandomEvent() {
        List<EventDefinition> eligible = definitions.values().stream()
                .filter(EventDefinition::isEnabled)
                .filter(def -> def.getWeight() > 0)
                .filter(def -> registry.isRegistered(def.getId()))
                .filter(def -> !Objects.equals(def.getId(), lastEventId))
                .filter(def -> cooldowns.getOrDefault(def.getId(), 0) <= 0)
                .collect(Collectors.toList());

        if (eligible.isEmpty()) {
            eligible = definitions.values().stream()
                    .filter(EventDefinition::isEnabled)
                    .filter(def -> def.getWeight() > 0)
                    .filter(def -> registry.isRegistered(def.getId()))
                    .collect(Collectors.toList());
        }

        if (eligible.isEmpty()) return null;

        int totalWeight = eligible.stream().mapToInt(EventDefinition::getWeight).sum();
        int roll = ThreadLocalRandom.current().nextInt(totalWeight) + 1;

        int cumulative = 0;
        for (EventDefinition def : eligible) {
            cumulative += def.getWeight();
            if (roll <= cumulative) {
                return registry.create(def);
            }
        }

        return registry.create(eligible.get(0));
    }

    private void setCooldownsAfterStart(String startedId) {
        for (String id : new HashSet<>(cooldowns.keySet())) {
            int current = cooldowns.getOrDefault(id, 0);
            if (current > 0) {
                cooldowns.put(id, current - 1);
            }
        }

        EventDefinition started = definitions.get(startedId.toLowerCase());
        if (started != null && started.getCooldownCycles() > 0) {
            cooldowns.put(startedId.toLowerCase(), started.getCooldownCycles());
        }
    }

    public void forceEndActiveEvent(boolean broadcast) {
        if (activeEvent == null) return;

        String displayName = activeEvent.getDisplayName();

        if (activeEvent.isRunning()) {
            activeEvent.onForceEnd(this);
        }

        activeEvent = null;
        state = EventState.IDLE;

        if (broadcast) {
            broadcastTitle(LangManager.get("events.broadcast.ended-title"), displayName);
            broadcastConfiguredMessage("scheduler.messages.event-end", displayName);
        }
    }

    public void startQueuedEvent(boolean broadcast) {
        if (queuedEvent == null) return;

        if (activeEvent != null && activeEvent.isRunning()) {
            forceEndActiveEvent(false);
        }

        activeEvent = queuedEvent;
        queuedEvent = null;

        state = EventState.RUNNING;
        activeEvent.start(this);
        lastEventId = activeEvent.getId();

        setCooldownsAfterStart(activeEvent.getId());

        if (broadcast) {
            broadcastTitle(LangManager.get("events.broadcast.started-title"), activeEvent.getDisplayName());
            broadcastConfiguredMessage("scheduler.messages.event-start", activeEvent.getDisplayName());
        }
    }

    public Map<String, Double> getCurrentChances() {
        List<EventDefinition> eligible = definitions.values().stream()
                .filter(EventDefinition::isEnabled)
                .filter(def -> def.getWeight() > 0)
                .filter(def -> registry.isRegistered(def.getId()))
                .filter(def -> !Objects.equals(def.getId(), lastEventId))
                .filter(def -> cooldowns.getOrDefault(def.getId(), 0) <= 0)
                .collect(Collectors.toList());

        if (eligible.isEmpty()) return Collections.emptyMap();

        int total = eligible.stream().mapToInt(EventDefinition::getWeight).sum();
        Map<String, Double> map = new LinkedHashMap<>();

        for (EventDefinition def : eligible) {
            double pct = (def.getWeight() * 100.0) / total;
            map.put(def.getId(), pct);
        }

        return map;
    }

    public long getSecondsUntilNextHour() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextHour = now.truncatedTo(ChronoUnit.HOURS).plusHours(1);
        return ChronoUnit.SECONDS.between(now, nextHour);
    }

    public void broadcastConfiguredMessage(String path, String eventName) {
        List<String> lines = color(config.getStringList(path));
        for (String line : lines) {
            Bukkit.broadcastMessage(line.replace("%event%", eventName));
        }
    }

    public void sendMessage(Player player, String message) {
        player.sendMessage(color(message));
    }

    public List<String> color(List<String> input) {
        return input.stream().map(this::color).toList();
    }

    public String color(String input) {
        return input == null ? "" : input.replace("&", "§");
    }

    public YamlConfiguration getConfig() {
        return config;
    }

    public Map<String, EventDefinition> getDefinitions() {
        return definitions;
    }

    public GameEvent getActiveEvent() {
        return activeEvent;
    }

    public GameEvent getQueuedEvent() {
        return queuedEvent;
    }

    public EventState getState() {
        return state;
    }

    public EventDefinition getDefinition(String id) {
        return definitions.get(id.toLowerCase());
    }

    public List<String> getEventIds() {
        return new ArrayList<>(definitions.keySet());
    }

    public void broadcastTitle(String title, String subtitle) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(color(title), color(subtitle), 10, 60, 10);
        }
    }
}