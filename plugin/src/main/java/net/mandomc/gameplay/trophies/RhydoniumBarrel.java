package net.mandomc.gameplay.trophies;

import java.util.List;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.persistence.PersistentDataType;

final class RhydoniumBarrel implements PreviewBlock {
    static final String ITEM = "rhydonium_barrel";

    private static final NamespacedKey CURRENT = new NamespacedKey("mandomc", "current_fuel");
    private static final NamespacedKey MAX = new NamespacedKey("mandomc", "max_fuel");
    private static final List<String> STAGES = List.of("block", "quarter", "half", "three", "full");

    @Override public int width() { return 1; }
    @Override public int height() { return 2; }
    @Override public String messages() { return "barrel-text."; }
    @Override public Block site(Block clicked, BlockFace face) { return clicked.getRelative(BlockFace.UP); }
    @Override public PlacementPlan plan(TrophyJournal.Cell site, BlockFace face, float playerYaw) {
        // ItemDisplay renders the model's north (orange panel) face toward entity yaw.
        int towardPlayer = Math.floorMod(Math.round(playerYaw / 90f) + 2, 4) * 90;
        return new PlacementPlan(site, towardPlayer, TrophyJournal.Face.UP, width(), height());
    }

    static int stage(int current, int maximum) {
        return maximum <= 0 ? 0 : (int) Math.clamp(4L * current / maximum, 0, 4);
    }

    @Override public NamespacedKey model(ItemStack held) {
        var data = held.getPersistentDataContainer();
        Integer current = data.get(CURRENT, PersistentDataType.INTEGER);
        int stage;
        if (current != null) {
            int maximum = data.getOrDefault(MAX, PersistentDataType.INTEGER, 500);
            stage = stage(current, maximum);
        } else {
            // Imported portable stacks can carry just their legacy stage (CMD 6..10).
            var meta = held.getItemMeta();
            var model = meta.getItemModel();
            if (model != null && model.getNamespace().equals("mandomc") && STAGES.stream()
                    .anyMatch(suffix -> model.getKey().equals("legacy/" + ITEM + "_" + suffix))) return model;
            var floats = meta.getCustomModelDataComponent().getFloats();
            stage = floats.isEmpty() ? 4 : Math.clamp(Math.round(floats.getFirst()) - 6, 0, 4);
        }
        return new NamespacedKey("mandomc", "legacy/" + ITEM + "_" + STAGES.get(stage));
    }
}
