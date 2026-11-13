import java.lang.classfile.*;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.*;
import java.nio.file.*;
import java.util.*;

/** Add only the new owner's lifecycle calls to the current integrated command hub. */
public final class LeaderboardLifecycleOverlay {
    public static void main(String[] args) throws Exception {
        var feature = ClassDesc.of("net.mandomc.gameplay.leaderboards.LeaderboardFeature");
        var plugin = ClassDesc.of("net.mandomc.bootstrap.MandoPlugin");
        var cf = ClassFile.of(); var before = cf.parse(Path.of(args[0])); int[] count = {0, 0};
        var output = cf.transformClass(before, (builder, element) -> {
            if (element instanceof MethodModel method && method.methodName().equalsString("<init>")) {
                builder.transformMethod(method, MethodTransform.transformingCode((code, instruction) -> {
                    if (instruction instanceof ReturnInstruction ret && ret.opcode() == Opcode.RETURN) {
                        count[0]++;
                        code.aload(1).checkcast(plugin).invokestatic(feature, "start", MethodTypeDesc.of(ConstantDescs.CD_void, plugin));
                    }
                    code.with(instruction);
                }));
            } else if (element instanceof MethodModel method && method.methodName().equalsString("close")) {
                count[1]++;
                builder.transformMethod(method, MethodTransform.transformingCode(new CodeTransform() {
                    @Override public void atStart(CodeBuilder code) {
                        code.invokestatic(feature, "stop", MethodTypeDesc.of(ConstantDescs.CD_void));
                    }
                    @Override public void accept(CodeBuilder code, CodeElement instruction) { code.with(instruction); }
                }));
            } else builder.with(element);
        });
        if (count[0] != 1 || count[1] != 1) throw new IllegalStateException("Unexpected lifecycle shape: " + Arrays.toString(count));
        var after = cf.parse(output);
        for (var method : before.methods()) {
            if (Set.of("<init>", "close").contains(method.methodName().stringValue())) continue;
            var match = after.methods().stream().filter(m -> m.methodName().equals(method.methodName())
                    && m.methodType().equals(method.methodType())).findFirst().orElseThrow();
            var a = method.findAttribute(Attributes.code()); var b = match.findAttribute(Attributes.code());
            if (a.isPresent() && !Arrays.equals(a.get().codeArray(), b.orElseThrow().codeArray()))
                throw new IllegalStateException("Unrelated method changed: " + method.methodName());
        }
        if (!cf.verify(output).isEmpty()) throw new IllegalStateException("Lifecycle verification failed: " + cf.verify(output));
        Files.write(Path.of(args[1]), output);
    }
}
