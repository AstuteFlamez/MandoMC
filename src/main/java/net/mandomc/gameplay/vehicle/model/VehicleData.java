package net.mandomc.gameplay.vehicle.model;

import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import net.mandomc.gameplay.vehicle.rotation.BoneRotator;
import net.mandomc.gameplay.vehicle.rotation.RotationLimits;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.ItemStack;

public class VehicleData {

    private LivingEntity entity;
    private ActiveModel activeModel;
    private ModeledEntity modeledEntity;

    private String modelKey;
    private ItemStack item;

    private double speed;
    private double scale;

    private String movementSound;
    private int movementSoundLength;

    /** Human-readable name used as the title of the vehicle interact GUI. */
    private String displayName = "";

    /** Number of slots in the vehicle interact GUI (hardcoded to 54). */
    private int guiSize = 54;

    // --- Rotation config (aerial vehicles) ---
    private float rollSmoothing = 0.15f;
    private String rotatorBone = "body";
    private RotationLimits rotationLimits = RotationLimits.DEFAULT;
    private double accelerationPerTick = 0.08;
    private double decelerationPerTick = 0.10;
    private double boostMultiplier = 1.30;
    private int boostDurationTicks = 14;
    private int boostCooldownTicks = 80;
    private float boostShakeDegrees = 0.8f;
    private int statusCooldownTicks = 20;

    /** Runtime bone rotator, created on driver mount and cleared on dismount. */
    private BoneRotator boneRotator;

    public VehicleData(ItemStack item, double speed, double scale, String modelKey) {
        this.item = item;
        this.speed = speed;
        this.scale = scale;
        this.modelKey = modelKey;
    }

    public LivingEntity getEntity() {
        return entity;
    }

    public ActiveModel getActiveModel() {
        return activeModel;
    }

    public ModeledEntity getModeledEntity() {
        return modeledEntity;
    }

    public ItemStack getItem() {
        return item;
    }

    public double getSpeed() {
        return speed;
    }

    public double getScale() {
        return scale;
    }

    public String getModelKey() {
        return modelKey;
    }

    public String getMovementSound() {
        return movementSound;
    }

    public int getMovementSoundLength() {
        return movementSoundLength;
    }

    public void setEntity(LivingEntity entity) {
        this.entity = entity;
    }

    public void setActiveModel(ActiveModel activeModel) {
        this.activeModel = activeModel;
    }

    public void setModeledEntity(ModeledEntity modeledEntity) {
        this.modeledEntity = modeledEntity;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public void setScale(double scale) {
        this.scale = scale;
    }

    public void setModelKey(String modelKey) {
        this.modelKey = modelKey;
    }

    public void setMovementSound(String movementSound) {
        this.movementSound = movementSound;
    }

    public void setMovementSoundLength(int movementSoundLength) {
        this.movementSoundLength = movementSoundLength;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName != null ? displayName : "";
    }

    public int getGuiSize() {
        return guiSize;
    }

    public void setGuiSize(int guiSize) {
        this.guiSize = guiSize;
    }

    // --- Rotation accessors ---

    public float getRollSmoothing() {
        return rollSmoothing;
    }

    public void setRollSmoothing(float rollSmoothing) {
        this.rollSmoothing = rollSmoothing;
    }

    public String getRotatorBone() {
        return rotatorBone;
    }

    public void setRotatorBone(String rotatorBone) {
        this.rotatorBone = rotatorBone != null ? rotatorBone : "body";
    }

    public RotationLimits getRotationLimits() {
        return rotationLimits;
    }

    public void setRotationLimits(RotationLimits rotationLimits) {
        this.rotationLimits = rotationLimits != null ? rotationLimits : RotationLimits.DEFAULT;
    }

    public BoneRotator getBoneRotator() {
        return boneRotator;
    }

    public void setBoneRotator(BoneRotator boneRotator) {
        this.boneRotator = boneRotator;
    }

    public double getAccelerationPerTick() {
        return accelerationPerTick;
    }

    public void setAccelerationPerTick(double accelerationPerTick) {
        this.accelerationPerTick = Math.max(0, accelerationPerTick);
    }

    public double getDecelerationPerTick() {
        return decelerationPerTick;
    }

    public void setDecelerationPerTick(double decelerationPerTick) {
        this.decelerationPerTick = Math.max(0, decelerationPerTick);
    }

    public double getBoostMultiplier() {
        return boostMultiplier;
    }

    public void setBoostMultiplier(double boostMultiplier) {
        this.boostMultiplier = Math.max(1.0, boostMultiplier);
    }

    public int getBoostDurationTicks() {
        return boostDurationTicks;
    }

    public void setBoostDurationTicks(int boostDurationTicks) {
        this.boostDurationTicks = Math.max(0, boostDurationTicks);
    }

    public int getBoostCooldownTicks() {
        return boostCooldownTicks;
    }

    public void setBoostCooldownTicks(int boostCooldownTicks) {
        this.boostCooldownTicks = Math.max(0, boostCooldownTicks);
    }

    public float getBoostShakeDegrees() {
        return boostShakeDegrees;
    }

    public void setBoostShakeDegrees(float boostShakeDegrees) {
        this.boostShakeDegrees = Math.max(0f, boostShakeDegrees);
    }

    public int getStatusCooldownTicks() {
        return statusCooldownTicks;
    }

    public void setStatusCooldownTicks(int statusCooldownTicks) {
        this.statusCooldownTicks = Math.max(0, statusCooldownTicks);
    }
}
