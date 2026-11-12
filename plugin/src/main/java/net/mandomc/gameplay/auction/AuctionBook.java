package net.mandomc.gameplay.auction;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable escrow snapshots persisted atomically with the existing credit balances. No Bukkit item access on IO threads. */
public record AuctionBook(Map<UUID, Listing> listings, Map<UUID, Claim> claims, Set<UUID> accepted) {
    public record Listing(UUID id, UUID seller, String sellerName, String item, long price, long expires) { }
    public record Claim(UUID id, UUID owner, String item) { }
    public AuctionBook { listings = Map.copyOf(listings); claims = Map.copyOf(claims); accepted = Set.copyOf(accepted); }
    public static AuctionBook empty() { return new AuctionBook(Map.of(), Map.of(), Set.of()); }
    public static AuctionBook read(ConfigurationSection section) {
        if (section == null) return empty();
        if (section.getInt("schema-version") != 1) throw new IllegalArgumentException("auction-house: unsupported saved schema");
        var listings = new HashMap<UUID, Listing>(); var claims = new HashMap<UUID, Claim>(); var accepted = new HashSet<UUID>();
        for (String id : section.getStringList("accepted")) accepted.add(UUID.fromString(id));
        var saved = section.getConfigurationSection("listings");
        if (saved != null) for (String key : saved.getKeys(false)) {
            var row = Objects.requireNonNull(saved.getConfigurationSection(key)); UUID id = UUID.fromString(key);
            var item = item(row.getString("item")); long price = row.getLong("price"), expires = row.getLong("expires");
            if (price < 1 || price > net.mandomc.gameplay.credits.CreditLedger.MAX || expires < 1)
                throw new IllegalArgumentException("auction-house: invalid listing " + key);
            listings.put(id, new Listing(id, UUID.fromString(row.getString("seller")),
                    Objects.requireNonNull(row.getString("seller-name")), item, price, expires));
            accepted.add(id);
        }
        saved = section.getConfigurationSection("claims");
        if (saved != null) for (String key : saved.getKeys(false)) {
            var row = Objects.requireNonNull(saved.getConfigurationSection(key)); UUID id = UUID.fromString(key);
            claims.put(id, new Claim(id, UUID.fromString(row.getString("owner")), item(row.getString("item"))));
        }
        if (!Collections.disjoint(listings.keySet(), claims.keySet())) throw new IllegalArgumentException("auction-house: duplicate escrow ownership");
        return new AuctionBook(listings, claims, accepted);
    }
    private static String item(String value) {
        if (value == null || value.length() > 2_000_000 || Base64.getDecoder().decode(value).length == 0)
            throw new IllegalArgumentException("auction-house: invalid saved item");
        return value;
    }
    public void write(ConfigurationSection root) {
        var section = root.createSection("auction-house"); section.set("schema-version", 1);
        section.set("accepted", accepted.stream().map(UUID::toString).sorted().toList());
        var listingRows = section.createSection("listings");
        listings.values().stream().sorted(Comparator.comparing(row -> row.id().toString())).forEach(row -> {
            var saved = listingRows.createSection(row.id().toString()); saved.set("seller", row.seller().toString());
            saved.set("seller-name", row.sellerName()); saved.set("item", row.item());
            saved.set("price", row.price()); saved.set("expires", row.expires());
        });
        var rows = section.createSection("claims");
        for (var row : claims.values()) {
            var saved = rows.createSection(row.id().toString()); saved.set("owner", row.owner().toString()); saved.set("item", row.item());
        }
    }
    public AuctionBook expire(long now) {
        var next = new HashMap<>(listings); var returns = new HashMap<>(claims);
        for (var row : listings.values()) if (row.expires() <= now) {
            next.remove(row.id()); returns.put(row.id(), new Claim(row.id(), row.seller(), row.item()));
        }
        return next.size() == listings.size() ? this : new AuctionBook(next, returns, accepted);
    }
    public AuctionBook list(Listing row, int limit, long now) {
        if (accepted.contains(row.id())) throw new IllegalArgumentException("stale");
        if (listings.values().stream().filter(r -> r.seller().equals(row.seller()) && r.expires() > now).count() >= limit)
            throw new IllegalArgumentException("listing-limit");
        var next = new HashMap<>(listings); next.put(row.id(), row); var seen = new HashSet<>(accepted); seen.add(row.id());
        return new AuctionBook(next, claims, seen);
    }
    public AuctionBook move(UUID id, UUID owner) {
        var row = listings.get(id); if (row == null) throw new IllegalArgumentException("stale");
        var next = new HashMap<>(listings); next.remove(id); var returns = new HashMap<>(claims);
        returns.put(id, new Claim(id, owner, row.item())); return new AuctionBook(next, returns, accepted);
    }
    public AuctionBook complete(UUID owner, Set<UUID> ids) {
        var next = new HashMap<>(claims);
        for (UUID id : ids) { var row = next.get(id); if (row != null && row.owner().equals(owner)) next.remove(id); }
        return new AuctionBook(listings, next, accepted);
    }
}
