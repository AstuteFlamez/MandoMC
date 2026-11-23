package net.mandomc.gameplay.guide;

import java.util.Comparator;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Mannequin;

public final class DroidLook {
    private DroidLook() {}

    public static void update(Mannequin droid, Location anchor) {
        var nearest = droid.getWorld().getPlayers().stream()
                .filter(player -> !player.isDead() && player.getGameMode() != GameMode.SPECTATOR)
                .filter(player -> player.getLocation().distanceSquared(anchor) < 25)
                .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(anchor)));
        Location look = nearest.map(player -> anchor.clone().setDirection(
                player.getEyeLocation().toVector().subtract(droid.getEyeLocation().toVector()))).orElse(anchor);
        droid.setRotation(look.getYaw(), look.getPitch());
        droid.setBodyYaw(look.getYaw());
    }
}
