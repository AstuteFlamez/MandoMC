package net.mandomc.gameplay.arrival;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import net.mandomc.gameplay.enchantments.EnchantmentService;
import net.mandomc.gameplay.items.ItemCatalog;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Grants one complete kit on first join; never grants again after death or reconnect. */
public final class StarterKit implements Listener {
    private static final Set<EquipmentSlot> ARMOR_SLOTS = Set.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private record Entry(String item, int amount, boolean custom) { }
    private final JavaPlugin plugin;
    private final ItemCatalog catalog;
    private boolean tutorialRequired;
    public void tutorialRequired(boolean required) { tutorialRequired = required; }
    private final NamespacedKey granted;
    private final NamespacedKey pending;
    private final List<Entry> entries = new ArrayList<>();
    private final String message;
    private final net.mandomc.gameplay.tutorial.TutorialHandbook handbook;

    public StarterKit(JavaPlugin plugin, ItemCatalog catalog, ConfigurationSection config, String message) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.message = message;
        this.handbook = new net.mandomc.gameplay.tutorial.TutorialHandbook(plugin);
        granted = new NamespacedKey(plugin, "starter_kit_v1");
        pending = new NamespacedKey(plugin, "starter_kit_pending_v1");
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("arrival/starter-kit.yml: expected schema-version 1");
        for (String group : List.of("materials", "custom-items")) {
            var section = config.getConfigurationSection(group);
            if (section == null) throw new IllegalArgumentException("arrival/starter-kit.yml: missing " + group);
            for (String id : section.getKeys(false)) {
                int amount = section.getInt(id, -1);
                if (amount < 1 || amount > 64) throw new IllegalArgumentException("arrival/starter-kit.yml: invalid amount for " + id);
                boolean custom = group.equals("custom-items");
                if (custom ? !catalog.ids().contains(id) : Material.matchMaterial(id) == null)
                    throw new IllegalArgumentException("arrival/starter-kit.yml: unknown item " + id);
                entries.add(new Entry(id, amount, custom));
            }
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler public void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (tutorialRequired) return;
        if ((!player.hasPlayedBefore() || player.getPersistentDataContainer().has(pending))
                && !player.getPersistentDataContainer().has(granted)) {
            player.getPersistentDataContainer().set(pending, PersistentDataType.BYTE, (byte) 1);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) grant(player);
            });
        }
    }

    /** Atomic delivery: construct everything, check capacity, then add it and record ownership. */
    public boolean grant(Player player) {
        if ((net.mandomc.gameplay.tutorial.TutorialInventory.active(player) || net.mandomc.gameplay.admin.StaffMode.active(player))) return false;
        if (player.getPersistentDataContainer().has(granted)) return false;
        var kit = new ArrayList<ItemStack>();
        var armor = new EnumMap<EquipmentSlot, ItemStack>(EquipmentSlot.class);
        var inventory = player.getInventory();
        try {
            for (Entry entry : entries) {
                var item = entry.custom() ? catalog.create(entry.item())
                        : new ItemStack(Material.matchMaterial(entry.item()));
                if (item == null || item.isEmpty()) throw new IllegalStateException("unavailable item " + entry.item());
                if (entry.amount() > item.getMaxStackSize()) throw new IllegalStateException("amount exceeds stack size for " + entry.item());
                item.setAmount(entry.amount());
                if (entry.item().equals("blue_training_lightsaber")) EnchantmentService.markStarterSaber(item);
                var slot = item.getType().getEquipmentSlot();
                if (ARMOR_SLOTS.contains(slot) && item.getAmount() == 1 && inventory.getItem(slot).isEmpty()
                        && !armor.containsKey(slot)) {
                    armor.put(slot, item);
                } else {
                    kit.add(item);
                }
            }
            // First joins normally have an empty inventory. Check a copy so no partial kit is ever issued.
            var trial = plugin.getServer().createInventory(null, 36);
            var storage = player.getInventory().getStorageContents();
            for (int i = 0; i < storage.length; i++) trial.setItem(i, storage[i] == null ? null : storage[i].clone());
            // Reuse a marked handbook (including offhand) without adding another copy.
            // Refresh only the trial inventory until the entire kit is known to fit.
            boolean hasHandbook = false;
            ItemStack offhand = player.getInventory().getItemInOffHand();
            ItemStack refreshedOffhand = null;
            for (int slot = 0; slot < trial.getSize(); slot++) {
                if (handbook.isHandbook(trial.getItem(slot))) {
                    var current = handbook.create();
                    current.setAmount(trial.getItem(slot).getAmount());
                    trial.setItem(slot, current);
                    hasHandbook = true;
                    break;
                }
            }
            if (!hasHandbook && handbook.isHandbook(offhand)) {
                refreshedOffhand = handbook.create();
                refreshedOffhand.setAmount(offhand.getAmount());
                hasHandbook = true;
            }
            if (!hasHandbook) kit.add(handbook.create());
            if (!trial.addItem(kit.stream().map(ItemStack::clone).toArray(ItemStack[]::new)).isEmpty()) {
                return false;
            }
            inventory.setStorageContents(trial.getContents());
            armor.forEach(inventory::setItem);
            if (refreshedOffhand != null) player.getInventory().setItemInOffHand(refreshedOffhand);
            player.getPersistentDataContainer().set(granted, PersistentDataType.BYTE, (byte) 1);
            player.getPersistentDataContainer().remove(pending);
            player.saveData();
            player.sendActionBar(MiniMessage.miniMessage().deserialize(message));
            return true;
        } catch (RuntimeException error) {
            plugin.getLogger().severe("Starter kit not delivered to " + player.getUniqueId() + ": " + error.getMessage());
            return false;
        }
    }
}
