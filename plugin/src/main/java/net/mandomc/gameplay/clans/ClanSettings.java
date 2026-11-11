package net.mandomc.gameplay.clans;

import org.bukkit.configuration.ConfigurationSection;

public record ClanSettings(int members, int chunks, int raidThreshold, long raidMillis,
                           long inactiveMillis, long lootMillis, long resetMillis, int restoreBudget,
                           boolean friendlyFire) {
    public static ClanSettings read(ConfigurationSection config) {
        if(config.getInt("schema-version")!=1)throw new IllegalArgumentException("clans/settings.yml: invalid schema");
        return new ClanSettings(value(config,"max-members",1,100),value(config,"max-claims",1,100),
                value(config,"raid-warpoints",1,1000),value(config,"raid-seconds",1,86400)*1000L,
                value(config,"inactive-seconds",1,31536000)*1000L,value(config,"public-loot-seconds",1,604800)*1000L,
                value(config,"wilderness-reset-seconds",1,31536000)*1000L,
                value(config,"restore-blocks-per-step",1,256),config.getBoolean("friendly-fire",false));
    }
    private static int value(ConfigurationSection c,String key,int min,int max) {
        int value=c.getInt(key,-1);
        if(value<min||value>max)throw new IllegalArgumentException("clans/settings.yml: invalid "+key);
        return value;
    }
}
