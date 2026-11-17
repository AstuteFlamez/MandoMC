package net.mandomc.gameplay.saber;

import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Upgrade state travels with the saber through inventory, drops, storage and bounded throws. */
public final class SaberUpgrades {
    public static final List<String> TRACKS=List.of("damage","speed","guard","throw");
    private SaberUpgrades() { }
    private static NamespacedKey key(String track){return new NamespacedKey("mandomc","forge_"+track);}
    public static int level(ItemStack item,String track){
        if(item==null || !item.hasItemMeta())return 0;
        return Math.clamp(item.getItemMeta().getPersistentDataContainer().getOrDefault(key(track),PersistentDataType.INTEGER,0),0,10);
    }
    public static int spent(ItemStack item){return TRACKS.stream().mapToInt(t->level(item,t)).sum();}
    public static boolean eligible(int spent,int level,int budget,int maximum){return spent<budget && level<maximum;}
    public static ItemStack upgraded(ItemStack original,String track,double perLevel,String summary){
        if(!TRACKS.contains(track))throw new IllegalArgumentException("Unknown saber upgrade");
        var item=original.clone();var meta=item.getItemMeta();int next=level(item,track)+1;
        meta.getPersistentDataContainer().set(key(track),PersistentDataType.INTEGER,next);
        if(track.equals("damage") || track.equals("speed")) {
            var attribute=track.equals("damage")?Attribute.ATTACK_DAMAGE:Attribute.ATTACK_SPEED;
            var modifiers=meta.getAttributeModifiers(attribute);
            if(modifiers!=null)for(var modifier:List.copyOf(modifiers))if(modifier.getKey().equals(key(track)))meta.removeAttributeModifier(attribute,modifier);
            meta.addAttributeModifier(attribute,new AttributeModifier(key(track),next*perLevel,AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
        }
        var lore=meta.lore()==null?new ArrayList<Component>():new ArrayList<>(meta.lore());
        lore.removeIf(c->"mandomc:forge-stats".equals(c.insertion()));
        String levels=TRACKS.stream().map(t->t+" "+(t.equals(track)?next:level(original,t))).collect(java.util.stream.Collectors.joining(" · "));
        lore.add(Component.text(summary+" "+levels,NamedTextColor.GRAY).insertion("mandomc:forge-stats"));
        meta.lore(lore);item.setItemMeta(meta);return item;
    }
}
