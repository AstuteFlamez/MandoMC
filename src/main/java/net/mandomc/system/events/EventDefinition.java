package net.mandomc.system.events;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EventDefinition {

    private final String id;
    private final boolean enabled;
    private final String displayName;
    private final int weight;
    private final Material icon;
    private final List<String> description;
    private final int cooldownCycles;
    private final Map<String, Object> settings;

    public EventDefinition(
            String id,
            boolean enabled,
            String displayName,
            int weight,
            Material icon,
            List<String> description,
            int cooldownCycles,
            Map<String, Object> settings
    ) {
        this.id = id;
        this.enabled = enabled;
        this.displayName = displayName;
        this.weight = weight;
        this.icon = icon;
        this.description = description == null ? Collections.emptyList() : description;
        this.cooldownCycles = cooldownCycles;
        this.settings = settings == null ? Collections.emptyMap() : settings;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getWeight() {
        return weight;
    }

    public Material getIcon() {
        return icon;
    }

    public List<String> getDescription() {
        return description;
    }

    public int getCooldownCycles() {
        return cooldownCycles;
    }

    public Map<String, Object> getSettings() {
        return settings;
    }

    public Object getSetting(String key) {
        return settings.get(key);
    }
}