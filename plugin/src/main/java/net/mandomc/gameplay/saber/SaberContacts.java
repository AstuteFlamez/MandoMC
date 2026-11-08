package net.mandomc.gameplay.saber;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** A continuous overlap is one contact; leaving and crossing again permits another hit. */
public final class SaberContacts {
    private final Set<UUID> touching = new HashSet<>();

    public boolean enter(UUID victim) {
        return touching.add(victim);
    }

    public void retain(Set<UUID> stillTouching) {
        touching.retainAll(stillTouching);
    }
}
