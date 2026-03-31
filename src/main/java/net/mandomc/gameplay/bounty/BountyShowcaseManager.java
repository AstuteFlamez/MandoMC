package net.mandomc.gameplay.bounty;

import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.ItemHologramData;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import net.mandomc.MandoMC;
import net.mandomc.gameplay.bounty.config.BountyConfig;
import net.mandomc.core.modules.core.EconomyModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.entity.Display;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.mandomc.gameplay.bounty.model.Bounty;

/**
 * Renders a configured bounty showcase using FancyHolograms text and a native ItemDisplay head.
 */
public final class BountyShowcaseManager {

    private static final String BACK_SUFFIX = "_back";
    private static final String HEAD_SUFFIX = "_head";
    private static UUID currentHeadTargetId;
    private static BukkitTask refreshTask;
    private static BountyConfig bountyConfig;

    private BountyShowcaseManager() {
    }

    public static void start(BountyConfig config) {
        bountyConfig = config;
        stop();
        update();
        refreshTask = Bukkit.getScheduler().runTaskTimer(MandoMC.getInstance(), BountyShowcaseManager::update, 20L * 60, 20L * 60);
    }

    public static void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    public static void update() {
        ConfigurationSection section = bountyConfig != null ? bountyConfig.getShowcaseSection() : null;

        if (section == null || !section.getBoolean("enabled", false)) {
            remove();
            return;
        }

        if (BountyStorage.getAll().isEmpty()) {
            remove();
            return;
        }

        Bounty top = BountyStorage.getAll().stream()
                .max(Comparator.comparingDouble(Bounty::getTotal))
                .orElse(null);

        if (top == null) {
            remove();
            return;
        }

        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) {
            remove();
            return;
        }

        Location base = new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw", 180D),
                (float) section.getDouble("pitch", 0D)
        );

        String hologramId = section.getString("id", "bounty_showcase");
        double textOffset = section.getDouble("text-y-offset", 2.35);
        double npcOffset = section.getDouble("npc-y-offset", 0D);
        float yaw = base.getYaw();
        float pitch = base.getPitch();

        if (Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
            remove();
            return;
        }

        updateText(hologramId, base.clone().add(0, textOffset, 0), yaw, pitch, top, section);
        updateText(hologramId + BACK_SUFFIX, base.clone().add(0, textOffset, 0), yaw + 180F, pitch, top, section);
        // Item hologram heads render facing opposite our text direction, so offset by 180 degrees.
        updateHead(hologramId + HEAD_SUFFIX, base.clone().add(0, npcOffset, 0), yaw + 180F, pitch, top);
        updateHead(hologramId + HEAD_SUFFIX + BACK_SUFFIX, base.clone().add(0, npcOffset, 0), yaw + 360F, pitch, top);
    }

    public static void remove() {
        ConfigurationSection section = bountyConfig != null ? bountyConfig.getShowcaseSection() : null;
        String hologramId = section != null ? section.getString("id", "bounty_showcase") : "bounty_showcase";

        removeText(hologramId);
        removeText(hologramId + BACK_SUFFIX);
        removeHead(hologramId + HEAD_SUFFIX);
        removeHead(hologramId + HEAD_SUFFIX + BACK_SUFFIX);
    }

    private static void updateText(String hologramId, Location location, float yaw, float pitch, Bounty bounty, ConfigurationSection section) {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(hologramId).ifPresent(manager::removeHologram);

        TextHologramData data = new TextHologramData(hologramId, location);
        data.setText(buildLines(bounty, section));
        data.setBillboard(Display.Billboard.FIXED);
        data.setTextShadow(true);
        data.setBackground(Color.fromARGB(0, 0, 0, 10));
        data.setLocation(location.clone().setDirection(directionFromYawPitch(yaw, pitch)));

        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);
    }

    private static void removeText(String hologramId) {
        if (Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
            return;
        }

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(hologramId).ifPresent(manager::removeHologram);
    }

    private static void updateHead(String headId, Location location, float yaw, float pitch, Bounty bounty) {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        UUID targetId = bounty.getTarget();

        if (Objects.equals(currentHeadTargetId, targetId)) {
            manager.getHologram(headId).ifPresent(existing -> {
                existing.getData().setLocation(location.clone().setDirection(directionFromYawPitch(yaw, pitch)));
            });
            return;
        }

        manager.getHologram(headId).ifPresent(manager::removeHologram);

        ItemHologramData data = new ItemHologramData(headId, location);
        data.setItemStack(buildTargetHead(Bukkit.getOfflinePlayer(targetId)));
        data.setBillboard(Display.Billboard.FIXED);
        data.setLocation(location.clone().setDirection(directionFromYawPitch(yaw, pitch)));

        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);
        currentHeadTargetId = targetId;
    }

    private static void removeHead(String headId) {
        if (Bukkit.getPluginManager().getPlugin("FancyHolograms") == null) {
            return;
        }

        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(headId).ifPresent(manager::removeHologram);
        currentHeadTargetId = null;
    }

    private static ItemStack buildTargetHead(OfflinePlayer target) {
        ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(target);
            head.setItemMeta(meta);
        }
        return head;
    }

    private static List<String> buildLines(Bounty bounty, ConfigurationSection section) {
        List<String> raw = section.getStringList("lines");
        List<String> lines = new ArrayList<>();

        if (raw.isEmpty()) {
            raw = List.of(
                    "&c&lBounty Terminal",
                    "&7Target &8• &f%target%",
                    "&7Value  &8• &6$%amount%",
                    "&7Sector &8• &f%location%",
                    "&7Status &8• %status%"
            );
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(bounty.getTarget());
        String targetName = Optional.ofNullable(target.getName()).orElse("Unknown");
        String amount = EconomyModule.format(bounty.getTotal());

        String worldName = "Unknown";
        String x = "-";
        String y = "-";
        String z = "-";
        String coords = "No snapshot";
        String locationLine = "No snapshot";

        Location loc = bounty.getLastKnownLocation();
        if (loc != null && loc.getWorld() != null) {
            worldName = formatWorldName(loc.getWorld().getName());
            x = String.valueOf(loc.getBlockX());
            y = String.valueOf(loc.getBlockY());
            z = String.valueOf(loc.getBlockZ());
            coords = x + ", " + y + ", " + z;
            locationLine = worldName + " • " + coords;
        }

        String statusLine = buildStatusLine(target, bounty.getLastSeen());
        String countdown = buildCountdown(target, bounty.getLastSeen());

        for (String line : raw) {
            lines.add(color(line
                    .replace("%target%", targetName)
                    .replace("%amount%", amount)
                    .replace("%world%", worldName)
                    .replace("%x%", x)
                    .replace("%y%", y)
                    .replace("%z%", z)
                    .replace("%coords%", coords)
                    .replace("%location%", locationLine)
                    .replace("%time%", statusLine)
                    .replace("%status%", statusLine)
                    .replace("%countdown%", countdown)));
        }

        return lines;
    }

    private static String buildStatusLine(OfflinePlayer target, long lastSeen) {
        if (target.isOnline()) {
            return "&aONLINE &8• &7next sweep in &f" + buildCountdown(target, lastSeen);
        }

        if (lastSeen <= 0L) {
            return "&cOFFLINE &8• &7no intel";
        }

        return "&cOFFLINE &8• &7last seen &f" + formatElapsed(lastSeen);
    }

    private static String buildCountdown(OfflinePlayer target, long lastSeen) {
        if (!target.isOnline()) {
            return "-";
        }

        long intervalMillis = Math.max(1, bountyConfig != null ? bountyConfig.getTrackingIntervalSeconds() : 600) * 1000L;
        long elapsed = Math.max(0L, System.currentTimeMillis() - Math.max(0L, lastSeen));
        long remaining = Math.max(0L, intervalMillis - elapsed);

        if (remaining < 60000L) {
            return "<1m";
        }

        long remainingMinutes = (long) Math.ceil(remaining / 60000D);
        return remainingMinutes + "m";
    }

    private static String formatElapsed(long lastSeen) {
        long diff = System.currentTimeMillis() - lastSeen;
        if (diff < 0 || diff < 60000L) {
            return "just now";
        }

        long minutes = diff / 60000L;
        if (minutes < 60L) {
            return minutes + "m ago";
        }

        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours + "h ago";
        }

        long days = hours / 24L;
        return days + "d ago";
    }

    private static String formatWorldName(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return "Unknown";
        }

        String[] parts = worldName.replace('_', ' ').split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() == 0 ? worldName : builder.toString();
    }

    private static org.bukkit.util.Vector directionFromYawPitch(float yaw, float pitch) {
        double yawRad = Math.toRadians(yaw);
        double pitchRad = Math.toRadians(pitch);
        double x = -Math.sin(yawRad) * Math.cos(pitchRad);
        double y = -Math.sin(pitchRad);
        double z = Math.cos(yawRad) * Math.cos(pitchRad);
        return new org.bukkit.util.Vector(x, y, z);
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
