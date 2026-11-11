package net.mandomc.verification;

import java.nio.file.Files;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.crates.*;
import net.mandomc.gameplay.ilum.*;
import net.mandomc.gameplay.territory.TerritoryService;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Full suite runs only on the isolated server copy. Production mode checks content without creating actors. */
public final class IlumCratesProbe extends JavaPlugin implements Listener {
    private MandoPlugin mando;private CrateService crates;private ParkourService parkour;private World world;
    private final List<Player> actors=new ArrayList<>();private final Map<UUID,Object> handles=new HashMap<>();
    private Map<UUID,Object> online;private int checks;private Player alice,bob;private ItemStack[] originalAlice,originalBob;
    private ItemStack fixed;private TextDisplay crateLabel;
    @Override public void onEnable(){later(100,this::begin);}
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    @SuppressWarnings("unchecked") private void begin()throws Exception{
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        crates=Bukkit.getServicesManager().load(CrateService.class);parkour=Bukkit.getServicesManager().load(ParkourService.class);
        check(crates!=null&&parkour!=null,"Native crate and parkour services initialized");world=parkour.world();
        check(world.getKey().toString().equals("minecraft:ilum"),"Original dimension key");
        var territories=Bukkit.getServicesManager().load(TerritoryService.class);
        check(territories!=null&&!territories.combat(parkour.start())&&!territories.edits(parkour.start()),"Ilum is a native safezone");
        check(territories.mechanisms(parkour.start()),"Original mechanisms permitted");
        check(!world.getPVP(),"PvP disabled");check(!world.getGameRuleValue(GameRules.COMMAND_BLOCKS_WORK),"Legacy arbitrary command blocks disabled");
        for(var at:parkour.checkpoints().values())check(at.clone().subtract(.5,1,.5).getBlock().getType()==Material.HEAVY_WEIGHTED_PRESSURE_PLATE,"Original checkpoint plate retained");
        check(world.getEntitiesByClass(Mannequin.class).stream().filter(n->n.customName()!=null).count()==2,"Both native skinned NPCs exist");
        for(var npc:world.getEntitiesByClass(Mannequin.class)){
            check(npc.getProfile().properties().stream().anyMatch(p->p.getName().equals("textures")),"Original skin profile loaded");
            check(npc.isImmovable()&&npc.isInvulnerable(),"NPC is immovable and invulnerable");
        }
        check(world.getEntitiesByClass(TextDisplay.class).size()>=10,"Recovered hints and leaderboard render natively");
        check(Bukkit.getWorld(NamespacedKey.minecraft("ilumreview"))==null,"Generated review map retired");
        var entry=mando.items().create("ilum_kyber_crate_key");check(entry!=null&&entry.getItemMeta().getItemModel().toString().equals("mandomc:keys/ilum_kyber_crate_key"),"Original key artwork wired into catalog");
        var spawn=Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));
        check(spawn.getBlockAt(3,93,-16).getType()==Material.AIR,"Unwanted original spawn crate removed");
        check(spawn.getBlockAt(-24,97,20).getState() instanceof org.bukkit.block.Container||spawn.getBlockAt(-24,97,20).getType()==Material.ENDER_CHEST,"User's chosen Kyber crate retained");
        var labels=spawn.getEntitiesByClass(TextDisplay.class).stream().filter(e->e.text()!=null&&net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.text()).contains("Crate")).toList();
        check(labels.size()==2,"Only the selected Kyber and KOTH crate holograms exist");
        for(var label:labels)check(!label.isDefaultBackground()&&label.getBackgroundColor()!=null&&label.getBackgroundColor().getAlpha()==0&&label.isShadowed(),"Crate hologram background transparent with text shadow retained");
        var crateWarp=mando.warps().destination("crates");
        check(crateWarp.getWorld()==spawn&&Math.abs(crateWarp.getX()+32.522060072695176)<1e-10&&crateWarp.getY()==96&&Math.abs(crateWarp.getZ()-16.540950565404483)<1e-10,"Exact captured crate warp position");
        check(Math.abs(crateWarp.getYaw()+89.548004f)<.00001&&Math.abs(crateWarp.getPitch()-.59998304f)<.00001,"Exact captured crate warp facing");
        check("crates".equals(net.mandomc.gameplay.travel.WarpService.canonical("crate"))&&"crates".equals(net.mandomc.gameplay.travel.WarpService.canonical("crates")),"Both crate warp aliases resolve");
        var menuField=mando.warps().getClass().getDeclaredField("slots");menuField.setAccessible(true);
        check(new HashSet<>(((Map<?,?>)menuField.get(mando.warps())).values()).equals(Set.of("spawn","hoth","tatooine","mandalore","ilum")),"Warp menu retains all planets and excludes crates");
        check(mando.warps().destination("ilum").getWorld()==world,"Public Ilum warp resolves original world");
        check(mando.warps().destination("mandalore").getWorld().getKey().getKey().equals("mandalore_review"),"Existing Mandalore route preserved");
        for(String command:List.of("crates","parkour","parkourfinish","c","auctionhouse","warp"))check(mando.getCommand(command)!=null,"Command preserved/registered "+command);
        if(new java.io.File(getDataFolder(),"check-handbook").exists()){
            var arrival=Arrays.stream(PlayerLoginEvent.getHandlerList().getRegisteredListeners()).map(org.bukkit.plugin.RegisteredListener::getListener)
                    .filter(listener->listener.getClass().getName().equals("net.mandomc.gameplay.arrival.ArrivalListener")).findFirst().orElseThrow();
            var field=arrival.getClass().getDeclaredField("handbook");field.setAccessible(true);var book=field.get(arrival);
            var item=(ItemStack)book.getClass().getMethod("create").invoke(book);
            var meta=(org.bukkit.inventory.meta.BookMeta)item.getItemMeta();
            var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            check(meta.getPageCount()==11,"Bound tutorial owner serves the validated eleven-page handbook");
            check(plain.serialize(meta.page(3)).contains("Jawa Shops"),"Jawa Shops is page three");
            check(plain.serialize(meta.page(9)).contains("Events"),"Combined Events is page nine");
            check(meta.pages().stream().noneMatch(page->plain.serialize(page).startsWith("Armor")),"Removed armor page is absent");
        }
        if(!new java.io.File(getDataFolder(),"full-suite").exists()){success();return;}
        Bukkit.getPluginManager().registerEvents(this,this);
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        var list=server.getClass().getMethod("getPlayerList").invoke(server);
        var field=Class.forName("net.minecraft.server.players.PlayerList").getDeclaredField("playersByUUID");field.setAccessible(true);online=(Map<UUID,Object>)field.get(list);
        alice=actor(parkour.start());bob=actor(parkour.start());
        originalAlice=gear(alice);originalBob=gear(bob);
        parkour.begin(alice);parkour.begin(bob);
        check(parkour.active(alice)&&parkour.active(bob),"Independent concurrent sessions");
        check(alice.getGameMode()==GameMode.ADVENTURE&&alice.getInventory().getItem(1).getType()==Material.COMPASS,"Legacy course controls and mode");
        check(alice.getInventory().getItem(40)==null||alice.getInventory().getItem(40).isEmpty(),"Offhand stored, not left available");
        var stored=alice.getPersistentDataContainer().get(ParkourInventory.INVENTORY,PersistentDataType.BYTE_ARRAY);
        equalGear(originalAlice,ItemStack.deserializeItemsFromBytes(stored),"Escrow contains exact complete inventory");
        reach(alice,2);check(parkour.run(alice).checkpoint()==0,"Skipped checkpoint rejected");
        reach(alice,1);reach(alice,2);check(parkour.run(bob).checkpoint()==0,"Other player's checkpoint unaffected");
        parkour.retry(alice);check(alice.getLocation().distanceSquared(parkour.checkpoints().get(2))<.1,"Retry returns to original second checkpoint");
        Mannequin ahsoka=npc("Ahsoka");position(alice,ahsoka.getLocation().clone().add(1,0,1));
        interactPacket(alice,ahsoka);
        later(210,this::afterAhsoka);
    }
    private void afterAhsoka()throws Exception{
        check(alice.getLocation().distanceSquared(new Location(world,-111,145,-182))<1,"Recovered Ahsoka conversation teleports to upper course");
        reach(alice,3);reach(alice,4);
        var hit=new EntityDamageByEntityEvent(bob,alice,EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK).withDirectEntity(bob).withCausingEntity(bob).build(),5);
        var before=alice.getLocation();Bukkit.getPluginManager().callEvent(hit);check(hit.isCancelled(),"PvP denied without resetting another runner");
        check(alice.getLocation().distanceSquared(before)<.01,"Punching cannot return another runner to checkpoint");
        Mannequin obi=npc("Obi-Wan");position(alice,obi.getLocation().clone().add(1,0,1));
        interactPacket(alice,obi);
        // The live player stood roughly seven blocks from Obi-Wan after the dialogue.
        // Stay inside its advertised ten-block conversation radius throughout completion.
        position(alice,obi.getLocation().clone().add(1,0,-7));
        later(260,this::afterFinish);
    }
    private void afterFinish()throws Exception{
        check(!parkour.active(alice),"Obi-Wan completes session");check(!ParkourInventory.stored(alice),"Escrow cleared after restoration");
        preservedOriginal(originalAlice,alice.getInventory().getContents());
        check(alice.getGameMode()==GameMode.CREATIVE&&alice.getHealth()==17&&alice.getFoodLevel()==13,"Original game mode, health and food restored");
        var destination=mando.warps().destination("spawn");
        check(alice.getWorld()==destination.getWorld()&&alice.getLocation().distanceSquared(destination)<.1,"Completion returns to the actual spawn command destination");
        check(alice.getPersistentDataContainer().has(new NamespacedKey("mandomc","ilum_last_key"),PersistentDataType.LONG),"Completion cooldown persisted");
        check(crates.keys(alice,crates.definitions().get("kyber"))==1,"Successful run awards one physical key");
        mando.warps().open(alice);
        var menu=alice.getOpenInventory().getTopInventory();
        check(menu.getItem(24).getType()==Material.GRAY_STAINED_GLASS_PANE&&List.of(20,28,32,39,43).stream().allMatch(slot->menu.getItem(slot).getType()==Material.STICK),"Actual warp GUI replaces only crate entry with filler");
        alice.closeInventory();
        for(String alias:List.of("crate","crates")){
            Bukkit.dispatchCommand(alice,"warp "+alias);
            var target=mando.warps().destination("crates");
            check(alice.getWorld()==target.getWorld()&&alice.getLocation().distanceSquared(target)<.000001,"Actual warp command travels using "+alias);
            check(alice.getLocation().getYaw()==target.getYaw()&&alice.getLocation().getPitch()==target.getPitch(),"Actual warp command preserves facing using "+alias);
        }
        check(parkour.active(bob)&&parkour.run(bob).checkpoint()==0,"Other runner remains active");
        parkour.leave(bob,true,false);equalGear(originalBob,bob.getInventory().getContents(),"Leaving restores other runner's exact gear");
        var crate=crates.definitions().get("kyber");crates.preview(alice,crate);
        var view=alice.getOpenInventory();check(view.getTopInventory().getSize()==27,"Reward preview opens");
        check(view.getTopInventory().getItem(0).getItemMeta().isHideTooltip(),"Unused menu cells are hidden-tooltip gray panes");
        var shift=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,10,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);
        Bukkit.getPluginManager().callEvent(shift);check(shift.isCancelled(),"Shift extraction blocked");
        var hotbar=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,10,ClickType.NUMBER_KEY,InventoryAction.HOTBAR_SWAP,0);
        Bukkit.getPluginManager().callEvent(hotbar);check(hotbar.isCancelled(),"Hotbar extraction blocked");
        var drag=new InventoryDragEvent(view,new ItemStack(Material.DIAMOND),new ItemStack(Material.DIAMOND),false,Map.of(10,new ItemStack(Material.DIAMOND)));
        Bukkit.getPluginManager().callEvent(drag);check(drag.isCancelled(),"Drag extraction blocked");
        click(alice,22);click(alice,22);later(3,this::opening);
    }
    private void opening()throws Exception{
        check(crates.keys(alice,crates.definitions().get("kyber"))==0,"Rapid clicks consume exactly one key");
        var bytes=alice.getPersistentDataContainer().get(CrateService.PENDING,PersistentDataType.BYTE_ARRAY);
        check(bytes!=null,"Reward decision persisted before rolling");fixed=ItemStack.deserializeBytes(bytes);
        check(mando.items().identify(fixed).endsWith("_kyber"),"Pending reward is a genuine catalog crystal");
        click(alice,22);later(3,this::closedEarly);
    }
    private void closedEarly()throws Exception{
        check(!alice.getPersistentDataContainer().has(CrateService.PENDING),"Closing animation claims fixed reward");
        check(alice.getInventory().containsAtLeast(fixed,1),"Fixed reward delivered on close");
        int crystals=countCrystals(alice);crates.finishPending(alice);crates.claim(alice);
        check(countCrystals(alice)==crystals,"Repeated claim cannot duplicate reward");
        // Replaying is allowed, while the legacy 24-hour key limit remains authoritative in player data.
        parkour.begin(alice);for(int cp=1;cp<=4;cp++)reach(alice,cp);
        position(alice,npc("Obi-Wan").getLocation().clone().add(1,0,1));parkour.finish(alice);
        check(crates.keys(alice,crates.definitions().get("kyber"))==0,"Second completion during cooldown cannot farm keys");
        // Actual native inventory/PDC serialization emulates recovery with all original metadata intact.
        parkour.begin(bob);byte[] persisted=bob.getPersistentDataContainer().serializeToBytes();
        bob.getPersistentDataContainer().readFromBytes(persisted,true);parkour.join(new PlayerJoinEvent(bob,Component.empty()));
        later(3,this::recovered);
    }
    private void recovered()throws Exception{
        check(!parkour.active(bob),"Interrupted run recovers instead of leaking course inventory");
        equalGear(originalBob,bob.getInventory().getContents(),"Restart recovery restores exact armor/offhand/gear");
        var crate=crates.definitions().get("kyber");
        Player full=actor(new Location(Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")),2,93,-16));
        for(int slot=0;slot<36;slot++)full.getInventory().setItem(slot,new ItemStack(Material.STONE,64));
        var key=mando.items().create(crate.keyItem());key.setAmount(2);full.getInventory().setItem(0,key);
        crates.open(full,crate);check(crates.keys(full,crate)==2&&!full.getPersistentDataContainer().has(CrateService.PENDING),"Full inventory never consumes a key");
        full.getInventory().setItem(35,null);crates.open(full,crate);
        var expected=ItemStack.deserializeBytes(full.getPersistentDataContainer().get(CrateService.PENDING,PersistentDataType.BYTE_ARRAY));
        full.getInventory().setItem(35,new ItemStack(Material.STONE,64));full.closeInventory();
        later(3,()->{
            check(full.getPersistentDataContainer().has(CrateService.PENDING),"Reward retained when space disappears mid-opening");
            byte[] saved=full.getPersistentDataContainer().serializeToBytes();full.getPersistentDataContainer().readFromBytes(saved,true);
            full.getInventory().setItem(35,null);crates.claim(full);
            check(!full.getPersistentDataContainer().has(CrateService.PENDING)&&full.getInventory().containsAtLeast(expected,1),"Persisted pending reward recovered exactly");
            var forged=actor(full.getLocation());var imitation=new ItemStack(Material.ECHO_SHARD);var meta=imitation.getItemMeta();meta.displayName(Component.text("Kyber Crate Key"));imitation.setItemMeta(meta);forged.getInventory().setItem(0,imitation);
            check(crates.keys(forged,crate)==0,"Renamed vanilla item cannot forge a key");
            success();
        });
    }
    private ItemStack[] gear(Player player){
        player.setGameMode(GameMode.CREATIVE);player.setHealth(17);player.setFoodLevel(13);player.setSaturation(2.5f);
        player.getInventory().setItem(0,new ItemStack(Material.DIAMOND,3));player.getInventory().setItem(17,mando.items().create("blue_kyber"));
        player.getInventory().setHelmet(new ItemStack(Material.LEATHER_HELMET));player.getInventory().setItemInOffHand(new ItemStack(Material.IRON_INGOT,4));
        return ItemStack.deserializeItemsFromBytes(ItemStack.serializeItemsAsBytes(player.getInventory().getContents()));
    }
    private void equalGear(ItemStack[] expected,ItemStack[] actual,String message){
        check(expected.length==actual.length,message+" size");
        for(int i=0;i<expected.length;i++){
            boolean emptyExpected=expected[i]==null||expected[i].isEmpty();boolean emptyActual=actual[i]==null||actual[i].isEmpty();
            check(emptyExpected&&emptyActual||!emptyExpected&&!emptyActual&&expected[i].equals(actual[i]),message+" slot "+i);
        }
    }
    private void preservedOriginal(ItemStack[] expected,ItemStack[] actual){for(int i=0;i<expected.length;i++)if(expected[i]!=null&&!expected[i].isEmpty())check(expected[i].equals(actual[i]),"Original slot "+i+" retained");}
    private int countCrystals(Player player){int n=0;for(var item:player.getInventory().getContents()){var id=mando.items().identify(item);if(id!=null&&id.endsWith("_kyber"))n+=item.getAmount();}return n;}
    private Mannequin npc(String name){return world.getEntitiesByClass(Mannequin.class).stream().filter(e->e.customName()!=null&&net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(e.customName()).contains(name)).findFirst().orElseThrow();}
    private void reach(Player player,int cp)throws Exception{
        var before=player.getLocation();var to=parkour.checkpoints().get(cp).clone().subtract(0,.95,0);
        var event=new PlayerMoveEvent(player,before,to);Bukkit.getPluginManager().callEvent(event);position(player,event.getTo());
    }
    private void position(Player player,Location at)throws Exception{Object handle=handles.get(player.getUniqueId());handle.getClass().getMethod("setPos",double.class,double.class,double.class).invoke(handle,at.getX(),at.getY(),at.getZ());}
    private void interactPacket(Player player,Entity target)throws Exception{
        Class<?> hand=Class.forName("net.minecraft.world.InteractionHand");
        Class<?> vec=Class.forName("net.minecraft.world.phys.Vec3");
        Class<?> packetType=Class.forName("net.minecraft.network.protocol.game.ServerboundInteractPacket");
        Object packet=packetType.getConstructor(int.class,hand,vec,boolean.class).newInstance(target.getEntityId(),
                hand.getField("MAIN_HAND").get(null),vec.getConstructor(double.class,double.class,double.class).newInstance(0,1,0),false);
        Object listener=handles.get(player.getUniqueId()).getClass().getField("connection").get(handles.get(player.getUniqueId()));
        listener.getClass().getMethod("handleInteract",packetType).invoke(listener,packet);
    }
    private void click(Player player,int slot){var event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"Menu click intercepted");}
    @SuppressWarnings("unchecked") private Player actor(Location at)throws Exception{
        var player=nativeTestPlayer(at.getWorld(),at);actors.add(player);player.setGravity(false);
        var handle=player.getClass().getMethod("getHandle").invoke(player);handles.put(player.getUniqueId(),handle);online.put(player.getUniqueId(),handle);
        Object level=at.getWorld().getClass().getMethod("getHandle").invoke(at.getWorld());
        level.getClass().getMethod("addNewPlayer",Class.forName("net.minecraft.server.level.ServerPlayer")).invoke(level,handle);return player;
    }
    private interface Check{void run()throws Exception;}
    private void later(long ticks,Check action){Bukkit.getScheduler().runTaskLater(this,()->{try{action.run();}catch(Throwable error){fail(error);}},ticks);}
    private void success()throws Exception{
        String result="ILUM CRATES PROBE PASSED: "+checks+" native assertions\n";Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),result);getLogger().info(result);cleanup();
    }
    private void fail(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"ILUM CRATES PROBE FAILED",error);try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());}catch(Exception ignored){}cleanup();}
    @EventHandler(priority=EventPriority.MONITOR) public void observedClose(InventoryCloseEvent event){
        if(actors.stream().anyMatch(player->player.getUniqueId().equals(event.getPlayer().getUniqueId()))){
            getLogger().info("Fixture inventory close: "+event.getPlayer().getName()+" "+event.getReason()+" pending="+event.getPlayer().getPersistentDataContainer().has(CrateService.PENDING));
            try{var menu=event.getView().getClass().getMethod("getHandle").invoke(event.getView());
                getLogger().info("Fixture menu: "+menu.getClass().getName()+" reachable="+menu.getClass().getField("checkReachable").get(menu));
            }catch(Exception ignored){}
        }
    }
    @SuppressWarnings("unchecked") private void cleanup(){
        Bukkit.getScheduler().cancelTasks(this);
        HandlerList.unregisterAll((Listener)this);
        for(var actor:List.copyOf(actors))try{
            if(parkour!=null&&parkour.active(actor))parkour.leave(actor,true,false);
            actor.closeInventory();if(online!=null)online.remove(actor.getUniqueId());
            Object handle=handles.get(actor.getUniqueId());
            for(var world:Bukkit.getWorlds()){Object level=world.getClass().getMethod("getHandle").invoke(world);((List<Object>)level.getClass().getMethod("players").invoke(level)).remove(handle);}
            handle.getClass().getMethod("discard").invoke(handle);
        }catch(Exception error){getLogger().warning("Probe actor cleanup: "+error);}
        actors.clear();handles.clear();
    }
    @Override public void onDisable(){cleanup();}

    // Version-specific native actors are confined to this integration fixture, never production gameplay.
    private Player nativeTestPlayer(World world,Location location)throws Exception{
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass=Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass=Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass=Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level=world.getClass().getMethod("getHandle").invoke(world);
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"IlumProbe"+actors.size());
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class.forName("net.minecraft.world.entity.Entity").getField("valid").setBoolean(handle,true);
        Class<?> flowClass=Class.forName("net.minecraft.network.protocol.PacketFlow");
        Class<?> connectionClass=Class.forName("net.minecraft.network.Connection");
        Object connection=connectionClass.getConstructor(flowClass).newInstance(flowClass.getField("SERVERBOUND").get(null));
        // A real, socket-free channel keeps the native actor connected; otherwise Paper correctly
        // closes its menus with CANT_USE before an animation can run.
        Object channel=Class.forName("io.netty.channel.embedded.EmbeddedChannel").getConstructor().newInstance();
        var channelField=connectionClass.getDeclaredField("channel");channelField.setAccessible(true);channelField.set(connection,channel);
        Class<?> cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Class<?> listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object listener=listenerClass.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);
        playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);
        var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,location.getX(),location.getY(),location.getZ());
        return (Player)playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
}
