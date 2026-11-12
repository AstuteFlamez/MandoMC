package net.mandomc.integrations.models;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.events.AddModelEvent;
import com.ticxo.modelengine.api.model.ActiveModel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.mandomc.gameplay.mobs.HerdAge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/** Reuses adult geometry and UVs. Age changes affect rendering and collision together. */
public final class HerdModels implements Listener {
    private final Map<UUID, ActiveModel> models = new HashMap<>();

    public HerdModels(JavaPlugin plugin) {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Covers models already attached before this listener, without repeatedly scanning worlds.
        for (var world : Bukkit.getWorlds()) for (var entity : world.getLivingEntities()) {
            var modeled = ModelEngineAPI.getModeledEntity(entity);
            if (modeled != null) for (var model : modeled.getModels().values()) track(entity, model);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, () -> models.entrySet().removeIf(entry -> {
            var entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || entry.getValue().isDestroyed()) return true;
            scale(entity, entry.getValue());
            return false;
        }), 1, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void attached(AddModelEvent event) {
        var entity = Bukkit.getEntity(event.getTarget().getBase().getUUID());
        if (entity != null) track(entity, event.getModel());
    }

    private void track(Entity entity, ActiveModel model) {
        String id = model.getBlueprint().getName();
        if (!"bantha".equals(id) && !"dewback".equals(id)) return;
        entity.getPersistentDataContainer().set(HerdAge.KIND, org.bukkit.persistence.PersistentDataType.STRING,
                "bantha".equals(id) ? "Bantha" : "Dewback");
        models.put(entity.getUniqueId(), model);
        scale(entity, model);
    }

    public static void scale(Entity entity, ActiveModel model) {
        double scale = HerdAge.scale(entity);
        if (model.getScale().x() != scale) model.setScale(scale);
        if (model.getHitboxScale().x() != scale) model.setHitboxScale(scale);
    }
}
