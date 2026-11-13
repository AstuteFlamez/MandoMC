package net.mandomc.gameplay.messaging;

import net.kyori.adventure.text.format.NamedTextColor;

public enum ChatCategory {
    COMBAT(NamedTextColor.DARK_RED), ADMIN(NamedTextColor.RED), EVENT(NamedTextColor.YELLOW),
    SUCCESS(NamedTextColor.GREEN), INFO(NamedTextColor.AQUA), ECONOMY(NamedTextColor.GOLD),
    SOCIAL(NamedTextColor.LIGHT_PURPLE), STATUS(NamedTextColor.GRAY), ERROR(NamedTextColor.RED);

    private final NamedTextColor color;
    ChatCategory(NamedTextColor color) { this.color = color; }
    public NamedTextColor color() { return color; }
}
