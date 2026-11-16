package net.mandomc.gameplay.trophies;

/** Physical footprint and rendering compensation for the imported building models. */
public record SignShape(int width, int height, float modelScale, float verticalOffset) {
    public static SignShape forItem(String id) {
        if (id == null) return null;
        return switch (id) {
            case "crates_sign", "shops_sign" -> new SignShape(3, 1, 1, 0);
            case "leaderboards_sign" -> new SignShape(5, 1, 2, 0);
            case "cantina_sign" -> new SignShape(1, 4, 2, 1);
            default -> null;
        };
    }
}
