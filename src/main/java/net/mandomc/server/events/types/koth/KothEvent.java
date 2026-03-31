package net.mandomc.server.events.types.koth;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

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

    private static final String REWARD_MARKER_KEY = "koth_reward_chest";
    private static final String REWARD_COMMANDS_KEY = "koth_reward_commands";
    private static final String REWARD_MESSAGE_KEY = "koth_reward_message";

    private Location rewardChestLocation;
    private List<String> rewardCommands = new ArrayList<>();
    private String rewardClaimedMessage = "&e%player% &7claimed the KOTH reward chest!";

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
        Block block = chestLoc.getBlock();
        block.setType(parseChestMaterial(stringSetting("chest-material", "CHEST")));

        rewardChestLocation = chestLoc;
        markRewardChest(block, rewardCommands, rewardClaimedMessage);
        broadcast(chestSpawnMessage);
    }

    public static boolean isRewardChest(Block block) {
        if (!(block.getState() instanceof TileState tileState)) {
            return false;
        }
        return tileState.getPersistentDataContainer().has(markerKey(), PersistentDataType.BYTE);
    }

    public static void claimRewardChest(Player player, Block block) {
        if (!(block.getState() instanceof TileState tileState)) {
            return;
        }
        if (!tileState.getPersistentDataContainer().has(markerKey(), PersistentDataType.BYTE)) {
            return;
        }

        String encodedCommands = tileState.getPersistentDataContainer()
                .get(commandsKey(), PersistentDataType.STRING);
        String claimMessage = tileState.getPersistentDataContainer()
                .get(messageKey(), PersistentDataType.STRING);

        block.setType(Material.AIR);

        for (String command : decodeCommands(encodedCommands)) {
            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    command.replace("%player%", player.getName())
                           .replace("%uuid%", player.getUniqueId().toString())
            );
        }

        String message = claimMessage != null ? claimMessage : "&e%player% &7claimed the KOTH reward chest!";
        Bukkit.broadcastMessage(colorStatic(message.replace("%player%", player.getName())));
    }

    public void clearRewardChest() {
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

    private static NamespacedKey markerKey() {
        return new NamespacedKey(MandoMC.getInstance(), REWARD_MARKER_KEY);
    }

    private static NamespacedKey commandsKey() {
        return new NamespacedKey(MandoMC.getInstance(), REWARD_COMMANDS_KEY);
    }

    private static NamespacedKey messageKey() {
        return new NamespacedKey(MandoMC.getInstance(), REWARD_MESSAGE_KEY);
    }

    private static void markRewardChest(Block block, List<String> commands, String message) {
        if (!(block.getState() instanceof TileState tileState)) {
            return;
        }
        tileState.getPersistentDataContainer().set(markerKey(), PersistentDataType.BYTE, (byte) 1);
        tileState.getPersistentDataContainer().set(commandsKey(), PersistentDataType.STRING, String.join("\n", commands));
        tileState.getPersistentDataContainer().set(messageKey(), PersistentDataType.STRING, message == null ? "" : message);
        tileState.update(true, false);
    }

    private static List<String> decodeCommands(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return List.of();
        }
        List<String> commands = new ArrayList<>();
        for (String line : encoded.split("\n")) {
            if (!line.isBlank()) {
                commands.add(line);
            }
        }
        return commands;
    }
}