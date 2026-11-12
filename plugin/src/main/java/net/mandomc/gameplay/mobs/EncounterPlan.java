package net.mandomc.gameplay.mobs;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Rolls a whole encounter before world mutations, so caps cannot truncate its composition. */
public record EncounterPlan(List<Member> members, int leaderLevel) {
    public record Member(String type, int level) {}
    public enum Kind { PATROL, BANTHA, DEWBACK, MASSIFF }

    public EncounterPlan {
        members = List.copyOf(members);
    }

    /** Independent opportunities preserve the legacy wildlife rate while increasing only free patrol slots. */
    public static List<Kind> opportunities(RandomGenerator random, SandySettings settings, boolean patrolAvailable) {
        var result = new ArrayList<Kind>();
        if (patrolAvailable && random.nextDouble() < settings.patrolChance()) result.add(Kind.PATROL);
        if (random.nextDouble() < settings.wildlifeChance()) result.add(Kind.values()[1 + random.nextInt(3)]);
        return List.copyOf(result);
    }

    public static EncounterPlan roll(Kind kind, RandomGenerator random, SandySettings settings) {
        int leader = random.nextInt(settings.minLevel(), settings.maxLevel() + 1);
        var types = new ArrayList<String>();
        if (kind == Kind.PATROL) {
            int raiders = random.nextInt(settings.raidersMin(), settings.raidersMax() + 1);
            // Both accepted variants appear in every patrol.
            types.add("TuskenRaiderSniper");
            types.add("TuskenRaiderMelee");
            for (int i = 2; i < raiders; i++) {
                types.add(random.nextBoolean() ? "TuskenRaiderSniper" : "TuskenRaiderMelee");
            }
            var companions = new ArrayList<String>();
            for (int i = random.nextInt(settings.banthasMax() + 1); i > 0; i--) companions.add("Bantha");
            for (int i = random.nextInt(settings.massiffsMax() + 1); i > 0; i--) companions.add("Massiff");
            // Shuffle before filling the remaining budget, so neither species always wins the cap.
            while (!companions.isEmpty() && types.size() < settings.groupCap()) {
                types.add(companions.remove(random.nextInt(companions.size())));
            }
        } else {
            String type = switch (kind) {
                case BANTHA -> "Bantha";
                case DEWBACK -> "Dewback";
                case MASSIFF -> "Massiff";
                default -> throw new IllegalArgumentException("Unknown encounter");
            };
            int size = random.nextInt(settings.wildlifeMin(), settings.wildlifeMax() + 1);
            for (int i = 0; i < size; i++) types.add(type);
        }
        var members = new ArrayList<Member>();
        for (int i = 0; i < types.size(); i++) {
            int level = i == 0 ? leader : Math.clamp(leader + random.nextInt(-1, 2),
                    settings.minLevel(), settings.maxLevel());
            members.add(new Member(types.get(i), level));
        }
        return new EncounterPlan(members, leader);
    }

    public boolean fits(int nearby, int loaded, SandySettings settings) {
        return nearby + members.size() <= settings.localCap() && loaded + members.size() <= settings.worldCap();
    }
}
