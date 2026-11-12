import java.lang.classfile.*;
import java.lang.constant.*;
import java.lang.classfile.instruction.*;
import java.nio.file.*;
import java.util.*;

/** Visual-only patch of the owned sell tray; all sale/custody methods stay byte-identical. */
public final class PatchSellGui {
    public static void main(String[] args) throws Exception {
        var cf = ClassFile.of();
        for (int index = 0; index < 2; index++) {
            var before = cf.parse(Path.of(args[index * 2]));
            String target = index == 0 ? "open" : "<init>";
            int[] count = {0};
            int[] infoItems = {0};
            byte[] result = cf.transformClass(before, ClassTransform.transformingMethodBodies(
                    method -> method.methodName().equalsString(target), (code, element) -> {
                if (target.equals("open") && element instanceof ConstantInstruction constant
                        && "gray-title".equals(constant.constantValue())) {
                    count[0]++;
                    code.ldc("buyer-gui-title");
                } else if (target.equals("open") && element instanceof InvokeInstruction invoke
                        && invoke.owner().asInternalName().equals("org/bukkit/inventory/ItemStack")
                        && invoke.name().equalsString("setItemMeta") && infoItems[0]++ == 0) {
                    // First setItemMeta is the info book: retain its real hover tooltip,
                    // while the original gold symbol is painted by the background.
                    code.dup().ldc("mandomc:gui/info")
                            .invokestatic(ClassDesc.of("org.bukkit.NamespacedKey"), "fromString",
                                    MethodTypeDesc.of(ClassDesc.of("org.bukkit.NamespacedKey"), ConstantDescs.CD_String))
                            .invokeinterface(ClassDesc.of("org.bukkit.inventory.meta.ItemMeta"), "setItemModel",
                                    MethodTypeDesc.of(ConstantDescs.CD_void, ClassDesc.of("org.bukkit.NamespacedKey")));
                    code.with(element);
                } else if (target.equals("<init>") && element instanceof InvokeInstruction invoke
                        && invoke.owner().asInternalName().equals("net/mandomc/gameplay/ui/MenuItems")
                        && invoke.name().equalsString("fill")
                        && invoke.type().equalsString("(Lorg/bukkit/inventory/Inventory;)V")) {
                    count[0]++;
                    code.pop(); // Empty, protected controls let the title artwork show through.
                } else code.with(element);
            }));
            if (target.equals("open") && infoItems[0] < 1) throw new IllegalStateException("Missing info item");
            if (count[0] != 1) throw new IllegalStateException("Expected one visual edit in " + target);
            var after = cf.parse(result);
            for (var method : before.methods()) {
                if (method.methodName().equalsString(target)) continue;
                var match = after.methods().stream().filter(m -> m.methodName().equals(method.methodName())
                        && m.methodType().equals(method.methodType())).findFirst().orElseThrow();
                var a = method.findAttribute(Attributes.code()); var b = match.findAttribute(Attributes.code());
                if (a.isPresent() && !Arrays.equals(a.get().codeArray(), b.orElseThrow().codeArray()))
                    throw new IllegalStateException("Unrelated method changed: " + method.methodName());
            }
            Files.createDirectories(Path.of(args[index * 2 + 1]).getParent());
            Files.write(Path.of(args[index * 2 + 1]), result);
        }
        System.out.println("Only title, info presentation and decorative pane fill changed; every other method verified identical.");
    }
}
