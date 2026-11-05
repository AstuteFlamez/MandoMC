package net.mandomc.server.events.types.koth;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayers;
import com.massivecraft.factions.Faction;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KothActiveTask extends BukkitRunnable {

    private static final int PERIOD_TICKS = 10;

    private final KothEvent event;
    private final Set<UUID> bossBarPlayers = new HashSet<>();

    private String currentContestantKey;
    private int captureTicks;
    private boolean contestedLastTick;
    private int particlePhase;
    private static final String[] VANISH_METADATA_KEYS = {
            "vanished",
            "essentials:vanished",
            "PremiumVanish.IsVanished"
    };

    public KothActiveTask(KothEvent event) {
        this.event = event;
    }

    @Override
    public void run() {
        Location center = event.getCenter();
        if (center == null || center.getWorld() == null) {
            cancel();
            return;
        }

        World world = center.getWorld();

        refreshBossBarPlayers(world);
        spawnBoundaryParticles(world, center, event.getRadius(), event.getBoundaryParticle());

        Map<String, Contestant> contestants = getContestantsInside(world, center, event.getRadius(), event.getYTolerance());

        if (contestants.size() == 1) {
            Contestant contestant = contestants.values().iterator().next();

            if (currentContestantKey == null || !currentContestantKey.equals(contestant.key())) {
                currentContestantKey = contestant.key();
                captureTicks = 0;
                contestedLastTick = false;

                sendWorldMessage(
                        world,
                        event.getCaptureMessage().replace("%player%", contestant.displayName())
                );
            }

            captureTicks += PERIOD_TICKS;
            updateBossBarCapturing(contestant.displayName(), captureTicks, event.getCaptureSeconds() * 20);

            if (captureTicks >= event.getCaptureSeconds() * 20) {
                event.completeCapture(contestant.representative(), contestant.displayName());
                cancel();
            }

            return;
        }

        if (contestants.size() > 1) {
            if (!contestedLastTick) {
                sendWorldMessage(world, event.getContestedMessage());
            }

            contestedLastTick = true;
            currentContestantKey = null;
            captureTicks = 0;
            updateBossBarContested();
            return;
        }

        contestedLastTick = false;
        currentContestantKey = null;
        captureTicks = 0;
        updateBossBarWaiting();
    }

    private Map<String, Contestant> getContestantsInside(World world, Location center, double radius, double yTolerance) {
        double radiusSquared = radius * radius;
        Map<String, Contestant> contestants = new LinkedHashMap<>();

        for (Player player : world.getPlayers()) {
            if (!isEligible(player)) continue;

            Location loc = player.getLocation();
            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            double dy = Math.abs(loc.getY() - center.getY());
            double distSquared = (dx * dx + dz * dz);
            boolean inside = distSquared <= radiusSquared && dy <= yTolerance;

            if (!inside) {
                continue;
            }

            Contestant contestant = resolveContestant(player);
            contestants.putIfAbsent(contestant.key(), contestant);
        }

        return contestants;
    }

    private boolean isEligible(Player player) {
        if (player == null || !player.isOnline()) return false;
        if (player.isDead()) return false;

        GameMode gameMode = player.getGameMode();
        if (gameMode == GameMode.SPECTATOR || gameMode == GameMode.CREATIVE) {
            return false;
        }

        return !isVanished(player);
    }

    private boolean isVanished(Player player) {
        for (String metadataKey : VANISH_METADATA_KEYS) {
            if (hasTrueMetadata(player, metadataKey)) {
                return true;
            }
        }
        return false;
    }

    private Contestant resolveContestant(Player player) {
        FPlayer fPlayer = FPlayers.getInstance().getByPlayer(player);

        if (fPlayer != null) {
            Faction faction = fPlayer.getFaction();

            if (faction != null && isRealFaction(faction)) {
                return new Contestant("faction:" + faction.getId(), faction.getTag(), player);
            }
        }

        return new Contestant("player:" + player.getUniqueId(), player.getName(), player);
    }

    private boolean isRealFaction(Faction faction) {
        try {
            if (faction.isWilderness()) return false;
        } catch (Throwable ignored) {
        }

        try {
            if (faction.isSafeZone()) return false;
        } catch (Throwable ignored) {
        }

        try {
            if (faction.isWarZone()) return false;
        } catch (Throwable ignored) {
        }

        String tag = faction.getTag();
        if (tag == null) return false;

        String lowered = tag.toLowerCase();
        return !lowered.equals("wilderness")
                && !lowered.equals("safezone")
                && !lowered.equals("warzone");
    }

    private void refreshBossBarPlayers(World world) {
        if (event.getBossBar() == null) return;
        Set<UUID> desiredPlayers = new HashSet<>();
        for (Player player : world.getPlayers()) {
            if (isEligible(player)) {
                UUID playerId = player.getUniqueId();
                desiredPlayers.add(playerId);
                if (!bossBarPlayers.contains(playerId)) {
                    event.getBossBar().addPlayer(player);
                    bossBarPlayers.add(playerId);
                }
            }
        }

        for (UUID tracked : new HashSet<>(bossBarPlayers)) {
            if (desiredPlayers.contains(tracked)) {
                continue;
            }
            Player player = Bukkit.getPlayer(tracked);
            if (player != null) {
                event.getBossBar().removePlayer(player);
            }
            bossBarPlayers.remove(tracked);
        }
    }

    private void updateBossBarCapturing(String contestantName, int captureTicks, int neededTicks) {
        if (event.getBossBar() == null) return;

        double progress = Math.min(1.0, Math.max(0.0, (double) captureTicks / neededTicks));
        event.getBossBar().setProgress(progress);
        event.getBossBar().setColor(BarColor.GREEN);
        event.getBossBar().setTitle("§6KOTH §7- §e" + contestantName + " §7capturing... §f" + (int) Math.round(progress * 100) + "%");
    }

    private void updateBossBarContested() {
        if (event.getBossBar() == null) return;

        event.getBossBar().setProgress(1.0);
        event.getBossBar().setColor(BarColor.RED);
        event.getBossBar().setTitle("§cKOTH §7- Contested!");
    }

    private void updateBossBarWaiting() {
        if (event.getBossBar() == null) return;

        event.getBossBar().setProgress(0.0);
        event.getBossBar().setColor(BarColor.BLUE);
        event.getBossBar().setTitle("§6KOTH §7- Hold the hill!");
    }

    private void spawnBoundaryParticles(World world, Location center, double radius, Particle particle) {
        int points = 20;
        double offset = particlePhase % 2 == 0 ? 0.0 : (Math.PI / points);

        for (int i = 0; i < points; i++) {
            double angle = (2 * Math.PI * i / points) + offset;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            double y = center.getY() + 0.15;

            world.spawnParticle(particle, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        particlePhase++;
    }

    private void sendWorldMessage(World world, String message) {
        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }

    private record Contestant(String key, String displayName, Player representative) {
    }

    private boolean hasTrueMetadata(Player player, String metadataKey) {
        if (!player.hasMetadata(metadataKey)) {
            return false;
        }
        for (MetadataValue value : player.getMetadata(metadataKey)) {
            try {
                if (value.asBoolean()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }
}