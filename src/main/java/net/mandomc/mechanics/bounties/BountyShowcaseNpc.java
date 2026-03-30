package net.mandomc.mechanics.bounties;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.mandomc.MandoMC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Packet-backed fake player NPC used by the bounty showcase.
 * Supports both Paper (mojang-mapped) and Spigot (obfuscated) runtimes.
 */
public final class BountyShowcaseNpc {

    // Stable names — identical on both Paper and Spigot
    private static final String ENTITY_CLASS              = "net.minecraft.world.entity.Entity";
    private static final String CLIENT_INFO_CLASS         = "net.minecraft.server.level.ClientInformation";
    private static final String GAME_PROFILE_CLASS        = "com.mojang.authlib.GameProfile";
    private static final String PACKET_CLASS              = "net.minecraft.network.protocol.Packet";
    private static final String PLAYER_INFO_UPDATE_CLASS  = "net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket";
    private static final String PLAYER_INFO_REMOVE_CLASS  = "net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket";

    // Mojang name first (Paper), Spigot obfuscated name second
    private static final String[] ENTITY_PLAYER_NAMES = {
        "net.minecraft.server.level.ServerPlayer",                           // Paper
        "net.minecraft.server.level.EntityPlayer"                            // Spigot
    };
    private static final String[] SPAWN_ENTITY_NAMES = {
        "net.minecraft.network.protocol.game.ClientboundAddEntityPacket",    // Paper
        "net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity"       // Spigot
    };
    private static final String[] DESTROY_ENTITY_NAMES = {
        "net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket", // Paper
        "net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy"       // Spigot
    };
    private static final String[] HEAD_ROTATION_NAMES = {
        "net.minecraft.network.protocol.game.ClientboundRotateHeadPacket",      // Paper
        "net.minecraft.network.protocol.game.PacketPlayOutEntityHeadRotation"   // Spigot
    };
    private static final String[] ENTITY_TYPE_NAMES = {
        "net.minecraft.world.entity.EntityType",    // Paper
        "net.minecraft.world.entity.EntityTypes"    // Spigot
    };
    private static final String[] VEC3_NAMES = {
        "net.minecraft.world.phys.Vec3",    // Paper
        "net.minecraft.world.phys.Vec3D"    // Spigot
    };

    private final UUID targetId;
    private final UUID npcUuid;
    private final String targetName;
    private final Location location;
    private final float yaw;
    private final float pitch;
    private final Object handle;

    private BountyShowcaseNpc(UUID targetId,
                              UUID npcUuid,
                              String targetName,
                              Location location,
                              float yaw,
                              float pitch,
                              Object handle) {
        this.targetId = targetId;
        this.npcUuid = npcUuid;
        this.targetName = targetName;
        this.location = location.clone();
        this.yaw = yaw;
        this.pitch = pitch;
        this.handle = handle;
    }

    public static BountyShowcaseNpc create(OfflinePlayer target, Location location, float yaw, float pitch) {
        try {
            UUID npcUuid = UUID.randomUUID();
            Object gameProfile = createGameProfile(target, npcUuid);
            Object entityPlayer = createEntityPlayer(gameProfile, location, yaw, pitch);
            return new BountyShowcaseNpc(
                    target.getUniqueId(),
                    npcUuid,
                    Optional.ofNullable(target.getName()).orElse("Unknown"),
                    location,
                    yaw,
                    pitch,
                    entityPlayer
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to create showcase NPC", exception);
        }
    }

    public UUID getTargetId() {
        return targetId;
    }

    public void spawnTo(Player viewer) {
        if (!shouldShowTo(viewer)) {
            return;
        }

        try {
            sendPacket(viewer, createPlayerInfoAddPacket());
            sendPacket(viewer, createSpawnPacket());
            sendPacket(viewer, createHeadRotationPacket());

            Bukkit.getScheduler().runTaskLater(MandoMC.getInstance(), () -> {
                if (viewer.isOnline()) {
                    try {
                        hideFromTabList(viewer);
                    } catch (Exception exception) {
                        MandoMC.getInstance().getLogger().warning("Failed to remove showcase NPC from tab list for viewer " + viewer.getName() + ": " + exception.getMessage());
                    }
                }
            }, 20L);
        } catch (Exception exception) {
            MandoMC.getInstance().getLogger().warning("Failed to spawn showcase NPC for viewer " + viewer.getName() + ": " + exception.getMessage());
        }
    }

    public void spawnToAll() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            spawnTo(viewer);
        }
    }

    public void destroy() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            destroyFor(viewer);
        }
    }

    public void destroyFor(Player viewer) {
        try {
            sendPacket(viewer, createDestroyPacket());
            hideFromTabList(viewer);
        } catch (Exception exception) {
            MandoMC.getInstance().getLogger().warning("Failed to destroy showcase NPC for viewer " + viewer.getName() + ": " + exception.getMessage());
        }
    }

    public boolean matches(UUID nextTargetId, Location nextLocation, float nextYaw, float nextPitch) {
        if (!Objects.equals(targetId, nextTargetId)) {
            return false;
        }
        if (!Objects.equals(location.getWorld(), nextLocation.getWorld())) {
            return false;
        }
        return Math.abs(location.getX() - nextLocation.getX()) < 0.01
                && Math.abs(location.getY() - nextLocation.getY()) < 0.01
                && Math.abs(location.getZ() - nextLocation.getZ()) < 0.01
                && Math.abs(yaw - nextYaw) < 0.01f
                && Math.abs(pitch - nextPitch) < 0.01f;
    }

    public boolean shouldShowTo(Player viewer) {
        return viewer.getWorld().equals(location.getWorld());
    }

    private static Object createGameProfile(OfflinePlayer target, UUID npcUuid) throws Exception {
        // Build NPC profile with the NPC's UUID and target's display name via Paper API
        PlayerProfile npcProfile = Bukkit.getServer().createProfile(npcUuid, sanitizeName(target.getName()));

        // Fetch target skin; join() to ensure textures are present before we read them
        PlayerProfile targetProfile = Bukkit.getServer().createProfile(target.getUniqueId(), target.getName());
        try {
            targetProfile = targetProfile.update().join();
        } catch (Exception ignored) {
        }

        // Copy the textures property via Paper's own API — no direct GameProfile reflection needed
        for (ProfileProperty property : targetProfile.getProperties()) {
            if ("textures".equals(property.getName())) {
                npcProfile.setProperty(new ProfileProperty(property.getName(), property.getValue(), property.getSignature()));
            }
        }

        // Resolve buildGameProfile() by return type to bypass Paper's name-based reflection proxy,
        // which blocks getMethod("getProperties") on authlib's GameProfile class.
        Method buildMethod = Arrays.stream(npcProfile.getClass().getDeclaredMethods())
                .filter(m -> m.getParameterCount() == 0
                        && m.getReturnType().getName().equals(GAME_PROFILE_CLASS))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find buildGameProfile on CraftPlayerProfile"));
        buildMethod.setAccessible(true);
        return buildMethod.invoke(npcProfile);
    }

    private static Object createEntityPlayer(Object gameProfile, Location location, float yaw, float pitch) throws Exception {
        Object craftServer = Bukkit.getServer();
        Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);

        String craftPackage = craftServer.getClass().getPackage().getName();
        Class<?> craftWorldClass = Class.forName(craftPackage + ".CraftWorld");
        Object worldServer = craftWorldClass.getMethod("getHandle").invoke(location.getWorld());

        Class<?> clientInformationClass = Class.forName(CLIENT_INFO_CLASS);
        // Try mojang name 'createDefault' first (Paper), then obfuscated 'a' (Spigot)
        Object clientInformation = invokeStaticFactory(clientInformationClass, "createDefault", "a");

        Class<?> entityPlayerClass = classForAnyName(ENTITY_PLAYER_NAMES);

        // Use constructor scanning rather than getConstructor() to avoid subclass type mismatch
        // (e.g. DedicatedServer passed where MinecraftServer is declared)
        Constructor<?> ctor = Arrays.stream(entityPlayerClass.getDeclaredConstructors())
                .filter(c -> c.getParameterCount() == 4)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find ServerPlayer 4-arg constructor"));
        ctor.setAccessible(true);
        Object entityPlayer = ctor.newInstance(minecraftServer, worldServer, gameProfile, clientInformation);

        // Scan hierarchy by params — bypasses Paper's name-remapping proxy
        Method forcePosition = scanMethod(entityPlayerClass,
            new String[]{"moveTo", "forceSetPositionRotation"},
            double.class, double.class, double.class, float.class, float.class);
        forcePosition.invoke(entityPlayer, location.getX(), location.getY(), location.getZ(), yaw, pitch);
        return entityPlayer;
    }

    private Object createPlayerInfoAddPacket() throws Exception {
        Class<?> packetClass = Class.forName(PLAYER_INFO_UPDATE_CLASS);
        // Scan getDeclaredMethods for the static Collection-taking factory — bypasses Paper's proxy
        Method factory = Arrays.stream(packetClass.getDeclaredMethods())
            .filter(m -> java.lang.reflect.Modifier.isStatic(m.getModifiers())
                && m.getParameterCount() == 1
                && m.getParameterTypes()[0].isAssignableFrom(Collection.class))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Cannot find PlayerInfoUpdate static factory"));
        factory.setAccessible(true);
        return factory.invoke(null, List.of(handle));
    }

    private Object createSpawnPacket() throws Exception {
        Class<?> entityClass = Class.forName(ENTITY_CLASS);

        // Scan hierarchy by return type — bypasses Paper's proxy
        int entityId = (int) scanMethodByReturn(entityClass, int.class, new String[]{"getId", "aA"}).invoke(handle);
        UUID entityUuid = (UUID) scanMethodByReturn(entityClass, UUID.class, new String[]{"getUUID", "cY"}).invoke(handle);

        Class<?> entityTypeClass = classForAnyName(ENTITY_TYPE_NAMES);
        Method entityTypeMethod = Arrays.stream(entityClass.getMethods())
                .filter(m -> m.getParameterCount() == 0 && entityTypeClass.isAssignableFrom(m.getReturnType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not resolve entity type accessor"));
        Object entityType = entityTypeMethod.invoke(handle);

        Class<?> vec3Class = classForAnyName(VEC3_NAMES);
        Object zeroVector = vec3Class.getConstructor(double.class, double.class, double.class)
                .newInstance(0D, 0D, 0D);

        Class<?> spawnPacketClass = classForAnyName(SPAWN_ENTITY_NAMES);
        return spawnPacketClass.getConstructor(
                        int.class,
                        UUID.class,
                        double.class, double.class, double.class,
                        float.class, float.class,
                        entityTypeClass,
                        int.class,
                        vec3Class,
                        double.class)
                .newInstance(
                        entityId,
                        entityUuid,
                        location.getX(), location.getY(), location.getZ(),
                        pitch, yaw,
                        entityType,
                        0,
                        zeroVector,
                        (double) yaw
                );
    }

    private Object createHeadRotationPacket() throws Exception {
        Class<?> entityClass = Class.forName(ENTITY_CLASS);
        Class<?> headPacketClass = classForAnyName(HEAD_ROTATION_NAMES);
        return headPacketClass.getConstructor(entityClass, byte.class)
                .newInstance(handle, toAngle(yaw));
    }

    private Object createDestroyPacket() throws Exception {
        Class<?> entityClass = Class.forName(ENTITY_CLASS);
        int entityId = (int) scanMethodByReturn(entityClass, int.class, new String[]{"getId", "aA"}).invoke(handle);
        Class<?> destroyPacketClass = classForAnyName(DESTROY_ENTITY_NAMES);
        return destroyPacketClass.getConstructor(int[].class).newInstance((Object) new int[]{entityId});
    }

    private void hideFromTabList(Player viewer) throws Exception {
        Class<?> removePacketClass = Class.forName(PLAYER_INFO_REMOVE_CLASS);
        Object packet = removePacketClass.getConstructor(List.class).newInstance(List.of(npcUuid));
        sendPacket(viewer, packet);
    }

    private static void sendPacket(Player viewer, Object packet) throws Exception {
        Object handle = viewer.getClass().getMethod("getHandle").invoke(viewer);

        // Try Paper field name 'connection' first, then Spigot 'g'
        Field connectionField = findField(handle.getClass(), "connection", "g");
        Object connection = connectionField.get(handle);

        // Scan for send(Packet) by parameter type rather than by name
        Class<?> packetClass = Class.forName(PACKET_CLASS);
        Method sendMethod = Arrays.stream(connection.getClass().getMethods())
                .filter(m -> m.getParameterCount() == 1
                        && packetClass.isAssignableFrom(m.getParameterTypes()[0]))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not resolve packet send method on " + connection.getClass().getName()));

        sendMethod.invoke(connection, packet);
    }

    // --- reflection helpers ---

    /** Try each class name in order; return the first that resolves. */
    private static Class<?> classForAnyName(String[] names) throws ClassNotFoundException {
        ClassNotFoundException last = null;
        for (String name : names) {
            try {
                return Class.forName(name);
            } catch (ClassNotFoundException e) {
                last = e;
            }
        }
        throw last;
    }

    /**
     * Scan the class hierarchy via getDeclaredMethods(), bypassing Paper's name-remapping proxy.
     * Matches by exact parameter types; prefers a method whose name is in preferredNames,
     * falls back to the first signature match.
     */
    private static Method scanMethod(Class<?> clazz, String[] preferredNames, Class<?>... params) {
        List<Method> candidates = new ArrayList<>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (Arrays.equals(m.getParameterTypes(), params)) {
                    candidates.add(m);
                }
            }
        }
        if (preferredNames != null) {
            for (String name : preferredNames) {
                for (Method m : candidates) {
                    if (name.equals(m.getName())) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
        }
        if (!candidates.isEmpty()) {
            Method m = candidates.get(0);
            m.setAccessible(true);
            return m;
        }
        throw new IllegalStateException("Method not found (params=" + Arrays.toString(params) + ") on " + clazz.getName());
    }

    /**
     * Scan the class hierarchy for a no-arg method with the given return type.
     * Prefers methods whose name is in preferredNames; falls back to first match.
     */
    private static Method scanMethodByReturn(Class<?> clazz, Class<?> returnType, String[] preferredNames) {
        List<Method> candidates = new ArrayList<>();
        for (Class<?> c = clazz; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType() == returnType) {
                    candidates.add(m);
                }
            }
        }
        if (preferredNames != null) {
            for (String name : preferredNames) {
                for (Method m : candidates) {
                    if (name.equals(m.getName())) {
                        m.setAccessible(true);
                        return m;
                    }
                }
            }
        }
        if (!candidates.isEmpty()) {
            Method m = candidates.get(0);
            m.setAccessible(true);
            return m;
        }
        throw new IllegalStateException("No no-arg method returning " + returnType.getName() + " found in hierarchy of " + clazz.getName());
    }

    /** Invoke a static no-arg factory; scans getDeclaredMethods() to bypass Paper's proxy. */
    private static Object invokeStaticFactory(Class<?> clazz, String... names) throws Exception {
        for (String name : names) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (name.equals(m.getName())
                        && m.getParameterCount() == 0
                        && java.lang.reflect.Modifier.isStatic(m.getModifiers())) {
                    m.setAccessible(true);
                    return m.invoke(null);
                }
            }
        }
        // Last resort: any static no-arg method returning the same class
        for (Method m : clazz.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isStatic(m.getModifiers())
                    && m.getParameterCount() == 0
                    && clazz.isAssignableFrom(m.getReturnType())) {
                m.setAccessible(true);
                return m.invoke(null);
            }
        }
        throw new NoSuchMethodException("No static factory found on " + clazz.getName());
    }

    /** Find a field by trying public fields first, then declared (accessible). */
    private static Field findField(Class<?> clazz, String... names) throws NoSuchFieldException {
        for (String name : names) {
            try {
                return clazz.getField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        // Walk the hierarchy for declared fields
        for (String name : names) {
            Class<?> c = clazz;
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored) {
                    c = c.getSuperclass();
                }
            }
        }
        throw new NoSuchFieldException("None of " + Arrays.toString(names) + " found on " + clazz.getName());
    }

    private static String sanitizeName(String name) {
        String value = name == null || name.isBlank() ? "BountyNPC" : name.replaceAll("[^A-Za-z0-9_]", "");
        if (value.isBlank()) {
            value = "BountyNPC";
        }
        return value.length() > 16 ? value.substring(0, 16) : value;
    }

    private static byte toAngle(float degrees) {
        return (byte) Math.floorMod((int) (degrees * 256.0F / 360.0F), 256);
    }
}
