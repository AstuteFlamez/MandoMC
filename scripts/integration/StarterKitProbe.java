package net.mandomc.verification;

import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.mandomc.bootstrap.MandoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Exercises the scheduled first join and the retry path on a disposable Paper copy. */
public final class StarterKitProbe extends JavaPlugin {
    private MandoPlugin mando;
    private NativeProbePlayers actors;
    private int checks;

    @Override public void onEnable() {
        if (getServer().getPort() != 25590) return;
        later(() -> {
            mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
            actors = new NativeProbePlayers("FirstKit");
            var player = actors.create(Bukkit.getWorlds().getFirst().getSpawnLocation());
            var inventory = player.getInventory();
            inventory.clear();
            mando.starterKit().join(new PlayerJoinEvent(player, Component.empty()));
            later(() -> {
                check(inventory.containsAtLeast(new ItemStack(Material.DIRT), 64) && !inventory.containsAtLeast(new ItemStack(Material.DIRT), 65), "64 dirt");
                check(inventory.containsAtLeast(new ItemStack(Material.GRASS_BLOCK), 16) && !inventory.containsAtLeast(new ItemStack(Material.GRASS_BLOCK), 17), "16 grass");
                var leg = mando.items().create("dewback_leg");
                check(inventory.containsAtLeast(leg, 64) && !inventory.containsAtLeast(leg, 65), "64 catalog Dewback Legs");
                check(!inventory.containsAtLeast(new ItemStack(Material.COOKED_BEEF), 1), "no plain steak");
                var saplings = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft("saplings"), Material.class);
                for (var sapling : saplings.getValues()) {
                    var stack = new ItemStack(sapling);
                    check(inventory.containsAtLeast(stack, 1) && !inventory.containsAtLeast(stack, 2), sapling.name());
                }
                for (String id : List.of("robes_helmet", "robes_chestplate", "robes_leggings", "robes_boots")) {
                    var robe = mando.items().create(id);
                    check(robe.isSimilar(inventory.getItem(robe.getType().getEquipmentSlot())), "equipped " + id);
                    check(!inventory.containsAtLeast(robe, 1), "no duplicate " + id);
                }
                var sabers = Arrays.stream(inventory.getStorageContents()).filter(mando.sabers()::isSaber).toList();
                check(sabers.size() == 1 && "blue_training_lightsaber".equals(mando.items().identify(sabers.getFirst())), "one blue saber");
                var saber = sabers.getFirst();
                mando.enchantments().maintain(saber);
                check(damageBonus(saber) == damageBonus(new ItemStack(Material.WOODEN_SWORD)), "wooden sword damage after item maintenance");
                check(!mando.starterKit().grant(player), "once per player");
                var ordinary = mando.items().create("blue_training_lightsaber");
                mando.enchantments().maintain(ordinary);
                check(damageBonus(ordinary) == 7, "regular saber damage");
                verifyRetry(player);
                getLogger().info("STARTER KIT PASSED " + checks + " native checks");
                Bukkit.shutdown();
            });
        });
    }

    private double damageBonus(ItemStack item) {
        var mods = item.getData(io.papermc.paper.datacomponent.DataComponentTypes.ATTRIBUTE_MODIFIERS);
        return mods.modifiers().stream().filter(entry -> entry.attribute().equals(Attribute.ATTACK_DAMAGE))
                .mapToDouble(entry -> entry.modifier().getAmount()).sum();
    }

    private void verifyRetry(Player player) {
        var marker = new NamespacedKey(mando, "starter_kit_v1");
        player.getPersistentDataContainer().remove(marker);
        var inventory = player.getInventory();
        inventory.clear();
        for (int i = 0; i < 14; i++) inventory.setItem(i, new ItemStack(Material.DIAMOND, 64));
        inventory.setHelmet(new ItemStack(Material.IRON_HELMET));
        var before = Arrays.stream(inventory.getContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        check(!mando.starterKit().grant(player) && Arrays.equals(before, inventory.getContents()), "full kit deferred without partial changes");
        inventory.setItem(13, null);
        check(mando.starterKit().grant(player), "kit fits after freeing one slot");
        check(inventory.getHelmet().getType() == Material.IRON_HELMET && inventory.containsAtLeast(mando.items().create("robes_helmet"), 1),
                "existing helmet preserved and spare robe stored");
    }

    private void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        checks++;
    }
    private interface Action { void run() throws Exception; }
    private void later(Action action) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                action.run();
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "STARTER KIT FAILED", error);
                Bukkit.shutdown();
            }
        }, 20);
    }
    @Override public void onDisable() { if (actors != null) actors.close(); }
}
