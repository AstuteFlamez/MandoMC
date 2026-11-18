package net.mandomc.commands;

import java.util.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.bootstrap.MandoPlugin;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

/** Operator-only held-item editing; survival acquisition and native anvil caps remain independent. */
public final class EnchantCommand implements CommandExecutor, TabCompleter {
    private final MandoPlugin plugin;
    private final ConfigurationSection messages;
    public EnchantCommand(MandoPlugin plugin, ConfigurationSection config) {
        this.plugin = plugin; messages = Objects.requireNonNull(config.getConfigurationSection("messages"));
        var command = Objects.requireNonNull(plugin.getCommand("enchant")); command.setExecutor(this); command.setTabCompleter(this);
    }
    private void send(CommandSender sender, String key, String value) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(messages.getString(key, key), Placeholder.unparsed("value", value)));
    }
    private Enchantment find(String value) {
        value = value.toLowerCase(Locale.ROOT).replace('-', '_');
        if (!value.contains(":")) {
            var custom = Registry.ENCHANTMENT.get(new NamespacedKey("mandomc", value)); if (custom != null) return custom;
        }
        var key = NamespacedKey.fromString(value); return key == null ? null : Registry.ENCHANTMENT.get(key);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mandomc.admin.enchant")) { send(sender, "denied", ""); return true; }
        if (args.length < 2 || args.length > 3) { send(sender, "usage", ""); return true; }
        var enchantment = find(args[0]);
        if (enchantment == null || Set.of(Enchantment.LURE, Enchantment.LUCK_OF_THE_SEA).contains(enchantment)) { send(sender, "unknown", args[0]); return true; }
        int level;
        try { level = Integer.parseInt(args[1]); } catch (NumberFormatException invalid) { send(sender, "level", ""); return true; }
        if (level < 0 || level > 255) { send(sender, "level", ""); return true; }
        Player player = args.length == 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player self ? self : null;
        if (player == null) { send(sender, "player", ""); return true; }
        if (!player.equals(sender) && !sender.hasPermission("mandomc.admin.others")) { send(sender, "denied", ""); return true; }
        var item = player.getInventory().getItemInMainHand().clone();
        if (item.isEmpty()) { send(sender, "hold", ""); return true; }
        boolean book = item.getItemMeta() instanceof EnchantmentStorageMeta;
        if (!book && level > 0 && !enchantment.canEnchantItem(item)) { send(sender, "incompatible", ""); return true; }
        boolean jetpack = enchantment.getKey().equals(net.mandomc.gameplay.boba.JetpackChestplate.KEY);
        if (jetpack && level > 2) { send(sender, "jetpack-cap", ""); return true; }
        if (jetpack && !book && level > 0) {
            var upgraded = plugin.bobaEquipment().chestplates().upgrade(item, level);
            if (upgraded != null) item = upgraded;
        }
        var meta = item.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta stored) {
            stored.removeStoredEnchant(enchantment); if (level > 0) stored.addStoredEnchant(enchantment, level, true);
            if (stored.getStoredEnchants().isEmpty()) { item.setItemMeta(stored); item.setType(Material.BOOK); meta = item.getItemMeta(); }
        } else { meta.removeEnchant(enchantment); if (level > 0) meta.addEnchant(enchantment, level, true); }
        item.setItemMeta(meta); plugin.enchantments().maintain(item); plugin.bobaEquipment().chestplates().maintain(item);
        player.getInventory().setItemInMainHand(item); send(sender, "done", enchantment.getKey().getKey() + " " + level + " · " + player.getName());
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("mandomc.admin.enchant")) return List.of();
        if (args.length == 1) {
            var names = new TreeSet<String>(); for (var enchant : Registry.ENCHANTMENT)
                if (!Set.of(Enchantment.LURE, Enchantment.LUCK_OF_THE_SEA).contains(enchant)) names.add(enchant.getKey().getKey());
            return names.stream().filter(name -> name.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2) return List.of("0", "1", "2", "3", "4", "5").stream().filter(level -> level.startsWith(args[1])).toList();
        if (args.length == 3 && sender.hasPermission("mandomc.admin.others")) return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
