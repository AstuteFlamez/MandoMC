package net.mandomc.gameplay.spawners;

import java.util.ArrayList;
import java.util.Set;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

/** Keep the native cage empty; the placed creature preview is rendered by ModelEngine. */
public final class SpawnerItems {
    private static final Set<String> IDS=Set.of("massiff_spawner","dewback_spawner","bantha_spawner","jawa_spawner");
    private SpawnerItems() { }
    public static boolean known(String id) {return id!=null && IDS.contains(id);}
    public static boolean hideDefaultCreature(ItemStack item) {
        if(!(item.getItemMeta() instanceof BlockStateMeta meta) || !(meta.getBlockState() instanceof CreatureSpawner state))return false;
        if(meta.hasBlockState() && state.getSpawnedType()==null && state.getPotentialSpawns().isEmpty())return false;
        state.setSpawnedType(null);meta.setBlockState(state);item.setItemMeta(meta);return true;
    }
    public static boolean refresh(ItemCatalog items,ItemStack item) {
        String id=items.identify(item);if(!known(id))return false;
        boolean changed=hideDefaultCreature(item);
        var meta=item.getItemMeta();var lore=meta.lore();
        if(lore!=null) {
            var updated=new ArrayList<>(lore);var template=items.create(id).getItemMeta().lore();
            for(int i=0;i<updated.size();i++)if(PlainTextComponentSerializer.plainText().serialize(updated.get(i))
                    .equals("Place in Wilderness. Break with a pickaxe to recover.")) {
                updated.remove(i);updated.addAll(i,template.subList(template.size()-4,template.size()));i+=3;changed=true;
            }
            String combine="Shift-right-click to combine matching spawners.";
            boolean hint=updated.stream().anyMatch(line->PlainTextComponentSerializer.plainText().serialize(line).equals(combine));
            if(!hint)for(int i=0;i<updated.size();i++)if(PlainTextComponentSerializer.plainText().serialize(updated.get(i)).equals("Break with a pickaxe to recover.")){
                updated.add(i+1,template.getLast());changed=true;break;
            }
            if(changed){meta.lore(updated);item.setItemMeta(meta);}
        }
        return changed;
    }
}
