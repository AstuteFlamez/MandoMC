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
                builder.withMethod(method.methodName(), method.methodType(), method.flags().flagsMask(), mb ->
                        mb.withCode(code -> {
                            if (type == 'V') code.return_();
                            else code.iconst_0().ireturn();
                        }));
            } else builder.with(element);
        });
        if (!found.equals(expected)) throw new IllegalStateException("Native hooks changed: " + name + " " + found);
        return result;
    }
}
