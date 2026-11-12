import java.lang.classfile.*;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.nio.file.*;

/** Mechanical call-site/lifecycle additions against the exact current release, without recompiling stale features. */
public final class ReloadOverlay {
    public static void main(String[] args) throws Exception {
        var input=Path.of(args[0]); var output=Path.of(args[1]);var cf=ClassFile.of();
        byte[] bytes=Files.readAllBytes(input);var model=cf.parse(bytes);int[] count={0,0,0};
        boolean bootstrap=model.thisClass().asInternalName().equals("net/mandomc/bootstrap/MandoPlugin");
        var bridge=ClassDesc.of("net.mandomc.integrations.weapons.ReloadIndicator");
        byte[] after=cf.transformClass(model,ClassTransform.transformingMethodBodies((b,e)->{
            if(e instanceof InvokeInstruction i&&i.name().equalsString("sendActionBar")) {
                if(!i.type().equalsString("(Lnet/kyori/adventure/text/Component;)V")
                        ||!i.owner().asInternalName().equals("org/bukkit/entity/Player"))
                    throw new IllegalStateException("Unrouted actionbar signature: "+i);
                b.invokestatic(ClassDesc.of("net.mandomc.gameplay.hud.ActionBars"),"send",
                        MethodTypeDesc.ofDescriptor("(Lorg/bukkit/entity/Player;Lnet/kyori/adventure/text/Component;)V"));count[0]++;
            } else b.with(e);
        }));
        if(bootstrap) {
            if(new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1).contains("net/mandomc/integrations/weapons/ReloadIndicator"))
                throw new IllegalStateException("Reload bootstrap already installed");
            after=cf.transformClass(cf.parse(after),(cb,ce)->{
                if (!(ce instanceof MethodModel m)) { cb.with(ce); return; }
                boolean enable=m.methodName().equalsString("onEnable"),disable=m.methodName().equalsString("onDisable");
                cb.transformMethod(m,MethodTransform.transformingCode((b,e)->{
                    if(enable&&e instanceof InvokeInstruction i&&i.owner().asInternalName().equals("net/mandomc/gameplay/boba/BobaEquipmentFeature")&&i.name().equalsString("enable")) {
                        b.with(e);b.aload(0).invokestatic(bridge,"enable",MethodTypeDesc.ofDescriptor("(Lorg/bukkit/plugin/java/JavaPlugin;)V"));count[1]++;return;
                    }
                    if(disable&&e instanceof ReturnInstruction r&&r.opcode()==Opcode.RETURN) {
                        b.aload(0).invokestatic(bridge,"disable",MethodTypeDesc.ofDescriptor("(Lorg/bukkit/plugin/java/JavaPlugin;)V"));count[2]++;
                    }
                    b.with(e);
                }));
            });
            if(count[1]!=1||count[2]!=1)throw new IllegalStateException("Unexpected lifecycle anchors "+java.util.Arrays.toString(count));
        }
        if(count[0]+count[1]+count[2]>0){Files.createDirectories(output.getParent());Files.write(output,after);}
        System.out.println(model.thisClass().asInternalName()+" "+java.util.Arrays.toString(count));
    }
}
