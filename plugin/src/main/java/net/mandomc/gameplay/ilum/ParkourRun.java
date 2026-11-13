package net.mandomc.gameplay.ilum;

import java.util.UUID;

public record ParkourRun(UUID id, int checkpoint, boolean ranked) {
    public ParkourRun {
        if (id==null || checkpoint<0 || checkpoint>4) throw new IllegalArgumentException("Invalid parkour run");
    }
    public ParkourRun reach(int number) {
        return number==checkpoint+1 ? new ParkourRun(id,number,ranked) : this;
    }
    public boolean canFinish() { return checkpoint==4; }
    public ParkourRun practice() { return new ParkourRun(id,checkpoint,false); }
    public static boolean rewardReady(long lastReward, long now, long cooldownMillis) {
        return lastReward==0 || now>=lastReward && now-lastReward>=cooldownMillis;
    }
}
