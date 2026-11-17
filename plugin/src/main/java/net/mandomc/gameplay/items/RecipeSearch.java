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

    public static boolean matches(String query, String id, String name) {
        String haystack = normalize(id + " " + name);
        String normalized = normalize(query);
        return normalized.isBlank() || java.util.Arrays.stream(normalized.split(" +")).allMatch(haystack::contains);
    }
}
