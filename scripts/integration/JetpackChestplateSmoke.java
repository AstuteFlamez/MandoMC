package net.mandomc.verification;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.boba.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Narrow exact-candidate startup gate, on a disposable copy of the current runtime only. */
public final class JetpackChestplateSmoke extends JavaPlugin {
    private MandoPlugin main;
    private BobaEquipment equipment;
    private Player actor;
    private Cow target;
    private int checks;
    @Override public void onEnable() {later(160,this::begin);}
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
        var upgraded=craftingProof(armor);
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
            getLogger().info("JETPACK CHESTPLATE SMOKE PASSED: "+checks+" assertions; exact candidate/full startup/native registered services");
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
        menu.getClass().getMethod("clicked",int.class,int.class,type,Class.forName("net.minecraft.world.entity.player.Player"))
                .invoke(menu,slot,0,type.getField(action).get(null),handle);
    }
    private ItemStack craftingProof(ItemStack armor)throws Exception {
        var recipes=equipment.chestplates();var enchant=Registry.ENCHANTMENT.get(JetpackChestplate.KEY);
        check(enchant!=null,"real datapack Jetpack enchantment exists in native registry");
        check(!main.getCommand("mandomc").getTabCompleter().onTabComplete(Bukkit.getConsoleSender(),main.getCommand("mandomc"),"mmc",new String[]{"jetpackt"}).contains("jetpacktest"),"old visual test route removed");
        check(Bukkit.getPluginManager().getPlugin("MandoMC")!=null&&equipment.visualTrials()==null,"no visual test controller instantiated");
        bookAndTooltipProof(armor);
        var pack=main.items().create(BobaEquipment.JETPACK);
        pack.setData(io.papermc.paper.datacomponent.DataComponentTypes.GLIDER);
        pack.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE,io.papermc.paper.datacomponent.item.Equippable.equippable(EquipmentSlot.OFF_HAND));
        actor.getInventory().setItemInOffHand(pack);
        check(!equipment.toggle(actor)&&actor.getInventory().getItem(1).getAmount()==1,"legacy offhand pack cannot activate or spend coal");
        recipes.migrateIngredient(pack);
        check(!pack.hasData(io.papermc.paper.datacomponent.DataComponentTypes.GLIDER)&&!pack.hasData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE),"legacy ingredient loses old offhand glide components");
        actor.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        var input=new ItemStack[9];input[0]=armor.clone();input[8]=pack.clone();
        var nativeCraft=Bukkit.getServer().craftItemResult(input,actor.getWorld(),actor);
        var upgraded=nativeCraft.getResult();
        check(recipes.upgraded(upgraded),"native 3x3 craft creates Jetpack enchantment");
        check(Arrays.stream(nativeCraft.getResultingMatrix()).allMatch(this::empty),"native 3x3 craft consumes one chestplate and one pack");
        var stripped=upgraded.clone();stripped.removeEnchantment(enchant);stripped=recipes.cleanRemovedEnchantment(stripped);
        check(stripped.equals(armor),"craft preserves damage, name, lore, trim, protection, PDC and every original item component");
        check(upgraded.hasData(io.papermc.paper.datacomponent.DataComponentTypes.GLIDER),"crafted chestplate has native glide flag");
        check(upgraded.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE).slot()==EquipmentSlot.CHEST,"native chest equipment and appearance retained");
        for(var material:Material.values())if(material.name().endsWith("_CHESTPLATE")&&material.isItem()) {
            input[0]=new ItemStack(material);var result=Bukkit.getServer().craftItemResult(input,actor.getWorld(),actor);
            check(recipes.upgraded(result.getResult())&&result.getResult().getType()==material,"native craft supports "+material);
        }
        input[0]=armor.clone();input[8]=new ItemStack(Material.ECHO_SHARD);
        check(empty(Bukkit.getServer().craftItemResult(input,actor.getWorld(),actor).getResult()),"unidentified echo shard cannot pay for upgrade");
        input[8]=pack.clone();input[0]=upgraded.clone();
        check(recipes.level(Bukkit.getServer().craftItemResult(input,actor.getWorld(),actor).getResult())==2,"level I chest plus I book combines to level II");
        input[0]=recipes.upgrade(upgraded,1);
        check(empty(Bukkit.getServer().craftItemResult(input,actor.getWorld(),actor).getResult()),"level II chest rejects further upgrades");
        input[0]=armor.clone();input[4]=new ItemStack(Material.DIRT);
        check(empty(Bukkit.getServer().craftItemResult(input,actor.getWorld(),actor).getResult()),"extra ingredient rejects recipe");
        var grid=(org.bukkit.inventory.CraftingInventory)actor.getOpenInventory().getTopInventory();
        grid.setMatrix(new ItemStack[]{armor.clone(),null,null,pack.clone()});
        check(recipes.upgraded(grid.getResult()),"native 2x2 inventory grid prepares upgrade");
        click(0,"PICKUP");
        check(recipes.upgraded(actor.getItemOnCursor())&&Arrays.stream(grid.getMatrix()).allMatch(this::empty),"actual normal result click transfers upgrade and consumes inputs once");
        actor.setItemOnCursor(null);var doublePack=pack.clone();doublePack.setAmount(2);
        grid.setMatrix(new ItemStack[]{armor.clone(),doublePack,null,null});click(0,"QUICK_MOVE");
        check(Arrays.stream(actor.getInventory().getStorageContents()).filter(recipes::upgraded).count()==1,"actual shift craft produces exactly one upgraded chestplate");
        check(Arrays.stream(grid.getMatrix()).filter(i->!empty(i)).mapToInt(ItemStack::getAmount).sum()==1,"actual shift craft spends only one of two packs");
        click(0,"QUICK_MOVE");
        check(Arrays.stream(actor.getInventory().getStorageContents()).filter(recipes::upgraded).count()==1,"repeated output click cannot duplicate chestplate");
        grid.setMatrix(new ItemStack[4]);
        var view=org.bukkit.inventory.MenuType.GRINDSTONE.create(actor,net.kyori.adventure.text.Component.text("Probe"));actor.openInventory(view);
        view.getTopInventory().setItem(0,upgraded.clone());var ground=view.getTopInventory().getItem(2);
        check(!empty(ground)&&!recipes.upgraded(ground),"native grindstone removes Jetpack enchantment");
        check(!ground.hasData(io.papermc.paper.datacomponent.DataComponentTypes.GLIDER),"native grindstone also removes upgrade-owned glide flag");
        actor.closeInventory();
        var prior=armor.clone();prior.setData(io.papermc.paper.datacomponent.DataComponentTypes.GLIDER);
        var priorUpgrade=recipes.upgrade(prior);priorUpgrade.removeEnchantment(enchant);
        check(recipes.cleanRemovedEnchantment(priorUpgrade).hasData(io.papermc.paper.datacomponent.DataComponentTypes.GLIDER),"removing upgrade preserves a preexisting independent glide flag");
        return upgraded;
    }
    private void bookAndTooltipProof(ItemStack armor)throws Exception {
        var service=equipment.chestplates();var enchant=Registry.ENCHANTMENT.get(JetpackChestplate.KEY);
        check(enchant.getMaxLevel()==2,"native enchantment is capped at level II");
        var prototype=main.items().create(BobaEquipment.JETPACK);
        check(prototype.getType()==Material.ENCHANTED_BOOK&&(prototype.getItemMeta().getItemModel()==null||NamespacedKey.minecraft("enchanted_book").equals(prototype.getItemMeta().getItemModel())),"drop catalog uses vanilla enchanted book, no jetpack model");
        var drop=actor.getWorld().dropItem(actor.getLocation(),prototype);
        check(drop.getItemStack().getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta m&&m.getStoredEnchantLevel(enchant)==1,"actual drop spawn receives real stored Jetpack I");
        drop.remove();var first=service.book(1);service.maintain(first);var stable=first.clone();service.maintain(first);
        check(stable.equals(first),"book maintenance does not resend changing metadata each tick");
        var second=service.book(1);
        var inputs=new ItemStack[9];inputs[0]=first;inputs[7]=second;
        var combined=Bukkit.getServer().craftItemResult(inputs,actor.getWorld(),actor);
        check(service.bookLevel(combined.getResult())==2,"native crafting combines two I books into II");
        check(Arrays.stream(combined.getResultingMatrix()).allMatch(this::empty),"native II book craft consumes both I books exactly once");
        inputs[0]=combined.getResult();inputs[7]=service.book(2);
        check(empty(Bukkit.getServer().craftItemResult(inputs,actor.getWorld(),actor).getResult()),"two II books cannot create III");
        inputs[0]=armor.clone();inputs[7]=service.book(2);
        check(service.level(Bukkit.getServer().craftItemResult(inputs,actor.getWorld(),actor).getResult())==2,"II book applies level II directly to chestplate");
        var custom=main.items().create("beskar_chestplate");
        check(custom!=null,"actual Beskar chestplate exists for screenshot regression");
        custom.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION,4);
        var original=custom.clone();var upgraded=service.upgrade(custom);
        var lore=upgraded.getItemMeta().lore();
        check(upgraded.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS),"native above-badge enchantment block hidden on catalog armor");
        check(lore.getFirst().equals(original.getItemMeta().lore().getFirst()),"original rarity/category badge remains the first lore row");
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        int jetpack=-1;int protection=-1;
        for(int i=0;i<lore.size();i++) {
            if(plain.serialize(lore.get(i)).contains("Jetpack"))jetpack=i;
            if(plain.serialize(lore.get(i)).toLowerCase(java.util.Locale.ROOT).contains("protection"))protection=i;
        }
        check(jetpack>0&&protection>jetpack,"Jetpack I and other enchantments display below badges");
        service.maintain(upgraded);var once=upgraded.clone();service.maintain(upgraded);
        check(once.equals(upgraded),"tooltip maintenance never duplicates generated enchantment lines");
        upgraded.removeEnchantment(enchant);upgraded=service.cleanRemovedEnchantment(upgraded);
        check(upgraded.equals(original),"removing Jetpack restores original lore, flags and components exactly");
        var legacy=new ItemStack(Material.ECHO_SHARD);var meta=legacy.getItemMeta();
        meta.getPersistentDataContainer().set(net.mandomc.gameplay.items.ItemCatalog.ITEM_ID,org.bukkit.persistence.PersistentDataType.STRING,BobaEquipment.JETPACK);legacy.setItemMeta(meta);
        check(service.maintain(legacy)&&legacy.getType()==Material.ENCHANTED_BOOK&&service.bookLevel(legacy)==1,"legacy model tokens migrate into native I books");
        var grid=(CraftingInventory)actor.getOpenInventory().getTopInventory();
        var left=service.book(1);left.setAmount(2);var right=service.book(1);right.setAmount(2);
        grid.setMatrix(new ItemStack[]{left,right,null,null});click(0,"PICKUP");
        check(service.bookLevel(actor.getItemOnCursor())==2,"actual 2x2 output click crafts II book");
        check(Arrays.stream(grid.getMatrix()).filter(i->!empty(i)).mapToInt(ItemStack::getAmount).sum()==2,"actual book click consumes one from each input stack");
        actor.setItemOnCursor(null);click(0,"QUICK_MOVE");
        check(Arrays.stream(grid.getMatrix()).allMatch(this::empty),"shift book crafting consumes the remaining pair");
        check(Arrays.stream(actor.getInventory().getStorageContents()).anyMatch(i->service.bookLevel(i)==2),"shift result delivers real II book");
        var grind=MenuType.GRINDSTONE.create(actor,net.kyori.adventure.text.Component.text("Book removal"));actor.openInventory(grind);
        grind.getTopInventory().setItem(0,service.book(1));var plainBook=grind.getTopInventory().getItem(2);
        check(plainBook!=null&&plainBook.getType()==Material.BOOK,"native grindstone turns I book into ordinary book");
        service.maintain(plainBook);
        check(plainBook.getType()==Material.BOOK&&service.bookLevel(plainBook)==0,"maintenance cannot resurrect a removed book enchantment");
        actor.closeInventory();
    }
    private Player nativePlayer(Location location)throws Exception {
        Class<?> serverClass=Class.forName("net.minecraft.server.MinecraftServer"),levelClass=Class.forName("net.minecraft.server.level.ServerLevel"),
                profileClass=Class.forName("com.mojang.authlib.GameProfile"),infoClass=Class.forName("net.minecraft.server.level.ClientInformation"),playerClass=Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());Object level=location.getWorld().getClass().getMethod("getHandle").invoke(location.getWorld());
        Object profile=profileClass.getConstructor(UUID.class,String.class).newInstance(UUID.randomUUID(),"BobaStartupTest");
        Object handle=playerClass.getConstructor(serverClass,levelClass,profileClass,infoClass).newInstance(server,level,profile,infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass=Class.forName("net.minecraft.network.protocol.PacketFlow"),connectionClass=Class.forName("net.minecraft.network.Connection"),cookieClass=Class.forName("net.minecraft.server.network.CommonListenerCookie"),listenerClass=Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object connection=connectionClass.getConstructor(flowClass).newInstance(flowClass.getField("SERVERBOUND").get(null));Object cookie=cookieClass.getMethod("createInitial",profileClass,boolean.class).invoke(null,profile,false);
        Object listener=listenerClass.getConstructor(serverClass,connectionClass,playerClass,cookieClass).newInstance(server,connection,handle,cookie);playerClass.getField("connection").set(handle,listener);
        var timer=listenerClass.getDeclaredField("clientLoadedTimeoutTimer");timer.setAccessible(true);timer.setInt(listener,0);var waiting=listenerClass.getDeclaredField("waitingForRespawn");waiting.setAccessible(true);waiting.setBoolean(listener,false);
        playerClass.getMethod("setPos",double.class,double.class,double.class).invoke(handle,location.getX(),location.getY(),location.getZ());
        Object list=serverClass.getMethod("getPlayerList").invoke(server);
        for(Class<?> c=list.getClass();c!=null;c=c.getSuperclass())try{var map=c.getDeclaredField("playersByUUID");map.setAccessible(true);((Map<UUID,Object>)map.get(list)).put((UUID)profileClass.getMethod("id").invoke(profile),handle);break;}catch(NoSuchFieldException ignored){}
        return (Player)playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
    private interface Work {void run()throws Exception;}
    private void later(int ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"JETPACK CHESTPLATE SMOKE FAILED",error);cleanup();Bukkit.shutdown();}},ticks);}
    private void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;getLogger().info("PASS "+why);}
    private void cleanup(){if(equipment!=null&&actor!=null)equipment.detach(actor);if(main!=null&&main.combat()!=null&&actor!=null)main.combat().clear(actor);if(target!=null)target.remove();}
    @Override public void onDisable(){cleanup();}
}
