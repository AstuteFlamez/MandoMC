package net.mandomc.gameplay.credits;

import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Integer cents, atomic transfers and replace-on-success persistence. Call mutations on one IO executor. */
public final class CreditLedger {
    public static final long MAX = 9_000_000_000_000_000L;
    private final Path file;
    private volatile Map<UUID, Long> balances;
    private volatile net.mandomc.gameplay.auction.AuctionBook auctions = net.mandomc.gameplay.auction.AuctionBook.empty();
    public net.mandomc.gameplay.auction.AuctionBook auctions() { return auctions; }
    public CreditLedger(Path file) throws Exception {
        this.file = file;
        if (!Files.exists(file)) { balances = Map.of(); return; }
        var yaml = new YamlConfiguration(); yaml.load(file.toFile());
        if (yaml.getInt("schema-version", -1) != 1 || !yaml.isConfigurationSection("balances"))
            throw new IllegalArgumentException("credits-data.yml: invalid schema");
        var result = new HashMap<UUID, Long>();
        for (String id : yaml.getConfigurationSection("balances").getKeys(false)) {
            Object raw = yaml.get("balances." + id);
            if (!(raw instanceof Long || raw instanceof Integer)) throw new IllegalArgumentException("credits-data.yml: invalid cents for " + id);
            long cents = ((Number) raw).longValue();
            validate(cents); result.put(UUID.fromString(id), cents);
        }
        balances = Map.copyOf(result);
        auctions = net.mandomc.gameplay.auction.AuctionBook.read(yaml.getConfigurationSection("auction-house"));
    }
    public static long amount(String input) {
        if (!input.matches("[0-9]{1,14}(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException("amount");
        try { long cents = new BigDecimal(input).movePointRight(2).longValueExact(); validate(cents); return cents; }
        catch (ArithmeticException error) { throw new IllegalArgumentException("amount"); }
    }
    private static void validate(long cents) { if (cents < 0 || cents > MAX) throw new IllegalArgumentException("amount"); }
    public static String format(long cents) { return String.format(Locale.US, "%,d.%02d", cents / 100, cents % 100); }
    public long balance(UUID id) { return balances.getOrDefault(id, 0L); }
    /** First login only: an existing account, including a zero balance, is never replaced. */
    public synchronized boolean initializeAccount(UUID id, boolean newPlayer, long initialCents) throws Exception {
        validate(initialCents);
        if (balances.containsKey(id)) return false;
        var updated = new HashMap<>(balances);
        updated.put(id, newPlayer ? initialCents : 0L);
        save(updated);
        return newPlayer;
    }
    public List<Map.Entry<UUID, Long>> top(int limit) {
        return balances.entrySet().stream().sorted(Map.Entry.<UUID, Long>comparingByValue().reversed()
                .thenComparing(e -> e.getKey().toString())).limit(limit).toList();
    }
    public synchronized void transfer(UUID from, UUID to, long cents) throws Exception {
        validate(cents);
        if (cents == 0) throw new IllegalArgumentException("amount");
        if (from.equals(to)) throw new IllegalArgumentException("self-pay");
        if (balance(from) < cents) throw new IllegalArgumentException("insufficient");
        if (balance(to) > MAX - cents) throw new IllegalArgumentException("limit");
        var next = new HashMap<>(balances);
        next.put(from, balance(from) - cents); next.put(to, balance(to) + cents);
        save(next);
    }
    public synchronized void adjust(UUID id, String operation, long cents) throws Exception {
        validate(cents);
        long next = switch (operation) {
            case "set" -> cents;
            case "give" -> Math.addExact(balance(id), cents);
            case "take" -> balance(id) - cents;
            default -> throw new IllegalArgumentException("usage");
        };
        if (next < 0) throw new IllegalArgumentException("insufficient");
        if (next > MAX) throw new IllegalArgumentException("limit");
        var updated = new HashMap<>(balances); updated.put(id, next); save(updated);
    }
    /** Shop credit and inventory claims share the same atomic snapshot and receipt namespace as auctions. */
    public synchronized void supply(UUID transaction, UUID owner, String item, long price, boolean buying) throws Exception {
        validate(price);
        if (price == 0 || auctions.accepted().contains(transaction)) throw new IllegalArgumentException("stale");
        long balance = balance(owner);
        if (buying && balance < price) throw new IllegalArgumentException("insufficient");
        if (!buying && balance > MAX - price) throw new IllegalArgumentException("limit");
        var updated = new HashMap<>(balances);
        updated.put(owner, buying ? balance - price : balance + price);
        var accepted = new HashSet<>(auctions.accepted()); accepted.add(transaction);
        var claims = new HashMap<>(auctions.claims());
        if (buying) {
            if (item == null || Base64.getDecoder().decode(item).length == 0) throw new IllegalArgumentException("amount");
            claims.put(transaction, new net.mandomc.gameplay.auction.AuctionBook.Claim(transaction, owner, item));
        }
        save(updated, new net.mandomc.gameplay.auction.AuctionBook(auctions.listings(), claims, accepted));
    }
    /** Every deposited stack is accepted in the same durable snapshot as the one total payout. */
    public synchronized void sellSupplies(UUID owner, Set<UUID> receipts, long price) throws Exception {
        validate(price);
        if(price==0 || receipts.isEmpty() || !Collections.disjoint(auctions.accepted(),receipts))throw new IllegalArgumentException("stale");
        if(balance(owner)>MAX-price)throw new IllegalArgumentException("limit");
        var updated=new HashMap<>(balances);updated.put(owner,balance(owner)+price);
        var accepted=new HashSet<>(auctions.accepted());accepted.addAll(receipts);
        save(updated,new net.mandomc.gameplay.auction.AuctionBook(auctions.listings(),auctions.claims(),accepted));
    }
    public synchronized void listAuction(net.mandomc.gameplay.auction.AuctionBook.Listing row, int limit, long now) throws Exception {
        validate(row.price());
        if (row.price() == 0 || row.expires() <= now) throw new IllegalArgumentException("amount");
        save(balances, auctions.expire(now).list(row, limit, now));
    }
    public synchronized void buyAuction(UUID buyer, UUID id, long price, long now) throws Exception {
        var row = auctions.listings().get(id);
        if (row == null || row.price() != price || row.expires() <= now) throw new IllegalArgumentException("stale");
        if (row.seller().equals(buyer)) throw new IllegalArgumentException("self-buy");
        if (balance(buyer) < price) throw new IllegalArgumentException("insufficient");
        if (balance(row.seller()) > MAX - price) throw new IllegalArgumentException("limit");
        var updated = new HashMap<>(balances); updated.put(buyer, balance(buyer) - price);
        updated.put(row.seller(), balance(row.seller()) + price);
        save(updated, auctions.move(id, buyer));
    }
    public synchronized void cancelAuction(UUID seller, UUID id) throws Exception {
        var row = auctions.listings().get(id);
        if (row == null || !row.seller().equals(seller)) throw new IllegalArgumentException("stale");
        save(balances, auctions.move(id, seller));
    }
    public synchronized void expireAuctions(long now) throws Exception {
        var next = auctions.expire(now); if (next != auctions) save(balances, next);
    }
    public synchronized void completeAuctionClaims(UUID owner, Set<UUID> ids) throws Exception {
        save(balances, auctions.complete(owner, ids));
    }
    private void save(Map<UUID, Long> updated) throws Exception { save(updated, auctions); }
    private void save(Map<UUID, Long> updated, net.mandomc.gameplay.auction.AuctionBook market) throws Exception {
        var yaml = new YamlConfiguration(); yaml.set("schema-version", 1); yaml.createSection("balances");
        updated.forEach((id, cents) -> yaml.set("balances." + id, cents));
        market.write(yaml);
        net.mandomc.config.AtomicData.write(file, yaml.saveToString());
        balances = Map.copyOf(updated);
        auctions = market;
    }
}
