import java.lang.classfile.*;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.constant.*;
import java.nio.file.*;

/** Preserve the actual integrated hub; add two direct, verifier-checked feature lifecycle calls. */
public final class ChatLifecycleOverlay {
    public static void main(String[] args)throws Exception{
        var feature=ClassDesc.of("net.mandomc.gameplay.messaging.MessagingFeature");
        var plugin=ClassDesc.of("org.bukkit.plugin.java.JavaPlugin");
        var input=Files.readAllBytes(Path.of(args[0]));var cf=ClassFile.of();
        CodeTransform constructor=(builder,element)->{
            if(element instanceof ReturnInstruction instruction&&instruction.opcode()==Opcode.RETURN)
                builder.aload(1).checkcast(plugin).invokestatic(feature,"start",MethodTypeDesc.of(ConstantDescs.CD_void,plugin));
            builder.with(element);
        };
        var addStart=ClassTransform.transformingMethodBodies(method->method.methodName().stringValue().equals("<init>"),constructor);
        CodeTransform stop=new CodeTransform(){
            @Override public void atStart(CodeBuilder builder){builder.invokestatic(feature,"stop",MethodTypeDesc.of(ConstantDescs.CD_void));}
            @Override public void accept(CodeBuilder builder,CodeElement element){builder.with(element);}
        };
        var addStop=ClassTransform.transformingMethodBodies(method->method.methodName().stringValue().equals("close")&&method.methodType().stringValue().equals("()V"),stop);
        var started=cf.transformClass(cf.parse(input),addStart);
        var output=cf.transformClass(cf.parse(started),addStop);
        if(!cf.verify(output).isEmpty())throw new IllegalStateException("Chat lifecycle verifier rejected output: "+cf.verify(output));
        Files.write(Path.of(args[1]),output);
    }
}
