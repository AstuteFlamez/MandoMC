package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.inventory.StonecutterMenu;
import net.mandomc.bootstrap.MandoPlugin;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.core.compatibility.HitBox;
import org.bukkit.event.entity.EntityDeathEvent;
import net.mandomc.gameplay.crates.CrateService;
import net.mandomc.gameplay.dungeon.*;
import net.mandomc.gameplay.events.*;
import net.mandomc.gameplay.spawners.MobSpawners;
import net.mandomc.gameplay.trophies.RancorTrophies;
import net.mandomc.integrations.mobs.MythicMobAccess;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Uses real Paper entities, inventories and persistence in a disposable loopback runtime only. */
public final class SpawnersEventFixesProbe extends JavaPlugin {
    private MandoPlugin mando;private NativeProbePlayers actors;private Player player,observer;
    private World world;private MythicMobAccess mobs;private MobSpawners spawners;private LivingEntity gray;
    private JabbaEvent jabba;private int checks;private Inventory shop;
    @FunctionalInterface private interface Work {void run() throws Exception;}
    private void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);checks++;}
    private void later(int ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->{try{work.run();}catch(Throwable e){finish(e);}},ticks);}
    private void until(BooleanSupplier ready,Work work){waitFor(ready,work,0);}
    private void waitFor(BooleanSupplier ready,Work work,int age){later(2,()->{if(ready.getAsBoolean())work.run();else if(age>300)throw new AssertionError("Async operation timeout");else waitFor(ready,work,age+2);});}
    private Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void invoke(Object o,String name)throws Exception{var m=o.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}
    private String plain(Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
    @Override public void onEnable(){later(180,()->{if(Boolean.getBoolean("mandomc.feedback.restart"))restart();else start();});}
    private void restart()throws Exception{
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        check(Bukkit.getPort()==25591,"Disposable restart port");
        check(mando!=null&&mando.isEnabled(),"Mando enabled after actual JVM restart");
        var dev=Bukkit.getWorld(NamespacedKey.minecraft("devworld"));
        for(int i=0;i<3;i++){
            var block=dev.getBlockAt(-199+i*7,200,-204);
            check(block.getState() instanceof CreatureSpawner,"Spawner block survives restart "+i);
            check(List.of("dewback_spawner","bantha_spawner","jawa_spawner").get(i).equals(((CreatureSpawner)block.getState()).getPersistentDataContainer().get(MobSpawners.TYPE,PersistentDataType.STRING)),"Spawner species survives restart "+i);
        }
        var hoth=Bukkit.getWorld(NamespacedKey.minecraft("hoth"));
        check("massiff_spawner".equals(((CreatureSpawner)hoth.getBlockAt(100,200,100).getState()).getPersistentDataContainer().get(MobSpawners.TYPE,PersistentDataType.STRING)),"Hoth spawner persists across restart");
        check(((Map<?,?>)field(field(mando,"spawners"),"sites")).size()>=4,"Persisted loaded spawners resume tracking");
        getLogger().info("SPAWNERS RESTART PASSED: "+checks+" assertions");Bukkit.shutdown();
    }
    private void start()throws Exception{
        check(Bukkit.getPort()==25591,"Disposable port");
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando!=null&&mando.isEnabled(),"Mando enabled");
        world=Bukkit.getWorld(NamespacedKey.minecraft("devworld"));world.getWorldBorder().setCenter(0,0);world.getWorldBorder().setSize(1024);
        for(int x=-14;x<-10;x++)for(int z=-14;z<-10;z++)world.getChunkAt(x,z).addPluginChunkTicket(this);
        for(int x=-214;x<-170;x++)for(int z=-214;z<-170;z++){
            world.getBlockAt(x,199,z).setType(Material.STONE,false);
            for(int y=200;y<207;y++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        actors=new NativeProbePlayers("SpawnerFix");player=actors.create(new Location(world,-200.5,200,-200.5));observer=actors.create(new Location(world,-190.5,200,-190.5));
        player.setGameMode(GameMode.SURVIVAL);player.setInvulnerable(false);mando.ranks().apply(player);
        mobs=new MythicMobAccess();spawners=(MobSpawners)field(mando,"spawners");
        recipes();stonecutter();trophyNames();placeSpawners();experience();
        mando.combat().clear(player);
        mando.credits().auctionTask(()->{mando.credits().ledger().adjust(player.getUniqueId(),"set",1_000_000_000L);return null;},(v,e)->{
            if(e!=null){finish(e);return;}later(1,this::singlePurchase);
        });
    }
    private void recipes(){
        var matrix=new ItemStack[9];matrix[1]=new ItemStack(Material.BOOK);matrix[3]=mando.items().create("durasteel");matrix[5]=mando.items().create("durasteel");
        for(int i:List.of(4,6,7,8))matrix[i]=new ItemStack(Material.OBSIDIAN);
        var recipe=Bukkit.getCraftingRecipe(matrix,world);check(recipe!=null&&recipe.getResult().getType()==Material.ENCHANTING_TABLE,"Durasteel enchanting table recipe matches real crafting");
        var crafting=player.openWorkbench(null,true);var inventory=(CraftingInventory)crafting.getTopInventory();inventory.setMatrix(matrix);inventory.setResult(recipe.getResult());
        Bukkit.getPluginManager().callEvent(new PrepareItemCraftEvent(inventory,crafting,false));
        check(inventory.getResult()!=null&&inventory.getResult().getType()==Material.ENCHANTING_TABLE,"Survival and item handlers keep enchanting table result");player.closeInventory();
    }
    private ClientboundUpdateRecipesPacket lastRecipePacket()throws Exception{
        var channels=(Map<UUID,EmbeddedChannel>)field(actors,"channels");var channel=channels.get(player.getUniqueId());channel.runPendingTasks();
        ClientboundUpdateRecipesPacket last=null;Object packet;
        while((packet=channel.readOutbound())!=null)if(packet instanceof ClientboundUpdateRecipesPacket recipes)last=recipes;
        check(last!=null,"Filtered recipe packet sent");return last;
    }
    private void stonecutter()throws Exception{
        var server=((CraftServer)Bukkit.getServer()).getServer();var context=SlotDisplayContext.fromLevel(server.overworld());
        var cp=((CraftPlayer)player).getHandle();var view=(org.bukkit.inventory.view.StonecutterView)player.openStonecutter(null,true);
        for(String id:List.of("obiwan","vader","quigon","windu","ventress_yellow","ahsoka_tano_white_lightsaber")){
            var input=mando.items().create(id);view.getTopInventory().setInputItem(input);
            var nativeInput=CraftItemStack.asNMSCopy(input);var nativeMenu=(StonecutterMenu)cp.containerMenu;
            var actual=nativeMenu.getVisibleRecipes().entries();check(!actual.isEmpty(),"Allowed options remain for "+id);
            var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),server.registryAccess());
            ClientboundUpdateRecipesPacket.STREAM_CODEC.encode(buffer,lastRecipePacket());
            var decoded=ClientboundUpdateRecipesPacket.STREAM_CODEC.decode(buffer);buffer.release();
            var visible=decoded.stonecutterRecipes().selectByInput(nativeInput).entries();
            check(visible.size()==actual.size(),"Client/server filtered size agrees for "+id);
            for(int n=0;n<actual.size();n++){
                var bukkit=view.getRecipes().get(n);
                var definition=((net.mandomc.gameplay.items.CraftingRecipes)field(mando,"craftingRecipes")).definitions().stream()
                        .filter(r->("refinish_"+r.id()).equals(bukkit.getKey().getKey())).findFirst().orElseThrow();
                check(input.isSimilar(mando.items().create(definition.ingredients().get('I'))),"Only compatible input appears "+id+" "+n);
                var shown=CraftItemStack.asBukkitCopy(visible.get(n).recipe().optionDisplay().resolveForFirstStack(context));
                nativeMenu.clickMenuButton(cp,n);
                check(shown.isSimilar(view.getTopInventory().getResult()),"Actual selection matches filtered wire index "+id+" "+n);
            }
            var damaged=input.clone();var meta=(org.bukkit.inventory.meta.Damageable)damaged.getItemMeta();meta.setDamage(1);damaged.setItemMeta(meta);
            view.getTopInventory().setInputItem(damaged);check(view.getRecipeAmount()==0,"Damaged saber hides all options "+id);
            var packet=lastRecipePacket();check(packet.stonecutterRecipes().selectByInput(CraftItemStack.asNMSCopy(damaged)).isEmpty(),"Damaged input hidden from client too "+id);
        }
        view.getTopInventory().setInputItem(new ItemStack(Material.STONE,3));check(view.getRecipeAmount()>0,"Vanilla stone options retained");
        var nativeMenu=(StonecutterMenu)cp.containerMenu;nativeMenu.clickMenuButton(cp,0);check(view.getTopInventory().getResult()!=null,"Vanilla selection retained");
        player.closeInventory();var restored=lastRecipePacket();
        check(restored.stonecutterRecipes().size()==server.getRecipeManager().stonecutterRecipes().size(),"Complete recipe list restored on close");
    }
    private void trophyNames()throws Exception{
        var trophies=Bukkit.getServicesManager().load(RancorTrophies.class);check(trophies!=null,"Decoration owner available");
        var tell=RancorTrophies.class.getDeclaredMethod("tell",Player.class,String.class,ItemStack.class);tell.setAccessible(true);
        for(String id:List.of("rancor_skull","durasteel_helmet","beskar_helmet","robes_helmet")){
            var item=mando.items().create(id);actors.drain(player);
            tell.invoke(trophies,player,"placed",item);tell.invoke(trophies,player,"returned",item);
            var messages=actors.drain(player);var name=plain(item.getItemMeta().displayName());
            check(messages.size()==2&&messages.stream().allMatch(c->plain(c).contains(name)),"Actual saved name in place/return "+id);
        }
    }
    private void placeSpawners()throws Exception{
        int offset=0;
        for(String id:List.of("massiff_spawner","dewback_spawner","bantha_spawner","jawa_spawner")){
            var block=world.getBlockAt(-206+offset,200,-204);var before=block.getState();block.setType(Material.SPAWNER,false);
            var item=mando.items().create(id);var event=new BlockPlaceEvent(block,before,block.getRelative(0,-1,0),item,player,true,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(event);check(!event.isCancelled(),"Wilderness placement permitted "+id);
            check(id.equals(((CreatureSpawner)block.getState()).getPersistentDataContainer().get(MobSpawners.TYPE,PersistentDataType.STRING)),"Spawner type persists "+id);
            offset+=7;
        }
        var sites=(Map<?,Integer>)field(spawners,"sites");for(int attempt=0;attempt<5;attempt++){sites.replaceAll((k,v)->0);invoke(spawners,"tick");}
        for(String type:List.of("Massiff","Dewback","Bantha","JawaBrown"))check(world.getLivingEntities().stream().anyMatch(e->mobs.type(e).equals(type)),"Spawner creates real themed mob "+type);
        for(var entity:world.getLivingEntities())if(MobSpawners.TYPES.containsValue(mobs.type(entity)))mobs.remove(entity);
        var block=world.getBlockAt(-206,200,-204);player.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        var denied=new BlockBreakEvent(block,player);Bukkit.getPluginManager().callEvent(denied);check(denied.isCancelled(),"Non-pickaxe cannot lose spawner");
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE_PICKAXE));var broken=new BlockBreakEvent(block,player);Bukkit.getPluginManager().callEvent(broken);
        check(!broken.isCancelled()&&!broken.isDropItems()&&broken.getExpToDrop()==0,"Pickaxe recovers spawner without native XP/drop duplication");block.setType(Material.AIR,false);
    }
    private void experience()throws Exception{
        var seen=new HashMap<UUID,Integer>();var listener=new Listener(){};
        Bukkit.getPluginManager().registerEvent(EntityDeathEvent.class,listener,EventPriority.MONITOR,(l,event)->{
            var death=(EntityDeathEvent)event;seen.put(death.getEntity().getUniqueId(),death.getDroppedExp());
        },this);
        var at=player.getLocation().clone().add(0,0,8);
        var melee=mobs.spawn("Massiff",at,1);melee.setAI(false);melee.damage(2000,player);
        check(melee.isDead()&&Objects.equals(seen.get(melee.getUniqueId()),8),"Player melee kill drops configured native XP once");
        var gun=mobs.spawn("Massiff",at,1);gun.setAI(false);gun.setHealth(1);
        var wm=WeaponMechanics.getInstance();var definition=wm.getWeaponConfigurations().getObject("E11.Projectile",Projectile.class);
        var origin=player.getLocation().add(0,.5,0);var direction=gun.getLocation().add(0,.5,0).toVector().subtract(origin.toVector()).normalize();
        var bolt=definition.create(player,origin,direction,mando.items().create("e11"),"E11",EquipmentSlot.HAND);
        try {
            var box=HitBox.getHitbox(gun.getBoundingBox());box.setLivingEntity(gun);var hit=box.rayTrace(origin.toVector(),direction);
            check(hit!=null,"Real E11 projectile intersects custom mob");wm.getWeaponHandler().getHitHandler().handleHit(hit,bolt);
            check(gun.isDead()&&Objects.equals(seen.get(gun.getUniqueId()),8),"Real player gun kill drops the same native XP: dead="+gun.isDead()+" xp="+seen.get(gun.getUniqueId())+" health="+gun.getHealth()+" killer="+gun.getKiller());
        } finally {bolt.remove();}
        var environmental=mobs.spawn("Massiff",at,1);environmental.setAI(false);environmental.damage(2000);
        check(environmental.isDead()&&Objects.equals(seen.get(environmental.getUniqueId()),0),"Environmental mob death gives no player XP");
        HandlerList.unregisterAll(listener);
    }
    private void singlePurchase()throws Exception{
        gray=mobs.spawn("JawaGray",player.getLocation().clone().add(2,0,0),1);
        var config=(YamlConfiguration)field(mando.jawas(),"config");config.set("npcs.feedback.category","minerals");
        gray.getPersistentDataContainer().set(new NamespacedKey(mando,"jawa_npc_v1"),PersistentDataType.STRING,"feedback");
        mando.jawas().open(player,gray);shop=player.getOpenInventory().getTopInventory();
        check(plain(player.getOpenInventory().title()).contains("Spawners"),"Spawners menu title");
        for(int i=0;i<4;i++)check(List.of("massiff_spawner","dewback_spawner","bantha_spawner","jawa_spawner").get(i).equals(mando.items().identify(shop.getItem(i))),"Requested shop order "+i);
        var busy=(Set<UUID>)field(mando.credits().auctions(),"busy");
        click(0,ClickType.LEFT);
        until(()->count("massiff_spawner")==1&&!busy.contains(player.getUniqueId()),()->{
            check(player.getOpenInventory().getTopInventory()==shop,"Normal purchase keeps inventory open");
            check(mando.credits().ledger().balance(player.getUniqueId())==990_000_000L,"One item charged exact price");
            click(0,ClickType.SHIFT_LEFT);
            until(()->count("massiff_spawner")==65&&!busy.contains(player.getUniqueId()),this::afterStack);
        });
    }
    private int count(String id){return Arrays.stream(player.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->id.equals(mando.items().identify(i))).mapToInt(ItemStack::getAmount).sum();}
    private void click(int slot,ClickType type){var e=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,type,type==ClickType.SHIFT_LEFT?InventoryAction.MOVE_TO_OTHER_INVENTORY:InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(e);check(e.isCancelled(),"Menu transfer cancelled");}
    private void afterStack()throws Exception{
        check(player.getOpenInventory().getTopInventory()==shop,"Stack purchase keeps inventory open");
        check(mando.credits().ledger().balance(player.getUniqueId())==350_000_000L,"Stack charged 64 unit prices");
        click(3,ClickType.SHIFT_LEFT);later(12,()->{
            check(count("jawa_spawner")==0&&mando.credits().ledger().balance(player.getUniqueId())==350_000_000L,"Unaffordable stack neither charged nor delivered");
            check(player.getOpenInventory().getTopInventory()==shop,"Failed purchase keeps inventory open");
            for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
            click(0,ClickType.LEFT);check(mando.credits().ledger().balance(player.getUniqueId())==350_000_000L,"Full inventory does not debit purchase");
            check(player.getOpenInventory().getTopInventory()==shop,"Full-inventory denial keeps shop open");
            player.getInventory().clear();player.closeInventory();koth();palace();
        });
    }
    private void koth()throws Exception{
        var event=(KothEvent)mando.events().event("koth");var settings=event.settings();var hoth=Bukkit.getWorld(NamespacedKey.fromString(settings.world()));
        mando.combat().clear(player);
        player.teleport(new Location(hoth,100.5,200,100.5));
        for(int x=94;x<=106;x++)for(int z=94;z<=106;z++){
            hoth.getBlockAt(x,199,z).setType(Material.STONE,false);
            for(int y=200;y<=204;y++)hoth.getBlockAt(x,y,z).setType(Material.AIR,false);
        }
        var site=hoth.getBlockAt(100,200,100);var original=site.getState();site.setType(Material.SPAWNER,false);
        var place=new BlockPlaceEvent(site,original,site.getRelative(0,-1,0),mando.items().create("massiff_spawner"),player,true,EquipmentSlot.HAND);
        spawners.validate(place);check(!place.isCancelled(),"Hoth Wilderness permits purchased spawners");spawners.place(place);
        for(int attempt=0;attempt<5;attempt++){((Map<?,Integer>)field(spawners,"sites")).replaceAll((k,v)->0);invoke(spawners,"tick");}
        check(hoth.getNearbyEntities(site.getLocation(),8,5,8).stream().anyMatch(e->mobs.type(e).equals("Massiff")),"Scoped spawner creates real mob on Hoth");
        var natural=hoth.spawn(site.getLocation().add(6,0,6),Cow.class);
        check(!natural.isValid(),"Hoth still denies unrelated natural/custom creature spawns");
        for(var entity:hoth.getNearbyEntities(site.getLocation(),8,5,8))if(mobs.type(entity).equals("Massiff"))mobs.remove(entity);
        mando.combat().clear(player);
        // Socket-free actors do not complete cross-dimension teleport acknowledgements. Use a native actor in the destination.
        observer=actors.create(new Location(world,-190.5,200,-190.5));mando.ranks().apply(observer);
        Bukkit.getPluginManager().subscribeToPermission(org.bukkit.Server.BROADCAST_CHANNEL_USERS,observer);
        check(Bukkit.getOnlinePlayers().contains(observer),"Observer participates in global online audience");
        player=actors.create(new Location(hoth,settings.x(),settings.y(),settings.z()));
        player.setGameMode(GameMode.SURVIVAL);player.setInvulnerable(false);mando.ranks().apply(player);player.getInventory().clear();actors.drain(player);actors.drain(observer);
        check(event.eligible(player),"Fresh native hill player eligible: online="+player.isOnline()+" invulnerable="+player.isInvulnerable()+" mode="+player.getGameMode());
        event.start();for(int i=0;i<=settings.captureTicks()/10;i++)event.sample(List.of(player));
        check(!event.running()&&player.getUniqueId().equals(event.winner()),"KOTH completed exactly once: running="+event.running()+" winner="+event.winner()+" eligible="+event.eligible(player)+" holder="+event.capture().holder()+" ticks="+event.capture().ticks()+" location="+player.getLocation()+" dead="+player.isDead());
        check(count("koth_crate_key")==1,"Physical key is immediately in winner inventory");
        var messages=actors.drain(observer);check(messages.stream().anyMatch(c->plain(c).contains("captured the canyon")),"Observer in another world receives global victory: "+messages.stream().map(this::plain).toList());
        check(messages.stream().anyMatch(c->plain(c).contains("received one KOTH Crate Key")),"Global reward names recipient");
        var crates=Bukkit.getServicesManager().load(CrateService.class);player.getPersistentDataContainer().set(new NamespacedKey("mandomc","crate_keys_koth"),PersistentDataType.INTEGER,2);
        check(crates.keys(player,crates.definitions().get("koth"))==3,"Existing virtual keys remain usable after physical migration");
        for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Material.STONE,64));
        event.start();for(int i=0;i<=settings.captureTicks()/10;i++)event.sample(List.of(player));
        check(count("koth_crate_key")==0,"Full inventory retains physical award in durable mail");
        player.getInventory().clear();crates.claim(player);check(count("koth_crate_key")==1,"Saved full-inventory KOTH key can be collected");
        crates.claim(player);check(count("koth_crate_key")==1,"Repeated key claim cannot duplicate award");
    }
    private void palace()throws Exception{
        jabba=(JabbaEvent)mando.events().event("jabba");var pos=jabba.settings().rooms().getFirst().spawns().getFirst();
        var target=pos.location(Bukkit.getWorld(NamespacedKey.fromString(jabba.settings().world())));
        player=actors.create(target);player.setGameMode(GameMode.SURVIVAL);player.setInvulnerable(true);mando.ranks().apply(player);
        check(mando.events().start("jabba"),"Real palace starts");later(35,()->{
            var owned=new ArrayList<>(jabba.progress().mobs());check(!owned.isEmpty(),"Palace room mobs exist before event end");
            var blueprint=YamlConfiguration.loadConfiguration(mando.getDataFolder().toPath().resolve(jabba.settings().blueprints()).toFile());
            var cell=blueprint.getStringList("gates."+jabba.settings().rooms().getFirst().gate()+".blocks").stream().filter(row->!row.endsWith("minecraft:air")).findFirst().orElseThrow().split("\\|",2)[0];
            var gate=JabbaSettings.Pos.parse(cell).location(player.getWorld()).getBlock();
            check(gate.getType().isAir(),"Entry gate is open before ending palace");
            Location before=player.getLocation();jabba.stop(true);
            check(gate.getType().isAir(),"Entry gate stays open at event end");
            check(!jabba.running()&&jabba.evacuating(),"Ended palace has independent exit window");
            check(owned.stream().allMatch(id->Bukkit.getEntity(id)==null||!Bukkit.getEntity(id).isValid()),"Palace mobs despawn immediately");
            check(player.getLocation().distanceSquared(before)<.01,"Player remains in palace at event end");
            var scheduled=field(jabba,"extraction");check(scheduled!=null,"Delayed extraction is scheduled");
            long deadline=(Long)field(scheduled,"nextRun");
            check(deadline-Bukkit.getCurrentTick()>=5999 && deadline-Bukkit.getCurrentTick()<=6000,"Actual extraction task is scheduled 6000 ticks later");
            var config=YamlConfiguration.loadConfiguration(mando.getDataFolder().toPath().resolve("events/jabba.yml").toFile());check(config.getInt("exit-delay-seconds")==300,"Five minute exit interval");
            later(40,()->{
                check(gate.getType().isAir(),"Entry gate stays open throughout exit window");
                check(jabba.evacuating()&&jabba.settings().contains(player.getLocation()),"Exit window survives event scheduler ticks");
                ((Runnable)field(scheduled,"rTask")).run();
                ((org.bukkit.scheduler.BukkitTask)scheduled).cancel();
                check(!jabba.evacuating()&&!jabba.settings().contains(player.getLocation()),"Delayed cleanup extracts player");
                check(!gate.getType().isAir(),"Delayed cleanup restores closed entry gate");
                check(jabba.progress().phase()==DungeonProgress.Phase.IDLE,"Palace resets after exit window");
                finish(null);
            });
        });
    }
    private void finish(Throwable error){
        try{if(jabba!=null)jabba.close();if(gray!=null&&gray.isValid())mobs.remove(gray);if(actors!=null)actors.close();}catch(Throwable cleanup){if(error==null)error=cleanup;}
        if(error==null){getLogger().info("SPAWNERS EVENT FIXES PASSED: "+checks+" assertions");try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),checks+" native assertions\n");}catch(Exception e){throw new RuntimeException(e);}}
        else getLogger().log(java.util.logging.Level.SEVERE,"SPAWNERS EVENT FIXES FAILED",error);
        Bukkit.shutdown();
    }
}
