package net.mandomc.gameplay.credits;

import java.util.*;
import java.util.concurrent.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.commands.KnownPlayer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class CreditService implements AutoCloseable, org.bukkit.event.Listener {
    private final JavaPlugin plugin;
    private final CreditLedger ledger;
    private final long initialBalance;
    private final ConfigurationSection messages;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final Set<UUID> pending = new HashSet<>();
    private volatile boolean closed;
    private net.mandomc.gameplay.auction.AuctionHouse auctions;
    public CreditService(JavaPlugin plugin, ConfigurationSection config) throws Exception {
        this.plugin = plugin;
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("credits.yml: expected schema-version 1");
        try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(plugin.getResource("economy/credits.yml")),
                java.nio.charset.StandardCharsets.UTF_8)) {
            config.getRoot().setDefaults(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader));
        }
        try { initialBalance = CreditLedger.amount(config.getString("initial-balance", "2500.00")); }
        catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("economy/credits.yml: invalid initial-balance", error);
        }
        messages = Objects.requireNonNull(config.getConfigurationSection("messages"), "credits.yml: missing messages");
        for (String key : List.of("balance", "paid", "received", "adjusted", "top-title", "top-row", "top-empty", "amount", "self-pay", "insufficient", "limit", "usage", "player-missing", "players-only", "denied", "saving", "save-failed"))
            if (!messages.isString(key)) throw new IllegalArgumentException("credits.yml: missing messages." + key);
        ledger = new CreditLedger(plugin.getDataFolder().toPath().resolve("data/credits-data.yml"));
        auctions = new net.mandomc.gameplay.auction.AuctionHouse(plugin, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (String command : List.of("balance", "pay", "balancetop", "credits")) {
            var binding = Objects.requireNonNull(plugin.getCommand(command));
            binding.setExecutor((sender, cmd, label, args) -> { execute(sender, cmd.getName(), args); return true; });
            binding.setTabCompleter((sender, cmd, label, args) -> {
                if (!cmd.testPermissionSilent(sender)) return List.of();
                if (cmd.getName().equals("credits") && args.length == 1)
                    return List.of("give", "take", "set").stream().filter(s -> s.startsWith(args[0])).toList();
                int index = cmd.getName().equals("credits") ? 2 : 1;
                if (args.length != index || cmd.getName().equals("balancetop")) return List.of();
                return Bukkit.getOnlinePlayers().stream().filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p))
                        .map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[index - 1].toLowerCase(Locale.ROOT))).toList();
            });
        }
    }
    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        boolean newPlayer = !event.getPlayer().hasPlayedBefore();
        // Snapshot Paper's first-play status before moving disk work to the sole economy writer.
        auctionTask(() -> ledger.initializeAccount(id, newPlayer, initialBalance), (created, error) -> {
            if (error != null) plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Credits account initialization failed for " + id + "; balance unchanged", error);
        });
    }
    public CreditLedger ledger() { return ledger; }
    public net.mandomc.gameplay.auction.AuctionHouse auctions() { return auctions; }
    /** All credit and market mutations share this one writer; callbacks run on the server thread. */
    public <T> void auctionTask(java.util.concurrent.Callable<T> work, java.util.function.BiConsumer<T, Throwable> done) {
        if (closed) { done.accept(null, new IllegalStateException("closed")); return; }
        writer.submit(() -> {
            T value = null; Throwable failure = null;
            try { value = work.call(); } catch (Exception error) { failure = error; }
            T result = value; Throwable error = failure;
            if (!closed && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> done.accept(result, error));
        });
    }
    private void send(CommandSender sender, String key, String name, String amount) {
        net.mandomc.gameplay.messaging.ChatMessages.send(sender,"economy/credits.yml","messages."+key,MiniMessage.miniMessage().deserialize(messages.getString(key),
                Placeholder.unparsed("player", name), Placeholder.unparsed("amount", amount)));
    }
    private void execute(CommandSender sender, String command, String[] args) {
        try {
            String permission = switch (command) { case "credits" -> "admin"; case "balancetop" -> "top"; default -> command; };
            if (!sender.hasPermission("mandomc.credits." + permission)) throw new IllegalArgumentException("denied");
            if (command.equals("balance")) {
                if (args.length > 1) throw new IllegalArgumentException("usage");
                KnownPlayer target;
                if (args.length == 1) target = KnownPlayer.find(sender, args[0]);
                else if (sender instanceof Player player) target = new KnownPlayer(player.getUniqueId(), player.getName());
                else throw new IllegalArgumentException("players-only");
                send(sender, "balance", target.name(), CreditLedger.format(ledger.balance(target.id()))); return;
            }
            if (command.equals("balancetop")) {
                if (args.length != 0) throw new IllegalArgumentException("usage");
                send(sender, "top-title", "", "");
                var top = ledger.top(10);
                if (top.isEmpty()) send(sender, "top-empty", "", "");
                for (var entry : top) {
                    var offline = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = offline.getName() == null ? entry.getKey().toString() : offline.getName();
                    send(sender, "top-row", name, CreditLedger.format(entry.getValue()));
                }
                return;
            }
            if (closed) throw new IllegalArgumentException("save-failed");
            if (command.equals("pay")) {
                if (!(sender instanceof Player player)) throw new IllegalArgumentException("players-only");
                if (args.length != 2) throw new IllegalArgumentException("usage");
                var target = KnownPlayer.find(sender, args[0]);
                long cents = CreditLedger.amount(args[1]);
                UUID from = player.getUniqueId(); String name = player.getName();
                submit(sender, from, () -> ledger.transfer(from, target.id(), cents), () -> {
                    send(sender, "paid", target.name(), CreditLedger.format(cents));
                    var recipient = Bukkit.getPlayer(target.id());
                    if (recipient != null) send(recipient, "received", name, CreditLedger.format(cents));
                });
            } else {
                if (args.length != 3 || !List.of("give", "take", "set").contains(args[0])) throw new IllegalArgumentException("usage");
                var target = KnownPlayer.find(sender, args[1]); long cents = CreditLedger.amount(args[2]);
                String actor = sender.getName(); String operation = args[0];
                submit(sender, target.id(), () -> ledger.adjust(target.id(), operation, cents), () -> {
                    plugin.getLogger().info("Credits: " + actor + " " + operation + " " + CreditLedger.format(cents) + " for " + target.id());
                    send(sender, "adjusted", target.name(), CreditLedger.format(ledger.balance(target.id())));
                });
            }
        } catch (IllegalArgumentException error) { send(sender, error.getMessage(), "", ""); }
    }
    @FunctionalInterface private interface Change { void run() throws Exception; }
    private void submit(CommandSender sender, UUID key, Change change, Runnable success) {
        if (!pending.add(key)) throw new IllegalArgumentException("saving");
        writer.submit(() -> {
            String failure = null;
            try { change.run(); }
            catch (IllegalArgumentException error) { failure = error.getMessage(); }
            catch (Exception error) {
                failure = "save-failed";
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Credits save failed; transaction rejected", error);
            }
            String result = failure;
            if (!closed && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                pending.remove(key);
                if (result == null) success.run(); else send(sender, result, "", "");
            });
        });
    }
    @Override public void close() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        if (auctions != null) auctions.close();
        closed = true; writer.shutdown();
        try { if (!writer.awaitTermination(10, TimeUnit.SECONDS)) plugin.getLogger().severe("Credits save is still finishing."); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }
}
