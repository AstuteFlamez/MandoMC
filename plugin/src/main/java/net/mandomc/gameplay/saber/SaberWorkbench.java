package net.mandomc.gameplay.saber;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.ui.MenuItems;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** One placed station opens native crafting, stonecutting and anvil transactions. */
public final class SaberWorkbench implements Listener, AutoCloseable {
    private final MandoPlugin plugin;
    private final YamlConfiguration config;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private record Session(Location station, Inventory inventory) { }
    private static final class Menu implements InventoryHolder {
        final UUID owner; final Location station; final Inventory inventory;
        Menu(Player player, Block block, Component title) {
            owner = player.getUniqueId(); station = block.getLocation();
            inventory = Bukkit.createInventory(this, 27, title); MenuItems.fill(inventory);
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    public SaberWorkbench(MandoPlugin plugin, YamlConfiguration config) {
        this.plugin = plugin; this.config = config;
        if (config.getInt("schema-version") != 1) throw new IllegalArgumentException("saber-workbench.yml: schema-version 1 required");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    private Component text(String key) { return MiniMessage.miniMessage().deserialize(config.getString("text." + key, key)); }
    // Keep the placed-block key so existing prototype stations migrate in place.
    private NamespacedKey marker(Block block) { return new NamespacedKey("mandomc", "forge_" + block.getX() + "_" + block.getY() + "_" + block.getZ()); }
    public boolean station(Block block) { return block.getType() == Material.SMITHING_TABLE && block.getChunk().getPersistentDataContainer().has(marker(block), PersistentDataType.BYTE); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void place(BlockPlaceEvent event) {
        var block = event.getBlockPlaced(); block.getChunk().getPersistentDataContainer().remove(marker(block));
        if (Set.of("saber_workbench", "saber_forge").contains(Objects.requireNonNullElse(plugin.items().identify(event.getItemInHand()), "")))
            block.getChunk().getPersistentDataContainer().set(marker(block), PersistentDataType.BYTE, (byte) 1);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void drops(BlockDropItemEvent event) {
        var block = event.getBlock(); if (!block.getChunk().getPersistentDataContainer().has(marker(block), PersistentDataType.BYTE)) return;
        for (var drop : event.getItems()) if (drop.getItemStack().getType() == Material.SMITHING_TABLE) drop.setItemStack(plugin.items().create("saber_workbench"));
        block.getChunk().getPersistentDataContainer().remove(marker(block));
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void broken(BlockBreakEvent event) {
        var block = event.getBlock(); if (station(block)) Bukkit.getScheduler().runTask(plugin, () -> {
            if (block.getType() != Material.SMITHING_TABLE) block.getChunk().getPersistentDataContainer().remove(marker(block));
        });
    }
    @EventHandler(ignoreCancelled = true) public void push(BlockPistonExtendEvent event) { if (event.getBlocks().stream().anyMatch(this::station)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void pull(BlockPistonRetractEvent event) { if (event.getBlocks().stream().anyMatch(this::station)) event.setCancelled(true); }
    private boolean allowed(Player player, Block block) {
        return station(block) && player.getWorld() == block.getWorld() && player.getLocation().distanceSquared(block.getLocation().add(.5, .5, .5)) <= 36
                && !player.isDead() && !player.isInsideVehicle() && !plugin.combat().tagged(player) && !plugin.sabers().busy(player)
                && !net.mandomc.gameplay.tutorial.TutorialInventory.active(player)
                && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE
                    || net.mandomc.gameplay.admin.StaffMode.active(player) && player.getGameMode() == GameMode.CREATIVE);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void use(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null || !station(event.getClickedBlock())) return;
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        event.setCancelled(true); var player = event.getPlayer();
        if (!allowed(player, event.getClickedBlock())) { player.sendMessage(text("unsafe")); return; }
        open(player, event.getClickedBlock());
    }
    public void open(Player player, Block block) {
        if (!allowed(player, block)) { player.sendMessage(text("unsafe")); return; }
        var menu = new Menu(player, block, text("title"));
        button(menu, 10, Material.CRAFTING_TABLE, "craft", "craft-description");
        button(menu, 13, Material.STONECUTTER, "style", "style-description");
        button(menu, 16, Material.ENCHANTED_BOOK, "enchant", "enchant-description");
        button(menu, 26, Material.BARRIER, "close", "close-description");
        player.openInventory(menu.inventory);
    }
    private void button(Menu menu, int slot, Material material, String name, String description) {
        var item = new ItemStack(material); item.editMeta(meta -> { meta.displayName(text(name)); meta.lore(List.of(text(description))); });
        menu.inventory.setItem(slot, item);
    }
    public void function(Player player, Block station, int slot) {
        if (!allowed(player, station)) { player.closeInventory(); return; }
        var builder = switch (slot) {
            case 10 -> MenuType.CRAFTING.builder();
            case 13 -> MenuType.STONECUTTER.builder();
            case 16 -> MenuType.ANVIL.builder();
            default -> null;
        };
        if (builder == null) return;
        // Native workstations must receive their title at creation; changing it afterward can throw.
        var title = text("title").append(Component.text(" · ")).append(text(slot == 10 ? "craft" : slot == 13 ? "style" : "enchant"));
        InventoryView view = builder.location(station.getLocation()).checkReachable(false).title(title).build(player);
        var session = new Session(station.getLocation(), view.getTopInventory());
        // InventoryOpenEvent must recognize this transient workbench view before staff isolation checks run.
        sessions.put(player.getUniqueId(), session);
        try {
            view.open();
        } finally {
            if (!view.getTopInventory().equals(player.getOpenInventory().getTopInventory()))
                sessions.remove(player.getUniqueId(), session);
        }
    }
    private Session session(InventoryView view) {
        var session = sessions.get(view.getPlayer().getUniqueId());
        return session != null && session.inventory().equals(view.getTopInventory()) ? session : null;
    }
    public boolean ownsMenu(Player player, InventoryView view) {
        var session = session(view);
        return session != null && player.getUniqueId().equals(view.getPlayer().getUniqueId())
                && allowed(player, session.station().getBlock());
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof Menu menu) {
            event.setCancelled(true);
            if (!menu.owner.equals(player.getUniqueId()) || !event.isLeftClick()) return;
            int slot = event.getRawSlot();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() != menu) return;
                if (slot == 26) player.closeInventory();
                else function(player, menu.station.getBlock(), slot);
            }); return;
        }
        var session = session(event.getView());
        if (session != null && !allowed(player, session.station().getBlock())) { event.setCancelled(true); Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory()); }
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
        var session = session(event.getView());
        if (session != null && event.getWhoClicked() instanceof Player player && !allowed(player, session.station().getBlock())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void enchant(PrepareAnvilEvent event) {
        var session = session(event.getView()); if (session == null) return;
        var left = event.getInventory().getItem(0); var right = event.getInventory().getItem(1);
        boolean saber = plugin.sabers().isSaber(left);
        boolean books = left != null && left.getType() == Material.ENCHANTED_BOOK && right != null && right.getType() == Material.ENCHANTED_BOOK;
        if (!allowed((Player) event.getView().getPlayer(), session.station().getBlock()) || !saber && !books) event.setResult(null);
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.inventory().equals(event.getInventory())) sessions.remove(event.getPlayer().getUniqueId());
    }
    @Override public void close() {
        for (var id : List.copyOf(sessions.keySet())) { var player = Bukkit.getPlayer(id); if (player != null) player.closeInventory(); }
        sessions.clear(); HandlerList.unregisterAll(this);
    }
}
