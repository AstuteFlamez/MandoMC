package net.mandomc.redstone;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Set;

/** Pinned Paper 26.1.2 signal boundary, installed before any worlds or native classes load. */
public final class RedstoneAgent {
    private static final Map<String, Set<String>> METHODS = Map.of(
            "net/minecraft/world/level/SignalGetter", Set.of("getDirectSignal", "getDirectSignalTo",
                    "getControlInputSignal", "hasSignal", "getSignal", "hasNeighborSignal", "getBestNeighborSignal"),
            "net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase",
                    Set.of("getSignal", "getDirectSignal", "getAnalogOutputSignal", "isSignalSource"),
            "net/minecraft/world/level/block/DispenserBlock", Set.of("tick"),
            "net/minecraft/world/level/block/CrafterBlock", Set.of("tick"),
            "net/minecraft/world/level/block/CommandBlock", Set.of("tick"),
            "net/minecraft/world/level/block/DaylightDetectorBlock", Set.of("updateSignalStrength"),
            "net/minecraft/world/level/block/piston/PistonBaseBlock", Set.of("triggerEvent"));

    /** Java's executable-JAR agent entry point, before Paper loads any native classes. */
    public static void agentmain(String arguments, Instrumentation instrumentation) {
        premain(arguments, instrumentation);
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        try {
            var helper = java.nio.file.Files.createTempFile("mandomc-ilum-circuits-", ".jar");
            helper.toFile().deleteOnExit();
            try (var out = new java.util.jar.JarOutputStream(java.nio.file.Files.newOutputStream(helper));
                    var input = RedstoneAgent.class.getResourceAsStream("/net/mandomc/redstone/IlumCircuits.class")) {
                out.putNextEntry(new java.util.jar.JarEntry("net/mandomc/redstone/IlumCircuits.class"));
                java.util.Objects.requireNonNull(input).transferTo(out); out.closeEntry();
            }
            instrumentation.appendToBootstrapClassLoaderSearch(new java.util.jar.JarFile(helper.toFile()));
        } catch (Exception error) { throw new IllegalStateException("Cannot install Ilum circuit boundary", error); }
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String name, Class<?> redefined,
                                    ProtectionDomain domain, byte[] bytes) {
                if (!METHODS.containsKey(name)) return null;
                try {
                    byte[] result = transformClass(name, bytes, loader);
                    System.setProperty("mandomc.redstone." + name, "disabled");
                    return result;
                } catch (Throwable failure) {
                    // Instrumentation normally swallows transformer errors. Refuse an unprotected boot instead.
                    failure.printStackTrace(System.err);
                    Runtime.getRuntime().halt(78);
                    return null;
                }
            }
        });
        System.setProperty("mandomc.redstone.agent", "26.1.2-v1");
    }

    public static byte[] transformClass(String name, byte[] bytes, ClassLoader loader) {
        Set<String> expected = METHODS.get(name);
        Set<String> found = new java.util.HashSet<>();
        var classFile = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(
                ClassHierarchyResolver.ofResourceParsing(loader)));
        byte[] result = classFile.transformClass(classFile.parse(bytes), (builder, element) -> {
            if (element instanceof MethodModel method && expected.contains(method.methodName().stringValue())) {
                String methodName = method.methodName().stringValue();
                if (!found.add(methodName) || method.code().isEmpty()) {
                    throw new IllegalStateException("Unexpected native method: " + name + "." + methodName);
                }
                String descriptor = method.methodType().stringValue();
                if (name.equals("net/minecraft/world/level/block/CommandBlock")) {
                    // A saved/queued redstone command must not execute once after restart. Always-active
                    // command blocks are independent native scripts, so retain their existing tick body.
                    builder.transformMethod(method, MethodTransform.transformingCode(CodeTransform.ofStateful(() -> {
                        boolean[] first = {true};
                        return (code, instruction) -> {
                            if (first[0]) {
                                first[0] = false;
                                var proceed = code.newLabel();
                                var entity = code.newLabel();
                                code.aload(2).aload(3).invokevirtual(ClassDesc.of("net.minecraft.server.level.ServerLevel"),
                                        "getBlockEntity", MethodTypeDesc.ofDescriptor("(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;"))
                                        .dup().instanceOf(ClassDesc.of("net.minecraft.world.level.block.entity.CommandBlockEntity"))
                                        .ifne(entity).pop().return_().labelBinding(entity)
                                        .checkcast(ClassDesc.of("net.minecraft.world.level.block.entity.CommandBlockEntity"))
                                        .invokevirtual(ClassDesc.of("net.minecraft.world.level.block.entity.CommandBlockEntity"),
                                                "isAutomatic", MethodTypeDesc.ofDescriptor("()Z"))
                                        .ifne(proceed).return_().labelBinding(proceed);
                            }
                            code.with(instruction);
                        };
                    })));
                    return;
                }
                char type = descriptor.charAt(descriptor.length() - 1);
                if (type != 'I' && type != 'Z' && type != 'V') {
                    throw new IllegalStateException("Unexpected native return type: " + descriptor);
                }
                // isSignalSource has no world argument. Preserve source identity; world-aware reads
                // below still return zero everywhere except Ilum.
                if (methodName.equals("isSignalSource")) { builder.with(method); return; }
                int levelSlot = name.equals("net/minecraft/world/level/SignalGetter") ? 0 : -1;
                int slot = (method.flags().flagsMask() & 8) == 0 ? 1 : 0;
                for (var parameter : method.methodTypeSymbol().parameterList()) {
                    String parameterName = parameter.descriptorString();
                    if (Set.of("Lnet/minecraft/world/level/Level;", "Lnet/minecraft/server/level/ServerLevel;",
                            "Lnet/minecraft/world/level/BlockGetter;", "Lnet/minecraft/world/level/SignalGetter;").contains(parameterName)) levelSlot = slot;
                    slot += parameterName.equals("J") || parameterName.equals("D") ? 2 : 1;
                }
                if (levelSlot < 0) throw new IllegalStateException("No world argument: " + name + descriptor);
                final int worldSlot = levelSlot;
                builder.transformMethod(method, MethodTransform.transformingCode(CodeTransform.ofStateful(() -> {
                    boolean[] first = {true};
                    return (code, instruction) -> {
                        if (first[0]) {
                            first[0] = false;
                            var original = code.newLabel();
                            code.aload(worldSlot).invokestatic(ClassDesc.of("net.mandomc.redstone.IlumCircuits"),
                                    "ilum", MethodTypeDesc.ofDescriptor("(Ljava/lang/Object;)Z")).ifne(original);
                            if (type == 'V') code.return_(); else code.iconst_0().ireturn();
                            code.labelBinding(original);
                        }
                        code.with(instruction);
                    };
                })));
            } else builder.with(element);
        });
        if (!found.equals(expected)) throw new IllegalStateException("Native hooks changed: " + name + " " + found);
        return result;
    }
}
