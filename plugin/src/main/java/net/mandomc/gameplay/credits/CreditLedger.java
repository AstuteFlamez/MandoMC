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
    }
    public static long amount(String input) {
        if (!input.matches("[0-9]{1,14}(?:\\.[0-9]{1,2})?")) throw new IllegalArgumentException("amount");
        try { long cents = new BigDecimal(input).movePointRight(2).longValueExact(); validate(cents); return cents; }
        catch (ArithmeticException error) { throw new IllegalArgumentException("amount"); }
    }
    private static void validate(long cents) { if (cents < 0 || cents > MAX) throw new IllegalArgumentException("amount"); }
    public static String format(long cents) { return String.format(Locale.US, "%,d.%02d", cents / 100, cents % 100); }
    public long balance(UUID id) { return balances.getOrDefault(id, 0L); }
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
    private void save(Map<UUID, Long> updated) throws Exception {
        var yaml = new YamlConfiguration(); yaml.set("schema-version", 1); yaml.createSection("balances");
        updated.forEach((id, cents) -> yaml.set("balances." + id, cents));
        net.mandomc.config.AtomicData.write(file, yaml.saveToString());
        balances = Map.copyOf(updated);
    }
}
