package net.mandomc.gameplay.clans;

import java.util.Locale;

/** Ordered rank authority; the leader is derived from the clan's single leader UUID. */
public enum ClanRole {
    RECRUIT("Recruit"), MEMBER("Member"), OFFICER("Officer"), LEADER("Leader");
    private final String label;
    ClanRole(String label){this.label=label;}
    public String label(){return label;}
    public boolean manages(){return ordinal()>=OFFICER.ordinal();}
    public static ClanRole read(String value) {
        String id=value.toUpperCase(Locale.ROOT).replace('-','_');
        return switch(id){case "CO_LEADER","COLEADER"->OFFICER;default->valueOf(id);};
    }
}
