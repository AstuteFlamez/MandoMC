package net.mandomc.gameplay.boba;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Keeps catalog rarity/category badges directly below the name while retaining real enchantments. */
public final class JetpackTooltip {
    private static final String MARKER="mandomc:jetpack-tooltip";
    private static final NamespacedKey PRIOR_HIDE=new NamespacedKey("mandomc","jetpack_prior_hide_enchants");
    private final ItemCatalog items;
    public JetpackTooltip(ItemCatalog items) { this.items=items; }
    public void update(ItemStack item,Enchantment jetpack) {
        var meta=item.getItemMeta();var original=meta.clone();
        var prior=meta.getPersistentDataContainer().get(PRIOR_HIDE,PersistentDataType.BYTE);
        var lore=new ArrayList<>(meta.lore()==null?List.<Component>of():meta.lore());
        lore.removeIf(line->MARKER.equals(line.insertion()));
        boolean active=item.getEnchantmentLevel(jetpack)>0;
        boolean catalog=items.identify(item)!=null&&!lore.isEmpty();
        if(active&&catalog) {
            if(prior==null) {
                prior=(byte)(meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)?1:0);
                meta.getPersistentDataContainer().set(PRIOR_HIDE,PersistentDataType.BYTE,prior);
            }
            var lines=new ArrayList<Component>();
            lines.add(jetpack.displayName(item.getEnchantmentLevel(jetpack)).decoration(TextDecoration.ITALIC,false).insertion(MARKER));
            if(prior==0)for(var entry:meta.getEnchants().entrySet())if(!entry.getKey().equals(jetpack))
                lines.add(entry.getKey().displayName(entry.getValue()).decoration(TextDecoration.ITALIC,false).insertion(MARKER));
            lines.add(Component.empty().insertion(MARKER));
            // Catalog layout is badge row, blank separator, then description/controls.
            int at=lore.size()>1&&lore.get(1).equals(Component.empty())?2:1;
            lore.addAll(at,lines);meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else if(prior!=null) {
            if(prior==0)meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().remove(PRIOR_HIDE);
        }
        if(meta.hasLore()||!lore.isEmpty())meta.lore(lore);
        if(!meta.equals(original))item.setItemMeta(meta);
    }
}
