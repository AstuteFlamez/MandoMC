package net.mandomc.verification;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.credits.CreditLedger;
import net.mandomc.gameplay.auction.AuctionBook;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable native command/menu, durable escrow and item-recovery checks. */
public final class AuctionProbe extends JavaPlugin {
    private final List<Player> actors=new ArrayList<>();private final Map<UUID,Object> handles=new HashMap<>();
    private List<Object> worldPlayers;private Map<UUID,Object> online;private MandoPlugin mando;private World world;
    private Player seller,buyer;private ItemStack stock,saber;private InventoryView confirmation;private int checks;private UUID claim;
    @Override public void onEnable(){later(80,this::begin);}
    @SuppressWarnings("unchecked") private void begin()throws Exception{
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando.isEnabled(),"combined native plugin enables");check(Bukkit.getPluginManager().getPlugin("CrazyAuctions")==null,"no external auction plugin installed");
        world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));var level=world.getClass().getMethod("getHandle").invoke(world);worldPlayers=(List<Object>)level.getClass().getMethod("players").invoke(level);
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);
        seller=actor(new Location(world,8000,280,8000));buyer=actor(new Location(world,8010,280,8000));
        check(!seller.isOp()&&seller.hasPermission("mandomc.auction"),"ordinary players can use native market");
        mando.credits().auctionTask(()->{mando.credits().ledger().adjust(buyer.getUniqueId(),"set",100000);return null;},(v,e)->{if(e!=null)fail(e);else later(1,this::list);});
    }
    private Player actor(Location point)throws Exception{var p=nativeTestPlayer(world,point);actors.add(p);p.setGameMode(GameMode.SURVIVAL);var h=p.getClass().getMethod("getHandle").invoke(p);handles.put(p.getUniqueId(),h);worldPlayers.add(h);online.put(p.getUniqueId(),h);return p;}
    private void command(Player player,String...args){Bukkit.getPluginCommand("auctionhouse").execute(player,"ah",args);}
    private void list(){stock=mando.items().create("durasteel_scrap");stock.setAmount(2);seller.getInventory().setItemInMainHand(stock.clone());command(seller,"sell","100");later(20,this::buy);}
    private void buy(){
        check(seller.getInventory().getItemInMainHand().isEmpty(),"held stack moved to durable listing escrow");check(mando.credits().ledger().auctions().listings().size()==1,"one native listing exists");
        for(int i=0;i<36;i++)buyer.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        command(buyer);var market=buyer.getOpenInventory().getTopInventory();int slot=find(market,"durasteel_scrap");check(slot>=0,"custom item visible in native market");check(market.getItem(44).getType()==Material.GRAY_STAINED_GLASS_PANE&&market.getItem(44).getItemMeta().isHideTooltip(),"native filler uses hidden tooltip");
        click(buyer,slot);confirmation=buyer.getOpenInventory();check(confirmation.getTopInventory().getSize()==9,"purchase requires native confirmation");click(buyer,0);later(20,this::collect);
    }
    private void collect(){
        check(mando.credits().ledger().balance(buyer.getUniqueId())==90000&&mando.credits().ledger().balance(seller.getUniqueId())==10000,"atomic sale uses existing balances without fees");
        check(mando.credits().ledger().auctions().listings().isEmpty()&&mando.credits().ledger().auctions().claims().size()==1,"full inventory preserves purchased item in claim escrow");
        var stale=new InventoryClickEvent(confirmation,InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(stale);later(10,()->{
            check(mando.credits().ledger().balance(buyer.getUniqueId())==90000,"stale confirmation cannot spend twice");buyer.getInventory().clear();command(buyer,"collect");later(10,()->{click(buyer,find(buyer.getOpenInventory().getTopInventory(),"durasteel_scrap"));later(20,this::cancel);});
        });
    }
    private void cancel()throws Exception{
        check(count(buyer,"durasteel_scrap")==2&&mando.credits().ledger().auctions().claims().isEmpty(),"claim delivers purchased stack exactly once");
        var purchased=Arrays.stream(buyer.getInventory().getContents()).filter(Objects::nonNull).filter(i->"durasteel_scrap".equals(mando.items().identify(i))).findFirst().orElseThrow();check(purchased.isSimilar(stock),"auction never adds persistent metadata to delivered item");
        var restarted=new CreditLedger(mando.getDataFolder().toPath().resolve("data/credits-data.yml"));check(restarted.balance(buyer.getUniqueId())==90000&&restarted.auctions().claims().isEmpty(),"money and completed escrow survive ledger reload");
        saber=mando.items().create("vader");var meta=(org.bukkit.inventory.meta.Damageable)saber.getItemMeta();meta.setDamage(17);saber.setItemMeta(meta);seller.getInventory().setItemInMainHand(saber.clone());command(seller,"sell","50");later(20,()->{command(seller,"selling");click(seller,find(seller.getOpenInventory().getTopInventory(),"vader"));click(seller,0);later(20,this::recovery);});
    }
    private void recovery(){
        check(count(seller,"vader")==1,"cancelled listing returns once");var returned=Arrays.stream(seller.getInventory().getContents()).filter(Objects::nonNull).filter(i->"vader".equals(mando.items().identify(i))).findFirst().orElseThrow();check(returned.isSimilar(saber),"cancel preserves saber damage and all metadata");
        var row=new AuctionBook.Listing(UUID.randomUUID(),seller.getUniqueId(),seller.getName(),Base64.getEncoder().encodeToString(stock.serializeAsBytes()),100,System.currentTimeMillis()+100000);claim=row.id();
        mando.credits().auctionTask(()->{mando.credits().ledger().listAuction(row,8,System.currentTimeMillis());mando.credits().ledger().buyAuction(buyer.getUniqueId(),row.id(),100,System.currentTimeMillis());return null;},(v,e)->{if(e!=null){fail(e);return;}
            // Recreate the crash window after player.dat save and before ledger claim acknowledgement.
            buyer.getInventory().addItem(stock.clone());buyer.getPersistentDataContainer().set(new NamespacedKey("mandomc","auction_delivered_v1"),PersistentDataType.STRING,claim+"=delivered");buyer.saveData();command(buyer,"collect");later(20,this::denials);
        });
    }
    private void denials(){
        check(count(buyer,"durasteel_scrap")==4&&!mando.credits().ledger().auctions().claims().containsKey(claim),"saved delivery receipt completes claim without redelivery");
        UUID refund=UUID.randomUUID();seller.getPersistentDataContainer().set(new NamespacedKey("mandomc","auction_escrow_v1"),PersistentDataType.STRING,refund+"="+Base64.getEncoder().encodeToString(stock.serializeAsBytes()));seller.saveData();command(seller,"collect");later(20,()->{
            check(count(seller,"durasteel_scrap")==2,"uncommitted listing receipt refunds original stack once");
            command(seller,"collect");later(10,()->{
                check(count(seller,"durasteel_scrap")==2,"repeated recovery does not duplicate refund");
                seller.setGameMode(GameMode.CREATIVE);seller.getInventory().setItemInMainHand(stock.clone());int before=mando.credits().ledger().auctions().listings().size();command(seller,"sell","1");check(mando.credits().ledger().auctions().listings().size()==before&&seller.getInventory().getItemInMainHand().getAmount()==2,"Creative trading denied before inventory changes");
                seller.setGameMode(GameMode.SURVIVAL);mando.combat().tag(seller);command(seller,"sell","1");check(mando.credits().ledger().auctions().listings().size()==before,"combat-tagged listing denied");
                quantityChecks();getLogger().info("AUCTION PROBE PASSED "+checks+" assertions");cleanup();Bukkit.shutdown();
            });
        });
    }
    @SuppressWarnings("unchecked") private void quantityChecks() throws Exception {
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());var list=server.getClass().getMethod("getPlayerList").invoke(server);
        var players=(List<Object>)list.getClass().getField("players").get(list);var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByName");field.setAccessible(true);var names=(Map<String,Object>)field.get(list);
        for(var p:actors){players.add(handles.get(p.getUniqueId()));names.put(p.getName().toLowerCase(Locale.ROOT),handles.get(p.getUniqueId()));}
        var attachment=seller.addAttachment(this,"mandomc.items.give",true);
        try {
            seller.getInventory().clear();buyer.getInventory().clear();var root=Bukkit.getPluginCommand("mandomc");
            root.execute(seller,"mmc",new String[]{"give","agrinium","130"});check(count(seller,"agrinium")==130,"self quantity gives exact 130 genuine ingots");
            check(Arrays.stream(seller.getInventory().getContents()).filter(Objects::nonNull).allMatch(i->i.getAmount()<=i.getMaxStackSize()),"quantity grant splits at actual stack limits");
            root.execute(seller,"mmc",new String[]{"give",buyer.getName(),"agrinium_shovel","3"});check(count(buyer,"agrinium_shovel")==3,"target quantity generates three unstackable tools");
            var ammo=mando.items().create("standard_plasma_cells");root.execute(Bukkit.getConsoleSender(),"mmc",new String[]{"give",buyer.getName(),"standard_plasma_cells","130"});
            check(Arrays.stream(buyer.getInventory().getContents()).filter(Objects::nonNull).filter(i->i.isSimilar(ammo)).mapToInt(ItemStack::getAmount).sum()==130,"console quantity gives 130 genuine vendor ammo cells");
            int before=count(seller,"agrinium");for(String bad:List.of("0","-1","2305","NaN","1.5"))root.execute(seller,"mmc",new String[]{"give","agrinium",bad});check(count(seller,"agrinium")==before,"invalid quantities never change inventory");
            for(int i=0;i<36;i++)buyer.getInventory().setItem(i,new ItemStack(Material.STONE,64));root.execute(seller,"mmc",new String[]{"give",buyer.getName(),"agrinium","2"});check(count(buyer,"agrinium")==0,"full-inventory quantity grant refuses entire delivery");
            seller.getInventory().clear();root.execute(seller,"mmc",new String[]{"give","agrinium"});check(count(seller,"agrinium")==1,"old self syntax still defaults to one");
        } finally {seller.removeAttachment(attachment);for(var p:actors){players.remove(handles.get(p.getUniqueId()));names.remove(p.getName().toLowerCase(Locale.ROOT),handles.get(p.getUniqueId()));}}
    }
    private int find(Inventory inventory,String id){for(int i=0;i<inventory.getSize();i++)if(id.equals(mando.items().identify(inventory.getItem(i))))return i;return -1;}
    private int count(Player player,String id){return Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(i->id.equals(mando.items().identify(i))).mapToInt(ItemStack::getAmount).sum();}
    private void click(Player player,int slot){if(slot<0)throw new IllegalStateException("Missing action item");var event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"native menu blocks item transfer");}
    private interface Check{void run()throws Exception;}
    private void later(long delay,Check action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable e){fail(e);}},delay);}
    private void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;getLogger().info("PASS "+message);}
    private void fail(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"AUCTION PROBE FAILED",error);cleanup();Bukkit.shutdown();}
    private void cleanup(){if(worldPlayers!=null)handles.values().forEach(worldPlayers::remove);if(online!=null)handles.keySet().forEach(online::remove);}
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
    }
    private static final class TestPlayer {
        private final UUID id = UUID.randomUUID();
        private final Inventory storage = Bukkit.createInventory(null, 36);
        private boolean allowed = true;
        private boolean online = true;
        private InventoryView view;
        private final Player player;
        private TestPlayer(String name) {
            var inventory = (PlayerInventory) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {PlayerInventory.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getStorageContents")) return storage.getContents();
                        try { return Inventory.class.getMethod(method.getName(), method.getParameterTypes()).invoke(storage, args); }
                        catch (NoSuchMethodException ignored) { return fallback(method.getReturnType()); }
                    });
            player = (Player) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "isOnline" -> online;
                    case "hasPermission" -> allowed;
                    case "getInventory" -> inventory;
                    case "getOpenInventory" -> view;
                    case "openInventory" -> { view = makeView((Inventory) args[0]); yield view; }
                    case "closeInventory" -> { view = makeView(Bukkit.createInventory(null, 9)); yield null; }
                    case "toString" -> name;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> fallback(method.getReturnType());
                };
            });
            view = makeView(Bukkit.createInventory(null, 9));
        }
        private InventoryView makeView(Inventory top) {
            return (InventoryView) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {InventoryView.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getTopInventory" -> top;
                        case "getBottomInventory" -> storage;
                        case "getPlayer" -> player;
                        case "getType" -> InventoryType.CHEST;
                        case "convertSlot" -> (int) args[0] < 54 ? args[0] : (int) args[0] - 54;
                        case "getInventory" -> (int) args[0] < 54 ? top : storage;
                        case "getItem" -> (int) args[0] < 54 ? top.getItem((int) args[0]) : storage.getItem((int) args[0] - 54);
                        default -> fallback(method.getReturnType());
                    });
        }
        private static Object fallback(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == double.class) return 0d;
            if (type == float.class) return 0f;
            if (type == long.class) return 0L;
            return null;
        }
    }
}
