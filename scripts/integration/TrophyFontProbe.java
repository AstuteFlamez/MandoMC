/** Parse the isolated category badge with Minecraft 26.1.2's actual client font codec. */
import java.util.*;import java.nio.file.*;import java.io.*;import java.lang.reflect.*;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
public class TrophyFontProbe {
 public static void main(String[] args)throws Exception{
  var pack=(PackResources)Proxy.newProxyInstance(TrophyFontProbe.class.getClassLoader(),new Class[]{PackResources.class},(p,m,a)->m.getName().equals("packId")?"MandoMC":null);
  var bytes=Files.readAllBytes(Path.of(args[0]));var custom=new Resource(pack,()->new ByteArrayInputStream(bytes));var load=FontManager.class.getDeclaredMethod("loadResourceStack",List.class,Identifier.class);load.setAccessible(true);
  var records=(List<Pair<?,GlyphProviderDefinition.Conditional>>)load.invoke(null,List.of(custom),Identifier.parse("mandomc:trophies"));
  if(records.size()!=1||!(records.getFirst().getSecond().definition() instanceof BitmapProvider.Definition bitmap)||!bitmap.file().toString().equals("mandomc:font/item_labels/trophy.png")||bitmap.ascent()!=8||bitmap.height()!=9||bitmap.codepointGrid().length!=1||bitmap.codepointGrid()[0].length!=1||bitmap.codepointGrid()[0][0]!=0xe000)throw new AssertionError("Incorrect isolated trophy badge definition");
  System.out.println("26.1.2 native client font CODEC PASSED: isolated mandomc:trophies U+E000, ascent8/height9.");
 }
}
