import java.lang.classfile.*;
import java.lang.classfile.attribute.LocalVariableTableAttribute;
import java.lang.classfile.instruction.*;
import java.lang.constant.*;
import java.nio.file.*;

/** Adds three source-equivalent calls to the exact deployed bootstrap; keeps all existing methods/APIs. */
public final class BobaBootstrapOverlay {
    public static void main(String[] args) throws Exception {
        var cf=ClassFile.of();byte[] before=Files.readAllBytes(Path.of(args[0]));var model=cf.parse(before);
        String feature="net/mandomc/gameplay/boba/BobaEquipmentFeature";
        if(new String(before,java.nio.charset.StandardCharsets.ISO_8859_1).contains(feature))
            throw new IllegalStateException("Boba bootstrap already installed");
        var enable=model.methods().stream().filter(m->m.methodName().equalsString("onEnable")).findFirst().orElseThrow();
        var vars=enable.code().orElseThrow().attributes().stream().filter(a->a instanceof LocalVariableTableAttribute)
                .map(a->(LocalVariableTableAttribute)a).findFirst().orElseThrow().localVariables();
        int root=vars.stream().filter(v->v.name().equalsString("root")&&v.type().equalsString("Lnet/mandomc/commands/RootCommand;"))
                .findFirst().orElseThrow().slot();
        int language=vars.stream().filter(v->v.name().equalsString("language")&&v.type().equalsString("Lorg/bukkit/configuration/file/YamlConfiguration;"))
                .findFirst().orElseThrow().slot();
        var type=ClassDesc.of("net.mandomc.gameplay.boba.BobaEquipmentFeature");int[] counts={0,0,0};
        byte[] enabled=cf.transformClass(model,ClassTransform.transformingMethodBodies(
                m->m.methodName().equalsString("onEnable"),(b,e)->{
                    if(e instanceof InvokeInstruction i&&i.owner().asInternalName().equals("net/mandomc/gameplay/items/ItemCatalog")
                            &&i.name().equalsString("reload")&&i.type().equalsString("()V")) {
                        b.aload(0).invokestatic(type,"installDefinitions",MethodTypeDesc.ofDescriptor("(Lorg/bukkit/plugin/java/JavaPlugin;)V"));counts[0]++;
                    }
                    if(e instanceof ConstantInstruction c&&c.constantValue().equals("mobs/drops/jabba.yml")) {
                        b.aload(0).aload(0).invokevirtual(ClassDesc.of("net.mandomc.bootstrap.MandoPlugin"),"items",
                                MethodTypeDesc.ofDescriptor("()Lnet/mandomc/gameplay/items/ItemCatalog;"))
                                .aload(root).aload(language).invokestatic(type,"enable",MethodTypeDesc.ofDescriptor(
                                "(Lorg/bukkit/plugin/java/JavaPlugin;Lnet/mandomc/gameplay/items/ItemCatalog;Lnet/mandomc/commands/RootCommand;Lorg/bukkit/configuration/ConfigurationSection;)V"));counts[1]++;
                    }
                    b.with(e);
                }));
        byte[] after=cf.transformClass(cf.parse(enabled),ClassTransform.transformingMethodBodies(m->m.methodName().equalsString("onDisable"),(b,e)->{
                    if(e instanceof ReturnInstruction i&&i.opcode()==Opcode.RETURN) {
                        b.aload(0).invokestatic(type,"disable",MethodTypeDesc.ofDescriptor("(Lorg/bukkit/plugin/java/JavaPlugin;)V"));counts[2]++;
                    }
                    b.with(e);
                }));
        if(counts[0]!=1||counts[1]!=1||counts[2]!=1)throw new IllegalStateException("Unexpected bootstrap anchors "+java.util.Arrays.toString(counts));
        if(!cf.verify(after).isEmpty())throw new IllegalStateException("Invalid bootstrap: "+cf.verify(after));
        Files.write(Path.of(args[1]),after);
        System.out.println("Bootstrap hooks verified: definitions=1, enable=1, disable=1; root slot="+root+", language slot="+language);
    }
}
