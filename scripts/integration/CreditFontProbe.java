/** Standalone primary-client-code verification; compile against the pinned client and its libraries. */
import java.util.*;import java.nio.file.*;import java.io.*;import java.lang.reflect.*;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.client.gui.font.FontManager;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.client.gui.font.providers.GlyphProviderDefinition;
public class CreditFontProbe {
 static Resource resource(String id,byte[] bytes){var pack=(PackResources)Proxy.newProxyInstance(CreditFontProbe.class.getClassLoader(),new Class[]{PackResources.class},(p,m,a)->m.getName().equals("packId")?id:null);return new Resource(pack,()->new ByteArrayInputStream(bytes));}
 public static void main(String[] args)throws Exception{
  var load=FontManager.class.getDeclaredMethod("loadResourceStack",List.class,Identifier.class);load.setAccessible(true);
  var base=resource("vanilla","{\"providers\":[{\"type\":\"bitmap\",\"file\":\"minecraft:font/ascii.png\",\"ascent\":7,\"height\":8,\"chars\":[\"$\"]}]}".getBytes());
  var custom=resource("MandoMC",Files.readAllBytes(Path.of(args[0])));
  var records=new ArrayList<>((List<Pair<?,GlyphProviderDefinition.Conditional>>)load.invoke(null,List.of(base,custom),Identifier.parse("minecraft:default")));
  Collections.reverse(records);boolean found=false;
  for(var record:records){var definition=record.getSecond().definition();if(definition instanceof BitmapProvider.Definition bitmap){for(var row:bitmap.codepointGrid())for(int cp:row)if(cp==36){var id=record.getFirst();var pack=id.getClass().getDeclaredMethod("pack");pack.setAccessible(true);if(!pack.invoke(id).equals("MandoMC")||!bitmap.file().toString().equals("mandomc:font/credit.png")||bitmap.ascent()!=7||bitmap.height()!=8)throw new AssertionError("wrong selected dollar provider");found=true;break;}}if(found)break;}
  if(!found)throw new AssertionError("dollar glyph missing");
  System.out.println("26.1.2 client FontManager CODEC/stack validation PASSED: top-pack credit bitmap wins for U+0024; baseline7/height8 accepted.");
 }
}
