package net.mandomc.gameplay.boba;

import org.bukkit.configuration.ConfigurationSection;

public record BobaEquipmentSettings(double speed, double steering, double launch, int launchTicks,
        int doubleSneakTicks, double range, int burstTicks, int emitInterval, double projectileSpeed,
        double radius, double damage, int damageInterval, int burnTicks, int cooldownTicks) {
    public static BobaEquipmentSettings read(ConfigurationSection c) {
        if (c.getInt("schema-version") != 1) throw new IllegalArgumentException("equipment/boba.yml: expected schema 1");
        return new BobaEquipmentSettings(number(c,"jetpack.speed",.1,2), number(c,"jetpack.steering",.01,1),
                number(c,"jetpack.launch",.1,1.5), integer(c,"jetpack.launch-ticks",1,20),
                integer(c,"jetpack.double-sneak-ticks",3,15), number(c,"flamethrower.range",1,24),
                integer(c,"flamethrower.burst-ticks",1,100), integer(c,"flamethrower.emit-interval",1,20),
                number(c,"flamethrower.projectile-speed",.1,2), number(c,"flamethrower.hit-radius",.1,1),
                number(c,"flamethrower.damage",.1,20), integer(c,"flamethrower.damage-interval",1,100),
                integer(c,"flamethrower.burn-ticks",0,200), integer(c,"flamethrower.cooldown-ticks",1,1200));
    }
    private static double number(ConfigurationSection c,String key,double min,double max) {
        double value=c.getDouble(key,Double.NaN);
        if (!Double.isFinite(value)||value<min||value>max) throw new IllegalArgumentException("equipment/boba.yml: invalid "+key);
        return value;
    }
    private static int integer(ConfigurationSection c,String key,int min,int max) {
        int value=c.getInt(key,-1);
        if (!c.isInt(key)||value<min||value>max) throw new IllegalArgumentException("equipment/boba.yml: invalid "+key);
        return value;
    }
}
