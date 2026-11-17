package net.mandomc.gameplay.guide;

import java.util.*;
import net.mandomc.gameplay.items.RecipeSearch;

/** Shared player vocabulary for material names, mob names and configuration search. */
final class GuideTerms {
    private GuideTerms() { }

    static String readable(String text) {
        return text.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ')
                .replace("minecraft:", "");
    }

    static Set<String> tokens(String text) {
        var result = new LinkedHashSet<String>();
        for (String word : RecipeSearch.normalize(readable(text)).split(" +")) {
            word = switch (word) {
                case "hp", "hearts" -> "health";
                case "spawning", "spawns" -> "spawn";
                case "stats", "statistics" -> "stat";
                case "dmg" -> "damage";
                case "speederbike" -> "speeder";
                case "starship", "ship", "aircraft" -> "vehicle";
                case "money", "credits" -> "credit";
                case "massif" -> "massiff";
                case "durastel" -> "durasteel";
                case "die", "dying", "died" -> "death";
                default -> word.length() > 3 && word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
            };
            if (!word.isBlank()) result.add(word);
        }
        return result;
    }

    static boolean matches(String word, Set<String> query) {
        return query.contains(word) || word.length() >= 5 && query.stream()
                .anyMatch(q -> q.length() >= 5 && distanceOne(word, q));
    }

    private static boolean distanceOne(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) return false;
        int i = 0, j = 0, edits = 0;
        while (i < a.length() && j < b.length()) {
            if (a.charAt(i) == b.charAt(j)) { i++; j++; }
            else {
                if (++edits > 1) return false;
                if (a.length() >= b.length()) i++;
                if (b.length() >= a.length()) j++;
            }
        }
        return edits + (a.length() - i) + (b.length() - j) <= 1;
    }
}
