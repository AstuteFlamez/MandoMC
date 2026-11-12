package net.mandomc.verification;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.inventory.ContainerInput;
public final class MaterialFixesProbe extends JavaPlugin {
 private int checks;
 @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();getLogger().info("MATERIAL FIXES PASSED: "+checks+" assertions");}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"MATERIAL FIXES FAILED",e);}finally{Bukkit.shutdown();}},100);}
 private void check(boolean b,String message){if(!b)throw new IllegalStateException(message);checks++;}
 private void run() throws Exception {
 var mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");var items=mando.items();var world=Bukkit.getWorlds().getFirst();var player=nativeTestPlayer(world,new Location(world,1000,280,1000));
 var hide=items.create("bantha_hide");check(hide.getType()==Material.LEATHER,"hide native leather");
 var raw=new ItemStack(Material.LEATHER,9);check(BanthaHideMaterials.refresh(items,raw)&&raw.getAmount()==9&&"bantha_hide".equals(items.identify(raw)),"raw conversion retains quantity");
 var named=new ItemStack(Material.LEATHER,4);var nm=named.getItemMeta();nm.displayName(Component.text("Foreign hide"));named.setItemMeta(nm);var saved=named.clone();check(!BanthaHideMaterials.refresh(items,named)&&named.equals(saved),"foreign hide unchanged");
 var legacy=hide.clone();legacy.setType(Material.ECHO_SHARD);legacy.setAmount(7);var lm=legacy.getItemMeta();lm.displayName(Component.text("My hide"));lm.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING,2,true);lm.getPersistentDataContainer().set(new NamespacedKey("probe","keep"),PersistentDataType.STRING,"payload");var lore=new ArrayList<>(lm.lore());lore.add(Component.text("My note"));lm.lore(lore);legacy.setItemMeta(lm);var expected=legacy.clone();expected.setType(Material.LEATHER);check(BanthaHideMaterials.refresh(items,legacy)&&legacy.equals(expected),"legacy conversion preserves complete metadata");check(!BanthaHideMaterials.refresh(items,legacy),"hide idempotent");
 var matrix=new ItemStack[9];matrix[0]=hide;matrix[1]=new ItemStack(Material.PAPER);matrix[2]=new ItemStack(Material.PAPER);matrix[3]=new ItemStack(Material.PAPER);check(Bukkit.craftItemResult(matrix,world,player).getResult().getType()==Material.BOOK,"native book crafting with hide");
 Arrays.fill(matrix,new ItemStack(Material.STICK));matrix[4]=hide;check(Bukkit.craftItemResult(matrix,world,player).getResult().getType()==Material.ITEM_FRAME,"native frame crafting with hide");
 var chest=Bukkit.createInventory(null,9);chest.setItem(0,new ItemStack(Material.LEATHER,11));Bukkit.getPluginManager().callEvent(new org.bukkit.event.inventory.InventoryOpenEvent(player.openInventory(chest)));check("bantha_hide".equals(items.identify(chest.getItem(0)))&&chest.getItem(0).getAmount()==11,"chest open normalization");
 var drop=world.dropItem(player.getLocation(),new ItemStack(Material.LEATHER,5));check("bantha_hide".equals(items.identify(drop.getItemStack()))&&drop.getItemStack().getAmount()==5,"spawn drop count preserved");drop.remove();
 var electrum=items.create("electrum");var em=electrum.getItemMeta();em.displayName(Component.text("Personal metal"));electrum.setItemMeta(em);check(new MetalMaterials(items).canonical(electrum).equals(electrum),"owned metal custom metadata retained");
 var badgeItem=items.create("tibanna_gas_vial");var bm=badgeItem.getItemMeta();bm.displayName(Component.text("Custom vial"));var bl=new ArrayList<>(bm.lore());bl.set(0,net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize("<white>Ē"+net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(bm.lore().getFirst()).substring(1)).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));bl.add(Component.text("Custom note"));bm.lore(bl);badgeItem.setItemMeta(bm);var expectedBadge=badgeItem.clone();var eb=expectedBadge.getItemMeta();var el=new ArrayList<>(eb.lore());el.set(0,items.create("tibanna_gas_vial").getItemMeta().lore().getFirst());eb.lore(el);expectedBadge.setItemMeta(eb);check(items.refreshMaterialBadge(badgeItem)&&badgeItem.equals(expectedBadge),"rarity row only refresh");
 var format=Class.forName("net.mandomc.gameplay.ilum.ParkourService").getDeclaredMethod("format",long.class);format.setAccessible(true);check("0:01.2".equals(format.invoke(null,1234L)),"actual Ilum one decimal");check("1:00.0".equals(format.invoke(null,59999L)),"Ilum minute carry");
 var server=((CraftServer)Bukkit.getServer()).getServer();var manager=server.getRecipeManager();var packet=new ClientboundUpdateRecipesPacket(manager.getSynchronizedItemProperties(),manager.getSynchronizedStonecutterRecipes());var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess());ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(buffer,packet);var decoded=ClientboundUpdateRecipesPacket.STREAM_CODEC.decode(buffer);var context=SlotDisplayContext.fromLevel(server.overworld());
 var cp=((CraftPlayer)player).getHandle();cp.valid=true;
 for(String id:List.of("vader","obiwan","quigon","windu","ventress_yellow","ahsoka_tano_white_lightsaber")){
  var input=CraftItemStack.asNMSCopy(items.create(id));var actual=manager.stonecutterRecipes().selectByInput(input).entries();var visible=decoded.stonecutterRecipes().selectByInput(input).entries();check(actual.size()==visible.size()&&!actual.isEmpty(),"client/server size "+id);
  int valid=0;var menu=new StonecutterMenu(123,cp.getInventory());cp.containerMenu=menu;menu.container.setItem(0,input.copy());
  for(int n=0;n<actual.size();n++){
   var a=CraftItemStack.asBukkitCopy(actual.get(n).recipe().optionDisplay().resolveForFirstStack(context));var b=CraftItemStack.asBukkitCopy(visible.get(n).recipe().optionDisplay().resolveForFirstStack(context));check(a.isSimilar(b),"wire index equality "+id+" "+n);
   menu.clickMenuButton(cp,n);var result=CraftItemStack.asBukkitCopy(menu.getSlot(1).getItem());
   if(!result.isEmpty()){check(result.isSimilar(b),"selected output equals display "+id+" "+n);valid++;}else check(!menu.getSlot(0).getItem().isEmpty(),"rejection preserves input");
  }
  check(valid>0,"family retains valid styles "+id);
  // Select a valid entry and exercise native repeated shift/output consumption.
  int selected=-1;for(int n=0;n<actual.size();n++){menu.clickMenuButton(cp,n);if(!menu.getSlot(1).getItem().isEmpty()){selected=n;break;}}
  var output=menu.getSlot(1).getItem().copy();cp.getInventory().clearContent();for(int s=0;s<36;s++)cp.getInventory().setItem(s,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,64));
  var blocked=menu.quickMoveStack(cp,1);check(blocked.isEmpty()&&!menu.getSlot(0).getItem().isEmpty(),"full inventory no consumption "+id);
  cp.getInventory().clearContent();menu.quickMoveStack(cp,1);check(menu.getSlot(0).getItem().isEmpty(),"successful shift consumes one "+id);int count=0;for(int s=0;s<36;s++)if(net.minecraft.world.item.ItemStack.isSameItemSameComponents(cp.getInventory().getItem(s),output))count+=cp.getInventory().getItem(s).getCount();check(count==output.getCount(),"exact output count "+id);menu.quickMoveStack(cp,1);int again=0;for(int s=0;s<36;s++)if(net.minecraft.world.item.ItemStack.isSameItemSameComponents(cp.getInventory().getItem(s),output))again+=cp.getInventory().getItem(s).getCount();check(again==count,"repeat empty shift no dupe "+id);
  menu.container.setItem(0,input.copy());menu.clickMenuButton(cp,selected);menu.checkReachable=false;cp.getInventory().clearContent();menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
  cp.connection.handleContainerClick(new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),(short)1,(byte)0,ContainerInput.PICKUP,new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<net.minecraft.network.HashedStack>(),net.minecraft.network.HashedStack.EMPTY));
  check(menu.getSlot(0).getItem().isEmpty()&&net.minecraft.world.item.ItemStack.isSameItemSameComponents(menu.getCarried(),output),"actual packet output correct "+id);menu.setCarried(net.minecraft.world.item.ItemStack.EMPTY);
  var worn=items.create(id);var wm=(org.bukkit.inventory.meta.Damageable)worn.getItemMeta();wm.setDamage(10);worn.setItemMeta(wm);menu.container.setItem(0,CraftItemStack.asNMSCopy(worn));menu.clickMenuButton(cp,selected);check(menu.getSlot(1).getItem().isEmpty(),"worn item denied "+id);
  // Simulate a stale or spoofed result after eligibility changes, then use the real network take path.
  menu.getSlot(1).set(output.copy());cp.connection.handleContainerClick(new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(menu.containerId,menu.getStateId(),(short)1,(byte)0,ContainerInput.PICKUP,new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<net.minecraft.network.HashedStack>(),net.minecraft.network.HashedStack.EMPTY));check(menu.getCarried().isEmpty()&&!menu.getSlot(0).getItem().isEmpty(),"take guard denies stale worn output "+id);
 }
 buffer.release();var vanilla=new StonecutterMenu(124,cp.getInventory());cp.containerMenu=vanilla;vanilla.container.setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,3));vanilla.clickMenuButton(cp,0);check(!vanilla.getSlot(1).getItem().isEmpty(),"vanilla selection retained");cp.getInventory().clearContent();for(int n=0;n<3;n++)vanilla.quickMoveStack(cp,1);check(vanilla.getSlot(0).getItem().isEmpty(),"vanilla shift consumes three normally");
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
