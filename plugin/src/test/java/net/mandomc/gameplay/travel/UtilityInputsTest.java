package net.mandomc.gameplay.travel;

import net.mandomc.commands.AdminCommands;
import org.bukkit.GameMode;
import org.junit.Test;
import static org.junit.Assert.*;

public class UtilityInputsTest {
    @Test public void speedsKeepNormalDefaultsAndBounds() {
        assertEquals(.1f, AdminCommands.speed("1", true), .00001f);
        assertEquals(.2f, AdminCommands.speed("1", false), .00001f);
        assertEquals(0f, AdminCommands.speed("0", false), .00001f);
        assertEquals(1f, AdminCommands.speed("10", true), .00001f);
        assertEquals(1f, AdminCommands.speed("10", false), .00001f);
        for (String value : new String[] {"NaN", "Infinity", "-1", "11", "oops"}) {
            assertThrows(IllegalArgumentException.class, () -> AdminCommands.speed(value, true));
        }
    }
    @Test public void coordinatesSupportRelativeAndRejectNonfinite() {
        assertEquals(12.5, AdminCommands.coordinate("~", 12.5), 0);
        assertEquals(10, AdminCommands.coordinate("~-2.5", 12.5), 0);
        assertEquals(-3, AdminCommands.coordinate("-3", 12.5), 0);
        for (String value : new String[] {"NaN", "Infinity", "~NaN", "30000000", "^2"}) {
            assertThrows(IllegalArgumentException.class, () -> AdminCommands.coordinate(value, 0));
        }
    }
    @Test public void allGameModesResolveWithoutAmbiguousSpectator() {
        assertEquals(GameMode.SURVIVAL, AdminCommands.mode("s"));
        assertEquals(GameMode.CREATIVE, AdminCommands.mode("1"));
        assertEquals(GameMode.ADVENTURE, AdminCommands.mode("ADVENTURE"));
        assertEquals(GameMode.SPECTATOR, AdminCommands.mode("sp"));
        assertThrows(IllegalArgumentException.class, () -> AdminCommands.mode("4"));
    }
    @Test public void savedLocationRoundTripsFullPrecision() {
        var saved = new SavedLocation("minecraft:tatooine", 1711.845046707056, 100, 2201.7401780545574,
                81.4468f, -11.09995f);
        assertEquals(saved, SavedLocation.decode(saved.encode()));
        assertThrows(IllegalArgumentException.class, () -> SavedLocation.decode("minecraft:overworld;NaN;1;2;0;0"));
        assertThrows(IllegalArgumentException.class, () -> SavedLocation.decode("bad data"));
        assertThrows(IllegalArgumentException.class, () -> new SavedLocation("minecraft:overworld", 1, 1, 1, 0, 91));
    }
    @Test public void homeNamesCannotEscapeTheirNamespaceOrCreateNestedPaths() {
        assertEquals("my-home_2", PersonalTravel.homeName("My-Home_2"));
        for (String value : new String[] {"", "../home", "a.b", "namespace:home", "a/b", "a b", "a".repeat(25)}) {
            assertThrows(IllegalArgumentException.class, () -> PersonalTravel.homeName(value));
        }
    }
}
