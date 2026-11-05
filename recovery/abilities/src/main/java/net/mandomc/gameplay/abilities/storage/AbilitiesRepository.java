package net.mandomc.gameplay.abilities.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.mandomc.core.storage.JsonRepository;
import net.mandomc.gameplay.abilities.model.AbilityBinding;
import net.mandomc.gameplay.abilities.model.AbilityClass;
import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;

/**
 * JSON-backed repository for per-player ability profiles.
 */
public class AbilitiesRepository extends JsonRepository<AbilityPlayerProfile, UUID> {
    public AbilitiesRepository(Plugin plugin) {
        super(plugin, "abilities/player_profiles.json");
    }

    @Override
    protected void populate(String json, Map<UUID, AbilityPlayerProfile> target) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (String playerKey : root.keySet()) {
            JsonObject profileObj = root.getAsJsonObject(playerKey);
            UUID playerId = UUID.fromString(playerKey);

            AbilityClass selectedClass = AbilityClass.fromInput(getAsString(profileObj, "selectedClass"))
                    .orElse(AbilityClass.UNSET);
            int skillTokens = getAsInt(profileObj, "skillTokens", 0);
            Map<String, Integer> unlockedLevels = readLevelMap(profileObj.getAsJsonObject("unlockedLevels"));
            Map<String, Integer> selectedLevels = readLevelMap(profileObj.getAsJsonObject("selectedLevels"));
            Map<Integer, AbilityBinding> bindings = readBindings(profileObj.getAsJsonObject("bindings"));

            target.put(playerId, new AbilityPlayerProfile(
                    playerId,
                    selectedClass,
                    skillTokens,
                    unlockedLevels,
                    selectedLevels,
                    bindings
            ));
        }
    }

    @Override
    protected String serialize(Map<UUID, AbilityPlayerProfile> data) {
        JsonObject root = new JsonObject();
        for (AbilityPlayerProfile profile : data.values()) {
            JsonObject profileObj = new JsonObject();
            AbilityClass selectedClass = profile.selectedClass() == null ? AbilityClass.UNSET : profile.selectedClass();
            profileObj.addProperty("selectedClass", selectedClass.name());
            profileObj.addProperty("skillTokens", profile.skillTokens());
            profileObj.add("unlockedLevels", writeLevelMap(profile.unlockedLevels()));
            profileObj.add("selectedLevels", writeLevelMap(profile.selectedLevels()));
            profileObj.add("bindings", writeBindings(profile.bindings()));
            root.add(profile.playerId().toString(), profileObj);
        }
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    @Override
    protected UUID idOf(AbilityPlayerProfile entity) {
        return entity.playerId();
    }

    public AbilityPlayerProfile getOrCreate(UUID playerId) {
        return getOrCreate(playerId, AbilityPlayerProfile::new);
    }

    private Map<String, Integer> readLevelMap(JsonObject json) {
        Map<String, Integer> levels = new HashMap<>();
        if (json == null) {
            return levels;
        }
        for (String key : json.keySet()) {
            levels.put(key, Math.max(0, json.get(key).getAsInt()));
        }
        return levels;
    }

    private JsonObject writeLevelMap(Map<String, Integer> levels) {
        JsonObject out = new JsonObject();
        levels.forEach((id, level) -> out.addProperty(id, Math.max(0, level)));
        return out;
    }

    private Map<Integer, AbilityBinding> readBindings(JsonObject json) {
        Map<Integer, AbilityBinding> bindings = new HashMap<>();
        if (json == null) {
            return bindings;
        }
        for (String key : json.keySet()) {
            int slot;
            try {
                slot = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                continue;
            }
            JsonObject bindingObj = json.getAsJsonObject(key);
            if (bindingObj == null) {
                continue;
            }
            String abilityId = getAsString(bindingObj, "abilityId");
            if (abilityId == null || abilityId.isBlank()) {
                continue;
            }
            int selectedLevel = getAsInt(bindingObj, "selectedLevel", 0);
            bindings.put(slot, new AbilityBinding(abilityId, selectedLevel));
        }
        return bindings;
    }

    private JsonObject writeBindings(Map<Integer, AbilityBinding> bindings) {
        JsonObject out = new JsonObject();
        bindings.forEach((slot, binding) -> {
            JsonObject obj = new JsonObject();
            obj.addProperty("abilityId", binding.abilityId());
            obj.addProperty("selectedLevel", Math.max(0, binding.selectedLevel()));
            out.add(String.valueOf(slot), obj);
        });
        return out;
    }

    private String getAsString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private int getAsInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
