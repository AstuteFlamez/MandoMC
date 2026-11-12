package net.mandomc.verification;

import java.util.*;
import java.lang.reflect.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.jawa.JawaSellInventory;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import com.ticxo.modelengine.api.ModelEngineAPI;
import io.lumine.mythic.bukkit.MythicBukkit;

/** Native tray/cursor conservation, batch payouts, metadata-safe returns and repaired gray appearance. */
public final class SellGuiProbe extends JavaPlugin {
    private final List<Player> actors=new ArrayList<>();private final Map<UUID,Object> handles=new HashMap<>();
    private List<Object> worldPlayers;private Map<UUID,Object> online;private MandoPlugin mando;private World world;private Player buyer;
    private LivingEntity gray;private Chunk chunk;private JawaSellInventory trays;private ItemStack treasure;private long balance;private int checks;
    private final NamespacedKey journal=new NamespacedKey("mandomc","jawa_sell_custody_v1");
    @Override public void onEnable(){later(120,this::begin);}
    @SuppressWarnings("unchecked") private void begin()throws Exception {
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando.jawas()!=null,"Jawa service available");
        world=Bukkit.getWorld(NamespacedKey.minecraft("devworld"));chunk=world.getChunkAt(4,4);chunk.addPluginChunkTicket(this);
        var level=world.getClass().getMethod("getHandle").invoke(world);worldPlayers=(List<Object>)level.getClass().getMethod("players").invoke(level);
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);
        buyer=nativeTestPlayer(world,new Location(world,67.5,200,67.5));actors.add(buyer);buyer.setGameMode(GameMode.SURVIVAL);var handle=buyer.getClass().getMethod("getHandle").invoke(buyer);handles.put(buyer.getUniqueId(),handle);worldPlayers.add(handle);online.put(buyer.getUniqueId(),handle);
        getLogger().info("BUYER TEST ACTOR "+buyer.getUniqueId());
        gray=(LivingEntity)MythicBukkit.inst().getAPIHelper().spawnMythicMob("JawaGray",new Location(world,67.5,200,69.5),1);gray.setGravity(false);gray.setAI(false);
        var f=mando.jawas().getClass().getDeclaredField("buyer");f.setAccessible(true);trays=(JawaSellInventory)f.get(mando.jawas());
        treasure=new ItemStack(Material.DIAMOND,4);var meta=treasure.getItemMeta();meta.displayName(net.kyori.adventure.text.Component.text("Keep this metadata"));meta.lore(List.of(net.kyori.adventure.text.Component.text("Not for sale")));treasure.setItemMeta(meta);
        later(20,()->{open();mixed();});
    }
    private void open(){mando.jawas().open(buyer,gray);check(top().getHolder() instanceof JawaSellInventory.Tray,"gray buyer opens its deposit inventory");}
    private Inventory top(){return buyer.getOpenInventory().getTopInventory();}
    private void mixed(){
        check(top().getSize()==36,"buyer tray uses 27 input slots and one control row");
        for(int i=0;i<27;i++)check(top().getItem(i)==null,"input slot starts empty "+i);
        check(new NamespacedKey("mandomc","gui/info").equals(top().getItem(27).getItemMeta().getItemModel()),"original gold info artwork uses invisible item with real tooltip");
        check(top().getItem(27).getType()==Material.BOOK&&top().getItem(27).getItemMeta().lore().size()>=8,"info item explains placement, sale, returns and cancellation");
        check(top().getItem(28)==null,"decorative panes do not obscure custom GUI artwork");
        var protectedClick=new InventoryClickEvent(buyer.getOpenInventory(),InventoryType.SlotType.CONTAINER,28,ClickType.LEFT,InventoryAction.PLACE_ALL);
        buyer.setItemOnCursor(new ItemStack(Material.STONE,5));Bukkit.getPluginManager().callEvent(protectedClick);
        check(protectedClick.isCancelled()&&top().getItem(28)==null&&buyer.getItemOnCursor().getAmount()==5,"empty decorative control slots remain protected without consuming cursor");buyer.setItemOnCursor(null);
        check(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(buyer.getOpenInventory().title()).contains("mandomc:gui/sell"),"actual open title uses isolated custom font");
        buyer.getInventory().setItem(0,new ItemStack(Material.SAND,32));var hide=mando.items().create("bantha_hide");hide.setAmount(3);buyer.getInventory().setItem(1,hide);buyer.getInventory().setItem(2,treasure.clone());
        shift(0);shift(1);shift(2);check(countTop(Material.SAND)==32&&countTop(Material.DIAMOND)==4,"shift-click accepts any item into the input area");
        check(buyer.getPersistentDataContainer().has(journal),"deposit is held in durable player custody");
        click(0,ClickType.RIGHT);check(buyer.getItemOnCursor().getAmount()==16,"right-click splits a deposited stack");click(3,ClickType.LEFT);
        click(1,ClickType.RIGHT);check(buyer.getItemOnCursor().getAmount()==2,"custom item split preserves quantity");
        var drag=new InventoryDragEvent(buyer.getOpenInventory(),new ItemStack(Material.AIR),buyer.getItemOnCursor().clone(),false,Map.of(4,one(hide),5,one(hide)));Bukkit.getPluginManager().callEvent(drag);
        check(drag.isCancelled()&&buyer.getItemOnCursor().isEmpty()&&countTopCustom("bantha_hide")==3,"drag distributes exact custom stacks without duplication");
        var title=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(top().getItem(31).getItemMeta().displayName());
        check(title.contains("4.00"),"quote totals partial bundles across input slots");
        click(2,ClickType.LEFT);var held=buyer.getItemOnCursor().clone();
        var blocked=new InventoryDragEvent(buyer.getOpenInventory(),new ItemStack(Material.AIR),held,false,Map.of(27,held));Bukkit.getPluginManager().callEvent(blocked);
        check(blocked.isCancelled()&&buyer.getItemOnCursor().isSimilar(held)&&top().getItem(27).getType()==Material.BOOK,"drag cannot overwrite the info item or consume cursor items");click(2,ClickType.LEFT);
        click(31,ClickType.LEFT);click(31,ClickType.LEFT);
        later(35,this::mixedDone);
    }
    private void mixedDone(){
        balance=400;check(mando.credits().ledger().balance(buyer.getUniqueId())==balance,"mixed tray pays exactly once for accepted quantities");
        check(count(Material.SAND)==0&&countCustom("bantha_hide")==0,"only accepted stacks leave player custody");
        check(count(Material.DIAMOND)==4&&buyer.getInventory().containsAtLeast(treasure,4),"unsellable items return with exact name and lore");
        check(!buyer.getPersistentDataContainer().has(journal),"finished mixed tray clears custody receipt");
        check(mando.credits().ledger().auctions().claims().values().stream().noneMatch(c->c.owner().equals(buyer.getUniqueId())),"sale creates no dangling item claims");
        open();shift(slot(Material.DIAMOND));click(31,ClickType.LEFT);later(10,()->{
            check(count(Material.DIAMOND)==4&&mando.credits().ledger().balance(buyer.getUniqueId())==balance,"unsupported-only tray returns everything and pays nothing");
            partial();
        });
    }
    private void partial(){
        buyer.getInventory().setItem(0,new ItemStack(Material.SAND,1));open();shift(0);click(31,ClickType.LEFT);
        later(30,()->{balance+=3;check(mando.credits().ledger().balance(buyer.getUniqueId())==balance,"one item can sell at the proportional rate with integer-cent rounding");cancel();});
    }
    private void cancel(){
        buyer.getInventory().clear();var hide=mando.items().create("bantha_hide");hide.setAmount(5);buyer.getInventory().setItem(0,hide);buyer.getInventory().setItem(1,treasure.clone());
        open();shift(0);shift(1);click(0,ClickType.LEFT);check(buyer.getItemOnCursor().getAmount()==5,"cursor can withdraw a deposited stack");buyer.closeInventory();
        check(countCustom("bantha_hide")==5&&count(Material.DIAMOND)==4&&buyer.getItemOnCursor().isEmpty(),"closing returns both tray contents and cursor exactly once");
        check(mando.credits().ledger().balance(buyer.getUniqueId())==balance,"closing without confirmation does not sell");
        overflow();
    }
    private void overflow(){
        buyer.getInventory().clear();buyer.getInventory().setItem(0,treasure.clone());open();shift(0);
        for(int i=0;i<36;i++)buyer.getInventory().setItem(i,new ItemStack(Material.STONE,64));buyer.closeInventory();
        check(buyer.getPersistentDataContainer().has(journal)&&count(Material.DIAMOND)==0,"full inventory retains an unsellable return in saved custody");
        mando.jawas().open(buyer,gray);check(!(top().getHolder() instanceof JawaSellInventory.Tray)&&buyer.getPersistentDataContainer().has(journal),"full inventory cannot overwrite pending returns with a new tray");
        buyer.getInventory().setItem(0,null);open();check(count(Material.DIAMOND)==4&&buyer.getInventory().containsAtLeast(treasure,4),"making room and reopening recovers original metadata");buyer.closeInventory();
        recovery();
    }
    private void recovery(){
        buyer.getInventory().clear();buyer.getInventory().setItem(0,treasure.clone());var hide=mando.items().create("bantha_hide");hide.setAmount(3);buyer.getInventory().setItem(1,hide);
        open();shift(0);click(raw(1),ClickType.LEFT);String saved=buyer.getPersistentDataContainer().get(journal,PersistentDataType.STRING);check(saved!=null&&!buyer.getItemOnCursor().isEmpty(),"journal includes deposited items and cursor custody");buyer.closeInventory();
        // Reconstruct the supported player snapshot after a crash: inventory excludes tray/cursor, journal retains both.
        buyer.getInventory().clear();buyer.getPersistentDataContainer().set(journal,PersistentDataType.STRING,saved);buyer.saveData();trays.recover(buyer);
        check(count(Material.DIAMOND)==4&&countCustom("bantha_hide")==3,"saved tray and cursor recover after restart");trays.recover(buyer);check(count(Material.DIAMOND)==4&&countCustom("bantha_hide")==3,"recovery cannot return a stack twice");
        getLogger().info("SELL GUI PROBE PASSED: "+checks+" assertions");cleanup();Bukkit.shutdown();
    }
    private ItemStack one(ItemStack item){var one=item.clone();one.setAmount(1);return one;}
    private int slot(Material material){for(int i=0;i<36;i++)if(buyer.getInventory().getItem(i)!=null&&buyer.getInventory().getItem(i).getType()==material)return i;throw new AssertionError("Missing material "+material);}
    private int raw(int slot){for(int i=36;i<72;i++)if(buyer.getOpenInventory().convertSlot(i)==slot)return i;throw new AssertionError("Missing storage slot");}
    private void shift(int slot){click(raw(slot),ClickType.SHIFT_LEFT);}
    private void click(int raw,ClickType kind){var event=new InventoryClickEvent(buyer.getOpenInventory(),InventoryType.SlotType.CONTAINER,raw,kind,kind==ClickType.SHIFT_LEFT?InventoryAction.MOVE_TO_OTHER_INVENTORY:InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"tray controls native transfer paths");}
    private int count(Material material){return Arrays.stream(buyer.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->i.getType()==material).mapToInt(ItemStack::getAmount).sum();}
    private int countCustom(String id){return Arrays.stream(buyer.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->id.equals(mando.items().identify(i))).mapToInt(ItemStack::getAmount).sum();}
    private int countTop(Material material){int n=0;for(int i=0;i<27;i++){var item=top().getItem(i);if(item!=null&&item.getType()==material)n+=item.getAmount();}return n;}
    private int countTopCustom(String id){int n=0;for(int i=0;i<27;i++){var item=top().getItem(i);if(item!=null&&id.equals(mando.items().identify(item)))n+=item.getAmount();}return n;}
    private interface Check{void run()throws Exception;}
    private void later(long ticks,Check action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"SELL GUI PROBE FAILED",error);cleanup();Bukkit.shutdown();}},ticks);}
    private void check(boolean value,String text){if(!value)throw new AssertionError(text);checks++;getLogger().info("PASS "+text);}
    private void cleanup(){Bukkit.getScheduler().cancelTasks(this);if(buyer!=null){buyer.closeInventory();buyer.getInventory().clear();buyer.setItemOnCursor(null);}if(gray!=null)gray.remove();if(worldPlayers!=null)handles.values().forEach(worldPlayers::remove);if(online!=null)handles.keySet().forEach(online::remove);if(chunk!=null)chunk.removePluginChunkTicket(this);}
    @Override public void onDisable(){cleanup();}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "Trade" + actors.size());
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
    }}
