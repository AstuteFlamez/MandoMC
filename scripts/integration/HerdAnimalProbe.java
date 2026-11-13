package net.mandomc.verification;

import com.ticxo.modelengine.api.ModelEngineAPI;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.bukkit.BukkitAdapter;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import net.mandomc.gameplay.mobs.HerdSettings;
import net.mandomc.integrations.mobs.*;
import net.mandomc.integrations.models.HerdModels;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Destructive fixture: own world/port guard, real native actors, actual horse menus and mount controllers. */
public final class HerdAnimalProbe extends JavaPlugin implements Listener {
    private final List<String> checks = new ArrayList<>();
    private final AtomicBoolean allowed = new AtomicBoolean(true);
    private boolean cancelTame, cancelBreed;
    private NativeProbePlayers players;
    private Player owner, other;
    private MythicMobAccess mobs;
    private HerdModels models;
    private HerdAnimals animals;
    private HerdBabies babies;
    private HerdTempt tempt;
    private net.mandomc.gameplay.mobs.SandySpawns spawns;
    private Horse first, second, dewback;
    private Location point;
    private final Map<UUID,List<ItemStack>> deaths = new HashMap<>();

    @Override public void onEnable() {
        if (Bukkit.getPort() != 25576 || !Bukkit.getWorlds().getFirst().getName().equals("herd_test")) {
            getLogger().severe("Requires disposable herd_test world on port 25576");
            Bukkit.getPluginManager().disablePlugin(this); return;
        }
        later(120, this::start);
    }
    private void start() throws Exception {
        mobs = new MythicMobAccess(); babies = new HerdBabies(this, mobs); models = new HerdModels(this);
        var config = new YamlConfiguration();
        config.set("schema-version", 1); config.set("bantha-food", "HAY_BLOCK"); config.set("dewback-food", "CACTUS");
        config.set("tame-chance", 1); // Deterministic attempts; production default remains 1/3.
        var lang = new YamlConfiguration();
        for (String key : List.of("tamed","owner","young","protected","feed")) lang.set(key,key);
        animals = new HerdAnimals(this, mobs, babies, models, HerdSettings.read(config), lang, l -> allowed.get());
        tempt = new HerdTempt(this,mobs,Material.HAY_BLOCK,Material.CACTUS,animals);
        Bukkit.getPluginManager().registerEvents(this,this);
        try (var reader = new java.io.InputStreamReader(Objects.requireNonNull(getResource("mobs/spawning.yml")))) {
            var spawnConfig=YamlConfiguration.loadConfiguration(reader); spawnConfig.set("spawn.chance",0);
            spawns=new net.mandomc.gameplay.mobs.SandySpawns(this,net.mandomc.gameplay.mobs.SandySettings.read(spawnConfig),mobs);
        }
        var world = Bukkit.getWorlds().getFirst(); point = new Location(world,100.5,20,100.5);
        for (int x=80;x<=120;x++) for (int z=80;z<=120;z++) {
            world.getBlockAt(x,19,z).setType(Material.SANDSTONE);
            for (int y=20;y<=26;y++) world.getBlockAt(x,y,z).setType(Material.AIR);
        }
        world.getChunkAt(point).addPluginChunkTicket(this);
        players = new NativeProbePlayers("HerdTest"); owner=players.create(point.clone().add(0,0,5)); other=players.create(point.clone().add(2,0,5));
        var saved = getDataFolder().toPath().resolve("animal-restart.txt");
        if (Files.exists(saved)) { later(20, this::restart); return; }
        later(20,this::spawnAnimals);
    }
    private void spawnAnimals() throws Exception {
        var world=point.getWorld();
        for (var e : world.getLivingEntities()) if (animals.herd(e)) mobs.remove(e);
        first=(Horse)mobs.spawn("Bantha",point,3); second=(Horse)mobs.spawn("Bantha",point.clone().add(2.75,0,0),2);
        dewback=(Horse)mobs.spawn("Dewback",point.clone().add(-5,0,0),2);
        first.setAI(false); second.setAI(false); dewback.setAI(false);
        later(20,this::feeding);
    }
    private void feeding() throws Exception {
        check(first.isAdult() && !first.isTamed(),"wild horse base stays adult and untamed");
        check(model(first).getMountManager().orElseThrow().getDriverBone()!=null,"Bantha driver locator loads");
        check(model(dewback).getMountManager().orElseThrow().getDriverBone()!=null,"Dewback driver locator loads");
        check(!model(first).getBones().get("saddle").isVisible(),"wild saddle is hidden");
        check(!models.occupied(first),"empty ModelEngine seat is not an animal rider");
        owner.getInventory().setItemInMainHand(new ItemStack(Material.HAY_BLOCK,16));
        cancelTame=true;
        check(!animals.feed(owner,first,EquipmentSlot.HAND)&&!first.isTamed()&&food()==16,"cancelled tame consumes nothing"); cancelTame=false;
        allowed.set(false); check(!animals.feed(owner,first,EquipmentSlot.HAND)&&food()==16,"protected tame consumes nothing"); allowed.set(true);
        var a=new PlayerInteractEntityEvent(owner,first,EquipmentSlot.HAND); Bukkit.getPluginManager().callEvent(a);
        var b=new PlayerInteractEntityEvent(owner,first,EquipmentSlot.OFF_HAND); Bukkit.getPluginManager().callEvent(b);
        check(a.isCancelled()&&b.isCancelled(),"custom feeding replaces vanilla hand actions");
        later(3,this::afterTame);
    }
    private void afterTame() throws Exception {
        check(first.isTamed()&&owner.getUniqueId().equals(first.getOwnerUniqueId()),"food establishes native owner");
        check(food()==15,"main/offhand duplicates consume one food");
        check(owner.getOpenInventory().getTopInventory().getHolder()!=first,"food takes priority over saddle menu");
        other.getInventory().setItemInMainHand(new ItemStack(Material.HAY_BLOCK,2));
        check(!animals.feed(other,first,EquipmentSlot.HAND)&&other.getInventory().getItemInMainHand().getAmount()==2,"other player cannot feed owned animal");
        first.setHealth(first.getAttribute(Attribute.MAX_HEALTH).getValue()-8);
        check(animals.feed(owner,first,EquipmentSlot.HAND)&&food()==14,"healing consumes one food");
        check(first.getHealth()==first.getAttribute(Attribute.MAX_HEALTH).getValue()-4,"configured heal amount applied");
        owner.setGameMode(GameMode.CREATIVE); check(animals.feed(owner,first,EquipmentSlot.HAND)&&food()==14,"Creative healing retains food"); owner.setGameMode(GameMode.ADVENTURE);
        check(animals.feed(owner,second,EquipmentSlot.HAND)&&second.isTamed(),"second parent tames normally");
        check(animals.feed(owner,first,EquipmentSlot.HAND)&&first.isLoveMode(),"healthy adult food enters native love mode");
        int count=food(); check(!animals.feed(owner,first,EquipmentSlot.HAND)&&food()==count,"already-in-love feeding consumes nothing");
        check(animals.feed(owner,second,EquipmentSlot.HAND)&&second.isLoveMode(),"both mature parents enter love mode");
        check(animals.pair(first,second),"same species and owner can pair");
        dewback.setOwner(owner); dewback.setLoveModeTicks(600);
        check(!animals.pair(first,dewback),"different species cannot pair");
        second.setOwner(other); check(!animals.pair(first,second),"different owners cannot pair"); second.setOwner(owner);
        int before=babyBanthas(); cancelBreed=true;
        check(!animals.breed(first,second)&&babyBanthas()==before,"cancelled birth leaves no offspring"); cancelBreed=false;
        allowed.set(false); check(!animals.breed(first,second)&&babyBanthas()==before,"protected birth leaves no offspring"); allowed.set(true);
        check(Bukkit.getMobGoals().hasGoal(first,HerdCourtship.PAIR),"native courtship goal is installed");
        first.setAI(true); second.setAI(true);
        later(85,this::offspring);
    }
    private void offspring() throws Exception {
        check(babyBanthas()==1,"native courtship produces exactly one offspring");
        check(first.getAge()>0&&second.getAge()>0&&!first.isLoveMode()&&!second.isLoveMode(),"both parents receive native breeding cooldown");
        check(!animals.breed(first,second),"cooldown prevents duplicate offspring");
        var child=point.getWorld().getLivingEntities().stream().filter(Horse.class::isInstance).map(Horse.class::cast)
                .filter(h->!h.isAdult()&&"Bantha".equals(mobs.type(h))).findFirst().orElseThrow();
        check(owner.getUniqueId().equals(child.getOwnerUniqueId()),"offspring inherits native owner");
        check(mobs.level(child)==1,"offspring retains the deployed base-stat policy");
        check(model(child).getScale().x()==.5,"bred offspring renders at half scale");
        owner.teleport(child.getLocation().clone().add(0,0,3));
        child.setAge(-10000); int count=food();
        check(animals.feed(owner,child,EquipmentSlot.HAND)&&child.getAge()==-9000&&food()==count-1,"baby feeding advances remaining growth by 10 percent");
        child.teleport(point.clone().add(8,0,0));
        var parent=(HerdCourtship.Parent)Bukkit.getMobGoals().getGoal(child,HerdCourtship.PARENT);
        check(parent!=null&&parent.shouldActivate(),"baby finds same-owner adult to follow"); parent.stop();
        owner.getInventory().setItemInMainHand(new ItemStack(Material.AIR)); owner.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        owner.teleport(first.getLocation().clone().add(0,0,3));
        animals.interact(owner,first);
        check(HerdEquipment.isMenu(owner.getOpenInventory().getTopInventory(),first),"unsaddled right click opens custom equipment GUI");
        check(owner.getOpenInventory().getTopInventory().getSize()==9,"custom GUI has one compact row");
        var denied=new InventoryOpenEvent(other.getOpenInventory());
        other.openInventory(first.getInventory());
        check(other.getOpenInventory().getTopInventory().getHolder()!=first,"native menu rejects non-owner open");
        owner.setItemOnCursor(new ItemStack(Material.DIAMOND)); click(owner,HerdEquipment.SADDLE_SLOT,"PICKUP",0);
        check(!HerdModels.saddled(first)&&owner.getItemOnCursor().getType()==Material.DIAMOND,"custom saddle control rejects wrong item");
        owner.setItemOnCursor(new ItemStack(Material.SADDLE)); click(owner,HerdEquipment.SADDLE_SLOT,"PICKUP",0);
        check(HerdModels.saddled(first)&&owner.getItemOnCursor().isEmpty(),"custom saddle transfer moves one real item");
        owner.setItemOnCursor(new ItemStack(Material.IRON_HORSE_ARMOR));
        check(!click(owner,1,"PICKUP",0)&&first.getInventory().getArmor()==null,"armor slot is denied"); owner.setItemOnCursor(new ItemStack(Material.AIR));
        var drag=new InventoryDragEvent(owner.getOpenInventory(),new ItemStack(Material.AIR),new ItemStack(Material.SADDLE),false,Map.of(HerdEquipment.SADDLE_SLOT,new ItemStack(Material.SADDLE)));
        Bukkit.getPluginManager().callEvent(drag); check(drag.isCancelled(),"top-slot drag cannot bypass equipment policy");
        owner.closeInventory(); later(3,this::riding);
    }
    private void riding() throws Exception {
        check(model(first).getBones().get("saddle").isVisible(),"saddle geometry follows real native slot");
        check(!models.mount(first,other),"other player cannot take driver seat");
        allowed.set(false); check(!models.mount(first,owner),"protected mount is rejected"); allowed.set(true);
        owner.getInventory().setItemInMainHand(new ItemStack(Material.HAY_BLOCK));
        check(!models.mount(first,owner),"held food takes priority over automatic mounting");
        owner.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        check(models.mount(first,owner),"owner mounts saddled adult");
        var manager=model(first).getMountManager().orElseThrow();
        check(owner.equals(manager.getDriver()),"ModelEngine tracks one driver");
        owner.openInventory(first.getInventory()); check(owner.getOpenInventory().getTopInventory().getHolder()!=first,"mounted equipment editing is denied");
        var controller=ModelEngineAPI.getMountPairManager().getController(owner.getUniqueId());
        check(controller!=null,"native walking ride controller is installed");
        controller.setInput(new com.ticxo.modelengine.api.mount.controller.MountController.MountInput(0,1,false,false));
        var modeled=ModelEngineAPI.getModeledEntity(first); controller.updateDriverMovement(modeled.getBase().getMoveController(),model(first));
        check(first.getVelocity().lengthSquared()>0,"rider input produces native movement");
        manager.dismountDriver();
        owner.teleport(first.getLocation().clone().add(0,0,3)); owner.setSneaking(true);
        animals.interact(owner,first); check(HerdEquipment.isMenu(owner.getOpenInventory().getTopInventory(),first),"sneak right click opens equipped menu"); owner.setSneaking(false);
        click(owner,HerdEquipment.SADDLE_SLOT,"PICKUP",0); check(!HerdModels.saddled(first)&&owner.getItemOnCursor().getType()==Material.SADDLE,"removing saddle returns the real item");
        owner.setItemOnCursor(new ItemStack(Material.AIR)); owner.getInventory().setItem(0,new ItemStack(Material.SADDLE,2));
        int raw=owner.getOpenInventory().getTopInventory().getSize()+27;
        click(owner,raw,"QUICK_MOVE",0);
        check(HerdModels.saddled(first)&&Arrays.stream(owner.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->i.getType()==Material.SADDLE).mapToInt(ItemStack::getAmount).sum()==1,"custom shift transfer equips one saddle without duplication");
        owner.closeInventory();
        dewback.getInventory().setSaddle(new ItemStack(Material.SADDLE)); dewback.setAI(true);
        owner.teleport(dewback.getLocation().clone().add(0,0,3));
        check(models.mount(dewback,owner),"Dewback saddle and driver use the same native riding flow");
        model(dewback).getMountManager().orElseThrow().dismountDriver();
        dewback.setHealth(0);
        check(deaths.get(dewback.getUniqueId()).stream().filter(i->i.getType()==Material.SADDLE).mapToInt(ItemStack::getAmount).sum()==1,"adult death drops exactly one equipped saddle");
        check(deaths.get(dewback.getUniqueId()).stream().noneMatch(i->i.getType()==Material.LEATHER),"horse body loot adds no vanilla leather");
        var child=point.getWorld().getLivingEntities().stream().filter(Horse.class::isInstance).map(Horse.class::cast).filter(h->!h.isAdult()&&animals.herd(h)).findFirst().orElseThrow();
        child.getInventory().setSaddle(new ItemStack(Material.SADDLE)); child.setHealth(0);
        check(deaths.get(child.getUniqueId()).isEmpty(),"baby death suppresses even injected saddle equipment");
        first.setAge(1234); first.setLoveModeTicks(0); first.setPersistent(true); mobs.persist(first);
        var pig=point.getWorld().spawn(point.clone().add(0,0,-7),Pig.class);
        var mobManager=MythicBukkit.inst().getMobManager();
        mobManager.registerActiveMob(BukkitAdapter.adapt(pig),mobManager.getMythicMob("Bantha").orElseThrow(),4);
        pig.getAttribute(Attribute.MAX_HEALTH).setBaseValue(100);pig.setHealth(30);pig.setAI(false);
        pig.getPersistentDataContainer().set(new NamespacedKey("mandomc","sandy_encounter_v1"),PersistentDataType.STRING,"legacy_migration");
        pig.setPersistent(true);mobs.persist(pig);
        Files.writeString(getDataFolder().toPath().resolve("animal-restart.txt"),first.getUniqueId()+"\n"+owner.getUniqueId()+"\n"+pig.getUniqueId());
        finish("ANIMALS-PASSED.txt");
    }
    private void restart() throws Exception {
        var data=Files.readAllLines(getDataFolder().toPath().resolve("animal-restart.txt"));
        var horse=(Horse)Bukkit.getEntity(UUID.fromString(data.get(0)));
        check(horse!=null&&horse.isTamed(),"tamed native horse survives restart");
        check(UUID.fromString(data.get(1)).equals(horse.getOwnerUniqueId()),"owner UUID survives restart");
        check(HerdModels.saddled(horse),"one real saddle survives restart");
        check(horse.getAge()>0&&horse.getAge()<=1234,"native breeding cooldown survives restart");
        check(model(horse).getBones().get("saddle").isVisible(),"saddle appearance restores after restart");
        var legacy=Bukkit.getEntity(UUID.fromString(data.get(2)));
        check(legacy instanceof Pig&&"Bantha".equals(mobs.type(legacy)),"saved pig-backed herd survives changed horse definition");
        check(((LivingEntity)legacy).getHealth()==30,"legacy stat normalization does not heal injury");
        owner.teleport(legacy.getLocation().clone().add(0,0,3));
        owner.getInventory().setItemInMainHand(new ItemStack(Material.HAY_BLOCK,2));
        animals.interact(owner,(LivingEntity)legacy);
        later(10,()->{
            check(!legacy.isValid()&&!deaths.containsKey(legacy.getUniqueId()),"legacy conversion removes original without a death reward");
            var converted=point.getWorld().getLivingEntities().stream().filter(Horse.class::isInstance).map(Horse.class::cast)
                    .filter(h->"legacy_migration".equals(h.getPersistentDataContainer().get(new NamespacedKey("mandomc","sandy_encounter_v1"),PersistentDataType.STRING)))
                    .findFirst().orElseThrow();
            check(converted.isTamed()&&owner.getUniqueId().equals(converted.getOwnerUniqueId()),"legacy interaction establishes owner on one replacement");
            check(converted.getHealth()==30&&mobs.level(converted)==1,"conversion preserves injury and base-stat policy after vendor refresh");
            check(!converted.hasAI(),"conversion preserves explicit legacy AI state");
            check(owner.getInventory().getItemInMainHand().getAmount()==1,"legacy conversion consumes exactly one successful food");
            finish("ANIMALS-RESTART-PASSED.txt");
        });
    }
    private com.ticxo.modelengine.api.model.ActiveModel model(Horse h) { return ModelEngineAPI.getModeledEntity(h).getModels().values().iterator().next(); }
    private int food(){return owner.getInventory().getItemInMainHand().getAmount();}
    private int babyBanthas(){return (int)point.getWorld().getLivingEntities().stream().filter(Horse.class::isInstance).map(Horse.class::cast).filter(h->!h.isAdult()&&h.isValid()&&"Bantha".equals(mobs.type(h))).count();}
    @SuppressWarnings({"unchecked","rawtypes"}) private boolean click(Player player,int raw,String input,int button)throws Exception{
        var action=input.equals("QUICK_MOVE")?InventoryAction.MOVE_TO_OTHER_INVENTORY:InventoryAction.SWAP_WITH_CURSOR;
        var event=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,raw,input.equals("QUICK_MOVE")?ClickType.SHIFT_LEFT:ClickType.LEFT,action);
        Bukkit.getPluginManager().callEvent(event); if(event.isCancelled())return false;
        Object handle=player.getClass().getMethod("getHandle").invoke(player); Object menu=handle.getClass().getField("containerMenu").get(handle);
        Class<?> type=Class.forName("net.minecraft.world.inventory.ContainerInput"),actor=Class.forName("net.minecraft.world.entity.player.Player");
        menu.getClass().getMethod("clicked",int.class,int.class,type,actor).invoke(menu,raw,button,Enum.valueOf((Class)type,input),handle);return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void tame(EntityTameEvent e){if(cancelTame)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void birth(EntityBreedEvent e){if(cancelBreed)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR) public void death(EntityDeathEvent e){deaths.put(e.getEntity().getUniqueId(),List.copyOf(e.getDrops()));}
    private void check(boolean test,String label){if(!test)throw new AssertionError(label);checks.add(label);getLogger().info("PASS "+label);}
    private void later(int ticks,Checked task){Bukkit.getScheduler().runTaskLater(this,()->{try{task.run();}catch(Throwable e){fail(e);}},ticks);}
    private interface Checked{void run()throws Exception;}
    private void finish(String name)throws Exception{Files.write(getDataFolder().toPath().resolve(name),checks);Bukkit.shutdown();}
    private void fail(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"Herd animal verification failed",e);try{Files.writeString(getDataFolder().toPath().resolve("ANIMALS-FAILED.txt"),e.toString());}catch(Exception ignored){}Bukkit.shutdown();}
    @Override public void onDisable(){if(spawns!=null)spawns.close();if(tempt!=null)tempt.close();if(models!=null)models.close();if(players!=null)players.close();}
}
