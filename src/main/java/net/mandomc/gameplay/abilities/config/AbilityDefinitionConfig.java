package net.mandomc.gameplay.abilities.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import net.mandomc.core.config.BaseConfig;
import net.mandomc.gameplay.abilities.model.AbilityClass;
import net.mandomc.gameplay.abilities.model.AbilityKind;

/**
 * Typed config wrapper for ability trees/levels/runtime tuning.
 */
public class AbilityDefinitionConfig extends BaseConfig {
    private final Plugin plugin;
    private Map<String, AbilityDefinition> definitions = new HashMap<>();
    private Map<AbilityClass, List<String>> classAbilityIds = new EnumMap<>(AbilityClass.class);
    private double tokenCostMultiplier = 1.0;
    private String cooldownBypassPermission = "mandomc.abilities.cooldown.bypass";

    public AbilityDefinitionConfig(Plugin plugin) {
        super(plugin, "abilities/settings.yml");
        this.plugin = plugin;
        ensureClassResources();
        reload();
    }

    @Override
    public void reload() {
        super.reload();
        this.definitions = loadDefinitions();
        this.classAbilityIds = loadClassAbilityIds();
        this.tokenCostMultiplier = Math.max(0.0, getDouble("settings.token-cost-multiplier", 1.0));
        this.cooldownBypassPermission = getString("settings.cooldown-bypass-permission", "mandomc.abilities.cooldown.bypass");
    }

    public Map<String, AbilityDefinition> allDefinitions() {
        return definitions;
    }

    public AbilityDefinition definition(String abilityId) {
        return definitions.get(abilityId);
    }

    public List<AbilityDefinition> definitionsForClass(AbilityClass abilityClass) {
        return classAbilityIds.getOrDefault(abilityClass, List.of()).stream()
                .map(definitions::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<String> abilityIdsForClass(AbilityClass abilityClass) {
        return classAbilityIds.getOrDefault(abilityClass, List.of());
    }

    public double tokenCostMultiplier() {
        return tokenCostMultiplier;
    }

    public String cooldownBypassPermission() {
        return cooldownBypassPermission;
    }

    private Map<AbilityClass, List<String>> loadClassAbilityIds() {
        Map<AbilityClass, List<String>> byClass = new EnumMap<>(AbilityClass.class);
        for (AbilityClass value : AbilityClass.values()) {
            byClass.put(value, new ArrayList<>());
        }
        definitions.values().forEach(def -> byClass.get(def.abilityClass()).add(def.id()));
        byClass.replaceAll((k, v) -> List.copyOf(v));
        return byClass;
    }

    private Map<String, AbilityDefinition> loadDefinitions() {
        Map<String, AbilityDefinition> parsed = new HashMap<>();
        File classFolder = new File(plugin.getDataFolder(), "abilities/classes");
        if (!classFolder.exists() && !classFolder.mkdirs()) {
            logger.warning("[abilities] Could not create abilities/classes folder.");
            return Collections.emptyMap();
        }
        File[] classFiles = classFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (classFiles == null || classFiles.length == 0) {
            logger.warning("[abilities] No class definition files found in abilities/classes.");
            return Collections.emptyMap();
        }
        for (File classFile : classFiles) {
            YamlConfiguration classConfig = YamlConfiguration.loadConfiguration(classFile);
            ConfigurationSection abilities = classConfig.getConfigurationSection("abilities");
            if (abilities == null) {
                continue;
            }
            for (String abilityId : abilities.getKeys(false)) {
                ConfigurationSection node = abilities.getConfigurationSection(abilityId);
                if (node == null) {
                    continue;
                }

                AbilityClass abilityClass = AbilityClass.fromInput(node.getString("class")).orElse(AbilityClass.JEDI);
                String display = node.getString("display", abilityId);
                int guiSlot = Math.max(0, node.getInt("slot", 0));
                int activationSlot = node.getInt("activationSlot", -1);
                if (activationSlot >= 1 && activationSlot <= 9) {
                    activationSlot = activationSlot - 1;
                }
                boolean bindable = node.getBoolean("bindable", true);
                AbilityKind kind = AbilityKind.fromConfig(node.getString("kind", "none"));
                List<String> requires = List.copyOf(node.getStringList("requires"));
                Map<Integer, AbilityLevelDefinition> levels = parseLevels(node, abilityId);

                parsed.put(abilityId, new AbilityDefinition(
                        abilityId,
                        display,
                        abilityClass,
                        kind,
                        guiSlot,
                        activationSlot,
                        bindable,
                        requires,
                        levels
                ));
            }
        }
        return parsed;
    }

    private Map<Integer, AbilityLevelDefinition> parseLevels(ConfigurationSection node, String abilityId) {
        ConfigurationSection levelsSection = node.getConfigurationSection("levels");
        if (levelsSection == null || levelsSection.getKeys(false).isEmpty()) {
            logger.warning("[abilities.yml] Ability '" + abilityId + "' has no levels; using fallback level 1.");
            return Map.of(1, fallbackLevel(1));
        }

        Map<Integer, AbilityLevelDefinition> levels = new HashMap<>();
        for (String levelKey : levelsSection.getKeys(false)) {
            ConfigurationSection levelSection = levelsSection.getConfigurationSection(levelKey);
            if (levelSection == null) {
                continue;
            }
            int level = parseLevel(levelKey);
            int tokenCost = Math.max(0, levelSection.getInt("tokenCost", 1));
            double cooldownSeconds = Math.max(0.0, levelSection.getDouble("cooldownSeconds", 2.0));
            double jumpMultiplier = Math.max(0.0, levelSection.getDouble("jumpMultiplier", 1.0));
            double pushPower = Math.max(0.0, levelSection.getDouble("pushPower", 1.0));
            double pushRange = Math.max(0.0, levelSection.getDouble("pushRange", 6.0));
            double pushRadius = Math.max(0.0, levelSection.getDouble("pushRadius", 0.0));
            ParticlePreset particle = parseParticle(levelSection.getConfigurationSection("particle"));
            levels.put(level, new AbilityLevelDefinition(
                    level,
                    tokenCost,
                    cooldownSeconds,
                    jumpMultiplier,
                    pushPower,
                    pushRange,
                    pushRadius,
                    particle
            ));
        }
        if (levels.isEmpty()) {
            levels.put(1, fallbackLevel(1));
        }
        return levels;
    }

    private ParticlePreset parseParticle(ConfigurationSection section) {
        if (section == null) {
            return ParticlePreset.defaults();
        }
        Particle type;
        try {
            type = Particle.valueOf(section.getString("type", "CLOUD").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            type = Particle.CLOUD;
        }
        return new ParticlePreset(
                type,
                Math.max(0, section.getInt("count", 10)),
                section.getDouble("spreadX", 0.2),
                section.getDouble("spreadY", 0.2),
                section.getDouble("spreadZ", 0.2),
                section.getDouble("speed", 0.01)
        );
    }

    private AbilityLevelDefinition fallbackLevel(int level) {
        return new AbilityLevelDefinition(level, 1, 2.0, 1.0, 1.0, 6.0, 0.0, ParticlePreset.defaults());
    }

    private int parseLevel(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private void ensureClassResources() {
        ensureResource("abilities/classes/jedi.yml");
        ensureResource("abilities/classes/sith.yml");
        ensureResource("abilities/classes/mandalorian.yml");
    }

    private void ensureResource(String path) {
        File target = new File(plugin.getDataFolder(), path);
        if (target.exists()) {
            return;
        }
        target.getParentFile().mkdirs();
        plugin.saveResource(path, false);
    }
}
