package com.astuteflamez.mandomc.features.vehicles;

import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
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
}