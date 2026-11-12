package net.mandomc.integrations.models;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.events.AddModelEvent;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.events.ModelSetupEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.mandomc.gameplay.mobs.HerdAge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import com.ticxo.modelengine.api.mount.controller.MountControllerTypes;
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
            if (modeled != null) for (var model : modeled.getModels().values()) track(entity, model, modeled);
        }
        Bukkit.getScheduler().runTaskTimer(plugin, () -> models.entrySet().removeIf(entry -> {
            var entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || entry.getValue().isDestroyed()) return true;
            scale(entity, entry.getValue());
            equipment(entity, entry.getValue());
            return false;
        }), 1, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void attached(AddModelEvent event) {
        var entity = Bukkit.getEntity(event.getTarget().getBase().getUUID());
        if (entity != null) track(entity, event.getModel(), event.getTarget());
    }

    @EventHandler public void setup(ModelSetupEvent event) {
        var entity = Bukkit.getEntity(event.getModeledEntity().getBase().getUUID());
        if (entity != null) track(entity, event.getActiveModel(), event.getModeledEntity());
    }

    private void track(Entity entity, ActiveModel model, ModeledEntity modeled) {
        String id = model.getBlueprint().getName();
        if (!"bantha".equals(id) && !"dewback".equals(id)) return;
        entity.getPersistentDataContainer().set(HerdAge.KIND, org.bukkit.persistence.PersistentDataType.STRING,
                "bantha".equals(id) ? "Bantha" : "Dewback");
        models.put(entity.getUniqueId(), model);
        if (entity instanceof Horse && model.getPivotOverride().isEmpty()) {
            // The native mount screen resolves this exact horse on the client. A hidden base
            // is otherwise omitted entirely by ModelEngine's spawn-packet filter.
            // Keep the real horse as an invisible pivot; equipment remains model-owned.
            model.setPivotOverride(ModelEngineAPI.getPivotOverrideRegistry().getOrCreate(entity));
            ((Horse) entity).setInvisible(true); // Include invisibility in the initial native spawn metadata too.
            ModelEngineAPI.getEntityHandler().setForcedInvisible(entity, true);
        }
        scale(entity, model);
        equipment(entity, model);
        modeled.getBase().setMaxStepHeight(1);
    }

    public static boolean saddled(Horse horse) {
        var saddle = horse.getInventory().getSaddle();
        return saddle != null && saddle.getType() == Material.SADDLE && saddle.getAmount() == 1;
    }

    private static void equipment(Entity entity, ActiveModel model) {
        boolean equipped = entity instanceof Horse horse && horse.isAdult() && horse.isTamed() && saddled(horse);
        var saddle = model.getBones().get("saddle");
        if (saddle != null && saddle.isVisible() != equipped) saddle.setVisible(equipped);
        model.getMountManager().ifPresent(manager -> {
            manager.setCanDrive(equipped);
            manager.setCanRide(false);
            if (!equipped && manager.hasRiders()) manager.dismountAll();
        });
    }

    public static boolean ridden(Entity entity) {
        if (entity.getPassengers().stream().anyMatch(Player.class::isInstance)) return true;
        var modeled = ModelEngineAPI.getModeledEntity(entity);
        return modeled != null && modeled.getModels().values().stream().anyMatch(model ->
                model.getMountManager().map(manager -> manager.hasRiders()).orElse(false));
    }

    public boolean occupied(Horse horse) { return ridden(horse); }

    public boolean mount(Horse horse, Player player) {
        var model = models.get(horse.getUniqueId());
        if (model == null || occupied(horse) || !saddled(horse)) return false;
        horse.getPathfinder().stopPathfinding();
        equipment(horse, model);
        return model.getMountManager().map(manager -> manager.mountDriver(player, MountControllerTypes.WALKING)).orElse(false);
    }

    public void close() {
        for (var model : models.values()) model.getMountManager().ifPresent(manager -> manager.dismountAll());
        models.clear();
    }

    public static void scale(Entity entity, ActiveModel model) {
        double scale = HerdAge.scale(entity);
        if (model.getScale().x() != scale) model.setScale(scale);
        if (model.getHitboxScale().x() != scale) model.setHitboxScale(scale);
    }
}
