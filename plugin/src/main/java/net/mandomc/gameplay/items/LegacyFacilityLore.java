package net.mandomc.gameplay.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Removes only the retired facility attribution, preserving all other lore components. */
public final class LegacyFacilityLore {
    private static final Set<String> IDS = Set.of("basic_power_cell", "durable_mid_grade_power_core", "batteries", "switch");
    private static final Pattern STATEMENT = Pattern.compile(
            "extracted\\s+from\\s+(?:(?:a|the)\\s+)?secure(?:d)?\\s+facility\\s+on\\s+tatooine[.!]?",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private LegacyFacilityLore() { }

    public static boolean affected(String id) { return id != null && IDS.contains(id); }

    public static List<Component> clean(List<Component> lore) {
        if (lore == null || lore.isEmpty()) return lore;
        ArrayList<Component> result = null;
        for (int start = 0; start < lore.size();) {
            var text = new StringBuilder();
            int matchEnd = -1;
            for (int end = start; end < Math.min(start + 3, lore.size()); end++) {
                if (end > start) text.append(' ');
                text.append(PLAIN.serialize(lore.get(end)).strip());
                if (STATEMENT.matcher(text.toString().strip()).matches()) { matchEnd = end; break; }
            }
            if (matchEnd < 0) {
                if (result != null) result.add(lore.get(start));
                start++;
                continue;
            }
            if (result == null) result = new ArrayList<>(lore.subList(0, start));
            // Retain a separator before custom following lore, but remove a now-trailing/doubled blank.
            if (!result.isEmpty() && blank(result.getLast())
                    && (matchEnd + 1 == lore.size() || blank(lore.get(matchEnd + 1)))) result.removeLast();
            start = matchEnd + 1;
        }
        return result == null ? lore : List.copyOf(result);
    }

    private static boolean blank(Component line) { return PLAIN.serialize(line).isBlank(); }
}
