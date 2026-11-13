import java.lang.classfile.*;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.constant.*;
import java.nio.file.*;
import java.util.*;

/** Inserts one guarded herd lifecycle call; every other current bootstrap method stays byte-identical. */
public final class HerdBootstrapPatch {
    public static void main(String[] args) throws Exception {
        var cf=ClassFile.of(); var original=cf.parse(Files.readAllBytes(Path.of(args[0])));
        var hook=ClassDesc.of("net.mandomc.bootstrap.HerdBootstrap");
        for (var method:original.methods()) if(method.code().isPresent()) for(var element:method.code().get()) {
            if(element instanceof InvokeInstruction call && call.owner().asSymbol().equals(hook))
                throw new IllegalStateException("Herd bootstrap hook already exists; rebuild from a fresh current baseline");
        }
        int[] count={0};
        byte[] output=cf.transformClass(original,ClassTransform.transformingMethodBodies(
                method->method.methodName().equalsString("onEnable"),(builder,element)->{
                    builder.with(element);
                    if(element instanceof InvokeInstruction call && call.opcode()==Opcode.INVOKESPECIAL
                            && call.owner().asInternalName().equals("net/mandomc/integrations/mobs/DewbackDrops")
                            && call.name().equalsString("<init>")) {
                        builder.aload(0).invokestatic(hook,"enable",MethodTypeDesc.of(ConstantDescs.CD_void,
                                ClassDesc.of("org.bukkit.plugin.java.JavaPlugin")));
                        count[0]++;
                    }
                }));
        if(count[0]!=1)throw new IllegalStateException("Expected one enabled-mobs constructor anchor, got "+count[0]);
        var changed=cf.parse(output);
        for(var method:original.methods()) if(!method.methodName().equalsString("onEnable")&&method.code().isPresent()) {
            var matching=changed.methods().stream().filter(m->m.methodName().equalsString(method.methodName().stringValue())
                    &&m.methodType().equalsString(method.methodType().stringValue())).findFirst().orElseThrow();
            if(!Arrays.equals(((CodeAttribute)method.code().get()).codeArray(),((CodeAttribute)matching.code().orElseThrow()).codeArray()))
                throw new IllegalStateException("Unrelated method changed: "+method.methodName());
        }
        Files.createDirectories(Path.of(args[1]).getParent()); Files.write(Path.of(args[1]),output);
        System.out.println("Inserted one herd lifecycle call; all unrelated bootstrap methods are byte-identical.");
    }
}
