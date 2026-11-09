package net.mandomc.gameplay.arrival;

import java.util.ArrayList;
import java.util.List;
import net.mandomc.gameplay.items.ItemCatalog;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Grants one complete kit on first join; never grants again after death or reconnect. */
public final class StarterKit implements Listener {
    private record Entry(String item, int amount, boolean custom) { }
    private final JavaPlugin plugin;
    private final ItemCatalog catalog;
    private final NamespacedKey granted;
    private final NamespacedKey pending;
    private final List<Entry> entries = new ArrayList<>();
    private final String message;

    public StarterKit(JavaPlugin plugin, ItemCatalog catalog, ConfigurationSection config, String message) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.message = message;
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
        if (player.getPersistentDataContainer().has(granted)) return false;
        var kit = new ArrayList<ItemStack>();
        try {
            for (Entry entry : entries) {
                var item = entry.custom() ? catalog.create(entry.item())
                        : new ItemStack(Material.matchMaterial(entry.item()));
                if (item == null || item.isEmpty()) throw new IllegalStateException("unavailable item " + entry.item());
                if (entry.amount() > item.getMaxStackSize()) throw new IllegalStateException("amount exceeds stack size for " + entry.item());
                item.setAmount(entry.amount());
                kit.add(item);
            }
            // First joins normally have an empty inventory. Check a copy so no partial kit is ever issued.
            var trial = plugin.getServer().createInventory(null, 36);
            var storage = player.getInventory().getStorageContents();
            for (int i = 0; i < storage.length; i++) trial.setItem(i, storage[i] == null ? null : storage[i].clone());
            if (!trial.addItem(kit.stream().map(ItemStack::clone).toArray(ItemStack[]::new)).isEmpty()) {
                throw new IllegalStateException("not enough inventory space");
            }
            player.getInventory().setStorageContents(trial.getContents());
            player.getPersistentDataContainer().set(granted, PersistentDataType.BYTE, (byte) 1);
            player.getPersistentDataContainer().remove(pending);
            player.sendMessage(MiniMessage.miniMessage().deserialize(message));
            return true;
        } catch (RuntimeException error) {
            plugin.getLogger().severe("Starter kit not delivered to " + player.getUniqueId() + ": " + error.getMessage());
            return false;
        }
    }
}
