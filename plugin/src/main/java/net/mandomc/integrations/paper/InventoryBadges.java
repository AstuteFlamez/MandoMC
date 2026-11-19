package net.mandomc.integrations.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore;
import com.github.retrooper.packetevents.protocol.component.builtin.item.ItemRarity;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientCreativeInventoryAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetCursorItem;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.Material;

/** Adds tooltip badges to inventory views without changing server-owned stacks or exact recipe choices. */
public final class InventoryBadges extends PacketListenerAbstract implements AutoCloseable {
    private static final String GENERATED = "mandomc:inventory-badge";
    private static final String GENERATED_WITHOUT_LORE = "mandomc:inventory-badge/no-original-lore";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final Map<String, String> materialCategories;
    private final Map<ItemRarity, Map<String, Component>> vanillaBadges;
    private final Map<String, Component> catalogBadges;
    private final Set<String> existingRows;

    public InventoryBadges(ItemCatalog items) {
        super(PacketListenerPriority.NORMAL);
        var categories = new HashMap<String, String>();
        for (Material material : Material.values()) {
            if (!material.isLegacy() && material.isItem()) categories.put(material.getKey().toString(), category(material));
        }
        materialCategories = Map.copyOf(categories);
        var byRarity = new EnumMap<ItemRarity, Map<String, Component>>(ItemRarity.class);
        var usedCategories = Set.copyOf(categories.values());
        for (ItemRarity rarity : ItemRarity.values()) {
            var rows = new HashMap<String, Component>();
            for (String category : usedCategories) {
                rows.put(category, items.badges(rarity.name().toLowerCase(Locale.ROOT), category));
            }
            byRarity.put(rarity, Map.copyOf(rows));
        }
        vanillaBadges = Map.copyOf(byRarity);
        existingRows = items.badgeRows();
        catalogBadges = items.catalogBadges();
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    private static String category(Material material) {
        String name = material.name();
        if (material == Material.ENCHANTED_BOOK) return "enchantment";
        if (name.endsWith("_SWORD") || name.endsWith("_SPEAR") || switch (material) {
            case BOW, CROSSBOW, TRIDENT, MACE -> true;
            default -> false;
        }) return "weapons";
        if (name.endsWith("_ARROW") || material == Material.ARROW) return "ammo";
        if (net.mandomc.gameplay.rules.SurvivalRules.armorMaterial(material) || switch (material) {
            case SHIELD, ELYTRA, TURTLE_HELMET -> true;
            default -> false;
        }) return "armor";
        if (name.endsWith("_BOAT") || name.endsWith("_RAFT") || name.endsWith("MINECART")) return "vehicles";
        if (name.endsWith("_INGOT") || name.endsWith("_NUGGET") || name.startsWith("RAW_") && !material.isBlock()) return "metals";
        if (material.isEdible() || name.endsWith("POTION") || name.endsWith("STEW")) return "consumables";
        if (material.getMaxDurability() > 0) return "tools";
        if (name.endsWith("_KEY")) return "keys";
        if (material.isBlock()) return "blocks";
        return "materials";
    }

    private ItemStack present(ItemStack stack) {
        if (stack.isEmpty() || stack.getComponent(ComponentTypes.TOOLTIP_DISPLAY)
                .filter(tooltip -> tooltip.isHideTooltip() || tooltip.getHiddenComponents().contains(ComponentTypes.LORE))
                .isPresent()) return stack;
        var originalLore = stack.getComponent(ComponentTypes.LORE);
        var lore = originalLore.orElse(ItemLore.EMPTY).getLines();
        if (!lore.isEmpty() && (generated(lore.getFirst())
                || existingRows.contains(PLAIN.serialize(lore.getFirst())))) return stack;

        Component badge = null;
        var customData = stack.getComponent(ComponentTypes.CUSTOM_DATA);
        if (customData.isPresent()) {
            var bukkit = customData.get().getCompoundTagOrNull("PublicBukkitValues");
            if (bukkit != null) {
                String id = bukkit.getStringTagValueOrNull("mandomc:item_id");
                if (id != null) badge = catalogBadges.get(id);
            }
        }
        if (badge == null) {
            var rarity = stack.getComponentOr(ComponentTypes.RARITY, ItemRarity.COMMON);
            String category = materialCategories.getOrDefault(stack.getType().getName().toString(), "materials");
            badge = vanillaBadges.get(rarity).get(category);
            if (badge == null) badge = vanillaBadges.get(rarity).get("materials");
        }
        var lines = new ArrayList<Component>(lore.size() + 1);
        lines.add(badge.insertion(originalLore.isPresent() ? GENERATED : GENERATED_WITHOUT_LORE));
        lines.addAll(lore);
        var decorated = stack.copy();
        decorated.setComponent(ComponentTypes.LORE, new ItemLore(lines));
        return decorated;
    }

    @Override public void onPacketSend(PacketSendEvent event) {
        if (event.isCancelled()) return;
        var type = event.getPacketType();
        if (type == PacketType.Play.Server.SET_SLOT) {
            var packet = new WrapperPlayServerSetSlot(event);
            var view = present(packet.getItem());
            if (view != packet.getItem()) { packet.setItem(view); event.markForReEncode(true); }
        } else if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            var packet = new WrapperPlayServerWindowItems(event);
            var view = new ArrayList<ItemStack>(packet.getItems().size());
            boolean changed = false;
            for (var stack : packet.getItems()) {
                var decorated = present(stack);
                view.add(decorated);
                changed |= decorated != stack;
            }
            var carried = packet.getCarriedItem().orElse(ItemStack.EMPTY);
            var decoratedCursor = present(carried);
            if (changed || decoratedCursor != carried) {
                packet.setItems(view);
                if (decoratedCursor != carried) packet.setCarriedItem(decoratedCursor);
                event.markForReEncode(true);
            }
        } else if (type == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            var packet = new WrapperPlayServerSetPlayerInventory(event);
            var view = present(packet.getStack());
            if (view != packet.getStack()) { packet.setStack(view); event.markForReEncode(true); }
        } else if (type == PacketType.Play.Server.SET_CURSOR_ITEM) {
            var packet = new WrapperPlayServerSetCursorItem(event);
            var view = present(packet.getStack());
            if (view != packet.getStack()) { packet.setStack(view); event.markForReEncode(true); }
        }
    }

    private static boolean generated(Component line) {
        return GENERATED.equals(line.insertion()) || GENERATED_WITHOUT_LORE.equals(line.insertion());
    }

    @Override public void onPacketReceive(PacketReceiveEvent event) {
        if (event.isCancelled() || event.getPacketType() != PacketType.Play.Client.CREATIVE_INVENTORY_ACTION) return;
        var packet = new WrapperPlayClientCreativeInventoryAction(event);
        var stack = packet.getItemStack();
        List<Component> lore = stack.getComponentOr(ComponentTypes.LORE, ItemLore.EMPTY).getLines();
        if (lore.isEmpty() || !generated(lore.getFirst())) return;
        var original = stack.copy();
        if (GENERATED_WITHOUT_LORE.equals(lore.getFirst().insertion())) original.unsetComponent(ComponentTypes.LORE);
        else original.setComponent(ComponentTypes.LORE, new ItemLore(List.copyOf(lore.subList(1, lore.size()))));
        packet.setItemStack(original);
        event.markForReEncode(true);
    }

    @Override public void close() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }
}
