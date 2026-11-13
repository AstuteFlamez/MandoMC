package net.mandomc.gameplay.jawa;

import java.util.*;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.ConfigurationSection;

/** Independent tick deadlines; no shared group phase or repeating global fire pulse. */
public final class JawaAttackClock {
    public record Settings(int firstMin,int firstMax,int cadenceMin,int cadenceMax) {
        public static Settings read(ConfigurationSection c) {
            var value=new Settings(c.getInt("initial-min-ticks"),c.getInt("initial-max-ticks"),c.getInt("cadence-min-ticks"),c.getInt("cadence-max-ticks"));
            if(value.firstMin<1 || value.firstMax<value.firstMin || value.firstMax>100 || value.cadenceMin<52 || value.cadenceMax<value.cadenceMin || value.cadenceMax>200)
                throw new IllegalArgumentException("mobs/jawas.yml: invalid attack timing; cadence must allow the full 51-tick action");
            return value;
        }
    }
    private record Deadline(UUID target,long next) { }
    private final Map<UUID,Deadline> deadlines=new HashMap<>();
    private final Settings settings;
    private final RandomGenerator random;
    public JawaAttackClock(Settings settings,RandomGenerator random){this.settings=settings;this.random=random;}
    public boolean ready(UUID jawa,UUID target,long tick) {
        var current=deadlines.get(jawa);
        if(current==null || !current.target().equals(target)) {
            deadlines.put(jawa,new Deadline(target,tick+random.nextInt(settings.firstMin(),settings.firstMax()+1)));return false;
        }
        if(tick<current.next())return false;
        deadlines.put(jawa,new Deadline(target,tick+random.nextInt(settings.cadenceMin(),settings.cadenceMax()+1)));return true;
    }
    public void disengage(UUID jawa){deadlines.remove(jawa);}
    public void clear(){deadlines.clear();}
}
