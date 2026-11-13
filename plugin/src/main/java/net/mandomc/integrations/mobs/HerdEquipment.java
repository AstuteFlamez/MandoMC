package net.mandomc.integrations.mobs;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.ui.MenuItems;
import net.mandomc.integrations.models.HerdModels;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Display-only saddle GUI. Every accepted transfer writes the actual horse saddle slot once. */
public final class HerdEquipment implements Listener {
    public static final int SADDLE_SLOT = 4;
    private final JavaPlugin plugin;
    private final HerdAnimals animals;
    private final HerdModels models;
    private final ConfigurationSection language;
    private final Set<Menu> menus = new HashSet<>();
    private final MiniMessage mini = MiniMessage.miniMessage();

    private static final class Menu implements InventoryHolder {
        private final UUID viewer;
        private final UUID horse;
        private final Inventory inventory;
        private Menu(Player player, Horse horse, Component title) {
            this.viewer = player.getUniqueId(); this.horse = horse.getUniqueId();
            inventory = Bukkit.createInventory(this, 9, title); MenuItems.fill(inventory);
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    public HerdEquipment(JavaPlugin plugin, HerdAnimals animals, HerdModels models, ConfigurationSection language) {
        this.plugin = plugin; this.animals = animals; this.models = models; this.language = language;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (var menu : Set.copyOf(menus)) {
                var player = Bukkit.getPlayer(menu.viewer);
                var horse = Bukkit.getEntity(menu.horse);
                if (player == null || player.getOpenInventory().getTopInventory() != menu.inventory) menus.remove(menu);
                else if (!(horse instanceof Horse h) || !access(player, h)) player.closeInventory();
                else render(menu, h);
            }
        }, 5, 5);
    }

    public static boolean isMenu(Inventory inventory, Horse horse) {
        return inventory.getHolder() instanceof Menu menu && menu.horse.equals(horse.getUniqueId());
    }
    private boolean access(Player player, Horse horse) { return animals.access(player, horse) && !models.occupied(horse); }
    private Component text(String key, String fallback, Horse horse) {
        return mini.deserialize(language.getString(key, fallback), Placeholder.unparsed("animal", animals.type(horse)))
                .decoration(TextDecoration.ITALIC, false);
    }
    public void open(Player player, Horse horse) {
        if (!access(player, horse)) return;
        var menu = new Menu(player, horse, text("gui-title", "<gold><animal> Equipment", horse));
        render(menu, horse); menus.add(menu); player.openInventory(menu.inventory);
    }
    private void render(Menu menu, Horse horse) {
        boolean equipped = HerdModels.saddled(horse);
        var icon = equipped ? horse.getInventory().getSaddle().clone() : new ItemStack(Material.LEATHER);
        var meta = icon.getItemMeta();
        meta.displayName(text(equipped ? "gui-equipped" : "gui-empty", equipped ? "<yellow>Saddle" : "<yellow>Saddle Slot", horse));
        meta.lore(java.util.List.of(text(equipped ? "gui-remove" : "gui-insert", equipped
                ? "<gray>Click to remove the saddle." : "<gray>Place a saddle here or shift-click one below.", horse)));
        icon.setItemMeta(meta); menu.inventory.setItem(SADDLE_SLOT, icon);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) return;
        var entity = Bukkit.getEntity(menu.horse);
        if (!(event.getPlayer() instanceof Player p) || !menu.viewer.equals(p.getUniqueId())
                || !(entity instanceof Horse h) || !access(p, h)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        var entity = Bukkit.getEntity(menu.horse);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.viewer.equals(player.getUniqueId())
                || !(entity instanceof Horse horse) || !access(player, horse)) { event.setCancelled(true); return; }
        int raw = event.getRawSlot();
        if (event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.CREATIVE) { event.setCancelled(true); return; }
        if (raw >= menu.inventory.getSize()) {
            if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                event.setCancelled(true);
                var stack = event.getCurrentItem();
                if (!HerdModels.saddled(horse) && stack != null && stack.getType() == Material.SADDLE) {
                    horse.getInventory().setSaddle(stack.asOne()); stack.subtract(1); event.setCurrentItem(stack); render(menu, horse);
                }
            }
            return; // Ordinary cursor/hotbar moves inside the player's own inventory stay native.
        }
        event.setCancelled(true);
        if (raw != SADDLE_SLOT || !(event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT
                || event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT)) return;
        var cursor = player.getItemOnCursor();
        if (!HerdModels.saddled(horse)) {
            if (cursor.getType() != Material.SADDLE || event.isShiftClick()) return;
            horse.getInventory().setSaddle(cursor.asOne()); cursor.subtract(1); player.setItemOnCursor(cursor);
        } else {
            var saddle = horse.getInventory().getSaddle().clone();
            if (event.isShiftClick()) {
                // A one-item native saddle needs one compatible or empty storage slot.
                boolean room = java.util.Arrays.stream(player.getInventory().getStorageContents())
                        .anyMatch(item -> item == null || item.isEmpty() || item.isSimilar(saddle) && item.getAmount() < item.getMaxStackSize());
                if (!room) return;
                horse.getInventory().setSaddle(null); player.getInventory().addItem(saddle);
            } else {
                if (!cursor.isEmpty()) return;
                horse.getInventory().setSaddle(null); player.setItemOnCursor(saddle);
            }
        }
        render(menu, horse);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        var horse = Bukkit.getEntity(menu.horse);
        if (!(event.getWhoClicked() instanceof Player p) || !menu.viewer.equals(p.getUniqueId())
                || !(horse instanceof Horse h) || !access(p, h)
                || event.getRawSlots().stream().anyMatch(slot -> slot < menu.inventory.getSize())) event.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Menu menu) menus.remove(menu);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Menu || event.getDestination().getHolder() instanceof Menu) event.setCancelled(true);
    }
    @EventHandler public void disabled(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) return;
        for (var menu : Set.copyOf(menus)) for (var viewer : java.util.List.copyOf(menu.inventory.getViewers())) viewer.closeInventory();
        menus.clear();
    }
}
