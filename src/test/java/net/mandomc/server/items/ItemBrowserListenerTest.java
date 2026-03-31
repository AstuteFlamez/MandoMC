package net.mandomc.server.items;

import net.mandomc.server.items.listener.ItemBrowserListener;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ItemBrowserListenerTest {

    @Test
    void handleNavigationReturnsFalseForNullDisplayName() throws Exception {
        ItemBrowserListener listener = new ItemBrowserListener();
        Method method = ItemBrowserListener.class.getDeclaredMethod(
                "handleNavigation",
                org.bukkit.entity.Player.class,
                org.bukkit.entity.Player.class,
                String.class,
                int.class,
                String.class
        );
        method.setAccessible(true);

        boolean handled = (boolean) method.invoke(listener, null, null, null, 0, null);
        assertFalse(handled);
    }
}
