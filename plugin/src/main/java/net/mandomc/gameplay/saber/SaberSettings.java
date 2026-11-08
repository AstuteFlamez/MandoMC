package net.mandomc.gameplay.saber;

import org.bukkit.configuration.ConfigurationSection;

public record SaberSettings(String itemId, String model, String hilt, double throwDamage, int cooldown,
        double range, double speed, double returnSpeed, double radius, double capacity, double cost,
        double cooling, int recoveryDelay, int overheat, double halfAngle, int reflectionLimit) {
    public static SaberSettings read(ConfigurationSection c) {
        if (c.getInt("schema-version") != 1) throw new IllegalArgumentException("sabers.yml: expected schema-version 1");
        return new SaberSettings(text(c,"item-id"), text(c,"item-model"), text(c,"hilt-model"),
                number(c,"throw-damage",0,100), (int)number(c,"throw-cooldown-ticks",1,1200),
                number(c,"throw-range",1,32), number(c,"throw-speed",0.1,3), number(c,"throw-return-speed",0.1,4),
                number(c,"throw-hit-radius",0.1,1), number(c,"guard-capacity",1,1000), number(c,"guard-cost-per-hit",1,1000),
                number(c,"guard-cooling-per-tick",0.01,10), (int)number(c,"guard-recovery-delay-ticks",0,400),
                (int)number(c,"overheat-ticks",1,1200), number(c,"block-half-angle-degrees",1,90),
                (int)number(c,"reflection-limit",1,10));
    }
    private static String text(ConfigurationSection c, String k) {
        String v=c.getString(k); if(v==null || v.isBlank()) throw new IllegalArgumentException("sabers.yml: missing "+k); return v;
    }
    private static double number(ConfigurationSection c,String k,double min,double max) {
        double v=c.getDouble(k,Double.NaN);
        if(!Double.isFinite(v)||v<min||v>max) throw new IllegalArgumentException("sabers.yml: invalid "+k);
        return v;
    }
}
