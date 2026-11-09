package net.mandomc.gameplay.mobs;
import org.bukkit.configuration.ConfigurationSection;

/** First-pass Bossk tuning, in server ticks and blocks. */
public record DioxisSettings(int flightTicks, int cloudTicks, double radius, int stunTicks,
        int immunityTicks, int poisonTicks, int poisonAmplifier, int fakeHitInterval, double arcHeight) {
    public static DioxisSettings read(ConfigurationSection c) {
        if(c==null)throw new IllegalArgumentException("bossk.yml: missing dioxis settings");
        return new DioxisSettings(c.getInt("flight-ticks"),c.getInt("cloud-ticks"),c.getDouble("radius"),
                c.getInt("stun-ticks"),c.getInt("stun-immunity-ticks"),c.getInt("poison-ticks"),
                c.getInt("poison-amplifier"),c.getInt("fake-hit-interval"),c.getDouble("arc-height",0.65));
    }
    public DioxisSettings {
        if(flightTicks<10||flightTicks>60||cloudTicks<20||cloudTicks>400||!Double.isFinite(radius)||radius<1||radius>8
                ||!Double.isFinite(arcHeight)||arcHeight<0||arcHeight>2
                ||stunTicks<1||stunTicks>60||immunityTicks<stunTicks||immunityTicks>400||poisonTicks<20||poisonTicks>400
                ||poisonAmplifier<0||poisonAmplifier>1||fakeHitInterval<4||fakeHitInterval>20)
            throw new IllegalArgumentException("bossk.yml: dioxis values exceed supported bounds");
    }
}
