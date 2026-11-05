package net.mandomc.core.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.mandomc.core.LangManager;
import net.mandomc.core.services.EconomyService;
import net.mandomc.server.discord.service.LinkService;

class LinkCommandTest {

    @BeforeEach
    void setUpLang() throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("link.players-only", "players-only");
        yaml.set("link.error", "link-error");
        yaml.set("link.already-linked", "already-linked");
        setStaticField(LangManager.class, "lang", yaml);
    }

    @Test
    void nonPlayerSenderGetsPlayersOnlyMessage() {
        CapturingSender sender = new CapturingSender(false);
        LinkCommand command = new LinkCommand(null, unavailableLinkService(), noOpEconomy());

        command.onCommand(sender.asCommandSender(), null, "link", new String[0]);

        assertEquals(List.of("players-only"), sender.messages);
    }

    @Test
    void unavailableLinkServiceReturnsErrorToPlayer() {
        CapturingSender sender = new CapturingSender(true);
        LinkCommand command = new LinkCommand(null, unavailableLinkService(), noOpEconomy());

        command.onCommand(sender.asPlayer(), null, "link", new String[0]);

        assertEquals(List.of("link-error"), sender.messages);
    }

    @Test
    void alreadyLinkedPlayerGetsAlreadyLinkedMessage() {
        CapturingSender sender = new CapturingSender(true);
        LinkCommand command = new LinkCommand(null, alreadyLinkedService(), noOpEconomy());

        command.onCommand(sender.asPlayer(), null, "link", new String[0]);

        assertEquals(List.of("already-linked"), sender.messages);
    }

    private LinkService unavailableLinkService() {
        return new LinkService() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public boolean isAlreadyLinked(UUID playerUuid) {
                return false;
            }

            @Override
            public String createPendingLink(UUID playerUuid) {
                return "ABC123";
            }

            @Override
            public boolean isLinked(UUID playerUuid) {
                return false;
            }
        };
    }

    private LinkService alreadyLinkedService() {
        return new LinkService() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public boolean isAlreadyLinked(UUID playerUuid) {
                return true;
            }

            @Override
            public String createPendingLink(UUID playerUuid) {
                return "ABC123";
            }

            @Override
            public boolean isLinked(UUID playerUuid) {
                return false;
            }
        };
    }

    private EconomyService noOpEconomy() {
        return (player, amount) -> true;
    }

    private void setStaticField(Class<?> target, String fieldName, Object value) throws Exception {
        Field field = target.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static final class CapturingSender {
        private final UUID uuid = UUID.randomUUID();
        private final boolean playerType;
        private final List<String> messages = new ArrayList<>();

        private CapturingSender(boolean playerType) {
            this.playerType = playerType;
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
                if (name.equals("getUniqueId")) {
                    return uuid;
                }
                if (name.equals("hasPermission")) {
                    return true;
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
