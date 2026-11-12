package net.mandomc.gameplay.mobs;
import org.bukkit.configuration.ConfigurationSection;

/** Bossk's scripted health-damage budgets, in damage points (two per heart). */
public record BosskShotSettings(double singleDamage,double burstTotalDamage) {
    public static BosskShotSettings read(ConfigurationSection c) {
        return new BosskShotSettings(c==null?6:c.getDouble("single-damage",6),c==null?8:c.getDouble("burst-total-damage",8));
    }
    public BosskShotSettings {
        if(!Double.isFinite(singleDamage)||!Double.isFinite(burstTotalDamage)||singleDamage<=0||singleDamage>20||burstTotalDamage<=0||burstTotalDamage>20)
            throw new IllegalArgumentException("mobs/bossk.yml: shooting damage budgets must be finite and between 0 and 20");
    }
    public double perShot(boolean single){return single?singleDamage:burstTotalDamage/3;}
}
