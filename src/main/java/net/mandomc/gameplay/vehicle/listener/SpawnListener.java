package net.mandomc.gameplay.vehicle.listener;

import java.util.UUID;

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

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;

import net.mandomc.core.config.MainConfig;
import net.mandomc.gameplay.vehicle.model.SeatConfig;
import net.mandomc.gameplay.vehicle.model.Vehicle;
import net.mandomc.gameplay.vehicle.model.VehicleData;
import net.mandomc.gameplay.vehicle.model.VehicleSkinOption;
import net.mandomc.gameplay.vehicle.config.VehicleConfigResolver;
import net.mandomc.gameplay.vehicle.manager.VehicleManager;
import net.mandomc.gameplay.vehicle.manager.VehicleSkinManager;

import java.util.List;
import net.mandomc.gameplay.vehicle.weapon.SpeederBike;
import net.mandomc.gameplay.vehicle.weapon.TieFighter;
import net.mandomc.gameplay.vehicle.weapon.WeaponSystem;
import net.mandomc.gameplay.vehicle.weapon.XWing;
import net.mandomc.core.LangManager;
import net.mandomc.core.integration.OptionalPluginSupport;
import net.mandomc.core.modules.server.VehicleModule;
import net.mandomc.server.items.ItemUtils;

/**
 * Handles vehicle spawning when a player right-clicks with a vehicle item.
 *
 * Validates spawn conditions, creates the backing entity and ModelEngine model,
 * assigns a weapon system, and registers the vehicle.
 */
public class SpawnListener implements Listener {

    private final MainConfig mainConfig;

    public SpawnListener(MainConfig mainConfig) {
        this.mainConfig = mainConfig;
    }

    /**
     * Handles right-click interactions to spawn a vehicle.
     *
     * @param event the player interact event
     */
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
            player.sendMessage(LangManager.get(
                    "vehicles.wrong-world",
                    "%world%",
                    mainConfig.getVehicleSpawnRestrictionDisplayWorld()
            ));
            return;
        }

        String itemId = ItemUtils.getItemId(item);
        if (itemId == null) {
            player.sendMessage(LangManager.get("vehicles.invalid-item"));
            return;
        }

        VehicleData vehicleData = createVehicleData(item);
        VehicleSkinOption activeSkin = VehicleSkinManager.resolveActiveSkin(item);
        WeaponSystem weaponSystem = createWeaponSystem(item);

        if (weaponSystem == null) {
            player.sendMessage(LangManager.get("vehicles.no-weapon-config"));
            return;
        }

        List<SeatConfig> seats = VehicleConfigResolver.getSeats(item);

        Vehicle vehicle = new Vehicle(weaponSystem, vehicleData, uuid, itemId);
        if (activeSkin != null) {
            vehicle.setSelectedSkinId(activeSkin.id());
        }
        vehicle.setSeats(seats);

        spawnVehicleEntities(player, world, location, vehicleData);
        registerVehicle(uuid, vehicle);
        consumeItem(inventory, item);

        player.sendMessage(LangManager.get("vehicles.deployed"));
    }

    /**
     * Returns true if the interaction is a right-click from the main hand.
     */
    private boolean isValidInteraction(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return false;
        Action action = event.getAction();
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    /**
     * Returns true if the player is allowed to spawn a vehicle.
     *
     * Fails if the item is null, not tagged as VEHICLE, or the player already
     * has an active vehicle.
     */
    private boolean canSpawnVehicle(Player player, UUID uuid, ItemStack item) {
        if (!OptionalPluginSupport.hasModelEngine() || !OptionalPluginSupport.hasWeaponMechanics()) {
            player.sendMessage(LangManager.get("vehicles.systems-unavailable"));
            return false;
        }
        if (item == null) return false;
        if (!ItemUtils.hasTag(item, "VEHICLE")) return false;

        if (VehicleModule.getActiveVehicles().containsKey(uuid)) {
            player.sendMessage(LangManager.get("vehicles.already-deployed"));
            return false;
        }

        return true;
    }

    /**
     * Returns true if the location is within the protected spawn zone.
     */
    private boolean isInSpawnZone(Location loc) {
        if (!loc.getWorld().getName().equalsIgnoreCase(mainConfig.getVehicleSpawnRestrictionWorld())) return false;

        double x = loc.getX();
        double z = loc.getZ();

        double minX = Math.min(mainConfig.getVehicleSpawnRestrictionX1(), mainConfig.getVehicleSpawnRestrictionX2());
        double maxX = Math.max(mainConfig.getVehicleSpawnRestrictionX1(), mainConfig.getVehicleSpawnRestrictionX2());
        double minZ = Math.min(mainConfig.getVehicleSpawnRestrictionZ1(), mainConfig.getVehicleSpawnRestrictionZ2());
        double maxZ = Math.max(mainConfig.getVehicleSpawnRestrictionZ1(), mainConfig.getVehicleSpawnRestrictionZ2());

        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    /**
     * Builds a VehicleData instance from the given vehicle item's config.
     */
    private VehicleData createVehicleData(ItemStack item) {
        double speed = VehicleConfigResolver.getSpeed(item);
        double scale = VehicleConfigResolver.getScale(item);
        VehicleSkinOption activeSkin = VehicleSkinManager.resolveActiveSkin(item);
        String modelKey = activeSkin != null
                ? activeSkin.modelKey()
                : VehicleConfigResolver.getModelKey(item);
        String movementSound = VehicleConfigResolver.getMovementSound(item);
        int movementSoundLength = VehicleConfigResolver.getMovementSoundLength(item);
        String displayName = VehicleConfigResolver.getDisplayName(item);
        int guiSize = VehicleConfigResolver.getGuiSize(item);
        ItemStack vehicleItem = activeSkin != null
                ? VehicleSkinManager.applySkinToItem(item, activeSkin)
                : item.clone();

        VehicleData data = new VehicleData(vehicleItem, speed, scale, modelKey);
        data.setMovementSound(movementSound);
        data.setMovementSoundLength(movementSoundLength);
        data.setDisplayName(displayName);
        data.setGuiSize(guiSize);

        return data;
    }

    /**
     * Selects the weapon system for the vehicle based on its model key.
     *
     * @return the weapon system, or null if the model key is unrecognized
     */
    private WeaponSystem createWeaponSystem(ItemStack item) {
        String vehicleId = VehicleConfigResolver.getVehicleId(item);
        if (vehicleId == null) return null;

        return switch (vehicleId.toLowerCase()) {
            case "xwing" -> new XWing();
            case "tiefighter" -> new TieFighter();
            case "speederbike" -> new SpeederBike();
            default -> null;
        };
    }

    /**
     * Spawns the vehicle's backing entity and attaches the ModelEngine model.
     */
    private void spawnVehicleEntities(Player player, World world, Location location, VehicleData vehicleData) {
        double scale = vehicleData.getScale();

        Pig entity = world.spawn(location, Pig.class);
        entity.setAI(false);
        entity.setGravity(true);
        VehicleManager.applyFallingVelocity(entity);
        entity.setInvisible(true);
        entity.setInvulnerable(false);
        entity.setPersistent(true);
        entity.setCollidable(false);

        LivingEntity living = entity;
        living.setSilent(true);

        AttributeInstance maxHealth = living.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(40);
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

    /**
     * Registers the vehicle into the active vehicle map.
     */
    private void registerVehicle(UUID uuid, Vehicle vehicle) {
        VehicleModule.registerVehicle(uuid, vehicle);
    }

    /**
     * Removes one item from the player's main hand after spawning.
     */
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
