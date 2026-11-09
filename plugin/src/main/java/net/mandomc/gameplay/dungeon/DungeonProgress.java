package net.mandomc.gameplay.dungeon;
import java.util.*;
/** Sole room authority. Only the last owned enemy advances; cache looting is independent. */
public final class DungeonProgress {
 public enum Phase {IDLE,OPENING,COMBAT,COMPLETE}
 private final int rooms;private int room;private Phase phase=Phase.IDLE;private final Set<UUID> mobs=new HashSet<>();
 public DungeonProgress(int rooms){if(rooms<1||rooms>32)throw new IllegalArgumentException("Invalid room count");this.rooms=rooms;}
 public void start(){if(phase!=Phase.IDLE)throw new IllegalStateException("Already started");room=1;phase=Phase.OPENING;}
 public void spawned(Collection<UUID> ids){if(phase!=Phase.OPENING||ids.isEmpty()||new HashSet<>(ids).size()!=ids.size())throw new IllegalStateException("Invalid room spawn");mobs.addAll(ids);phase=Phase.COMBAT;}
 public boolean died(UUID id){if(phase!=Phase.COMBAT||!mobs.remove(id)||!mobs.isEmpty())return false;if(room==rooms)phase=Phase.COMPLETE;else{room++;phase=Phase.OPENING;}return true;}
 public void reset(){mobs.clear();room=0;phase=Phase.IDLE;}
 public Phase phase(){return phase;}public int room(){return room;}public Set<UUID> mobs(){return Set.copyOf(mobs);}
}
