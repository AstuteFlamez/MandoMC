import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;

/** Adds the source-equivalent constructor registration to the exact current deployed class. */
public final class RedstoneOverlay {
    public static void main(String[] args) throws Exception {
        byte[] original=Files.readAllBytes(Path.of(args[0]));
        var cf=ClassFile.of();
        var model=cf.parse(original);
        String boundary="net/mandomc/integrations/paper/RedstoneCircuitBoundary";
        if(new String(original,java.nio.charset.StandardCharsets.ISO_8859_1).contains(boundary))
            throw new IllegalStateException("Redstone constructor already installed; refusing duplicate registration");
        int[] inserted={0};
        var type=ClassDesc.of("net.mandomc.integrations.paper.RedstoneCircuitBoundary");
        byte[] output=cf.transformClass(model,ClassTransform.transformingMethodBodies(
                method->method.methodName().equalsString("<init>")
                        && method.methodType().stringValue().startsWith("(Lorg/bukkit/plugin/java/JavaPlugin;"),
                (builder,element)->{
                    if(element instanceof ReturnInstruction instruction && instruction.opcode()==Opcode.RETURN) {
                        builder.new_(type).dup().aload(1).invokespecial(type,"<init>",
                                MethodTypeDesc.ofDescriptor("(Lorg/bukkit/plugin/java/JavaPlugin;)V")).pop();
                        inserted[0]++;
                    }
                    builder.with(element);
                }));
        if(inserted[0]!=1)throw new IllegalStateException("Unexpected SurvivalRules constructor exits: "+inserted[0]);
        if(!cf.verify(output).isEmpty())throw new IllegalStateException("Overlay class verification failed: "+cf.verify(output));
        Files.write(Path.of(args[1]),output);
    }
}
