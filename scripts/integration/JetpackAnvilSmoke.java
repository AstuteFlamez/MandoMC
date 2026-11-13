package net.mandomc.verification;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.boba.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Narrow exact-candidate startup gate, on a disposable copy of the current runtime only. */
public final class JetpackAnvilSmoke extends JavaPlugin implements org.bukkit.event.Listener {
    private MandoPlugin main;
    private BobaEquipment equipment;
    private Player actor;
    private Cow target;
    private int checks;
    @Override public void onEnable() {Bukkit.getPluginManager().registerEvents(this,this);later(160,this::begin);}
    private void begin() throws Exception {
        main=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        check(main!=null&&main.isEnabled(),"exact candidate plugin enabled");
        var failed=MandoPlugin.class.getDeclaredField("failed");failed.setAccessible(true);
        check(!failed.getBoolean(main),"full MandoMC bootstrap finished without fail-closed login gate");
        check(main.worlds()!=null&&main.warps()!=null&&main.events()!=null,"complete world/arrival/travel/event startup finished");
        var feature=Bukkit.getServicesManager().load(BobaEquipmentFeature.class);
        check(feature!=null,"additive bootstrap registered BobaEquipmentFeature");equipment=feature.equipment();
        for(String id:List.of("boba_jetpack","boba_flamethrower","ee3","standard_plasma_cells","jawa_ionization_blaster"))
            check(main.items().create(id)!=null,"actual current catalog creates "+id);
        check(main.getCommand("mandomc").getTabCompleter().onTabComplete(Bukkit.getConsoleSender(),main.getCommand("mandomc"),"mmc",new String[]{"eq"}).contains("equipment"),"actual equipment route registered");
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(main.getDataFolder(),"mobs/drops/jabba.yml"));
        check(config.getDouble("drops.BobaFett.equipment.boba_jetpack")==.10&&config.getDouble("drops.BobaFett.equipment.boba_flamethrower")==.15,"actual runtime loot installer registered both independent rolls");
        check(config.getStringList("drops.JawaBrown.salvage.pool").size()>0,"runtime Jawa salvage retained");
        var protection=new net.mandomc.integrations.regions.SaberProtection();Location where=null;
        var world=Bukkit.getWorld(NamespacedKey.minecraft("tatooine_review"));
        for(int x=-224;x<=224&&where==null;x+=16)for(int z=-224;z<=224;z+=16){
            var test=new Location(world,x+.5,180,z+.5,0,0);
            if(protection.allowed(test)&&protection.allowed(test.clone().add(0,0,4))){where=test;break;}
        }
        check(where!=null,"current native territory policy supplies an allowed isolated weapon fixture");
        where.getChunk().addPluginChunkTicket(this);actor=nativePlayer(where);
        actor.setGameMode(GameMode.SURVIVAL);
        actor.getInventory().setItemInOffHand(main.items().create(BobaEquipment.JETPACK));
        actor.getInventory().setItemInMainHand(main.items().create(BobaEquipment.FLAMETHROWER));
        actor.getInventory().setItem(1,new ItemStack(Material.COAL));actor.getInventory().setItem(2,new ItemStack(Material.FIRE_CHARGE,2));
        var armor=new ItemStack(Material.NETHERITE_CHESTPLATE);var meta=armor.getItemMeta();
        ((org.bukkit.inventory.meta.Damageable)meta).setDamage(37);meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION,4,true);
        meta.displayName(net.kyori.adventure.text.Component.text("Named armor"));
        meta.lore(List.of(net.kyori.adventure.text.Component.text("Original lore")));
        meta.getPersistentDataContainer().set(new NamespacedKey("test","owned"),org.bukkit.persistence.PersistentDataType.STRING,"retained");
        ((org.bukkit.inventory.meta.ArmorMeta)meta).setTrim(new org.bukkit.inventory.meta.trim.ArmorTrim(org.bukkit.inventory.meta.trim.TrimMaterial.GOLD,org.bukkit.inventory.meta.trim.TrimPattern.SENTRY));
        armor.setItemMeta(meta);
        var upgraded=anvilProof(armor);
        actor.getInventory().setChestplate(upgraded);
        check(equipment.toggle(actor),"actual registered jetpack activates");
        check(actor.getInventory().getItem(1)==null,"actual registered jetpack consumes first coal");
        check(actor.getInventory().getChestplate().equals(upgraded),"actual registered jetpack preserves chest metadata");
        check(equipment.attachment(actor)==null&&actor.getPassengers().isEmpty(),"enchanted chestplate creates no visual entities");
        check(actor.isGliding(),"registered native chest equipment enables glide state");
        check(Math.abs(equipment.seconds(actor)-.1)<.0001,"one newly consumed coal funds exactly 0.1 seconds");
        check(equipment.ownsActionBar(actor),"fuel warning/actionbar ownership is exposed to reload UI");
        equipment.toggle(actor);check(equipment.toggle(actor),"resuming enchanted chestplate retains purchased fuel fraction");
        long activation=System.nanoTime();later(2,()->depletionProof(activation));
        target=world.spawn(where.clone().add(0,0,3),Cow.class,c->{c.setAI(false);c.setGravity(false);});
        check(equipment.flames().activate(actor),"actual registered flame service accepts burst in native policy");
        check(actor.getInventory().getItem(2).getAmount()==1,"registered burst consumes exactly one fire charge");
        check(!equipment.flames().activate(actor)&&actor.getInventory().getItem(2).getAmount()==1,"registered cooldown prevents second ammo/use");
        later(45,()->{
            check(!equipment.powered(actor),"registered jetpack depletes after its purchased 0.1-second interval");
            check(target.getHealth()<target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),"registered native flame packets damage a real target");
            check(actor.getInventory().getItem(2).getAmount()==1,"entire registered burst spent only one fire charge");
            var levelTwo=equipment.chestplates().upgrade(actor.getInventory().getChestplate(),1);
            actor.getInventory().setChestplate(levelTwo);actor.getInventory().setItem(1,new ItemStack(Material.COAL));
            check(equipment.toggle(actor),"native level II chestplate activates through same controls");
            check(actor.getInventory().getItem(1)==null&&Math.abs(equipment.seconds(actor)-.2)<.00001,"native level II first coal buys exactly 0.2 seconds");
            equipment.toggle(actor);
            equipment.detach(actor);
            check(equipment.attachment(actor)==null,"registered cleanup removes attachment");
            getLogger().info("JETPACK AGRINIUM ANVIL SMOKE PASSED: "+checks+" assertions; exact candidate/full startup/native registered services");
            cleanup();Bukkit.shutdown();
        });
    }
    private void depletionProof(long activation)throws Exception {
        if(System.nanoTime()-activation<150_000_000L) { later(2,()->depletionProof(activation));return; }
        check(!equipment.powered(actor),"real-time credit depletes after at least 150ms; elapsed="+(System.nanoTime()-activation)+" remaining="+equipment.seconds(actor));
    }
    private boolean empty(ItemStack item) { return item==null||item.getType().isAir(); }
    private void click(int slot,String action)throws Exception {
        var handle=actor.getClass().getMethod("getHandle").invoke(actor);
        var menu=handle.getClass().getField("containerMenu").get(handle);
        var type=Class.forName("net.minecraft.world.inventory.ContainerInput");
        // Use the actual packet handler so Bukkit InventoryClickEvent and native consumption both run.
        var packetType=Class.forName("net.minecraft.network.protocol.game.ServerboundContainerClickPacket");
        var mapType=Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectMap");
        var changes=Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap").getConstructor().newInstance();
        var hashed=Class.forName("net.minecraft.network.HashedStack");
        var packet=packetType.getConstructor(int.class,int.class,short.class,byte.class,type,mapType,hashed)
                .newInstance(menu.getClass().getField("containerId").getInt(menu),
                    menu.getClass().getMethod("getStateId").invoke(menu),(short)slot,(byte)0,
                    type.getField(action).get(null),changes,hashed.getField("EMPTY").get(null));
        var connection=handle.getClass().getField("connection").get(handle);
        getLogger().info("PACKET gate immobile="+handle.getClass().getMethod("isImmobile").invoke(handle)
                +" valid="+menu.getClass().getMethod("stillValid",Class.forName("net.minecraft.world.entity.player.Player")).invoke(menu,handle)
                +" cost="+((org.bukkit.inventory.view.AnvilView)actor.getOpenInventory()).getRepairCost());
        connection.getClass().getMethod("handleContainerClick",packetType).invoke(connection,packet);
        getLogger().info("PACKET result cursor="+equipment.chestplates().bookLevel(actor.getItemOnCursor())+" xp="+actor.getLevel());
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR)
    public void observe(org.bukkit.event.inventory.InventoryClickEvent event)throws Exception {
        if(event.getRawSlot()!=2||!(event.getView() instanceof org.bukkit.inventory.view.AnvilView view))return;
        var method=JetpackChestplate.class.getDeclaredMethod("anvilResult",org.bukkit.inventory.view.AnvilView.class);method.setAccessible(true);
        var expected=(ItemStack)method.invoke(equipment.chestplates(),view);
        getLogger().info("CLICK EVENT cancelled="+event.isCancelled()+" equal="+java.util.Objects.equals(expected,event.getCurrentItem())
                );
    }
    private org.bukkit.inventory.view.AnvilView physicalAnvil(Location station)throws Exception {
        var view=actor.openAnvil(station,true);
        check(view instanceof org.bukkit.inventory.view.AnvilView,"real placed anvil opens native anvil view");
        return (org.bukkit.inventory.view.AnvilView)view;
    }
    private void inputs(org.bukkit.inventory.view.AnvilView view,ItemStack left,ItemStack right) {
        view.getTopInventory().setItem(0,left);view.getTopInventory().setItem(1,right);
    }
    private ItemStack anvilProof(ItemStack armor)throws Exception {
        var service=equipment.chestplates();
        var grid=new ItemStack[9];grid[0]=service.book(1);grid[8]=service.book(1);
        check(empty(Bukkit.getServer().craftItemResult(grid,actor.getWorld(),actor).getResult()),"crafting grid cannot combine Jetpack books");
        grid[0]=armor.clone();
        check(empty(Bukkit.getServer().craftItemResult(grid,actor.getWorld(),actor).getResult()),"crafting grid cannot apply Jetpack to chestplate");
        check(Bukkit.getRecipe(new NamespacedKey("mandomc","jetpack_book_ii"))==null,"old II-book crafting recipe removed");
        for(var material:Material.values())if(material.name().endsWith("_CHESTPLATE")&&material.isItem())
            check(Bukkit.getRecipe(new NamespacedKey("mandomc","jetpack_"+material.name().toLowerCase(java.util.Locale.ROOT)))==null,"old grid recipe removed "+material);
        var virtual=MenuType.ANVIL.create(actor,net.kyori.adventure.text.Component.text("Virtual"));actor.openInventory(virtual);
        var vv=(org.bukkit.inventory.view.AnvilView)virtual;inputs(vv,service.book(1),service.book(1));
        check(empty(vv.getTopInventory().getItem(2)),"virtual anvil without a physical Agrinium Anvil rejects upgrade");actor.closeInventory();
        var station=actor.getLocation().clone().add(2,0,0);station.getBlock().getRelative(0,-1,0).setType(Material.STONE,false);
        actor.setLevel(100);
        for(var material:new Material[]{Material.ANVIL,Material.CHIPPED_ANVIL,Material.DAMAGED_ANVIL}) {
            station.getBlock().setType(material,false);var view=physicalAnvil(station);
            inputs(view,service.book(1),service.book(1));
            check(service.bookLevel(view.getTopInventory().getItem(2))==2,"native preparation combines I books at "+material);
            int xp=actor.getLevel(),cost=view.getRepairCost();check(cost>0,"anvil operation requires native XP "+material);
            click(2,"PICKUP");
            check(service.bookLevel(actor.getItemOnCursor())==2,"normal native result click produces II at "+material);
            check(empty(view.getTopInventory().getItem(0))&&empty(view.getTopInventory().getItem(1)),"native click consumes both I books once "+material);
            check(actor.getLevel()==xp-cost,"native operation spends exact displayed XP "+material);
            actor.setItemOnCursor(null);actor.closeInventory();
        }
        station.getBlock().setType(Material.ANVIL,false);var view=physicalAnvil(station);
        inputs(view,service.book(1),service.book(1));int before=actor.getLevel();click(2,"QUICK_MOVE");
        check(Arrays.stream(actor.getInventory().getStorageContents()).anyMatch(i->service.bookLevel(i)==2),"shift click delivers II book to inventory");
        check(empty(view.getTopInventory().getItem(0))&&empty(view.getTopInventory().getItem(1)),"shift click consumes both anvil inputs once");
        int once=actor.getLevel();click(2,"QUICK_MOVE");check(actor.getLevel()==once&&once<before,"repeated output click cannot spend or duplicate result");
        inputs(view,service.book(1),service.book(1));actor.setLevel(0);click(2,"PICKUP");
        check(empty(actor.getItemOnCursor())&&!empty(view.getTopInventory().getItem(0))&&!empty(view.getTopInventory().getItem(1)),"insufficient XP cannot take result or consume inputs");
        actor.setLevel(100);
        var saved=Arrays.stream(actor.getInventory().getStorageContents()).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
        var full=new ItemStack[36];Arrays.setAll(full,i->new ItemStack(Material.STONE,64));actor.getInventory().setStorageContents(full);
        before=actor.getLevel();click(2,"QUICK_MOVE");
        check(actor.getLevel()==before&&!empty(view.getTopInventory().getItem(0))&&!empty(view.getTopInventory().getItem(1)),"full inventory shift click preserves inputs and XP");
        actor.getInventory().setStorageContents(saved);
        station.getBlock().setType(Material.AIR,false);click(2,"PICKUP");
        check(empty(actor.getItemOnCursor())&&!empty(view.getTopInventory().getItem(0)),"breaking station before pickup invalidates prepared output");
        station.getBlock().setType(Material.ANVIL,false);actor.closeInventory();view=physicalAnvil(station);
        var protection=new ItemStack(Material.ENCHANTED_BOOK);var em=(org.bukkit.inventory.meta.EnchantmentStorageMeta)protection.getItemMeta();em.addStoredEnchant(org.bukkit.enchantments.Enchantment.PROTECTION,1,true);protection.setItemMeta(em);
        inputs(view,new ItemStack(Material.IRON_CHESTPLATE),protection);
        check(empty(view.getTopInventory().getItem(2)),"existing global prohibition on unrelated enchantment books remains active");
        inputs(view,armor.clone(),service.book(2));
        check(service.level(view.getTopInventory().getItem(2))==2,"II book applies II directly to chestplate at anvil");
        click(2,"PICKUP");var levelTwo=actor.getItemOnCursor().clone();actor.setItemOnCursor(null);
        check(service.level(levelTwo)==2&&levelTwo.hasData(io.papermc.paper.datacomponent.DataComponentTypes.GLIDER),"native anvil output retains chest flight components");
        inputs(view,levelTwo.clone(),service.book(1));
        check(empty(view.getTopInventory().getItem(2)),"level II chest cannot upgrade beyond II");
        inputs(view,service.book(2),service.book(2));
        check(empty(view.getTopInventory().getItem(2)),"II books cannot combine into III");
        inputs(view,armor.clone(),service.book(1));
        check(service.level(view.getTopInventory().getItem(2))==1,"I book applies I to chestplate at anvil");
        click(2,"PICKUP");var upgraded=actor.getItemOnCursor().clone();actor.setItemOnCursor(null);
        var stripped=upgraded.clone();stripped.removeEnchantment(Registry.ENCHANTMENT.get(JetpackChestplate.KEY));stripped=service.cleanRemovedEnchantment(stripped);stripped.resetData(io.papermc.paper.datacomponent.DataComponentTypes.REPAIR_COST);
        check(stripped.equals(armor),"anvil retains all original chestplate properties except native prior-work cost");
        inputs(view,service.upgrade(armor),service.book(1));
        check(service.level(view.getTopInventory().getItem(2))==2,"I chest plus I book upgrades to II at anvil");
        view.getTopInventory().setItem(0,null);view.getTopInventory().setItem(1,null);actor.closeInventory();
        station.getBlock().setType(Material.AIR,false);station.getBlock().getRelative(0,-1,0).setType(Material.AIR,false);
        return upgraded;
    }
    private Player nativePlayer(Location location)throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());Object level=location.getWorld().getClass().getMethod("getHandle").invoke(location.getWorld());
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"BobaStartupTest");
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        playerClass.getField("valid").setBoolean(handle,true);
        Class<?> flowClass=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection"),cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie"),listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object connection=connectionClass.getConstructor(flowClass).newInstance(flowClass.getField("SERVERBOUND").get(null));
        // An open in-memory channel makes the real packet handler accept this isolated native actor.
        Object channel=Class.forName("io.netty.channel.embedded.EmbeddedChannel").getConstructor().newInstance();
        connectionClass.getField("channel").set(connection,channel);connectionClass.getField("preparing").setBoolean(connection,false);
        Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Object listener=listenerClass.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,location.getX(),location.getY(),location.getZ());
        Object list=serverClass.getMethod("getPlayerList").invoke(server);
        for(Class<?> c=list.getClass();c!=null;c=c.getSuperclass())try{var map=c.getDeclaredField("playersByUUID");map.setAccessible(true);((Map<UUID,Object>)map.get(list)).put((UUID)profileClass.getMethod("id").invoke(profile),handle);break;}catch(NoSuchFieldException ignored){}
        return (Player)playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
    private interface Work {void run()throws Exception;}
    private void later(int ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"JETPACK AGRINIUM ANVIL SMOKE FAILED",error);cleanup();Bukkit.shutdown();}},ticks);}
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;getLogger().info("PASS "+why);}
    private void cleanup(){if(equipment!=null&&actor!=null)equipment.detach(actor);if(main!=null&&main.combat()!=null&&actor!=null)main.combat().clear(actor);if(target!=null)target.remove();}
    @Override public void onDisable(){cleanup();}
}
