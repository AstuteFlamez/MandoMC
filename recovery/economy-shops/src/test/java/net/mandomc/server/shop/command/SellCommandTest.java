package net.mandomc.server.shop.command;

import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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

class SellCommandTest {

    @BeforeEach
    void setUpLang() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("shops.no-permission", "no-permission");
        yaml.set("shops.players-only", "players-only");
        yaml.set("shops.console-no-player-sell", "console-no-player-sell");
        yaml.set("shops.usage-sell", "usage-sell");
        yaml.set("shops.player-not-found", "player-not-found");
        setStaticField(LangManager.class, "lang", yaml);
    }

    @Test
    void noArgsRequiresAdvancedPermission() {
        CapturingSender sender = new CapturingSender(true, Map.of());
        SellCommand command = new SellCommand(new GUIManager());

        command.onCommand(sender.asPlayer(), null, "sell", new String[0]);

        assertEquals(List.of("no-permission"), sender.messages);
    }

    @Test
    void noArgsConsoleRequiresExplicitPlayer() {
        CapturingSender sender = new CapturingSender(false, Map.of());
        SellCommand command = new SellCommand(new GUIManager());

        command.onCommand(sender.asCommandSender(), null, "sell", new String[0]);

        assertEquals(List.of("console-no-player-sell"), sender.messages);
    }

    @Test
    void handQuickSellIsPlayersOnly() {
        CapturingSender sender = new CapturingSender(false, Map.of("mandomc.sell.advanced", true));
        SellCommand command = new SellCommand(new GUIManager());

        command.onCommand(sender.asCommandSender(), null, "sell", new String[]{"hand"});

        assertEquals(List.of("players-only"), sender.messages);
    }

    @Test
    void targetPlayerRequiresUsePermission() {
        CapturingSender sender = new CapturingSender(false, Map.of());
        SellCommand command = new SellCommand(new GUIManager());

        command.onCommand(sender.asCommandSender(), null, "sell", new String[]{"Someone"});

        assertEquals(List.of("no-permission"), sender.messages);
    }

    @Test
    void tabCompleteShowsAdvancedOptionsWithPermission() {
        CapturingSender sender = new CapturingSender(true, Map.of("mandomc.sell.advanced", true));
        SellCommand command = new SellCommand(new GUIManager());

        List<String> result = command.onTabComplete(sender.asPlayer(), null, "sell", new String[]{""});

        assertEquals(List.of("hand", "all"), result);
    }

    @Test
    void extraArgsShowUsage() {
        CapturingSender sender = new CapturingSender(true, Map.of());
        SellCommand command = new SellCommand(new GUIManager());

        command.onCommand(sender.asPlayer(), null, "sell", new String[]{"hand", "extra"});

        assertEquals(List.of("usage-sell"), sender.messages);
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
