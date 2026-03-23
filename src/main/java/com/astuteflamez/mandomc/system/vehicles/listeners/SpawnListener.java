package com.astuteflamez.mandomc.system.vehicles.listeners;

import com.astuteflamez.mandomc.core.MandoMC;
import com.astuteflamez.mandomc.system.items.ItemUtils;
import com.astuteflamez.mandomc.system.vehicles.Vehicle;
import com.astuteflamez.mandomc.system.vehicles.VehicleData;
import com.astuteflamez.mandomc.system.vehicles.managers.VehicleManager;
import com.astuteflamez.mandomc.system.vehicles.weapons.SpeederBike;
import com.astuteflamez.mandomc.system.vehicles.weapons.TieFighter;
import com.astuteflamez.mandomc.system.vehicles.weapons.WeaponSystem;
import com.astuteflamez.mandomc.system.vehicles.weapons.XWing;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

public class SpawnListener implements Listener {

    private static final String SPAWN_WORLD = "world";

    private static final int X1 = 703;
    private static final int Z1 = -176;

    private static final int X2 = 672;
    private static final int Z2 = -145;

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {

        if (!isValidInteraction(event)) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        PlayerInventory inventory = player.getInventory();
        ItemStack item = inventory.getItemInMainHand();

        if (!canSpawnVehicle(player, uuid, item)) return;

        Location location = player.getLocation();
        World world = player.getWorld();

        if (isInSpawnZone(location)) {

            player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cVehicles can only be deployed in Earth.");

            return;
        }

        VehicleData vehicleData = createVehicleData(item);
        WeaponSystem weaponSystem = createWeaponSystem(item);

        if (weaponSystem == null) {
            player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cThis vehicle has no weapon system configured.");
            return;
        }

        Vehicle vehicle = new Vehicle(weaponSystem, vehicleData, uuid);
        vehicle.getVehicleData().setItem(item);

        spawnVehicleEntities(player, world, location, vehicleData);

        registerVehicle(uuid, vehicle);
        consumeItem(inventory, item);

        player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §aVehicle deployed.");
    }

    private boolean isValidInteraction(PlayerInteractEvent event) {

        if (event.getHand() != EquipmentSlot.HAND) return false;

        Action action = event.getAction();

        return action == Action.RIGHT_CLICK_AIR
                || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean canSpawnVehicle(Player player, UUID uuid, ItemStack item) {

        if (item == null) return false;

        if (!ItemUtils.hasTag(item, "VEHICLE")) return false;

        if (MandoMC.activeVehicles.containsKey(uuid)) {

            player.sendMessage("§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cYou already have a vehicle deployed.");
            return false;
        }

        return true;
    }

    private boolean isInSpawnZone(Location loc) {

        if (!loc.getWorld().getName().equalsIgnoreCase(SPAWN_WORLD)) {
            return false;
        }

        double x = loc.getX();
        double z = loc.getZ();

        double minX = Math.min(X1, X2);
        double maxX = Math.max(X1, X2);

        double minZ = Math.min(Z1, Z2);
        double maxZ = Math.max(Z1, Z2);

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    private VehicleData createVehicleData(ItemStack item) {

        double speed = VehicleManager.getSpeed(item);
        double scale = VehicleManager.getScale(item);
        String modelKey = VehicleManager.getModelKey(item);
        String movementSound = VehicleManager.getMovementSound(item);
        int movementSoundLength = VehicleManager.getMovementSoundLength(item);

        VehicleData data = new VehicleData(item, speed, scale, modelKey);

        data.setMovementSound(movementSound);
        data.setMovementSoundLength(movementSoundLength);

        return data;
    }

    private WeaponSystem createWeaponSystem(ItemStack item) {

        String modelKey = VehicleManager.getModelKey(item);

        switch (modelKey.toLowerCase()) {

            case "xwing":
                return new XWing();

            case "tiefighter":
                return new TieFighter();

            case "speederbike":
                return new SpeederBike();

            default:
                return null;
        }
    }

    private void spawnVehicleEntities(Player player, World world, Location location, VehicleData vehicleData) {

        double scale = vehicleData.getScale();

        Pig entity = world.spawn(location, Pig.class);
        entity.setAI(false);
        entity.setGravity(true);
        entity.setInvisible(true);
        entity.setInvulnerable(false);
        entity.setPersistent(true);
        entity.setCollidable(false);

        LivingEntity living = entity;

        living.setSilent(true);

        AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);

        if (maxHealth != null) {
            maxHealth.setBaseValue(40);
        }

        living.setHealth(40);

        ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(entity);
        ActiveModel activeModel = ModelEngineAPI.createActiveModel(vehicleData.getModelKey());

        activeModel.setScale(scale);
        activeModel.setHitboxScale(scale);

        modeledEntity.addModel(activeModel, true);
        modeledEntity.getBase().setMaxStepHeight(1.0);

        vehicleData.setActiveModel(activeModel);
        vehicleData.setModeledEntity(modeledEntity);
        vehicleData.setEntity(entity);
    }

    private void registerVehicle(UUID uuid, Vehicle vehicle) {

        MandoMC.activeVehicles.put(uuid, vehicle);
    }

    private void consumeItem(PlayerInventory inventory, ItemStack item) {

        int amount = item.getAmount();

        if (amount > 1) {

            item.setAmount(amount - 1);
            inventory.setItemInMainHand(item);

        } else {

            inventory.setItemInMainHand(null);
        }
    }
}