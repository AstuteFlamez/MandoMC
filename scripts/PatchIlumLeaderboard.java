import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.*;
import java.nio.file.*;
import java.util.*;

/** Preserve the live timer correction and all unrelated methods while changing leaderboard backgrounds. */
public final class PatchIlumLeaderboard {
    private static final ClassDesc DISPLAY = ClassDesc.of("org.bukkit.entity.TextDisplay");
    private static final ClassDesc OWNER = ClassDesc.of("net.mandomc.gameplay.ilum.ParkourService");
    private static final ClassDesc COLOR = ClassDesc.of("org.bukkit.Color");

    private static void background(CodeBuilder code, boolean existing) {
        code.aload(0);
        if (existing) code.getfield(OWNER, "leaderboard", DISPLAY);
        code.iconst_0().invokeinterface(DISPLAY, "setDefaultBackground",
                MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_boolean));
        code.aload(0);
        if (existing) code.getfield(OWNER, "leaderboard", DISPLAY);
        code.iconst_0().invokestatic(COLOR, "fromARGB", MethodTypeDesc.of(COLOR, ConstantDescs.CD_int))
                .invokeinterface(DISPLAY, "setBackgroundColor", MethodTypeDesc.of(ConstantDescs.CD_void, COLOR));
    }

    public static void main(String[] args) throws Exception {
        var cf = ClassFile.of(); var before = cf.parse(Path.of(args[0])); int[] count = {0, 0};
        byte[] result = cf.transformClass(before, (builder, element) -> {
            if (element instanceof MethodModel method && method.methodName().equalsString("lambda$spawnDisplays$1")
                    && method.methodType().equalsString("(Lorg/bukkit/entity/TextDisplay;)V")) {
                count[0]++;
                builder.transformMethod(method, MethodTransform.transformingCode(new CodeTransform() {
                    @Override public void atStart(CodeBuilder code) { background(code, false); }
                    @Override public void accept(CodeBuilder code, CodeElement instruction) { code.with(instruction); }
                }));
            } else if (element instanceof MethodModel method && method.methodName().equalsString("updateLeaderboard")
                    && method.methodType().equalsString("()V")) {
                builder.transformMethod(method, MethodTransform.transformingCode((code, instruction) -> {
                    if (instruction instanceof InvokeInstruction call && call.owner().asSymbol().equals(DISPLAY)
                            && call.name().equalsString("text") && call.type().stringValue().equals("(Lnet/kyori/adventure/text/Component;)V")) {
                        count[1]++;
                        background(code, true);
                    }
                    code.with(instruction);
                }));
            } else builder.with(element);
        });
        if (count[0] != 1 || count[1] != 1) throw new IllegalArgumentException("Expected one leaderboard spawn and refresh: " + Arrays.toString(count));
        var after = cf.parse(result);
        for (var method : before.methods()) {
            if (Set.of("lambda$spawnDisplays$1", "updateLeaderboard").contains(method.methodName().stringValue())) continue;
            var match = after.methods().stream().filter(m -> m.methodName().equals(method.methodName())
                    && m.methodType().equals(method.methodType())).findFirst().orElseThrow();
            var a = method.findAttribute(Attributes.code()); var b = match.findAttribute(Attributes.code());
            if (a.isPresent() && !Arrays.equals(a.get().codeArray(), b.orElseThrow().codeArray()))
                throw new IllegalStateException("Unrelated Ilum method changed: " + method.methodName());
        }
        if (!cf.verify(result).isEmpty()) throw new IllegalStateException("Class verification failed: " + cf.verify(result));
        Files.createDirectories(Path.of(args[1]).getParent()); Files.write(Path.of(args[1]), result);
        System.out.println("Patched leaderboard spawn/refresh only; every unrelated method bytecode preserved.");
    }
}
