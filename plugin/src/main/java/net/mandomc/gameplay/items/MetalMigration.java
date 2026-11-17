package net.mandomc.gameplay.items;

import java.util.Map;

/** Stable aliases also recover old queued rewards without creating obsolete sheet items. */
public final class MetalMigration {
    public static final Map<String, String> SHEETS = Map.ofEntries(
            Map.entry("agrinium_sheet", "agrinium"), Map.entry("aurodium_sheet", "aurodium"),
            Map.entry("beskar_sheet", "beskar"), Map.entry("cortosis_sheet", "cortosis"),
            Map.entry("durasteel_sheet", "durasteel"), Map.entry("electrum_sheet", "electrum"),
            Map.entry("lommite_sheet", "lommite"), Map.entry("mandalorian_sheet", "mandalorian_iron"),
            Map.entry("neuranium_sheet", "neuranium"), Map.entry("phrik_sheet", "phrik"),
            Map.entry("songsteel_sheet", "songsteel"));
    private MetalMigration() { }
    public static boolean isSheet(String id) { return id != null && SHEETS.containsKey(id); }
    public static String ingotId(String id) { return id == null ? null : SHEETS.getOrDefault(id, id); }
}
