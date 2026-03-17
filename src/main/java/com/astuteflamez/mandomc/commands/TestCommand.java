package com.astuteflamez.mandomc.commands;

import com.astuteflamez.mandomc.MandoMC;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.animation.handler.AnimationHandler;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.UUID;

public class TestCommand implements CommandExecutor {

    private final MandoMC plugin;

    private final HashMap<UUID, Entity> entities = new HashMap<>();
    private final HashMap<UUID, ActiveModel> models = new HashMap<>();
    private final HashMap<UUID, Integer> state = new HashMap<>();

    public TestCommand(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        UUID uuid = player.getUniqueId();
        int stage = state.getOrDefault(uuid, 0);

        // ===============================
        // STAGE 0 → SPAWN + MOUNT ANIMATION
        // ===============================
        if (stage == 0) {

            Pig entity = player.getWorld().spawn(player.getLocation(), Pig.class);
            entity.setInvisible(true);
            entity.setAI(false);

            ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(entity);
            ActiveModel model = ModelEngineAPI.createActiveModel("xwing");

            modeledEntity.addModel(model, true);

            // Play "mount" animation
            AnimationHandler handler = model.getAnimationHandler();
            handler.playAnimation("mount", 0.3, 0.3, 1, true);

            entities.put(uuid, entity);
            models.put(uuid, model);
            state.put(uuid, 1);

            player.sendMessage("Spawned X-Wing + mount animation");

            return true;
        }

        // ===============================
        // STAGE 1 → DISMOUNT ANIMATION
        // ===============================
        if (stage == 1) {

            ActiveModel model = models.get(uuid);

            if (model != null) {
                AnimationHandler handler = model.getAnimationHandler();

                // Kill mount instantly
                handler.stopAnimation("mount");

                // Play dismount cleanly
                handler.playAnimation("dismount", 0.2, 0.2, 1, false);
            }

            state.put(uuid, 0);

            player.sendMessage("Dismount animation played");

            return true;
        }

        return true;
    }
}