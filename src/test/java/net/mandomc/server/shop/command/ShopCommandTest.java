package net.mandomc.server.shop.command;

import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.server.shop.ShopManager;
import net.mandomc.server.shop.model.Shop;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShopCommandTest {

    @BeforeEach
    void setUpLang() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("shops.no-permission", "no-permission");
        yaml.set("shops.usage-shop", "usage-shop");
        yaml.set("shops.player-not-found", "player-not-found");
        yaml.set("shops.console-no-player-shop", "console-no-player-shop");
        yaml.set("shops.not-found", "not-found");
        setStaticField(LangManager.class, "lang", yaml);
    }

    @AfterEach
    void clearShops() {
        ShopManager.clear();
    }

    @Test
    void noArgsShowsShopUsage() {
        CapturingSender sender = new CapturingSender(true, Map.of());
        ShopCommand command = new ShopCommand(new GUIManager());

        command.onCommand(sender.asPlayer(), null, "shop", new String[0]);

        assertEquals(List.of("usage-shop"), sender.messages);
    }

    @Test
    void selfOpenRequiresUsePermission() {
        ShopManager.register("metals", new Shop("metals", "Metals", 27, null, Map.of()));
        CapturingSender sender = new CapturingSender(true, Map.of());
        ShopCommand command = new ShopCommand(new GUIManager());

        command.onCommand(sender.asPlayer(), null, "shop", new String[]{"metals"});

        assertEquals(List.of("no-permission"), sender.messages);
    }

    @Test
    void otherTargetRequiresOthersPermission() {
        ShopManager.register("metals", new Shop("metals", "Metals", 27, null, Map.of()));
        CapturingSender sender = new CapturingSender(true, Map.of("mandomc.shop.use", true));
        ShopCommand command = new ShopCommand(new GUIManager());

        command.onCommand(sender.asPlayer(), null, "shop", new String[]{"metals", "Bilal"});

        assertEquals(List.of("no-permission"), sender.messages);
    }

    @Test
    void tabCompleteSuggestsShopIdsWhenPermitted() {
        ShopManager.register("metals", new Shop("metals", "Metals", 27, null, Map.of()));
        ShopManager.register("vehicles", new Shop("vehicles", "Vehicles", 27, null, Map.of()));

        CapturingSender sender = new CapturingSender(true, Map.of("mandomc.shop.use", true));
        ShopCommand command = new ShopCommand(new GUIManager());

        List<String> result = command.onTabComplete(sender.asPlayer(), null, "shop", new String[]{"m"});

        assertEquals(List.of("metals"), result);
    }

    @Test
    void tabCompleteReturnsEmptyWithoutPermissions() {
        ShopManager.register("metals", new Shop("metals", "Metals", 27, null, Map.of()));

        CapturingSender sender = new CapturingSender(true, Map.of());
        ShopCommand command = new ShopCommand(new GUIManager());

        List<String> result = command.onTabComplete(sender.asPlayer(), null, "shop", new String[]{""});

        assertEquals(List.of(), result);
    }

    private void setStaticField(Class<?> target, String fieldName, Object value) throws Exception {
        Field field = target.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static final class CapturingSender {
        private final UUID uuid = UUID.randomUUID();
        private final boolean playerType;
        private final Map<String, Boolean> permissions = new HashMap<>();
        private final List<String> messages = new ArrayList<>();

        private CapturingSender(boolean playerType, Map<String, Boolean> permissions) {
            this.playerType = playerType;
            this.permissions.putAll(permissions);
        }

        private CommandSender asCommandSender() {
            return (CommandSender) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{CommandSender.class},
                    handler()
            );
        }

        private Player asPlayer() {
            if (!playerType) {
                throw new IllegalStateException("Not configured as player");
            }
            return (Player) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{Player.class},
                    handler()
            );
        }

        private InvocationHandler handler() {
            return (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("sendMessage") && args != null && args.length > 0 && args[0] instanceof String text) {
                    messages.add(text);
                    return null;
                }
                if (name.equals("hasPermission") && args != null && args.length == 1 && args[0] instanceof String permission) {
                    return permissions.getOrDefault(permission, false);
                }
                if (name.equals("getUniqueId")) {
                    return uuid;
                }
                return defaultValue(method.getReturnType());
            };
        }

        private Object defaultValue(Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == double.class) {
                return 0D;
            }
            if (type == float.class) {
                return 0F;
            }
            return null;
        }
    }
}
