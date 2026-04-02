package net.mandomc.gameplay.abilities.service;

import java.text.DecimalFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.gameplay.abilities.config.AbilityDefinition;
import net.mandomc.gameplay.abilities.config.AbilityDefinitionConfig;
import net.mandomc.gameplay.abilities.config.AbilityLevelDefinition;
import net.mandomc.gameplay.abilities.config.ParticlePreset;
import net.mandomc.gameplay.abilities.model.AbilityBinding;
import net.mandomc.gameplay.abilities.model.AbilityClass;
import net.mandomc.gameplay.abilities.model.AbilityKind;
import net.mandomc.gameplay.abilities.model.AbilityNodeState;
import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;
import net.mandomc.gameplay.abilities.storage.AbilitiesRepository;

/**
 * Core runtime service for progression, bindings, cooldowns, and ability casts.
 */
public class AbilityService {
    private static final DecimalFormat COOLDOWN_FORMAT = new DecimalFormat("0.0");

    public enum UnlockResult {
        SUCCESS,
        NO_CLASS_SELECTED,
        UNKNOWN_ABILITY,
        WRONG_CLASS,
        LOCKED_BY_REQUIREMENTS,
        MAX_LEVEL_REACHED,
        LEVEL_CONFIG_MISSING,
        NOT_ENOUGH_TOKENS
    }

    private final Plugin plugin;
    private final AbilitiesRepository repository;
    private final AbilityDefinitionConfig config;
    private final AbilityTreeResolver treeResolver;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private BukkitTask periodicFlushTask;

    public AbilityService(Plugin plugin, AbilitiesRepository repository, AbilityDefinitionConfig config) {
        this.plugin = plugin;
        this.repository = repository;
        this.config = config;
        this.treeResolver = new AbilityTreeResolver();
    }

    public void start() {
        periodicFlushTask = plugin.getServer().getScheduler().runTaskTimer(plugin, repository::flushIfDirty, 20L * 60L, 20L * 60L);
    }

    public void shutdown() {
        if (periodicFlushTask != null) {
            periodicFlushTask.cancel();
            periodicFlushTask = null;
        }
        repository.flush();
    }

    public AbilityPlayerProfile profile(UUID playerId) {
        return repository.getOrCreate(playerId);
    }

    public void saveProfile(AbilityPlayerProfile profile) {
        repository.save(profile);
        repository.flushSoon(40L);
    }

    public void flushIfDirty() {
        repository.flushIfDirty();
    }

    public Collection<AbilityDefinition> abilitiesForClass(AbilityClass abilityClass) {
        if (abilityClass == null || abilityClass == AbilityClass.UNSET) {
            return List.of();
        }
        return config.definitionsForClass(abilityClass);
    }

    public Optional<AbilityDefinition> definition(String abilityId) {
        return Optional.ofNullable(config.definition(abilityId));
    }

    public boolean switchClass(Player player, AbilityClass abilityClass) {
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        profile.setSelectedClass(abilityClass);
        profile.bindings().clear();
        saveProfile(profile);
        return true;
    }

    public boolean hasSelectedClass(Player player) {
        AbilityClass selected = profile(player.getUniqueId()).selectedClass();
        return selected != null && selected != AbilityClass.UNSET;
    }

    public AbilityNodeState nodeState(AbilityPlayerProfile profile, String abilityId) {
        AbilityDefinition definition = config.definition(abilityId);
        if (definition == null) {
            return AbilityNodeState.LOCKED;
        }
        return treeResolver.stateFor(profile, definition, config.allDefinitions());
    }

    public UnlockResult unlockNextLevel(Player player, String abilityId) {
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        if (profile.selectedClass() == null || profile.selectedClass() == AbilityClass.UNSET) {
            return UnlockResult.NO_CLASS_SELECTED;
        }
        AbilityDefinition definition = config.definition(abilityId);
        if (definition == null) {
            return UnlockResult.UNKNOWN_ABILITY;
        }
        if (definition.abilityClass() != profile.selectedClass()) {
            return UnlockResult.WRONG_CLASS;
        }
        if (nodeState(profile, abilityId) == AbilityNodeState.LOCKED) {
            return UnlockResult.LOCKED_BY_REQUIREMENTS;
        }
        if (!treeResolver.canProgress(profile, definition)) {
            return UnlockResult.MAX_LEVEL_REACHED;
        }
        int nextLevel = profile.unlockedLevel(abilityId) + 1;
        AbilityLevelDefinition levelDefinition = definition.levels().get(nextLevel);
        if (levelDefinition == null) {
            return UnlockResult.LEVEL_CONFIG_MISSING;
        }
        int tokenCost = (int) Math.ceil(levelDefinition.tokenCost() * config.tokenCostMultiplier());
        if (profile.skillTokens() < tokenCost) {
            return UnlockResult.NOT_ENOUGH_TOKENS;
        }
        profile.setSkillTokens(profile.skillTokens() - tokenCost);
        profile.setUnlockedLevel(abilityId, nextLevel);
        profile.setSelectedLevel(abilityId, nextLevel);
        saveProfile(profile);
        return UnlockResult.SUCCESS;
    }

    public boolean canUpgradeLevel(Player player, String abilityId) {
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        AbilityDefinition definition = config.definition(abilityId);
        if (definition == null) {
            return false;
        }
        if (profile.selectedClass() == null || profile.selectedClass() == AbilityClass.UNSET) {
            return false;
        }
        if (definition.abilityClass() != profile.selectedClass()) {
            return false;
        }
        if (nodeState(profile, abilityId) == AbilityNodeState.LOCKED) {
            return false;
        }
        if (!treeResolver.canProgress(profile, definition)) {
            return false;
        }
        int nextLevel = profile.unlockedLevel(abilityId) + 1;
        AbilityLevelDefinition levelDefinition = definition.levels().get(nextLevel);
        if (levelDefinition == null) {
            return false;
        }
        int tokenCost = (int) Math.ceil(levelDefinition.tokenCost() * config.tokenCostMultiplier());
        return profile.skillTokens() >= tokenCost;
    }

    public boolean setSelectedLevel(Player player, String abilityId, int level) {
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        AbilityDefinition definition = config.definition(abilityId);
        if (definition == null) {
            return false;
        }
        if (profile.selectedClass() == null || profile.selectedClass() == AbilityClass.UNSET) {
            return false;
        }
        if (!treeResolver.isClassAbility(profile.selectedClass(), definition)) {
            return false;
        }
        int unlocked = profile.unlockedLevel(abilityId);
        if (unlocked <= 0) {
            return false;
        }
        int sanitized = Math.max(1, Math.min(level, unlocked));
        profile.setSelectedLevel(abilityId, sanitized);
        saveProfile(profile);
        return true;
    }

    public boolean bindAbility(Player player, int slot, String abilityId) {
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        int level = profile.selectedLevel(abilityId);
        if (level <= 0) {
            level = 1;
        }
        return bindAbility(player, slot, abilityId, level);
    }

    public boolean bindAbility(Player player, int slot, String abilityId, int level) {
        if (slot < 0 || slot > 8) {
            return false;
        }
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        AbilityDefinition definition = config.definition(abilityId);
        if (definition == null) {
            return false;
        }
        // Force-jump style abilities are slot-select triggered (double jump) rather than click-cast.
        if (!definition.bindable() && definition.kind() != AbilityKind.FORCE_JUMP) {
            return false;
        }
        if (profile.selectedClass() == null || profile.selectedClass() == AbilityClass.UNSET) {
            return false;
        }
        if (!treeResolver.isClassAbility(profile.selectedClass(), definition)) {
            return false;
        }
        if (!profile.isUnlocked(abilityId)) {
            return false;
        }
        int unlocked = profile.unlockedLevel(abilityId);
        int selectedLevel = Math.max(1, Math.min(level, unlocked));
        profile.bindings().put(slot, new AbilityBinding(abilityId, selectedLevel));
        saveProfile(profile);
        return true;
    }

    public void clearBinding(Player player, int slot) {
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        profile.bindings().remove(slot);
        saveProfile(profile);
    }

    public Optional<AbilityBinding> heldSlotBinding(Player player) {
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        return Optional.ofNullable(profile.bindings().get(player.getInventory().getHeldItemSlot()));
    }

    public void addTokens(Player player, int amount) {
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        profile.addSkillTokens(amount);
        saveProfile(profile);
    }

    public void setTokens(Player player, int amount) {
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        profile.setSkillTokens(amount);
        saveProfile(profile);
    }

    public boolean tryCastBoundAbility(Player player) {
        if (VehicleModule.getVehicleForPlayer(player.getUniqueId()) != null) {
            return false;
        }
        if (!isMainHandEmpty(player)) {
            return false;
        }

        AbilityPlayerProfile profile = profile(player.getUniqueId());
        if (profile.selectedClass() == null || profile.selectedClass() == AbilityClass.UNSET) {
            return false;
        }
        AbilityBinding binding = profile.bindings().get(player.getInventory().getHeldItemSlot());
        if (binding == null) {
            return false;
        }
        AbilityDefinition definition = config.definition(binding.abilityId());
        if (definition == null || !definition.bindable()) {
            return false;
        }
        if (!profile.isUnlocked(binding.abilityId())) {
            return false;
        }
        int level = resolveBindingLevel(profile, binding);
        AbilityLevelDefinition levelDefinition = definition.levels().get(level);
        if (levelDefinition == null) {
            return false;
        }
        if (isOnCooldown(player, binding.abilityId()) && !player.hasPermission(config.cooldownBypassPermission())) {
            return false;
        }
        boolean cast = castAbility(player, definition, levelDefinition);
        if (!cast) {
            return false;
        }
        setCooldown(player, binding.abilityId(), levelDefinition.cooldownSeconds());
        return true;
    }

    public boolean tryCastForceJump(Player player) {
        if (VehicleModule.getVehicleForPlayer(player.getUniqueId()) != null) {
            return false;
        }
        if (!isMainHandEmpty(player)) {
            return false;
        }
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        if (profile.selectedClass() == null || profile.selectedClass() == AbilityClass.UNSET) {
            return false;
        }
        int heldSlot = player.getInventory().getHeldItemSlot();
        List<AbilityDefinition> classAbilities = config.definitionsForClass(profile.selectedClass());
        AbilityDefinition chosen = null;
        int selectedLevel = 0;
        for (AbilityDefinition def : classAbilities) {
            if (def.kind() != AbilityKind.FORCE_JUMP) {
                continue;
            }
            int unlocked = profile.unlockedLevel(def.id());
            if (unlocked <= 0) {
                continue;
            }
            int requiredSlot = resolveForceJumpSlot(profile, def);
            if (requiredSlot >= 0 && requiredSlot != heldSlot) {
                continue;
            }
            int active = profile.selectedLevel(def.id());
            if (chosen == null || active > selectedLevel) {
                chosen = def;
                selectedLevel = active;
            }
        }
        if (chosen == null) {
            return false;
        }
        AbilityLevelDefinition levelDefinition = chosen.levels().get(selectedLevel);
        if (levelDefinition == null) {
            return false;
        }
        if (isOnCooldown(player, chosen.id()) && !player.hasPermission(config.cooldownBypassPermission())) {
            return false;
        }
        boolean cast = castAbility(player, chosen, levelDefinition);
        if (!cast) {
            return false;
        }
        setCooldown(player, chosen.id(), levelDefinition.cooldownSeconds());
        return true;
    }

    public String actionBarLine(Player player) {
        if (!isMainHandEmpty(player)) {
            return null;
        }
        AbilityPlayerProfile profile = profile(player.getUniqueId());
        AbilityBinding binding = profile.bindings().get(player.getInventory().getHeldItemSlot());
        if (binding == null) {
            return null;
        }
        AbilityDefinition definition = config.definition(binding.abilityId());
        if (definition == null) {
            return null;
        }
        long remainingMillis = remainingCooldownMillis(player, definition.id());
        String cooldownText = remainingMillis <= 0
                ? ChatColor.GREEN + "ready"
                : ChatColor.RED + COOLDOWN_FORMAT.format(remainingMillis / 1000.0) + "s";
        int selectedLevel = resolveBindingLevel(profile, binding);
        if (selectedLevel <= 0) {
            return null;
        }
        String levelLabel = toRoman(selectedLevel);
        return ChatColor.AQUA + stripColors(definition.displayName())
                + ChatColor.GRAY + " " + levelLabel + " "
                + ChatColor.DARK_GRAY + "- "
                + cooldownText;
    }

    public long remainingCooldownMillis(Player player, String abilityId) {
        Map<String, Long> byAbility = cooldowns.get(player.getUniqueId());
        if (byAbility == null) {
            return 0L;
        }
        long until = byAbility.getOrDefault(abilityId, 0L);
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public boolean isOnCooldown(Player player, String abilityId) {
        return remainingCooldownMillis(player, abilityId) > 0L;
    }

    public void clearCooldowns(UUID playerId) {
        cooldowns.remove(playerId);
    }

    private void setCooldown(Player player, String abilityId, double seconds) {
        long until = System.currentTimeMillis() + (long) (Math.max(0.0, seconds) * 1000L);
        cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(abilityId, until);
    }

    private boolean castAbility(Player player, AbilityDefinition definition, AbilityLevelDefinition levelDefinition) {
        return switch (definition.kind()) {
            case FORCE_PUSH -> castForcePush(player, levelDefinition);
            case FORCE_JUMP -> castForceJump(player, levelDefinition);
            case NONE -> false;
        };
    }

    private boolean castForceJump(Player player, AbilityLevelDefinition levelDefinition) {
        Vector boost = player.getLocation().getDirection().normalize().multiply(0.15);
        boost.setY(0.5 * Math.max(0.4, levelDefinition.jumpMultiplier()));
        player.setVelocity(player.getVelocity().add(boost));
        spawnParticles(player.getLocation().add(0, 0.2, 0), levelDefinition.particle());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.7f, 1.4f);
        return true;
    }

    private boolean castForcePush(Player player, AbilityLevelDefinition levelDefinition) {
        double range = Math.max(1.0, levelDefinition.pushRange());
        double radius = Math.max(0.0, levelDefinition.pushRadius());
        double power = Math.max(0.1, levelDefinition.pushPower());

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location impact = eye.clone().add(direction.clone().multiply(range));
        spawnParticles(impact, levelDefinition.particle());
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.7f);

        if (radius <= 0.1) {
            RayTraceResult result = player.getWorld().rayTraceEntities(
                    eye,
                    direction,
                    range,
                    entity -> entity instanceof LivingEntity && !entity.equals(player)
            );
            if (result != null && result.getHitEntity() instanceof LivingEntity living) {
                Vector knock = direction.clone().multiply(power).setY(0.25 + Math.min(0.6, power * 0.2));
                living.setVelocity(knock);
            }
            return true;
        }

        int affected = 0;
        for (Entity nearby : player.getWorld().getNearbyEntities(impact, Math.max(0.8, radius), Math.max(1.2, radius), Math.max(0.8, radius))) {
            if (!(nearby instanceof LivingEntity living)) {
                continue;
            }
            if (nearby.equals(player)) {
                continue;
            }
            Vector knock = nearby.getLocation().toVector().subtract(player.getLocation().toVector());
            if (knock.lengthSquared() < 0.001) {
                knock = direction.clone();
            }
            knock.normalize().multiply(power).setY(0.25 + Math.min(0.6, power * 0.2));
            living.setVelocity(knock);
            affected++;
        }
        return true;
    }

    private boolean isMainHandEmpty(Player player) {
        return player.getInventory().getItemInMainHand().getType().isAir();
    }

    private void spawnParticles(Location location, ParticlePreset preset) {
        Particle type = preset.type() == null ? Particle.CLOUD : preset.type();
        location.getWorld().spawnParticle(
                type,
                location,
                Math.max(0, preset.count()),
                preset.spreadX(),
                preset.spreadY(),
                preset.spreadZ(),
                preset.speed()
        );
    }

    private String stripColors(String text) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
    }

    private int resolveForceJumpSlot(AbilityPlayerProfile profile, AbilityDefinition definition) {
        for (Map.Entry<Integer, AbilityBinding> entry : profile.bindings().entrySet()) {
            AbilityBinding binding = entry.getValue();
            if (binding != null && definition.id().equalsIgnoreCase(binding.abilityId())) {
                return entry.getKey();
            }
        }
        return definition.activationSlot();
    }

    private int resolveBindingLevel(AbilityPlayerProfile profile, AbilityBinding binding) {
        int unlocked = profile.unlockedLevel(binding.abilityId());
        if (unlocked <= 0) {
            return 0;
        }
        if (binding.selectedLevel() <= 0) {
            return profile.selectedLevel(binding.abilityId());
        }
        return Math.max(1, Math.min(binding.selectedLevel(), unlocked));
    }

    private String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }
}
