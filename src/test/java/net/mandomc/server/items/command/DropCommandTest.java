package net.mandomc.server.items.command;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DropCommandTest {

    @AfterEach
    void tearDown() {
        DropCommand.resetTestWeaponMechanicsResolvers();
    }

    @Test
    void weaponMechanicsLookupOrderKeepsRawThenLowercaseFallback() {
        assertEquals(
                List.of("Proton_Torpedo", "proton_torpedo"),
                DropCommand.weaponMechanicsLookupOrder("Proton_Torpedo")
        );
    }

    @Test
    void resolveItemAttemptsRawThenLowercaseWhenWmReturnsNull() {
        DropCommand command = new DropCommand();
        List<String> attemptedIds = new ArrayList<>();
        String rawId = "Proton_Torpedo";

        DropCommand.useTestWeaponMechanicsResolvers(
                () -> true,
                id -> {
                    attemptedIds.add(id);
                    return null;
                }
        );

        ItemStack result = command.resolveItem(rawId, 4);
        assertNull(result);
        assertEquals(List.of(rawId, "proton_torpedo"), attemptedIds);
    }
}
