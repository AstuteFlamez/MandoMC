import java.lang.classfile.*;
import java.lang.constant.*;
import java.nio.file.*;
import java.util.*;

/** Change only the live parkour formatter, retaining every unrelated method verbatim. */
public final class PatchIlumTime {
    public static void main(String[] args) throws Exception {
        var cf = ClassFile.of(); var before = cf.parse(Path.of(args[0])); int[] count = {0};
        byte[] result = cf.transformClass(before, (builder, element) -> {
            if (element instanceof MethodModel method && method.methodName().equalsString("format")
                    && method.methodType().equalsString("(J)Ljava/lang/String;")) {
                count[0]++;
                builder.withMethodBody(method.methodName(), method.methodType(), method.flags().flagsMask(), code ->
                        code.lload(0).invokestatic(ClassDesc.of("net.mandomc.gameplay.ilum.IlumTimeFormat"), "format",
                                MethodTypeDesc.of(ConstantDescs.CD_String, ConstantDescs.CD_long)).areturn());
            } else builder.with(element);
        });
        if (count[0] != 1) throw new IllegalArgumentException("Expected exactly one public timer formatter");
        var after = cf.parse(result);
        for (var method : before.methods()) {
            if (method.methodName().equalsString("format") && method.methodType().equalsString("(J)Ljava/lang/String;")) continue;
            var match = after.methods().stream().filter(m -> m.methodName().equals(method.methodName())
                    && m.methodType().equals(method.methodType())).findFirst().orElseThrow();
            var a = method.findAttribute(Attributes.code()); var b = match.findAttribute(Attributes.code());
            if (a.isPresent() && !Arrays.equals(a.get().codeArray(), b.orElseThrow().codeArray()))
                throw new IllegalStateException("Unrelated Ilum method changed: " + method.methodName());
        }
        Files.createDirectories(Path.of(args[1]).getParent()); Files.write(Path.of(args[1]), result);
    }
}
