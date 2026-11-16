package net.mandomc.gameplay.admin;

import java.util.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Player.dat is the transaction boundary: original inventory, state and recovery marker save together. */
public final class StaffInventory {
    public static final NamespacedKey INVENTORY = key("inventory");
    private static final NamespacedKey STATE = key("state");
    private StaffInventory() { }
    private static NamespacedKey key(String suffix) { return new NamespacedKey("mandomc", "staff_" + suffix); }
    public static boolean active(Player player) { return player.getPersistentDataContainer().has(INVENTORY); }
    public static void capture(Player player) {
        if (active(player)) throw new IllegalStateException("Staff inventory already stored");
        if (!player.getItemOnCursor().isEmpty()) throw new IllegalStateException("Cursor must be empty");
        var state = new YamlConfiguration();
        state.set("schema-version", 1);
        var at = player.getLocation();
        state.set("world", at.getWorld().getKey().toString());
        state.set("x", at.getX()); state.set("y", at.getY()); state.set("z", at.getZ());
        state.set("yaw", at.getYaw()); state.set("pitch", at.getPitch());
        state.set("mode", player.getGameMode().name());
        state.set("health", player.getHealth()); state.set("food", player.getFoodLevel());
        state.set("saturation", player.getSaturation()); state.set("exhaustion", player.getExhaustion());
        state.set("level", player.getLevel()); state.set("exp", player.getExp()); state.set("total-exp", player.getTotalExperience());
        state.set("held", player.getInventory().getHeldItemSlot());
        state.set("flight", player.getAllowFlight()); state.set("flying", player.isFlying());
        state.set("walk-speed", player.getWalkSpeed()); state.set("fly-speed", player.getFlySpeed());
        state.set("invulnerable", player.isInvulnerable()); state.set("collidable", player.isCollidable());
        state.set("captured", System.currentTimeMillis());
        var effects = new ArrayList<Map<String, Object>>();
        for (var effect : player.getActivePotionEffects()) effects.add(Map.of(
                "type", effect.getType().getKey().toString(), "duration", effect.getDuration(),
                "amplifier", effect.getAmplifier(), "ambient", effect.isAmbient(),
                "particles", effect.hasParticles(), "icon", effect.hasIcon()));
        state.set("effects", effects);
        var data = player.getPersistentDataContainer();
        data.set(INVENTORY, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(player.getInventory().getContents()));
        data.set(STATE, PersistentDataType.STRING, state.saveToString());
        player.saveData();
    }
    private static YamlConfiguration state(Player player) {
        var raw = player.getPersistentDataContainer().get(STATE, PersistentDataType.STRING);
        if (raw == null) throw new IllegalStateException("Staff recovery state missing; original inventory retained");
        var state = new YamlConfiguration();
        try { state.loadFromString(raw); } catch (Exception error) { throw new IllegalStateException("Invalid staff recovery state", error); }
        if (state.getInt("schema-version") != 1) throw new IllegalStateException("Unknown staff recovery version");
        return state;
    }
    public static Location destination(Player player, Location fallback) {
        var state = state(player);
        var world = Bukkit.getWorld(Objects.requireNonNull(NamespacedKey.fromString(state.getString("world"))));
        if (world == null || net.mandomc.gameplay.tutorial.TutorialInventory.world(world)) return fallback.clone();
        return new Location(world, state.getDouble("x"), state.getDouble("y"), state.getDouble("z"),
                (float) state.getDouble("yaw"), (float) state.getDouble("pitch"));
    }
    /** Caller teleports out first. Failure leaves the complete escrow for a later recovery. */
    public static void restore(Player player) {
        var state = state(player);
        var bytes = Objects.requireNonNull(player.getPersistentDataContainer().get(INVENTORY, PersistentDataType.BYTE_ARRAY));
        var contents = ItemStack.deserializeItemsFromBytes(bytes);
        var mode = GameMode.valueOf(state.getString("mode"));
        player.getInventory().setContents(contents);
        player.setGameMode(mode);
        player.setAllowFlight(state.getBoolean("flight"));
        player.setFlying(state.getBoolean("flight") && state.getBoolean("flying"));
        player.setWalkSpeed((float) state.getDouble("walk-speed")); player.setFlySpeed((float) state.getDouble("fly-speed"));
        player.setInvulnerable(state.getBoolean("invulnerable")); player.setCollidable(state.getBoolean("collidable"));
        player.getInventory().setHeldItemSlot(state.getInt("held"));
        player.setHealth(Math.max(.5, Math.min(player.getMaxHealth(), state.getDouble("health"))));
        player.setFoodLevel(state.getInt("food")); player.setSaturation((float) state.getDouble("saturation"));
        player.setExhaustion((float) state.getDouble("exhaustion"));
        player.setTotalExperience(state.getInt("total-exp")); player.setLevel(state.getInt("level")); player.setExp((float) state.getDouble("exp"));
        player.setFireTicks(0); player.setFallDistance(0);
        for (var effect : List.copyOf(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
        long elapsed = Math.max(0, System.currentTimeMillis() - state.getLong("captured")) / 50;
        for (var effect : state.getMapList("effects")) {
            var type = PotionEffectType.getByKey(NamespacedKey.fromString(effect.get("type").toString()));
            int original = ((Number) effect.get("duration")).intValue();
            int duration = original == -1 ? -1 : (int) Math.max(0, original - elapsed);
            if (type != null && duration != 0) player.addPotionEffect(new PotionEffect(type, duration,
                    ((Number) effect.get("amplifier")).intValue(), Boolean.TRUE.equals(effect.get("ambient")),
                    Boolean.TRUE.equals(effect.get("particles")), Boolean.TRUE.equals(effect.get("icon"))));
        }
        player.getPersistentDataContainer().remove(INVENTORY);
        player.getPersistentDataContainer().remove(STATE);
        player.saveData();
    }
}
