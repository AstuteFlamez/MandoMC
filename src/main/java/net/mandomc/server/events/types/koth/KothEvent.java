package net.mandomc.server.events.types.koth;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import net.mandomc.MandoMC;
import net.mandomc.server.events.AbstractGameEvent;
import net.mandomc.server.events.model.EventDefinition;
import net.mandomc.server.events.EventManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KothEvent extends AbstractGameEvent {

    private final EventDefinition definition;

    private EventManager manager;
    private Location center;
    private double radius;
    private double yTolerance;
    private int captureSeconds;

    private BossBar bossBar;
    private KothActiveTask activeTask;

    private String startMessage;
    private String captureMessage;
    private String contestedMessage;
    private String winnerMessage;
    private String chestSpawnMessage;
    private String chestClaimedMessage;
    private String timeoutMessage;

    private Particle boundaryParticle;
    private Particle spiralParticle;

    private boolean captured;

    /*
     * Reward chest state
     */
    private static Location rewardChestLocation;
    private static List<String> rewardCommands = new ArrayList<>();
    private static String rewardClaimedMessage = "&e%player% &7claimed the KOTH reward chest!";

    public KothEvent(EventDefinition definition) {
        super(definition.getId(), definition.getDisplayName());
        this.definition = definition;
    }

    @Override
    protected void onStart(EventManager manager) {
        this.manager = manager;
        this.captured = false;
        this.timeoutMessage = color(stringSetting("timeout-message",
                "&cKOTH expired with no winner."));

        clearRewardChest();

        World world = Bukkit.getWorld(stringSetting("world", "world"));
        if (world == null) {
            Bukkit.getLogger().warning("[MandoMC] KOTH world not found.");
            manager.forceEndActiveEvent(false);
            return;
        }

        this.center = new Location(
                world,
                doubleSetting("x", 0.0),
                doubleSetting("y", 64.0),
                doubleSetting("z", 0.0)
        );

        this.radius = doubleSetting("radius", 8.0);
        this.yTolerance = doubleSetting("y-tolerance", 4.0);
        this.captureSeconds = intSetting("capture-seconds", 20);

        this.startMessage = color(stringSetting("start-message",
                "&6&lKOTH &7has started at &f(%x%, %y%, %z%)&7!"));
        this.captureMessage = color(stringSetting("capture-message",
                "&e%player% &7is capturing the hill!"));
        this.contestedMessage = color(stringSetting("contested-message",
                "&cThe hill is contested!"));
        this.winnerMessage = color(stringSetting("winner-message",
                "&a%player% &7captured the hill!"));
        this.chestSpawnMessage = color(stringSetting("chest-spawn-message",
                "&6A reward chest has appeared at the hill!"));
        this.chestClaimedMessage = color(stringSetting("chest-claimed-message",
                "&e%player% &7claimed the KOTH reward chest!"));

        rewardClaimedMessage = this.chestClaimedMessage;
        rewardCommands = stringListSetting("reward-commands");

        this.boundaryParticle = parseParticle(stringSetting("boundary-particle", "FLAME"), Particle.FLAME);
        this.spiralParticle = parseParticle(stringSetting("spiral-particle", "END_ROD"), Particle.END_ROD);

        this.bossBar = Bukkit.createBossBar(
                color(stringSetting("bossbar-title", "&6KOTH &7- Hold the hill!")),
                parseBarColor(stringSetting("bossbar-color", "BLUE")),
                parseBarStyle(stringSetting("bossbar-style", "SOLID"))
        );
        this.bossBar.setProgress(0.0);

        broadcast(formatCoordinates(startMessage, center));

        this.activeTask = new KothActiveTask(this);
        this.activeTask.runTaskTimer(MandoMC.getInstance(), 0L, 10L);
    }

    @Override
    protected void onEnd(EventManager manager) {
        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        if (!captured) {
            broadcast(timeoutMessage);
            return;
        }

        if (center != null && center.getWorld() != null) {
            new KothRewardSpiralTask(this, center.clone()).runTaskTimer(MandoMC.getInstance(), 0L, 2L);
        }
    }

    public void completeCapture(Player player, String displayName) {
        this.captured = true;
        broadcast(winnerMessage.replace("%player%", displayName));
        manager.forceEndActiveEvent(true);
    }

    public void spawnRewardChest() {
        if (center == null || center.getWorld() == null) return;

        Location chestLoc = center.getBlock().getLocation();
        chestLoc.getBlock().setType(parseChestMaterial(stringSetting("chest-material", "CHEST")));

        rewardChestLocation = chestLoc;
        broadcast(chestSpawnMessage);
    }

    public static boolean isRewardChest(Location location) {
        if (rewardChestLocation == null || location == null) return false;
        if (rewardChestLocation.getWorld() == null || location.getWorld() == null) return false;

        return rewardChestLocation.getWorld().equals(location.getWorld())
                && rewardChestLocation.getBlockX() == location.getBlockX()
                && rewardChestLocation.getBlockY() == location.getBlockY()
                && rewardChestLocation.getBlockZ() == location.getBlockZ();
    }

    public static void claimRewardChest(Player player) {
        if (rewardChestLocation == null) return;

        rewardChestLocation.getBlock().setType(Material.AIR);

        for (String command : rewardCommands) {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    command.replace("%player%", player.getName())
                           .replace("%uuid%", player.getUniqueId().toString())
            );
        }

        Bukkit.broadcastMessage(colorStatic(rewardClaimedMessage.replace("%player%", player.getName())));
        rewardChestLocation = null;
    }

    public static void clearRewardChest() {
        if (rewardChestLocation != null && rewardChestLocation.getWorld() != null) {
            rewardChestLocation.getBlock().setType(Material.AIR);
            rewardChestLocation = null;
        }
    }

    public Location getCenter() {
        return center;
    }

    public double getRadius() {
        return radius;
    }

    public double getYTolerance() {
        return yTolerance;
    }

    public int getCaptureSeconds() {
        return captureSeconds;
    }

    public BossBar getBossBar() {
        return bossBar;
    }

    public Particle getBoundaryParticle() {
        return boundaryParticle;
    }

    public Particle getSpiralParticle() {
        return spiralParticle;
    }

    public String getCaptureMessage() {
        return captureMessage;
    }

    public String getContestedMessage() {
        return contestedMessage;
    }

    private void broadcast(String message) {
        Bukkit.broadcastMessage(message);
    }

    private String formatCoordinates(String input, Location loc) {
        return input.replace("%x%", String.valueOf(loc.getBlockX()))
                .replace("%y%", String.valueOf(loc.getBlockY()))
                .replace("%z%", String.valueOf(loc.getBlockZ()));
    }

    private String stringSetting(String key, String def) {
        Object value = definition.getSetting(key);
        return value == null ? def : String.valueOf(value);
    }

    private List<String> stringListSetting(String key) {
        Object value = definition.getSetting(key);
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object obj : list) {
                out.add(String.valueOf(obj));
            }
            return out;
        }
        return new ArrayList<>();
    }

    private double doubleSetting(String key, double def) {
        Object value = definition.getSetting(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? def : Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return def;
        }
    }

    private int intSetting(String key, int def) {
        Object value = definition.getSetting(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? def : Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return def;
        }
    }

    private Particle parseParticle(String input, Particle def) {
        try {
            return Particle.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return def;
        }
    }

    private Material parseChestMaterial(String input) {
        Material material = Material.matchMaterial(input);
        return material == null ? Material.CHEST : material;
    }

    private BarColor parseBarColor(String input) {
        try {
            return BarColor.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BarColor.BLUE;
        }
    }

    private BarStyle parseBarStyle(String input) {
        try {
            return BarStyle.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BarStyle.SOLID;
        }
    }

    private String color(String input) {
        return input == null ? "" : input.replace("&", "§");
    }

    private static String colorStatic(String input) {
        return input == null ? "" : input.replace("&", "§");
    }
}