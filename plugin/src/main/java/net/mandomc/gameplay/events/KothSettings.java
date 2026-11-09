package net.mandomc.gameplay.events;

import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

public record KothSettings(String world, double x, double y, double z, double radius,
                           double height, int captureTicks, int maximumTicks) {
    public static KothSettings read(ConfigurationSection c) {
        String world = c.getString("settings.world", "");
        if (NamespacedKey.fromString(world) == null) throw new IllegalArgumentException("events/koth.yml: invalid settings.world");
        double x = number(c,"x",-30000000,30000000), y = number(c,"y",-64,320), z = number(c,"z",-30000000,30000000);
        double radius = number(c,"radius",1,64), height = number(c,"y-tolerance",.5,32);
        int capture = integer(c,"capture-seconds",1,3600), maximum = integer(c,"maximum-seconds",capture,7200);
        return new KothSettings(world,x,y,z,radius,height,capture*20,maximum*20);
    }
    private static int integer(ConfigurationSection c,String key,int min,int max) {
        double value = number(c,key,min,max);
        if (value != Math.rint(value)) throw new IllegalArgumentException("events/koth.yml: settings."+key+" must be whole seconds");
        return (int)value;
    }
    private static double number(ConfigurationSection c,String key,double min,double max) {
        double value = c.getDouble("settings."+key,Double.NaN);
        if (!Double.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("events/koth.yml: invalid settings."+key);
        return value;
    }
    public boolean inside(double px,double py,double pz) {
        double dx=px-x,dz=pz-z;
        return dx*dx+dz*dz <= radius*radius && Math.abs(py-y) <= height;
    }
}
