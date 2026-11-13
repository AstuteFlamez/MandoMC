package net.mandomc.gameplay.ilum;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Original inventory and original state are durably saved BEFORE temporary course items replace them. */
public final class ParkourInventory {
    public static final NamespacedKey INVENTORY = new NamespacedKey("mandomc","ilum_inventory");
    public static final NamespacedKey STATE = new NamespacedKey("mandomc","ilum_state");
    private ParkourInventory() { }

    public static boolean stored(Player player) { return player.getPersistentDataContainer().has(INVENTORY); }

    public static void capture(Player player) {
        if (stored(player)) throw new IllegalStateException("An Ilum inventory is already stored");
        var state=new YamlConfiguration();
        state.set("schema-version",1);
        state.set("mode",player.getGameMode().name());
        state.set("health",player.getHealth());
        state.set("food",player.getFoodLevel());
        state.set("saturation",player.getSaturation());
        state.set("held",player.getInventory().getHeldItemSlot());
        state.set("flight",player.getAllowFlight());
        state.set("flying",player.isFlying());
        state.set("walk-speed",player.getWalkSpeed());
        state.set("captured",System.currentTimeMillis());
        var effects=new ArrayList<Map<String,Object>>();
        for (var effect:player.getActivePotionEffects()) effects.add(Map.of(
                "type",effect.getType().getKey().toString(),"duration",effect.getDuration(),"amplifier",effect.getAmplifier(),
                "ambient",effect.isAmbient(),"particles",effect.hasParticles(),"icon",effect.hasIcon()));
        state.set("effects",effects);
        var data=player.getPersistentDataContainer();
        data.set(INVENTORY,PersistentDataType.BYTE_ARRAY,ItemStack.serializeItemsAsBytes(player.getInventory().getContents()));
        data.set(STATE,PersistentDataType.STRING,state.saveToString());
        player.saveData();
    }

    /** Restore in memory; caller commits this and all run/reward state in one player.saveData(). */
    public static void restore(Player player) {
        var data=player.getPersistentDataContainer();
        byte[] bytes=data.get(INVENTORY,PersistentDataType.BYTE_ARRAY);
        String raw=data.get(STATE,PersistentDataType.STRING);
        if (bytes==null || raw==null) throw new IllegalStateException("Stored Ilum inventory/state is incomplete");
        var state=new YamlConfiguration();
        try { state.loadFromString(raw); } catch (org.bukkit.configuration.InvalidConfigurationException error) {
            throw new IllegalStateException("Stored Ilum state is invalid; recovery data retained",error);
        }
        if (state.getInt("schema-version")!=1) throw new IllegalStateException("Unknown Ilum inventory version");
        var contents=ItemStack.deserializeItemsFromBytes(bytes);
        var mode=GameMode.valueOf(state.getString("mode"));
        player.getInventory().setContents(contents);
        player.setGameMode(mode);
        player.setAllowFlight(state.getBoolean("flight"));
        if (state.getBoolean("flight")) player.setFlying(state.getBoolean("flying"));
        player.setWalkSpeed((float)state.getDouble("walk-speed",.2));
        player.getInventory().setHeldItemSlot(state.getInt("held",0));
        player.setHealth(Math.max(.5,Math.min(player.getMaxHealth(),state.getDouble("health",20))));
        player.setFoodLevel(Math.max(0,Math.min(20,state.getInt("food",20))));
        player.setSaturation((float)Math.max(0,Math.min(20,state.getDouble("saturation",20))));
        player.setFireTicks(0);player.setFallDistance(0);
        for (var effect:List.copyOf(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
        long elapsed=Math.max(0,System.currentTimeMillis()-state.getLong("captured"))/50;
        for (var rawEffect:state.getMapList("effects")) {
            var type=PotionEffectType.getByKey(NamespacedKey.fromString(rawEffect.get("type").toString()));
            int original=((Number)rawEffect.get("duration")).intValue();
            int duration=original==-1 ? -1 : (int)Math.max(0,original-elapsed);
            if (type==null || duration==0) continue;
            player.addPotionEffect(new PotionEffect(type,duration,((Number)rawEffect.get("amplifier")).intValue(),
                    Boolean.TRUE.equals(rawEffect.get("ambient")),Boolean.TRUE.equals(rawEffect.get("particles")),Boolean.TRUE.equals(rawEffect.get("icon"))));
        }
        data.remove(INVENTORY);data.remove(STATE);
    }
}
