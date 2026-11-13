package net.mandomc.verification;
import java.util.*;
import java.util.function.Consumer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
public final class BanthaUtilitiesProbe extends JavaPlugin {
 private int checks;
 @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{verify();getLogger().info("BANTHA UTILITIES PASSED: "+checks+" assertions");}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"BANTHA UTILITIES FAILED",e);}finally{Bukkit.shutdown();}},100);}
 private void check(boolean value,String why){if(!value)throw new IllegalStateException(why);checks++;}
 private String name(ItemStack i){return i==null||!i.hasItemMeta()?"":PlainTextComponentSerializer.plainText().serialize(i.getItemMeta().displayName());}
 private Object field(Object o,String key)throws Exception{var f=o.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(o);}
 private void verify()throws Exception{
  var mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando!=null&&mando.isEnabled(),"current combined plugin enabled");
  var recipes=(CraftingRecipes)field(mando,"craftingRecipes");var guide=(RecipeGuide)field(mando,"recipeGuide");var world=Bukkit.getWorlds().getFirst();var actor=nativeTestPlayer(world,world.getSpawnLocation());
  var defs=recipes.guideDefinitions().stream().filter(r->r.id().startsWith("bantha_")).toList();check(defs.size()==20,"all 20 additions visible");
  for(var d:defs){
   check(d.category().equals("utilities"),d.id()+" utility category");
   String nativeId=d.id().equals("bantha_hide_from_rabbit_hide")?"leather":d.id().substring(7);
   check(Bukkit.getRecipe(NamespacedKey.minecraft(nativeId))==null,"no duplicate native recipe "+nativeId);
   check(Bukkit.getRecipe(new NamespacedKey("mandomc","craft_"+d.id()))!=null,"registered "+d.id());
   var matrix=new ItemStack[9];
   for(int y=0;y<d.shape().size();y++)for(int x=0;x<d.shape().get(y).length();x++){char c=d.shape().get(y).charAt(x);if(c!=' ')matrix[y*3+x]=recipes.ingredient(d.ingredients().get(c));}
   var result=Bukkit.craftItemResult(matrix,world,actor).getResult();
   check(result.isSimilar(recipes.result(d))&&result.getAmount()==d.amount(),"native matching "+d.id());
   var mirrored=new ItemStack[9];for(int y=0;y<3;y++)for(int x=0;x<3;x++)mirrored[y*3+x]=matrix[y*3+2-x];
   check(Bukkit.craftItemResult(mirrored,world,actor).getResult().isSimilar(result),"mirror "+d.id());
   for(int i=0;i<9;i++)if(matrix[i]!=null){var missing=matrix.clone();missing[i]=null;check(Bukkit.craftItemResult(missing,world,actor).getResult().isEmpty(),"missing cost "+d.id());break;}
   if(d.id().equals("bantha_book")){var moved=new ItemStack[9];int slot=8;for(var item:matrix)if(item!=null)moved[slot--]=item;check(Bukkit.craftItemResult(moved,world,actor).getResult().isSimilar(result),"book remains shapeless");}
   var detail=RecipeGuide.class.getDeclaredMethod("detail",Player.class,RecipeDefinition.class,Consumer.class);detail.setAccessible(true);detail.invoke(guide,actor,d,(Consumer<Player>)p->{});
   var inv=actor.getOpenInventory().getTopInventory();check(inv.getItem(25).isSimilar(recipes.result(d)),"selection preview result "+d.id());
   for(int y=0;y<d.shape().size();y++)for(int x=0;x<d.shape().get(y).length();x++){String id=d.ingredients().get(d.shape().get(y).charAt(x));if("bantha_hide".equals(id))check(mando.items().identify(inv.getItem(10+y*9+x)).equals("bantha_hide"),"hide preview identity "+d.id());}
  }
  check(recipes.definitions().stream().filter(r->r.id().startsWith("robes_")).allMatch(r->r.category().equals("armor")),"robes stay armor");
  check(recipes.definitions().stream().anyMatch(r->r.id().equals("agrinium_saddle")&&r.category().equals("utilities")),"saddle stays utility");
  var categories=RecipeGuide.class.getDeclaredMethod("categories",Player.class);categories.setAccessible(true);categories.invoke(guide,actor);
  var menu=actor.getOpenInventory().getTopInventory();int utilitySlot=-1;for(int i=0;i<menu.getSize();i++)if(name(menu.getItem(i)).toLowerCase(Locale.ROOT).contains("utilities"))utilitySlot=i;
  check(utilitySlot>=0,"Utilities selectable from /recipes category menu");
  var actions=(Map<Integer,Consumer<Player>>)field(menu.getHolder(),"actions");actions.get(utilitySlot).accept(actor);
  check(actor.getOpenInventory().getTopInventory()!=menu,"Utilities opens through category action");
  var rules=Class.forName("net.mandomc.gameplay.rules.SurvivalRules");check((boolean)rules.getMethod("armorMaterial",Material.class).invoke(null,Material.LEATHER_HORSE_ARMOR),"native horse armor remains subject to armor policy");
  actor.closeInventory();
 }
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "CombatTest");
        Object handle = playerClass.getConstructor(serverClass, levelClass, profileClass, infoClass)
                .newInstance(server, level, profile, infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object flow = flowClass.getField("SERVERBOUND").get(null);
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Object connection = connectionClass.getConstructor(flowClass).newInstance(flow);
        Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie = cookieClass.getMethod("createInitial", profileClass, boolean.class).invoke(null, profile, false);
        Class<?> listenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object packetListener = listenerClass.getConstructor(serverClass, connectionClass, playerClass, cookieClass)
                .newInstance(server, connection, handle, cookie);
        playerClass.getField("connection").set(handle, packetListener);
        // Isolated players have no network client to send its loaded acknowledgement.
        var loadTimer = listenerClass.getDeclaredField("clientLoadedTimeoutTimer");
        loadTimer.setAccessible(true);loadTimer.setInt(packetListener, 0);
        var waiting = listenerClass.getDeclaredField("waitingForRespawn");
        waiting.setAccessible(true);waiting.setBoolean(packetListener, false);
        playerClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
}
