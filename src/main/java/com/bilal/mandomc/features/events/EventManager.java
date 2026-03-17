package com.bilal.mandomc.features.events;

import com.bilal.mandomc.MandoMC;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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

        File file = new File(plugin.getDataFolder(), "events.yml");
        this.config = YamlConfiguration.loadConfiguration(file);

        definitions.clear();

        ConfigurationSection section = config.getConfigurationSection("events");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection eventSec = section.getConfigurationSection(id);
                if (eventSec == null) continue;

                boolean enabled = eventSec.getBoolean("enabled", true);
                String displayName = color(eventSec.getString("display-name", id));
                int weight = Math.max(0, eventSec.getInt("weight", 0));
                Material icon = Material.matchMaterial(eventSec.getString("icon", "PAPER"));
                if (icon == null) icon = Material.PAPER;

                List<String> description = color(eventSec.getStringList("description"));
                int cooldownCycles = Math.max(0, eventSec.getInt("cooldown-cycles", 0));

                Map<String, Object> settings = new HashMap<>();
                ConfigurationSection settingsSec = eventSec.getConfigurationSection("settings");
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

                definitions.put(id.toLowerCase(), definition);
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
            default -> {
            }
        }
    }

    public boolean startEvent(String id, boolean broadcast) {
        EventDefinition definition = definitions.get(id.toLowerCase());
        if (definition == null) {
            return false;
        }

        GameEvent event = registry.create(definition);
        if (event == null) {
            return false;
        }

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
            broadcastTitle("&a&lEVENT STARTED", activeEvent.getDisplayName());
        }

        return true;
    }

    public boolean queueEvent(String id) {
        EventDefinition definition = definitions.get(id.toLowerCase());
        if (definition == null) {
            return false;
        }

        GameEvent event = registry.create(definition);
        if (event == null) {
            return false;
        }

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

        if (eligible.isEmpty()) {
            return null;
        }

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
            broadcastTitle("&c&lEVENT ENDED", displayName);
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
            broadcastTitle("&a&lEVENT STARTED", activeEvent.getDisplayName());
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

        if (eligible.isEmpty()) {
            return Collections.emptyMap();
        }

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

    public long getSecondsUntilMinute(int minute) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target = now.withMinute(minute).withSecond(0).withNano(0);

        if (!target.isAfter(now)) {
            target = target.plusHours(1);
        }

        return ChronoUnit.SECONDS.between(now, target);
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

    public String getLastEventId() {
        return lastEventId;
    }

    public EventDefinition getDefinition(String id) {
        return definitions.get(id.toLowerCase());
    }

    public boolean setEnabled(String id, boolean enabled) {
        File file = new File(plugin.getDataFolder(), "events.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        if (!yaml.contains("events." + id)) {
            return false;
        }

        yaml.set("events." + id + ".enabled", enabled);

        try {
            yaml.save(file);
            reload();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<String> getEventIds() {
        return new ArrayList<>(definitions.keySet());
    }

    public void broadcastTitle(String title, String subtitle) {

        String coloredTitle = color(title);
        String coloredSubtitle = color(subtitle);

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendTitle(
                    coloredTitle,
                    coloredSubtitle,
                    10,   // fade in
                    60,   // stay
                    10    // fade out
            );
        }
    }
}