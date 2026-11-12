package net.mandomc.verification;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.arrival.ArrivalListener;
import net.mandomc.gameplay.tutorial.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Live mode reads book metadata only. Full command/item tests require the disposable-server marker. */
public final class HandbookProbe extends JavaPlugin {
    private MandoPlugin mando;
    private TutorialHandbook book;
    private Player actor;
    private Object handle;
    private Map<UUID,Object> online;
    private int checks;
    private boolean full;
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){fail(e);}},100);}
    @SuppressWarnings("unchecked") private void run()throws Exception {
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando!=null&&mando.isEnabled(),"Main plugin enabled");
        full=new java.io.File(getDataFolder(),"full-suite").exists();
        if(full)check(getServer().getPort()==25590,"Full suite is isolated");
        book=new TutorialHandbook(mando);check(book.definition().pages().size()==11,"Eleven approved feature pages");
        check(book.definition().pages().get(2).heading().equals("Jawa Shops"),"Page three renamed Jawa Shops");
        check(book.definition().pages().stream().filter(p->p.id().equals("jawas")).count()==1,"Only one Jawa page");
        check(book.definition().pages().stream().noneMatch(p->p.id().equals("armor_repairs")),"Armor page removed");
        check(book.definition().pages().get(8).heading().equals("Events"),"Combined events page nine");
        check(book.definition().pages().get(8).body().contains("KOTH")&&book.definition().pages().get(8).body().contains("Jabba"),"Both event guides retained");
        ItemStack item=book.create();check(item.getType()==Material.WRITTEN_BOOK&&book.isHandbook(item),"Native signed handbook item");
        BookMeta meta=(BookMeta)item.getItemMeta();check(meta.getPageCount()==11,"Written book retains all pages");
        check(meta.getTitle().equals("MandoMC Handbook")&&meta.getAuthor().equals("MandoMC"),"Book title and author");
        for(int i=1;i<=11;i++) {
            String page=PlainTextComponentSerializer.plainText().serialize(meta.page(i));
            String[] lines=page.split("\n",-1);check(lines.length<=14,"Native page height "+i);
            int titleLines=BookPageLayout.wrap(book.definition().pages().get(i-1).heading(),true).size();
            for(int line=0;line<lines.length;line++)check(BookPageLayout.width(lines[line],line<titleLines)<=114,"Native page width "+i+":"+line);
            check(!page.contains("<gray>")&&!page.contains("<gold>"),"No markup leaked into book text");
        }
        check(mando.getCommand("tutorial")!=null&&mando.getCommand("tutorial").getExecutor()!=null,"Tutorial command bound");
        check(mando.getCommand("spawn")!=null,"Spawn binding retained");
        if(!full){success();return;}
        World world=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));check(world!=null,"Batuu retained");
        actor=actor(world,world.getSpawnLocation());actor.getInventory().clear();
        var inventory=actor.getInventory();inventory.setItem(0,new ItemStack(Material.STONE,17));
        inventory.setHelmet(new ItemStack(Material.IRON_HELMET));inventory.setItemInOffHand(new ItemStack(Material.DIAMOND,3));
        check(actor.hasPermission("mandomc.arrival"),"Default player can use tutorial");
        var before=copy(inventory.getContents());
        command();check(handbooks()==1,"Command gives one physical handbook");
        check(inventory.getItem(0).equals(before[0]),"Held original item untouched");
        check(inventory.getHelmet().equals(before[39])&&inventory.getItemInOffHand().equals(before[40]),"Armor and offhand untouched");
        command();check(handbooks()==1,"Repeated command reuses existing book");
        int slot=0;while(!book.isHandbook(inventory.getItem(slot)))slot++;
        var oldBook=inventory.getItem(slot);var oldMeta=(BookMeta)oldBook.getItemMeta();
        oldMeta.pages(java.util.stream.IntStream.range(0,13).<net.kyori.adventure.text.Component>mapToObj(i->net.kyori.adventure.text.Component.text("Old page "+i)).toList());
        oldBook.setItemMeta(oldMeta);inventory.setItem(slot,oldBook);
        command();check(((BookMeta)inventory.getItem(slot).getItemMeta()).getPageCount()==11,"Issued thirteen-page book refreshes to eleven");
        check(PlainTextComponentSerializer.plainText().serialize(((BookMeta)inventory.getItem(slot).getItemMeta()).page(3)).startsWith("Jawa Shops"),"Refreshed existing book has Jawa Shops on page three");
        inventory.getItem(slot).setAmount(4);command();check(inventory.getItem(slot).getAmount()==4,"Existing handbook stack count preserved");
        inventory.clear();
        for(int i=0;i<36;i++){var stack=new ItemStack(Material.DIAMOND,64);stack.editMeta(m->m.displayName(net.kyori.adventure.text.Component.text("Owned "+UUID.randomUUID())));inventory.setItem(i,stack);}
        inventory.setHelmet(new ItemStack(Material.DIAMOND_HELMET));inventory.setItemInOffHand(new ItemStack(Material.EMERALD,12));
        before=copy(inventory.getContents());
        check(book.give(actor)==TutorialHandbook.Delivery.PREVIEW_ONLY,"Full inventory creates preview only");
        equal(before,inventory.getContents(),"Full inventory delivery cannot alter gear");
        command();equal(before,inventory.getContents(),"Opening full-inventory preview cannot alter gear");check(handbooks()==0,"Full inventory never invents a dropped/stored book");
        actor.setItemOnCursor(new ItemStack(Material.EMERALD,7));
        check(book.give(actor)==TutorialHandbook.Delivery.WAIT_FOR_ITEMS,"Held cursor defers delivery");
        command();check(actor.getItemOnCursor().getType()==Material.EMERALD&&actor.getItemOnCursor().getAmount()==7,"Cursor item preserved");
        equal(before,inventory.getContents(),"Cursor-full command leaves every owned slot untouched");actor.setItemOnCursor(null);
        inventory.setItem(12,book.create());check(book.give(actor)==TutorialHandbook.Delivery.UPDATED,"Existing book refreshes even when storage full");
        inventory.clear();inventory.setItemInOffHand(book.create());check(book.give(actor)==TutorialHandbook.Delivery.UPDATED,"Offhand handbook reused");
        check(handbooks()==1,"Offhand reuse adds no duplicate");
        inventory.clear();inventory.setItemInOffHand(new ItemStack(Material.IRON_INGOT,9));
        if(actor.getOpenInventory().getTopInventory() instanceof CraftingInventory crafting) {
            crafting.setMatrix(new ItemStack[]{new ItemStack(Material.DIAMOND),null,null,null});
            check(book.give(actor)==TutorialHandbook.Delivery.WAIT_FOR_ITEMS,"Crafting ingredients defer delivery");
            check(crafting.getMatrix()[0].getType()==Material.DIAMOND,"Crafting ingredient stays in place");crafting.setMatrix(new ItemStack[4]);
        }
        var arrivalField=MandoPlugin.class.getDeclaredField("arrival");arrivalField.setAccessible(true);var arrival=(ArrivalListener)arrivalField.get(mando);
        var seen=new NamespacedKey("mandomc","arrival_tutorial_v1");actor.getPersistentDataContainer().remove(seen);
        arrival.onPack(new PlayerResourcePackStatusEvent(actor,UUID.randomUUID(),PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        check(actor.getPersistentDataContainer().has(seen,PersistentDataType.BYTE),"First-join welcome marker retained");
        check(handbooks()==0,"First-join welcome does not silently award a book");
        arrival.onPack(new PlayerResourcePackStatusEvent(actor,UUID.randomUUID(),PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        check(handbooks()==0,"Subsequent pack load does not change handbook delivery");
        check(inventory.getItemInOffHand().getAmount()==9,"First-join chat preserves offhand gear");
        kitChecks(arrival);
    }
    private final NamespacedKey granted = new NamespacedKey("mandomc", "starter_kit_v1");
    private final NamespacedKey pending = new NamespacedKey("mandomc", "starter_kit_pending_v1");
    private void kitChecks(ArrivalListener arrival) {
        var kit = mando.starterKit();
        var inventory = actor.getInventory();
        inventory.clear();
        actor.getPersistentDataContainer().remove(granted);
        actor.getPersistentDataContainer().remove(pending);
        actor.getPersistentDataContainer().remove(new NamespacedKey("mandomc", "arrival_tutorial_v1"));
        check(!actor.hasPlayedBefore(), "Fixture is a native new player");
        kit.join(new org.bukkit.event.player.PlayerJoinEvent(actor, net.kyori.adventure.text.Component.empty()));
        check(actor.getPersistentDataContainer().has(pending), "First join marks kit pending before scheduled delivery");
        later(() -> {
            check(handbooks() == 1, "First join grants exactly one handbook with kit");
            check(!actor.getPersistentDataContainer().has(new NamespacedKey("mandomc", "arrival_tutorial_v1")), "Kit does not consume pack-gated welcome marker");
            arrival.onPack(new PlayerResourcePackStatusEvent(actor, UUID.randomUUID(), PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
            check(handbooks() == 1, "Pack success after kit gives no duplicate book");
            check(actor.getPersistentDataContainer().has(granted) && !actor.getPersistentDataContainer().has(pending), "Complete kit commits once-only marker");
            check(inventory.contains(Material.STONE_PICKAXE) && inventory.contains(Material.STONE_AXE)
                    && inventory.contains(Material.STONE_SHOVEL) && inventory.contains(Material.STONE_HOE)
                    && inventory.contains(Material.COOKED_BEEF, 64), "Original material kit retained");
            for (String name : List.of("robes_helmet", "robes_chestplate", "robes_leggings", "robes_boots", "e11", "standard_plasma_cells")) {
                check(Arrays.stream(inventory.getStorageContents()).filter(Objects::nonNull)
                        .anyMatch(i -> i.isSimilar(mando.items().create(name))), "Original generated kit item " + name);
            }
            var before = copy(inventory.getContents());
            kit.join(new org.bukkit.event.player.PlayerJoinEvent(returning(), net.kyori.adventure.text.Component.empty()));
            later(() -> {
                equal(before, inventory.getContents(), "Reconnect never refills kit");
                inventory.clear(); // Normal full-loot death has removed the original gear/book.
                arrival.respawn(new org.bukkit.event.player.PlayerRespawnEvent(actor, actor.getLocation(), false,
                        false, false, org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH));
                check(!kit.grant(actor), "Death does not reset kit eligibility");
                kit.join(new org.bukkit.event.player.PlayerJoinEvent(returning(), net.kyori.adventure.text.Component.empty()));
                later(() -> {
                    check(handbooks() == 0 && inventory.isEmpty(), "Respawn/reconnect after losing book grants no extra kit");
                    actor.getPersistentDataContainer().remove(granted);
                    kit.join(new org.bukkit.event.player.PlayerJoinEvent(returning(), net.kyori.adventure.text.Component.empty()));
                    check(!actor.getPersistentDataContainer().has(pending), "Established unmarked player is not treated as first join");
                    // Eleven free slots fit the old kit, but not the new twelve-item kit.
                    for (int i = 0; i < 25; i++) inventory.setItem(i, new ItemStack(Material.DIAMOND, 64));
                    inventory.setHelmet(new ItemStack(Material.IRON_HELMET));
                    inventory.setItemInOffHand(new ItemStack(Material.EMERALD, 7));
                    var fullBefore = copy(inventory.getContents());
                    kit.join(new org.bukkit.event.player.PlayerJoinEvent(actor, net.kyori.adventure.text.Component.empty()));
                    later(() -> {
                        equal(fullBefore, inventory.getContents(), "Insufficient room for handbook defers entire kit atomically");
                        check(!actor.getPersistentDataContainer().has(granted) && actor.getPersistentDataContainer().has(pending), "Failed first join retains pending retry");
                        inventory.setItem(24, null);
                        kit.join(new org.bukkit.event.player.PlayerJoinEvent(returning(), net.kyori.adventure.text.Component.empty()));
                        later(() -> {
                            check(handbooks() == 1 && actor.getPersistentDataContainer().has(granted), "Pending reconnect grants complete kit after freeing slot");
                            check(inventory.getHelmet().getType() == Material.IRON_HELMET && inventory.getItemInOffHand().getAmount() == 7, "Kit preserves armor/offhand");
                            existingBookKit(false);
                            existingBookKit(true);
                            try { success(); } catch (Exception error) { throw new RuntimeException(error); }
                        });
                    });
                });
            });
        });
    }
    private Player returning() {
        // Native inventory/PDC/server player with the persisted returning-player eligibility flag.
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> method.getName().equals("hasPlayedBefore") ? true : method.invoke(actor, args));
    }
    private void existingBookKit(boolean offhand) {
        var inventory = actor.getInventory();
        inventory.clear();actor.getPersistentDataContainer().remove(granted);
        var old = book.create();old.setAmount(3);
        var meta = (BookMeta) old.getItemMeta();meta.pages(net.kyori.adventure.text.Component.text("Old handbook"));old.setItemMeta(meta);
        if (offhand) inventory.setItemInOffHand(old); else inventory.setItem(0, old);
        for (int i = offhand ? 0 : 1; i < 36; i++) inventory.setItem(i, new ItemStack(Material.DIAMOND, 64));
        var before = copy(inventory.getContents());
        check(!mando.starterKit().grant(actor), "Existing book/full storage defers kit");
        equal(before, inventory.getContents(), "Failed kit does not refresh existing handbook or overwrite gear");
        for (int i = 25; i < 36; i++) inventory.setItem(i, null);
        check(mando.starterKit().grant(actor), "Existing book leaves eleven slots sufficient for kit");
        check(handbooks() == 3, "Existing handbook stack reused without duplicates");
        var current = offhand ? inventory.getItemInOffHand() : inventory.getItem(0);
        check(((BookMeta) current.getItemMeta()).getPageCount() == 11, "Existing handbook refreshed to current eleven pages");
        command();check(handbooks() == 3, "Tutorial still reuses starter-kit handbook stack");
    }
    private void later(Runnable run) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { run.run(); } catch (Throwable error) { fail(error); } }, 2);
    }
    private void command(){check(mando.getCommand("tutorial").execute(actor,"tutorial",new String[0]),"Actual tutorial command executes");}
    private int handbooks(){int n=0;for(var item:actor.getInventory().getContents())if(book.isHandbook(item))n+=item.getAmount();return n;}
    private ItemStack[] copy(ItemStack[] content){return Arrays.stream(content).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);}
    private void equal(ItemStack[] a,ItemStack[] b,String why){check(a.length==b.length,why+" length");for(int i=0;i<a.length;i++)check(Objects.equals(a[i],b[i]),why+" slot"+i);}
    private void success()throws Exception {
        String result="HANDBOOK PASSED: "+checks+" native assertions;11 feature pages, command/inventory/first-join/starter-kit checks\n";
        Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),result);getLogger().info(result);cleanup();
        if(getServer().getPort()==25590)Bukkit.shutdown();
    }
    private void fail(Throwable error){
        getLogger().log(java.util.logging.Level.SEVERE,"HANDBOOK FAILED",error);
        try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());}catch(Exception ignored){}
        cleanup();if(getServer().getPort()==25590)Bukkit.shutdown();
    }
    @SuppressWarnings("unchecked") private Player actor(World world,Location at)throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),
                playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object list=server.getClass().getMethod("getPlayerList").invoke(server);
        var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);
        Object level=world.getClass().getMethod("getHandle").invoke(world);
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"HandbookCheck");
        handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class.forName("net.minecraft.world.entity.Entity").getField("valid").setBoolean(handle,true);
        Class<?> flow=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection");
        Object connection=connectionClass.getConstructor(flow).newInstance(flow.getField("SERVERBOUND").get(null));
        Class<?> cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie");Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Class<?> listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");Object listener=listenerClass.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);
        playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);
        var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,at.getX(),at.getY(),at.getZ());
        var player=(Player)playerClass.getMethod("getBukkitEntity").invoke(handle);online.put(player.getUniqueId(),handle);
        level.getClass().getMethod("addNewPlayer",playerClass).invoke(level,handle);player.setGameMode(GameMode.SURVIVAL);player.setGravity(false);return player;
    }
    @SuppressWarnings("unchecked") private void cleanup(){
        if(actor==null)return;
        try {
            actor.setItemOnCursor(null);
            if(actor.getOpenInventory().getTopInventory() instanceof CraftingInventory c)c.setMatrix(new ItemStack[4]);
            actor.closeInventory();online.remove(actor.getUniqueId());
            for(var w:Bukkit.getWorlds()){Object level=w.getClass().getMethod("getHandle").invoke(w);((List<Object>)level.getClass().getMethod("players").invoke(level)).remove(handle);}
            handle.getClass().getMethod("discard").invoke(handle);
        }catch(Exception error){getLogger().warning("Book fixture cleanup: "+error);}
        actor=null;
    }
    @Override public void onDisable(){cleanup();}
}
