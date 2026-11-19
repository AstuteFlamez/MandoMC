package net.mandomc.gameplay.items;

import java.text.Normalizer;
import java.util.Locale;

/** Names and IDs share case-insensitive, punctuation-insensitive partial-word matching. */
public final class RecipeSearch {
    private RecipeSearch() { }

    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    public static String nativeDisplayName(String id, String fallback) {
        return switch (id) {
            case "minecraft:redstone" -> "electrum dust";
            case "minecraft:redstone_torch" -> "electrum torch";
            case "minecraft:redstone_block" -> "electrum dust block";
            case "minecraft:redstone_lamp" -> "electrum lamp";
            case "minecraft:repeater" -> "electrum repeater";
            case "minecraft:comparator" -> "electrum comparator";
            case "minecraft:observer" -> "electrum observer";
            case "minecraft:powered_rail" -> "electrum powered rail";
            case "minecraft:detector_rail" -> "electrum detector rail";
            default -> fallback;
        };
    }

    public static boolean matches(String query, String id, String name) {
        String haystack = normalize(id + " " + nativeDisplayName(id, name));
        String normalized = normalize(query);
        return normalized.isBlank() || java.util.Arrays.stream(normalized.split(" +")).allMatch(haystack::contains);
    }
}
