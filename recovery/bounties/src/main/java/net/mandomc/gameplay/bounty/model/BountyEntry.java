package net.mandomc.gameplay.bounty.model;

import java.util.UUID;

public class BountyEntry {

    private UUID placer;
    private double amount;
    private long placedAt;

    public BountyEntry(UUID placer, double amount) {
        this.placer = placer;
        this.amount = amount;
        this.placedAt = System.currentTimeMillis();
    }

    public UUID getPlacer() {
        return placer;
    }

    public double getAmount() {
        return amount;
    }

    public long getPlacedAt() {
        return placedAt;
    }
}