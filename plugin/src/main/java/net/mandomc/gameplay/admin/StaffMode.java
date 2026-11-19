package net.mandomc.gameplay.admin;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.ilum.ParkourInventory;
import net.mandomc.gameplay.ilum.ParkourService;
import net.mandomc.gameplay.tutorial.TutorialInventory;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.InventoryView;

/** Staff inventory is disposable; survival inventory is committed before privileges change. */
public final class StaffMode implements Listener, AutoCloseable {
    private final MandoPlugin plugin;
    private final ConfigurationSection messages;
    private final org.bukkit.scheduler.BukkitTask eligibility;
    private final Set<UUID> changing = new HashSet<>();
    public StaffMode(MandoPlugin plugin, ConfigurationSection config) {
        this.plugin = plugin;
        messages = Objects.requireNonNull(config.getConfigurationSection("messages"));
        Objects.requireNonNull(plugin.getCommand("staff")).setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) {
                if (args.length == 2 && args[0].equalsIgnoreCase("revokeop")) {
                    var target = Bukkit.getPlayerExact(args[1]);
                    if (target != null) { disable(target); plugin.ranks().revokeOperator(target); plugin.ranks().apply(target); }
                }
                return true;
            }
            if (args.length > 1 || args.length == 1 && !Set.of("on", "off").contains(args[0].toLowerCase(Locale.ROOT))) {
                send(player, "usage"); return true;
            }
            boolean enable = args.length == 0 ? !active(player) : args[0].equalsIgnoreCase("on");
            if (enable && !active(player)) enable(player);
            else if (!enable && active(player)) disable(player);
            else send(player, active(player) ? "enabled" : "disabled");
            return true;
        });
        plugin.getCommand("staff").setTabCompleter((sender, cmd, label, args) -> args.length == 1 ? List.of("on", "off") : List.of());
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (var player : Bukkit.getOnlinePlayers()) recover(player);
        eligibility = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (var player : Bukkit.getOnlinePlayers()) {
                if (!active(player) && player.isOp()) plugin.ranks().apply(player);
                else if (active(player) && !plugin.ranks().staffEligible(player)) disable(player);
            }
        }, 1, 1);
    }
    public static boolean active(Player player) { return StaffInventory.active(player); }
    private void send(Player player, String key) { player.sendMessage(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(messages.getString(key)))); }
    private void enable(Player player) {
        if (!plugin.ranks().staffEligible(player)) { send(player, "denied"); return; }
        if (player.isDead() || player.isInsideVehicle() || plugin.vehicles().mountedType(player) != null
                || plugin.combat().tagged(player) || TutorialInventory.active(player)
                || plugin.credits().auctions().pending(player)) { send(player, "busy"); return; }
        if (player.getPersistentDataContainer().has(net.mandomc.gameplay.crates.CrateService.PENDING)) {
            send(player, "busy");
            return;
        }
        for (String key : List.of("auction_escrow_v1", "jawa_sell_custody_v1"))
            if (player.getPersistentDataContainer().has(new NamespacedKey("mandomc", key))) { send(player, "busy"); return; }
        if (ParkourInventory.stored(player)) {
            var parkour = Bukkit.getServicesManager().load(ParkourService.class);
            if (parkour == null) { send(player, "busy"); return; }
            parkour.leave(player, true, false);
        }
        player.closeInventory();
        if (!player.getItemOnCursor().isEmpty()) { send(player, "busy"); return; }
        net.mandomc.integrations.weapons.WeaponMechanicsItems.stopActions(player);
        plugin.sabers().finish(player.getUniqueId());
        StaffInventory.capture(player);
        player.getInventory().clear();
        plugin.ranks().apply(player);
        player.setGameMode(GameMode.CREATIVE);
        player.setInvulnerable(true);
        player.saveData(); player.updateCommands(); send(player, "enabled");
    }
    public void disable(Player player) {
        if (!active(player) || !changing.add(player.getUniqueId())) return;
        try {
            player.closeInventory();
            plugin.sabers().finish(player.getUniqueId());
            var destination = StaffInventory.destination(player, plugin.warps().destination("spawn"));
            if (!player.teleport(destination)) { send(player, "return-blocked"); return; }
            player.setItemOnCursor(null);
            plugin.admin().god(player, false); plugin.admin().vanish(player, false);
            StaffInventory.restore(player);
            plugin.ranks().apply(player);
            player.saveData(); player.updateCommands(); send(player, "disabled");
        } finally { changing.remove(player.getUniqueId()); }
    }
    private void recover(Player player) {
        if (active(player)) disable(player);
        else if (plugin.ranks().staffEligible(player) && !TutorialInventory.active(player) && !ParkourInventory.stored(player)) {
            player.setGameMode(GameMode.SURVIVAL); player.setAllowFlight(false); player.setInvulnerable(false);
            plugin.admin().god(player, false); plugin.admin().vanish(player, false);
            plugin.ranks().apply(player);
        }
    }
    @EventHandler(priority = EventPriority.LOWEST) public void join(PlayerJoinEvent e) { recover(e.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST) public void quit(PlayerQuitEvent e) { disable(e.getPlayer()); }
    @EventHandler(priority = EventPriority.HIGHEST) public void drop(PlayerDropItemEvent e) { if (active(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void pickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && active(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void damage(EntityDamageEvent e) { if (e.getEntity() instanceof Player p && active(p)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void death(PlayerDeathEvent e) { if (active(e.getEntity())) { e.getDrops().clear(); e.setKeepInventory(true); e.setDroppedExp(0); } }
    private boolean persistent(InventoryView view) {
        var top = view.getTopInventory();
        if (top instanceof org.bukkit.inventory.HorseInventory) return true;
        return switch (top.getType()) {
            case ENDER_CHEST, BARREL, SHULKER_BOX, DISPENSER, DROPPER, HOPPER, FURNACE,
                    BLAST_FURNACE, SMOKER, BREWING, CHISELED_BOOKSHELF, CRAFTER,
                    BEACON, JUKEBOX, LECTERN -> true;
            default -> top.getHolder() instanceof org.bukkit.block.Container
                    || top.getHolder() instanceof org.bukkit.block.DoubleChest
                    || top.getHolder() instanceof org.bukkit.entity.Entity
                    && top.getType() != InventoryType.MERCHANT;
        };
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !active(player)
                || !persistent(event.getView())) return;
        // Inspect real storage without transferring items between it and the disposable inventory.
        if (event.getClickedInventory() == event.getView().getTopInventory() || event.isShiftClick()
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && active(player) && persistent(event.getView())
                && event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
        }
    }
    @Override public void close() { eligibility.cancel(); for (var player : List.copyOf(Bukkit.getOnlinePlayers())) disable(player); HandlerList.unregisterAll(this); }
}
