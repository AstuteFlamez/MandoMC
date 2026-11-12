package net.mandomc.gameplay.mobs;
import org.bukkit.configuration.ConfigurationSection;
/** Bounded debris budget and range for the shared Rancor effects. */
public record RancorSlamSettings(double radius,double ringStep,int ringInterval,int lifetime,int maxFragments,int maxWaves) {
    public static RancorSlamSettings read(ConfigurationSection c) {
        if(c==null)throw new IllegalArgumentException("mobs/rancor.yml: missing slam");
        return new RancorSlamSettings(c.getDouble("radius"),c.getDouble("ring-step"),c.getInt("ring-interval-ticks"),c.getInt("fragment-lifetime-ticks"),c.getInt("max-fragments"),c.getInt("max-waves"));
    }
    public RancorSlamSettings {
        if(!Double.isFinite(radius)||radius<2||radius>20||!Double.isFinite(ringStep)||ringStep<1||ringStep>4
                ||ringInterval<1||ringInterval>5||lifetime<10||lifetime>40||maxFragments<16||maxFragments>512||maxWaves<1||maxWaves>8)
            throw new IllegalArgumentException("mobs/rancor.yml: slam visual budget exceeds supported bounds");
    }
}
