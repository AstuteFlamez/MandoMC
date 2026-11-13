package net.mandomc.integrations.weapons;

import java.lang.reflect.*;
import java.util.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Paper 26.1.2 packet boundary. Only outgoing copies acquire a group; server inventory/cooldowns never change. */
public final class NativeReloadSweep {
    private final Class<?> stack, packet, slotPacket, contentPacket, inventoryPacket, cooldownPacket, context;
    private final Object useCooldownType, customDataType;
    private final Method asNative, copy, get, set, tag, compound, string;
    private final Method playerHandle, send;
    private final Field listener, connection, channel;
    private final Constructor<?> useCooldown, slotConstructor, contentConstructor, inventoryConstructor, cooldownConstructor;
    private final Method identifier;

    public NativeReloadSweep() throws ReflectiveOperationException {
        stack = type("net.minecraft.world.item.ItemStack");
        packet = type("net.minecraft.network.protocol.Packet");
        slotPacket = type("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket");
        contentPacket = type("net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket");
        inventoryPacket = type("net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket");
        cooldownPacket = type("net.minecraft.network.protocol.game.ClientboundCooldownPacket");
        context = type("io.netty.channel.ChannelHandlerContext");
        var component = type("net.minecraft.core.component.DataComponentType");
        var components = type("net.minecraft.core.component.DataComponents");
        useCooldownType = components.getField("USE_COOLDOWN").get(null);
        customDataType = components.getField("CUSTOM_DATA").get(null);
        asNative = type("org.bukkit.craftbukkit.inventory.CraftItemStack").getMethod("asNMSCopy", ItemStack.class);
        copy = stack.getMethod("copy"); get = stack.getMethod("get", component);
        set = stack.getMethod("set", component, Object.class);
        tag = type("net.minecraft.world.item.component.CustomData").getMethod("copyTag");
        var nbt = type("net.minecraft.nbt.CompoundTag");
        compound = nbt.getMethod("getCompoundOrEmpty", String.class);
        string = nbt.getMethod("getStringOr", String.class, String.class);
        playerHandle = type("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
        listener = type("net.minecraft.server.level.ServerPlayer").getField("connection");
        var common = type("net.minecraft.server.network.ServerCommonPacketListenerImpl");
        connection = common.getField("connection"); send = common.getMethod("send", packet);
        channel = type("net.minecraft.network.Connection").getField("channel");
        var id = type("net.minecraft.resources.Identifier"); identifier = id.getMethod("parse", String.class);
        useCooldown = type("net.minecraft.world.item.component.UseCooldown").getConstructor(float.class, Optional.class);
        slotConstructor = slotPacket.getConstructor(int.class, int.class, int.class, stack);
        contentConstructor = contentPacket.getConstructor(int.class, int.class, List.class, stack);
        inventoryConstructor = inventoryPacket.getConstructor(int.class, stack);
        cooldownConstructor = cooldownPacket.getConstructor(id, int.class);
    }
    private static Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name); }
    private static Object value(Object target, String method) throws ReflectiveOperationException {
        return target.getClass().getMethod(method).invoke(target);
    }

    public Session open(Player player, EquipmentSlot hand, String title, int ticks) throws ReflectiveOperationException {
        return new Session(player, hand == EquipmentSlot.HAND ? player.getInventory().getHeldItemSlot() : 40, title, ticks);
    }

    public final class Session implements AutoCloseable {
        private final Player player;
        private final int slot;
        private final String title, name;
        private final Object group, visualCooldown, networkListener, wire, pipeline, loop;
        private final Class<?> pipelineType, loopType;
        private final Map<Integer, Integer> layouts = new HashMap<>(); // Event-loop confined.
        private volatile boolean active = true;
        private volatile Throwable failure;
        private long clientEnd;

        private Session(Player player, int slot, String title, int ticks) throws ReflectiveOperationException {
            this.player = player; this.slot = slot; this.title = title;
            name = "mandomc_reload_" + UUID.randomUUID().toString().replace("-", "");
            group = identifier.invoke(null, "mandomc:reload/" + UUID.randomUUID());
            // A positive value is required by the codec. It is never applied to a server item.
            visualCooldown = useCooldown.newInstance(.05f, Optional.of(group));
            networkListener = listener.get(playerHandle.invoke(player));
            wire = Objects.requireNonNull(channel.get(connection.get(networkListener)), "Player has no native channel");
            pipelineType = type("io.netty.channel.ChannelPipeline"); loopType = type("io.netty.util.concurrent.EventExecutor");
            pipeline = type("io.netty.channel.Channel").getMethod("pipeline").invoke(wire);
            loop = type("io.netty.channel.Channel").getMethod("eventLoop").invoke(wire);
            var handlerType = type("io.netty.channel.ChannelOutboundHandler");
            Object handler = Proxy.newProxyInstance(handlerType.getClassLoader(), new Class<?>[]{handlerType}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "toString": return name;
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return proxy == args[0];
                    case "handlerAdded", "handlerRemoved": return null;
                    case "exceptionCaught":
                        return context.getMethod("fireExceptionCaught", Throwable.class).invoke(args[0], args[1]);
                    default:
                        var forwarded = Arrays.copyOfRange(args, 1, args.length);
                        if (method.getName().equals("write")) {
                            try { forwarded[0] = transform(forwarded[0]); }
                            catch (ReflectiveOperationException | RuntimeException error) { failure = error; } // Forward the real packet unchanged.
                        }
                        return context.getMethod(method.getName(), Arrays.copyOfRange(method.getParameterTypes(), 1,
                                method.getParameterCount())).invoke(args[0], forwarded);
                }
            });
            execute(() -> {
                try {
                    if (active) pipelineType.getMethod("addBefore", String.class, String.class, type("io.netty.channel.ChannelHandler"))
                            .invoke(pipeline, "packet_handler", name, handler);
                } catch (ReflectiveOperationException error) { failure = error; }
            });
            sendInventory(); sendCooldown(ticks);
        }

        private void execute(Runnable action) throws ReflectiveOperationException {
            if ((boolean) loopType.getMethod("inEventLoop").invoke(loop)) action.run();
            else loopType.getMethod("execute", Runnable.class).invoke(loop, action);
        }
        private boolean matches(Object item) throws ReflectiveOperationException {
            Object data = get.invoke(item, customDataType);
            if (data == null) return false;
            Object bukkit = compound.invoke(tag.invoke(data), "PublicBukkitValues");
            return title.equals(string.invoke(bukkit, "weaponmechanics:weapon-title", ""));
        }
        private Object decorate(Object item) throws ReflectiveOperationException {
            if (!active || !matches(item)) return item;
            Object image = copy.invoke(item);
            set.invoke(image, useCooldownType, visualCooldown);
            return image;
        }
        private int containerSlot(int window, int size) {
            if (window == 0) return slot == 40 ? 45 : slot < 9 ? 36 + slot : slot;
            if (slot == 40 || size < 36) return -1;
            return slot < 9 ? size - 9 + slot : size - 36 + slot - 9;
        }
        private Object transform(Object message) throws ReflectiveOperationException {
            if (!active || failure != null) return message;
            if (inventoryPacket.isInstance(message) && (int) value(message, "slot") == slot) {
                return inventoryConstructor.newInstance(slot, decorate(value(message, "contents")));
            }
            if (contentPacket.isInstance(message)) {
                int window = (int) value(message, "containerId");
                var items = (List<?>) value(message, "items"); layouts.put(window, items.size());
                int index = containerSlot(window, items.size());
                if (index < 0 || index >= items.size()) return message;
                var images = new ArrayList<Object>(items);
                images.set(index, decorate(images.get(index)));
                return contentConstructor.newInstance(window, value(message, "stateId"), images, value(message, "carriedItem"));
            }
            if (slotPacket.isInstance(message)) {
                int window = (int) value(message, "getContainerId"), index = (int) value(message, "getSlot");
                int target = window == -2 ? slot : containerSlot(window, layouts.getOrDefault(window, window == 0 ? 46 : 0));
                if (target >= 0 && index == target) return slotConstructor.newInstance(window, value(message, "getStateId"),
                        index, decorate(value(message, "getItem")));
            }
            return message;
        }
        private void sendInventory() throws ReflectiveOperationException {
            ItemStack current = player.getInventory().getItem(slot);
            if (current == null) current = new ItemStack(org.bukkit.Material.AIR);
            send.invoke(networkListener, inventoryConstructor.newInstance(slot, asNative.invoke(null, current)));
        }
        private void sendCooldown(int ticks) throws ReflectiveOperationException {
            send.invoke(networkListener, cooldownConstructor.newInstance(group, ticks));
            clientEnd = System.nanoTime() + ticks * 50_000_000L;
        }
        /** Re-arm only after client expiration during server lag; do not restart a normally progressing sweep. */
        public void maintain(int ticks) throws ReflectiveOperationException {
            if (failure != null) throw new IllegalStateException("Reload packet view failed", failure);
            if (active && ticks > 0 && System.nanoTime() >= clientEnd) sendCooldown(ticks);
        }
        @Override public void close() {
            if (!active) return;
            active = false;
            try {
                sendCooldown(0); sendInventory();
                execute(() -> {
                    try {
                        if (pipelineType.getMethod("get", String.class).invoke(pipeline, name) != null)
                            pipelineType.getMethod("remove", String.class).invoke(pipeline, name);
                    } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot remove reload packet view", error); }
                });
            } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot restore reload packet view", error); }
        }
    }
}
