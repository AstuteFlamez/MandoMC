package net.mandomc.integrations.paper;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.StonecutterView;

/** Pinned Paper 26.1.2 boundary: filter the same ordered recipe set on the wire and in the native menu. */
public final class StonecutterOptions {
    private final Method server, manager, allRecipes, properties, entries, entryRecipe, recipeHolder, holderId,
            identifier, selectInput, copy, menu, dataSet, broadcast, playerHandle, send;
    private final Constructor<?> inputSet, packet;
    private final Field visible, selected, connection;
    public StonecutterOptions() {
        try {
            var minecraftServer=Class.forName("net.minecraft.server.MinecraftServer");
            var recipeManager=Class.forName("net.minecraft.world.item.crafting.RecipeManager");
            var set=Class.forName("net.minecraft.world.item.crafting.SelectableRecipe$SingleInputSet");
            var entry=Class.forName("net.minecraft.world.item.crafting.SelectableRecipe$SingleInputEntry");
            var option=Class.forName("net.minecraft.world.item.crafting.SelectableRecipe");
            var holder=Class.forName("net.minecraft.world.item.crafting.RecipeHolder");
            var key=Class.forName("net.minecraft.resources.ResourceKey");
            var nativeItem=Class.forName("net.minecraft.world.item.ItemStack");
            var stonecutter=Class.forName("net.minecraft.world.inventory.StonecutterMenu");
            var baseMenu=Class.forName("net.minecraft.world.inventory.AbstractContainerMenu");
            var nativePlayer=Class.forName("net.minecraft.server.level.ServerPlayer");
            var listener=Class.forName("net.minecraft.server.network.ServerCommonPacketListenerImpl");
            server=Bukkit.getServer().getClass().getMethod("getServer");manager=minecraftServer.getMethod("getRecipeManager");
            allRecipes=recipeManager.getMethod("stonecutterRecipes");properties=recipeManager.getMethod("getSynchronizedItemProperties");
            entries=set.getMethod("entries");entryRecipe=entry.getMethod("recipe");recipeHolder=option.getMethod("recipe");
            holderId=holder.getMethod("id");identifier=key.getMethod("identifier");
            inputSet=set.getConstructor(List.class);selectInput=set.getMethod("selectByInput",nativeItem);
            packet=Class.forName("net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket").getConstructor(Map.class,set);
            copy=Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack").getMethod("asNMSCopy",ItemStack.class);
            menu=Class.forName("org.bukkit.craftbukkit.inventory.CraftInventoryView").getMethod("getHandle");
            visible=stonecutter.getDeclaredField("recipesForInput");visible.setAccessible(true);
            selected=stonecutter.getDeclaredField("selectedRecipeIndex");selected.setAccessible(true);
            dataSet=Class.forName("net.minecraft.world.inventory.DataSlot").getMethod("set",int.class);
            broadcast=baseMenu.getMethod("broadcastChanges");
            playerHandle=Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
            connection=nativePlayer.getField("connection");send=listener.getMethod("send",Class.forName("net.minecraft.network.protocol.Packet"));
        } catch(ReflectiveOperationException e) {throw new IllegalStateException("Stonecutter options require pinned Paper 26.1.2",e);}
    }
    private Object manager()throws ReflectiveOperationException{return manager.invoke(server.invoke(Bukkit.getServer()));}
    private void send(Player player,Object owner,Object options)throws ReflectiveOperationException {
        send.invoke(connection.get(playerHandle.invoke(player)),packet.newInstance(properties.invoke(owner),options));
    }
    public void filter(Player player,StonecutterView view,ItemStack input,Predicate<String> allowed) {
        try {
            var owner=manager();var keep=new ArrayList<Object>();
            for(var entry:(List<?>)entries.invoke(allRecipes.invoke(owner))) {
                var holder=(Optional<?>)recipeHolder.invoke(entryRecipe.invoke(entry));
                if(holder.isEmpty() || allowed.test(identifier.invoke(holderId.invoke(holder.get())).toString()))keep.add(entry);
            }
            var options=inputSet.newInstance(keep);var handle=menu.invoke(view);
            visible.set(handle,selectInput.invoke(options,copy.invoke(null,input)));
            dataSet.invoke(selected.get(handle),-1);
            view.getTopInventory().setResult(null);
            send(player,owner,options);broadcast.invoke(handle);
        } catch(ReflectiveOperationException e) {throw new IllegalStateException("Cannot synchronize filtered stonecutter options",e);}
    }
    public void restore(Player player) {
        try {var owner=manager();send(player,owner,allRecipes.invoke(owner));}
        catch(ReflectiveOperationException e) {throw new IllegalStateException("Cannot restore stonecutter recipe list",e);}
    }
}
