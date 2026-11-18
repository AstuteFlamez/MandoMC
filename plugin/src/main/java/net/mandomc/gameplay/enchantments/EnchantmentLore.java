package net.mandomc.gameplay.enchantments;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** One owner for applied enchantment lines, after the item's original description and controls. */
public final class EnchantmentLore {
    private static final String ROW = "mandomc:equipment-enchantments";
    private static final String LEGACY_ROW = "mandomc:jetpack-tooltip";
    private static final NamespacedKey PRIOR_HIDE = new NamespacedKey("mandomc", "enchants_prior_hide");
    private static final NamespacedKey LEGACY_HIDE = new NamespacedKey("mandomc", "jetpack_prior_hide_enchants");
    private EnchantmentLore() { }

    public static boolean update(ItemStack item) {
        if (item == null || item.isEmpty() || item.getType() == Material.ENCHANTED_BOOK) return false;
        var meta = item.getItemMeta(); var original = meta.clone();
        var prior = meta.getPersistentDataContainer().get(PRIOR_HIDE, PersistentDataType.BYTE);
        var legacy = meta.getPersistentDataContainer().get(LEGACY_HIDE, PersistentDataType.BYTE);
        if (!meta.hasEnchants() && prior == null && legacy == null) return false;
        if (prior == null) prior = legacy;
        var lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
        lore.removeIf(line -> Set.of(ROW, LEGACY_ROW).contains(java.util.Objects.requireNonNullElse(line.insertion(), "")));
        meta.getPersistentDataContainer().remove(LEGACY_HIDE);
        boolean jetpack = meta.getEnchants().keySet().stream().anyMatch(e -> e.getKey().equals(net.mandomc.gameplay.boba.JetpackChestplate.KEY));
        // Respect unrelated items that deliberately hid their native enchantments.
        if (prior == null && meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS) && !jetpack) return false;
        if (!meta.getEnchants().isEmpty()) {
            if (prior == null) prior = (byte) (meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS) ? 1 : 0);
            final boolean hidden = prior != 0;
            var entries = meta.getEnchants().entrySet().stream()
                    .filter(e -> !hidden || e.getKey().getKey().equals(net.mandomc.gameplay.boba.JetpackChestplate.KEY))
                    .sorted(Comparator.<java.util.Map.Entry<org.bukkit.enchantments.Enchantment, Integer>>comparingInt(e -> order(e.getKey().getKey()))
                            .thenComparing(e -> e.getKey().getKey().toString())).toList();
            if (!entries.isEmpty()) {
                if (!lore.isEmpty() && !PlainTextComponentSerializer.plainText().serialize(lore.getLast()).isBlank())
                    lore.add(Component.empty().insertion(ROW));
                for (var entry : entries) lore.add(entry.getKey().displayName(entry.getValue())
                        .decoration(TextDecoration.ITALIC, false).insertion(ROW));
            }
            if (entries.isEmpty()) meta.getPersistentDataContainer().remove(PRIOR_HIDE);
            else meta.getPersistentDataContainer().set(PRIOR_HIDE, PersistentDataType.BYTE, prior);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else if (prior != null) {
            if (prior == 0) meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().remove(PRIOR_HIDE);
        }
        if (meta.hasLore() || !lore.isEmpty()) meta.lore(lore.isEmpty() ? null : lore);
        if (meta.equals(original)) return false;
        item.setItemMeta(meta); return true;
    }

    private static int order(NamespacedKey key) {
        return switch (key.toString()) {
            case "mandomc:jetpack" -> 0;
            case "minecraft:sharpness" -> 1;
            case "mandomc:endurance" -> 2;
            case "mandomc:recovery" -> 3;
            default -> 4;
        };
    }
}
