package net.mandomc.gameplay.auction;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.credits.*;
import net.mandomc.gameplay.ui.MenuItems;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Native fixed-price market. Escrow and credits commit in one snapshot; player-data receipts recover inventory handoff. */
public final class AuctionHouse implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final CreditService credits;
    private final YamlConfiguration settings;
    private final Set<UUID> busy = new HashSet<>();
    private final NamespacedKey escrow, delivered;
    private final BukkitTask expiry;
    private final int limit;
    private final long duration, minimum, maximum;
    private static final class Menu implements InventoryHolder {
        final UUID viewer; final Inventory inventory; final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        Menu(Player player, Component title, int size) { viewer = player.getUniqueId(); inventory = Bukkit.createInventory(this, size, title); MenuItems.fill(inventory); }
        @Override public Inventory getInventory() { return inventory; }
    }
    public AuctionHouse(JavaPlugin plugin, CreditService credits) throws Exception {
        this.plugin = plugin; this.credits = credits;
        String file = "economy/auction-house.yml";
        if (!new File(plugin.getDataFolder(), file).isFile()) plugin.saveResource(file, false);
        settings = new YamlConfiguration(); settings.load(new File(plugin.getDataFolder(), file));
        if (settings.getInt("schema-version") != 1) throw new IllegalArgumentException(file + ": unsupported schema");
        limit = settings.getInt("listing-limit"); duration = settings.getLong("listing-hours") * 3_600_000L;
        minimum = CreditLedger.amount(settings.getString("minimum-price")); maximum = CreditLedger.amount(settings.getString("maximum-price"));
        if (limit < 1 || limit > 100 || duration < 3_600_000L || duration > 30L * 86_400_000 || minimum < 1 || maximum < minimum)
            throw new IllegalArgumentException(file + ": invalid limits, duration or prices");
        for (String key : List.of("title", "mine-title", "claims-title", "confirm-title", "price", "seller", "buy-hint", "cancel-hint", "claim-hint",
                "mine", "claims", "previous", "next", "refresh", "close", "confirm", "cancel", "usage", "players-only", "denied", "mode", "combat", "busy", "empty-hand", "amount", "listing-limit", "stale", "self-buy", "insufficient", "limit", "save-failed", "listed", "bought", "cancelled", "claimed", "inventory-full", "recovered", "remaining", "sort-price", "sort-newest"))
            if (!settings.isString("text." + key)) throw new IllegalArgumentException(file + ": missing text." + key);
        escrow = new NamespacedKey(plugin, "auction_escrow_v1"); delivered = new NamespacedKey(plugin, "auction_delivered_v1");
        var command = Objects.requireNonNull(plugin.getCommand("auctionhouse"));
        command.setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) { sender.sendMessage(text("players-only")); return true; }
            if (!allowed(player)) return true;
            if (args.length == 0) browse(player, false, 0);
            else if (args[0].equalsIgnoreCase("search") && args.length > 1) browse(player, false, 0, String.join(" ", Arrays.copyOfRange(args, 1, args.length)), false);
            else if (args[0].equalsIgnoreCase("sell") && (args.length == 2 || args.length == 3)) sell(player, args);
            else if (args.length == 1 && args[0].equalsIgnoreCase("selling")) browse(player, true, 0);
            else if (args.length == 1 && Set.of("collect", "claim", "expired").contains(args[0].toLowerCase(Locale.ROOT))) recover(player, p -> claims(p, 0));
            else send(player, "usage");
            return true;
        });
        command.setTabCompleter((sender, cmd, label, args) -> args.length == 1
                ? List.of("sell", "selling", "collect", "search").stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of());
        Bukkit.getPluginManager().registerEvents(this, plugin);
        expiry = Bukkit.getScheduler().runTaskTimer(plugin, () -> credits.auctionTask(() -> {
            credits.ledger().expireAuctions(System.currentTimeMillis()); return null;
        }, (ignored, error) -> { if (error != null) log(error); }), 20, 1200);
    }
    private Component text(String key, String... values) {
        var tags = new ArrayList<net.kyori.adventure.text.minimessage.tag.resolver.TagResolver>();
        for (int i = 0; i < values.length; i += 2) tags.add(Placeholder.unparsed(values[i], values[i + 1]));
        return MiniMessage.miniMessage().deserialize(settings.getString("text." + key), tags.toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new)).decoration(TextDecoration.ITALIC, false);
    }
    private void send(Player player, String key) { player.sendMessage(text(key)); }
    private boolean allowed(Player player) {
        if (!player.hasPermission("mandomc.auction")) { send(player, "denied"); return false; }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) { send(player, "mode"); return false; }
        var combat = ((MandoPlugin) plugin).combat();
        if (combat != null && combat.tagged(player)) { send(player, "combat"); return false; }
        if (busy.contains(player.getUniqueId())) { send(player, "busy"); return false; }
        return true;
    }
    private <T> void work(Player player, java.util.concurrent.Callable<T> task, Consumer<Player> success) {
        UUID id = player.getUniqueId(); busy.add(id);
        credits.auctionTask(task, (result, error) -> {
            busy.remove(id); Player online = Bukkit.getPlayer(id);
            if (error != null) {
                if (!(error instanceof IllegalArgumentException)) log(error);
                if (online != null) { send(online, error instanceof IllegalArgumentException && settings.isString("text." + error.getMessage()) ? error.getMessage() : "save-failed"); if (!receipts(online, escrow).isEmpty()) send(online, "save-failed"); }
            } else if (online != null) success.accept(online);
        });
    }
    private void log(Throwable error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Auction change rejected; stored state preserved", error); }
    private Map<UUID, String> receipts(Player player, NamespacedKey key) {
        var result = new LinkedHashMap<UUID, String>(); String saved = player.getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (saved != null && !saved.isEmpty()) for (String line : saved.split("\n")) {
            int split = line.indexOf('='); if (split < 0) throw new IllegalStateException("Malformed native auction receipt");
            result.put(UUID.fromString(line.substring(0, split)), line.substring(split + 1));
        }
        return result;
    }
    private void receipts(Player player, NamespacedKey key, Map<UUID, String> entries) {
        if (entries.isEmpty()) player.getPersistentDataContainer().remove(key);
        else player.getPersistentDataContainer().set(key, PersistentDataType.STRING,
                entries.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining("\n")));
    }
    private static String encode(ItemStack item) { return Base64.getEncoder().encodeToString(item.serializeAsBytes()); }
    private static ItemStack decode(String item) { return ItemStack.deserializeBytes(Base64.getDecoder().decode(item)); }
    private boolean give(Player player, ItemStack item) {
        var before = Arrays.stream(player.getInventory().getStorageContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        if (player.getInventory().addItem(item.clone()).isEmpty()) return true;
        player.getInventory().setStorageContents(before); send(player, "inventory-full"); return false;
    }
    private void sell(Player player, String[] args) {
        try {
            if (credits.ledger().auctions().listings().values().stream().filter(row -> row.seller().equals(player.getUniqueId())
                    && row.expires() > System.currentTimeMillis()).count() >= limit) { send(player, "listing-limit"); return; }
            long price = CreditLedger.amount(args[1]); var held = player.getInventory().getItemInMainHand();
            if (held.isEmpty()) { send(player, "empty-hand"); return; }
            int amount = args.length == 3 ? Integer.parseInt(args[2]) : held.getAmount();
            if (price < minimum || price > maximum || amount < 1 || amount > held.getAmount()) { send(player, "amount"); return; }
            var item = held.clone(); item.setAmount(amount); String bytes = encode(item);
            if (bytes.length() > 2_000_000) { send(player, "amount"); return; }
            UUID id = UUID.randomUUID(); var receipt = receipts(player, escrow); receipt.put(id, bytes);
            held = held.clone(); held.setAmount(held.getAmount() - amount); player.getInventory().setItemInMainHand(held);
            receipts(player, escrow, receipt);
            // The supported player-data save persists removed inventory and recovery receipt together.
            player.saveData();
            var row = new AuctionBook.Listing(id, player.getUniqueId(), player.getName(), bytes, price, System.currentTimeMillis() + duration);
            work(player, () -> { credits.ledger().listAuction(row, limit, System.currentTimeMillis()); return null; }, online -> {
                recover(online, p -> { send(p, "listed"); if (allowed(p)) browse(p, true, 0); });
            });
        } catch (IllegalArgumentException error) { send(player, "amount"); }
    }
    /** A writer barrier prevents a reconnect from refunding escrow whose listing commit is still queued. */
    private void recover(Player player, Consumer<Player> after) {
        if (busy.contains(player.getUniqueId())) return;
        work(player, () -> null, online -> {
            var pending = receipts(online, escrow); boolean changed = false;
            for (var entry : List.copyOf(pending.entrySet())) {
                if (credits.ledger().auctions().accepted().contains(entry.getKey()) || give(online, decode(entry.getValue()))) {
                    pending.remove(entry.getKey()); changed = true;
                }
            }
            if (changed) { receipts(online, escrow, pending); online.saveData(); }
            var completed = receipts(online, delivered);
            if (completed.isEmpty()) { after.accept(online); return; }
            Set<UUID> ids = Set.copyOf(completed.keySet());
            work(online, () -> { credits.ledger().completeAuctionClaims(online.getUniqueId(), ids); return null; }, current -> {
                var remaining = receipts(current, delivered); ids.forEach(remaining::remove); receipts(current, delivered, remaining); current.saveData(); after.accept(current);
            });
        });
    }
    private void deliver(Player player, UUID id) {
        var row = credits.ledger().auctions().claims().get(id);
        if (row == null || !row.owner().equals(player.getUniqueId())) { send(player, "stale"); return; }
        var completed = receipts(player, delivered);
        if (!completed.containsKey(id)) {
            if (!give(player, decode(row.item()))) return;
            completed.put(id, "delivered"); receipts(player, delivered, completed);
            // Inventory and receipt share player.dat. A crash before claim acknowledgement cannot redeliver it.
            player.saveData();
        }
        work(player, () -> { credits.ledger().completeAuctionClaims(player.getUniqueId(), Set.of(id)); return null; }, online -> {
            var left = receipts(online, delivered); left.remove(id); receipts(online, delivered, left); online.saveData();
            send(online, "claimed"); if (allowed(online)) claims(online, 0);
        });
    }
    private ItemStack preview(String bytes, List<Component> extra) {
        var item = decode(bytes); var meta = item.getItemMeta(); var lore = meta.lore();
        var lines = new ArrayList<Component>(lore == null ? List.of() : lore); lines.add(Component.empty()); lines.addAll(extra); meta.lore(lines); item.setItemMeta(meta); return item;
    }
    private void button(Menu menu, int slot, Material material, String key, Consumer<Player> action) {
        var item = new ItemStack(material); var meta = item.getItemMeta(); meta.displayName(text(key)); item.setItemMeta(meta);
        menu.inventory.setItem(slot, item); menu.actions.put(slot, action);
    }
    private void browse(Player player, boolean mine, int page) { browse(player, mine, page, "", false); }
    private boolean matches(AuctionBook.Listing row, String query) {
        if (query.isBlank()) return true;
        var item = decode(row.item()); var name = item.getItemMeta().displayName();
        String label = name == null ? item.getType().name() : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(name);
        return (label + " " + item.getType().name() + " " + row.sellerName()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
    private void browse(Player player, boolean mine, int page, String query, boolean byPrice) {
        var rows = credits.ledger().auctions().listings().values().stream().filter(r -> r.expires() > System.currentTimeMillis())
                .filter(r -> !mine || r.seller().equals(player.getUniqueId())).filter(r -> matches(r, query))
                .sorted((byPrice ? Comparator.comparingLong(AuctionBook.Listing::price) : Comparator.comparingLong(AuctionBook.Listing::expires).reversed()).thenComparing(AuctionBook.Listing::id)).toList();
        page = Math.max(0, Math.min(page, Math.max(0, (rows.size() - 1) / 45))); final int current = page;
        var menu = new Menu(player, text(mine ? "mine-title" : "title"), 54);
        for (int i = page * 45; i < Math.min(rows.size(), (page + 1) * 45); i++) {
            var row = rows.get(i); int slot = i % 45; boolean own = row.seller().equals(player.getUniqueId());
            menu.inventory.setItem(slot, preview(row.item(), List.of(text("price", "price", CreditLedger.format(row.price())), text("seller", "seller", row.sellerName()), text("remaining", "minutes", Long.toString(Math.max(1,(row.expires()-System.currentTimeMillis()+59999)/60000))), text(own ? "cancel-hint" : "buy-hint"))));
            menu.actions.put(slot, p -> confirm(p, row, own));
        }
        button(menu,45,Material.CHEST,"mine",p -> browse(p,!mine,0)); button(menu,46,Material.HOPPER,"claims",p -> recover(p,currentPlayer -> claims(currentPlayer,0)));
        if (page > 0) button(menu,48,Material.ARROW,"previous",p -> browse(p,mine,current-1,query,byPrice));
        if ((page+1)*45 < rows.size()) button(menu,50,Material.ARROW,"next",p -> browse(p,mine,current+1,query,byPrice));
        button(menu,47,Material.CLOCK,byPrice ? "sort-newest" : "sort-price",p -> browse(p,mine,0,query,!byPrice));
        button(menu,49,Material.SUNFLOWER,"refresh",p -> browse(p,mine,current,query,byPrice)); button(menu,53,Material.BARRIER,"close",Player::closeInventory);
        player.openInventory(menu.inventory);
    }
    private void confirm(Player player, AuctionBook.Listing row, boolean own) {
        var menu = new Menu(player, text("confirm-title"), 9); menu.inventory.setItem(4, preview(row.item(), List.of(text("price","price",CreditLedger.format(row.price())))));
        button(menu,0,Material.LIME_STAINED_GLASS_PANE,"confirm",p -> work(p, () -> {
            if (own) credits.ledger().cancelAuction(p.getUniqueId(),row.id());
            else credits.ledger().buyAuction(p.getUniqueId(),row.id(),row.price(),System.currentTimeMillis());
            return null;
        }, online -> { send(online, own ? "cancelled" : "bought"); if (allowed(online)) deliver(online,row.id()); }));
        button(menu,8,Material.RED_STAINED_GLASS_PANE,"cancel",p -> browse(p,false,0)); player.openInventory(menu.inventory);
    }
    private void claims(Player player, int page) {
        var rows = credits.ledger().auctions().claims().values().stream().filter(r -> r.owner().equals(player.getUniqueId())).sorted(Comparator.comparing(AuctionBook.Claim::id)).toList();
        page = Math.max(0, Math.min(page, Math.max(0,(rows.size()-1)/45))); final int current=page;
        var menu = new Menu(player,text("claims-title"),54);
        for(int i=page*45;i<Math.min(rows.size(),(page+1)*45);i++) {var row=rows.get(i);int slot=i%45;menu.inventory.setItem(slot,preview(row.item(),List.of(text("claim-hint"))));menu.actions.put(slot,p->deliver(p,row.id()));}
        if(page>0)button(menu,48,Material.ARROW,"previous",p->claims(p,current-1));
        if((page+1)*45<rows.size())button(menu,50,Material.ARROW,"next",p->claims(p,current+1));
        button(menu,45,Material.CHEST,"refresh",p->browse(p,false,0));button(menu,53,Material.BARRIER,"close",Player::closeInventory);player.openInventory(menu.inventory);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.viewer.equals(player.getUniqueId()) || !allowed(player)) return;
        if (!event.isLeftClick() && !event.isRightClick()) return;
        var action=menu.actions.get(event.getRawSlot()); if(action!=null)action.accept(player);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event) {
        if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);
    }
    @EventHandler public void join(PlayerJoinEvent event) { Bukkit.getScheduler().runTask(plugin,()->recover(event.getPlayer(),p->{})); }
    @Override public void close() { expiry.cancel(); HandlerList.unregisterAll(this); }
}
