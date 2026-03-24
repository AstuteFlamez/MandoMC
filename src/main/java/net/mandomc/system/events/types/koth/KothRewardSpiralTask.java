package net.mandomc.system.events.types.koth;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public class KothRewardSpiralTask extends BukkitRunnable {

    private final KothEvent event;
    private final Location target;

    private double currentY;
    private double angle;

    public KothRewardSpiralTask(KothEvent event, Location target) {
        this.event = event;
        this.target = target.clone().add(0.5, 0.0, 0.5);
        this.currentY = target.getY() + 8.0;
        this.angle = 0.0;
    }

    @Override
    public void run() {
        World world = target.getWorld();
        if (world == null) {
            cancel();
            return;
        }

        double radius = 1.35;

        for (int i = 0; i < 6; i++) {
            double helixAngle = angle + (i * (Math.PI / 6.0));

            double x1 = target.getX() + Math.cos(helixAngle) * radius;
            double z1 = target.getZ() + Math.sin(helixAngle) * radius;

            double x2 = target.getX() + Math.cos(helixAngle + Math.PI) * radius;
            double z2 = target.getZ() + Math.sin(helixAngle + Math.PI) * radius;

            double y = currentY - (i * 0.12);

            world.spawnParticle(event.getSpiralParticle(), x1, y, z1, 1, 0, 0, 0, 0);
            world.spawnParticle(event.getSpiralParticle(), x2, y, z2, 1, 0, 0, 0, 0);
        }

        angle += Math.PI / 5.0;
        currentY -= 0.35;

        if (currentY <= target.getY() + 0.1) {
            event.spawnRewardChest();
            cancel();
        }
    }
}