package net.mandomc.verification;

import java.nio.file.Files;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.enchantments.EnchantmentService;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.view.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Real native menus and packet pickups, isolated to a disposable loopback Paper fixture. */
public final class SaberEnchantmentProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private final List<Double> throwHits = new ArrayList<>();
    private LivingEntity victim;
    private MandoPlugin mando; private NativeProbePlayers actors; private Player player; private World world;
    private org.bukkit.block.Block station; private int checks;
    private final Enchantment endurance = Registry.ENCHANTMENT.get(EnchantmentService.ENDURANCE);
    private final Enchantment recovery = Registry.ENCHANTMENT.get(EnchantmentService.RECOVERY);
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }
    private void later(int ticks, Work work) { Bukkit.getScheduler().runTaskLater(this, () -> { try { work.run(); } catch (Throwable error) { finish(error); } }, ticks); }
    @Override public void onEnable() { later(160, this::start); }
    private Object handle() throws Exception { return player.getClass().getMethod("getHandle").invoke(player); }
    private Object menu() throws Exception { var handle = handle(); return handle.getClass().getField("containerMenu").get(handle); }
    private boolean button(int index) throws Exception { return (boolean) menu().getClass().getMethod("clickMenuButton", Class.forName("net.minecraft.world.entity.player.Player"), int.class).invoke(menu(), handle(), index); }
    private void click(int slot, String action) throws Exception {
        var handle = handle(); var menu = menu(); var type = Class.forName("net.minecraft.world.inventory.ContainerInput");
        var packetType = Class.forName("net.minecraft.network.protocol.game.ServerboundContainerClickPacket");
        var mapType = Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectMap");
        var changes = Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap").getConstructor().newInstance();
        var hashed = Class.forName("net.minecraft.network.HashedStack");
        var packet = packetType.getConstructor(int.class,int.class,short.class,byte.class,type,mapType,hashed).newInstance(
                menu.getClass().getField("containerId").getInt(menu), menu.getClass().getMethod("getStateId").invoke(menu),
                (short) slot, (byte) 0, type.getField(action).get(null), changes, hashed.getField("EMPTY").get(null));
        var connection = handle.getClass().getField("connection").get(handle);
        connection.getClass().getMethod("handleContainerClick", packetType).invoke(connection, packet);
    }
    private boolean empty(ItemStack item) { return item == null || item.isEmpty(); }
    private String plain(Component component) { return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component); }
    private ItemStack book(Enchantment enchantment, int level) {
        var item = new ItemStack(Material.ENCHANTED_BOOK); var meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(enchantment, level, false); item.setItemMeta(meta); mando.enchantments().decorate(item); return item;
    }
    private int stored(ItemStack item, Enchantment enchantment) { return item != null && item.getItemMeta() instanceof EnchantmentStorageMeta meta ? meta.getStoredEnchantLevel(enchantment) : 0; }
    private void start() throws Exception {
        check(Bukkit.getPort() == 25591, "Disposable fixture port"); mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(mando != null && mando.isEnabled(), "MandoMC enabled"); check(endurance != null && recovery != null, "Native custom registries");
        world = Bukkit.getWorld(NamespacedKey.minecraft("devworld")); check(world != null, "Fixture world");
        world.getWorldBorder().setCenter(0,0); world.getWorldBorder().setSize(1024);
        actors = new NativeProbePlayers("Saber"); player = actors.create(new Location(world,-201.5,200,-199.5));
        player.setGameMode(GameMode.SURVIVAL); player.setLevel(100); player.getInventory().clear();
        station = world.getBlockAt(-200,200,-200); station.setType(Material.SMITHING_TABLE,false);
        station.getChunk().getPersistentDataContainer().set(new NamespacedKey("mandomc","forge_-200_200_-200"),PersistentDataType.BYTE,(byte)1);
        check(mando.forge().station(station), "Existing placed forge migrates to workbench");
        presentations(); rewardsAndMigration(); adminEnchant();
        mando.forge().open(player,station); var top = player.getOpenInventory().getTopInventory();
        check(top.getItem(10).getType()==Material.CRAFTING_TABLE && top.getItem(13).getType()==Material.STONECUTTER && top.getItem(16).getType()==Material.ENCHANTED_BOOK,"Three workbench functions");
        check(player.getOpenInventory().getTitle().contains("Saber Workbench"),"Workbench title");
        mando.forge().function(player,station,10);check(player.getOpenInventory().getTopInventory().getType()==org.bukkit.event.inventory.InventoryType.WORKBENCH,"Native crafting grid");
        table(); later(2,this::tableRolls);
    }
    private void presentations() {
        int vanilla = 0;
        for (var enchantment : Registry.ENCHANTMENT) {
            if (enchantment.getKey().getNamespace().equals("minecraft")) vanilla++;
            var item = book(enchantment,1); var meta = item.getItemMeta(); var lore = meta.lore();
            check(lore != null && lore.size() >= 3, "Book badge and explanation: " + enchantment.getKey());
            check(lore.getFirst().children().stream().anyMatch(c -> c.font()!=null&&c.font().value().equals("enchantment")) || lore.getFirst().toString().contains("mandomc:enchantment"),"Enchantment texture tag: "+enchantment.getKey());
            var before=item.clone();mando.enchantments().decorate(item);check(item.equals(before),"Book styling idempotent: "+enchantment.getKey());
        }
        check(vanilla==43,"Complete pinned vanilla list");
        var jetpack=mando.bobaEquipment().chestplates().book(1);check(stored(jetpack,Registry.ENCHANTMENT.get(new NamespacedKey("mandomc","jetpack")))==1,"Jetpack remains a real book");
        var fish=book(Enchantment.LURE,3);mando.enchantments().maintain(fish);check(fish.getType()==Material.BOOK,"Fishing-only books removed");
        var mixed=book(Enchantment.LURE,1);var m=(EnchantmentStorageMeta)mixed.getItemMeta();m.addStoredEnchant(Enchantment.MENDING,1,false);mixed.setItemMeta(m);mando.enchantments().maintain(mixed);
        check(stored(mixed,Enchantment.LURE)==0&&stored(mixed,Enchantment.MENDING)==1,"Fishing removal preserves unrelated enchants");
        for(var e:List.of(endurance,recovery))check(book(e,3).getItemMeta().lore().stream().noneMatch(c->plain(c).matches(".*[0-9%].*")),"Custom descriptions omit numerical tuning");
    }
    private void rewardsAndMigration() throws Exception {
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"crates/crates.yml"));
        for(String crate:List.of("vote","koth")) {
            var entries=config.getMapList("crates."+crate+".rewards");
            check(entries.stream().anyMatch(e->"endurance_book".equals(e.get("item"))),crate+" Endurance reward");
            check(entries.stream().anyMatch(e->"recovery_book".equals(e.get("item"))),crate+" Recovery reward");
        }
        check(config.getMapList("crates.koth.rewards").stream().anyMatch(e->"mending_book".equals(e.get("item"))),"KOTH Mending reward");
        check(stored(mando.items().create("mending_book"),Enchantment.MENDING)==1,"Reward is Mending I");
        var saber=mando.items().create("vader");player.getInventory().setItemInMainHand(saber);
        check(Math.abs(saber.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE).iterator().next().getAmount()-7)<1e-8,"Base netherite damage");
        check(Math.abs(saber.getItemMeta().getAttributeModifiers(Attribute.ATTACK_SPEED).iterator().next().getAmount()+2.4)<1e-8,"Base netherite speed");
        var meta=saber.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("mandomc","forge_damage"),PersistentDataType.INTEGER,2);
        meta.getPersistentDataContainer().set(new NamespacedKey("mandomc","forge_guard"),PersistentDataType.INTEGER,3);saber.setItemMeta(meta);
        mando.enchantments().maintain(saber);check(saber.getEnchantmentLevel(Enchantment.SHARPNESS)==2&&saber.getEnchantmentLevel(endurance)==3,"Old material upgrades migrate");
        var once=saber.clone();mando.enchantments().maintain(saber);check(saber.equals(once),"Saber migration idempotent");
        check(!mando.enchantments().tableAllowed(Enchantment.MENDING)&&!mando.enchantments().tableAllowed(Enchantment.LURE),"Mending and fishing absent from tables");
    }
    private void adminEnchant() {
        player.getInventory().setItemInMainHand(mando.items().create("vader"));player.setOp(false);
        Bukkit.dispatchCommand(player,"enchant recovery 5");check(player.getInventory().getItemInMainHand().getEnchantmentLevel(recovery)==0,"Ordinary players cannot use admin enchanting");
        player.setOp(true);check(Bukkit.dispatchCommand(player,"enchant recovery 5"),"Bare custom enchant command");
        var item=player.getInventory().getItemInMainHand();check(item.getEnchantmentLevel(recovery)==5,"Admin Recovery V bypasses survival cap");
        check(Math.abs(mando.enchantments().regeneration(item)-2.25)<1e-8,"Admin level V changes the actual regeneration rate");
        Bukkit.dispatchCommand(player,"enchant endurance 5");check(player.getInventory().getItemInMainHand().getEnchantmentLevel(endurance)==5,"Admin Endurance V");
        Bukkit.dispatchCommand(player,"enchant sharpness 5");check(player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.SHARPNESS)==5,"Admin vanilla short names");
        Bukkit.dispatchCommand(player,"enchant recovery 0");check(player.getInventory().getItemInMainHand().getEnchantmentLevel(recovery)==0,"Level zero removes enchantment");
        check(recovery.getMaxLevel()==3&&endurance.getMaxLevel()==3,"Admin overrides do not change survival caps");
        player.setOp(false);
    }
    private void table() {
        player.closeInventory(); var center=station.getLocation().add(0,0,5);center.getBlock().setType(Material.ENCHANTING_TABLE,false);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(Math.abs(x)==2||Math.abs(z)==2)center.clone().add(x,0,z).getBlock().setType(Material.BOOKSHELF,false);
        player.openEnchanting(center,true);
    }
    private void tableRolls() throws Exception {
        var view=(EnchantmentView)player.getOpenInventory();var seen=new HashSet<Enchantment>();
        for(int seed=1;seed<=300;seed++) {
            player.setLevel(100);view.setEnchantmentSeed(seed);view.getTopInventory().setItem(0,new ItemStack(Material.BOOK));view.getTopInventory().setItem(1,new ItemStack(Material.LAPIS_LAZULI,64));
            var offers=view.getOffers();int choice=offers[2]!=null?2:offers[1]!=null?1:0;check(offers[choice]!=null,"Native book offer");
            for(var offer:offers)if(offer!=null)check(mando.enchantments().tableAllowed(offer.getEnchantment()),"Only approved offers: "+offer.getEnchantment().getKey());
            check(button(choice),"Native table transaction");var result=view.getTopInventory().getItem(0);
            check(result.getItemMeta() instanceof EnchantmentStorageMeta,"Native enchanted book output");
            for(var enchant:((EnchantmentStorageMeta)result.getItemMeta()).getStoredEnchants().keySet()) {check(mando.enchantments().tableAllowed(enchant),"No hidden excluded enchants");seen.add(enchant);}
            check(player.getLevel()==100-choice-1&&view.getTopInventory().getItem(1).getAmount()==63-choice,"Native table charges XP and lapis");
        }
        check(seen.contains(endurance)&&seen.contains(recovery)&&seen.contains(Enchantment.SWIFT_SNEAK),"Custom and Swift Sneak books genuinely roll");
        view.getTopInventory().setItem(0,mando.items().create("vader"));check(Arrays.stream(view.getOffers()).anyMatch(Objects::nonNull),"Shield-based saber is enchantable");
        check(button(0),"Direct saber enchanting");check(!view.getTopInventory().getItem(0).getEnchantments().isEmpty(),"Native saber enchants applied");
        player.closeInventory();player.getInventory().clear();combine();
    }
    private void inputs(ItemStack left,ItemStack right) {var view=(AnvilView)player.getOpenInventory();view.getTopInventory().setItem(0,left);view.getTopInventory().setItem(1,right);}
    private void combine() {
        mando.forge().function(player,station,16);inputs(book(endurance,1),book(endurance,1));
        later(3,()->{
            var view=(AnvilView)player.getOpenInventory();check(stored(view.getTopInventory().getResult(),endurance)==2,"I plus I produces II");
            int cost=view.getRepairCost(),xp=player.getLevel();click(2,"PICKUP");
            check(stored(player.getItemOnCursor(),endurance)==2,"Real packet takes combined book");
            check(empty(view.getTopInventory().getItem(0))&&empty(view.getTopInventory().getItem(1)),"Combining consumes both books");
            check(player.getLevel()==xp-cost,"Native displayed XP cost consumed exactly");
            player.setItemOnCursor(null);inputs(book(recovery,2),book(recovery,2));
            later(3,()->{check(stored(view.getTopInventory().getResult(),recovery)==3,"II plus II produces III");apply();});
        });
    }
    private void apply() {
        var saber=mando.items().create("vader");saber.addUnsafeEnchantment(Enchantment.SHARPNESS,5);saber.addUnsafeEnchantment(endurance,3);inputs(saber,book(recovery,3));
        later(3,()->{
            var view=(AnvilView)player.getOpenInventory();var result=view.getTopInventory().getResult();
            check(result!=null&&result.getEnchantmentLevel(recovery)==3&&result.getEnchantmentLevel(endurance)==3&&result.getEnchantmentLevel(Enchantment.SHARPNESS)==5,"Native saber book application preserves all enchants");
            player.setLevel(0);click(2,"PICKUP");check(empty(player.getItemOnCursor())&&!empty(view.getTopInventory().getItem(1)),"Insufficient XP cannot take result");
            player.setLevel(100);click(2,"PICKUP");var taken=player.getItemOnCursor();check(taken.getEnchantmentLevel(recovery)==3,"Native pickup applies Recovery");
            check(empty(view.getTopInventory().getItem(0))&&empty(view.getTopInventory().getItem(1)),"Upgrade consumes one saber and book");
            player.setItemOnCursor(null);player.closeInventory();style(taken);
        });
    }
    private void style(ItemStack saber) {
        mando.forge().function(player,station,13);var view=(StonecutterView)player.getOpenInventory();
        saber.editMeta(meta->meta.displayName(Component.text("My Saber")));view.getTopInventory().setInputItem(saber);
        later(3,()->{
            int choice=-1;for(int i=0;i<view.getRecipes().size();i++)if(view.getRecipes().get(i).getKey().getKey().equals("refinish_darth_maul_apprentice_lightsaber"))choice=i;
            check(choice>=0,"Style available for red saber");check(button(choice),"Native style selection");
            later(2,()->{
                var result=view.getTopInventory().getResult();check(result!=null,"Native style result");
                check(result.getEnchantmentLevel(recovery)==3&&result.getEnchantmentLevel(endurance)==3&&result.getEnchantmentLevel(Enchantment.SHARPNESS)==5,"Styles preserve all paid enchantments");
                check(plain(result.getItemMeta().displayName()).equals("My Saber"),"Styles preserve renames");
                click(1,"PICKUP");check("darth_maul_apprentice_lightsaber".equals(mando.items().identify(player.getItemOnCursor())),"Native style pickup returns selected identity");
                check(empty(view.getTopInventory().getInputItem()),"Style consumes one input");player.setItemOnCursor(null);player.closeInventory();recipes();
            });
        });
    }
    private void recipes() {
        for(String id:List.of("vader","darth_maul_apprentice_lightsaber")) {
            check(Bukkit.dispatchCommand(player,"recipes "+id),"Recipe command");var icon=player.getOpenInventory().getTopInventory().getItem(23);
            check(icon.getType()==Material.SMITHING_TABLE&&plain(icon.getItemMeta().displayName()).contains("Saber Workbench"),"Recipe station shows workbench for "+id);player.closeInventory();
        }
        mando.forge().function(player,station,16);inputs(mando.items().create("vader"),book(recovery,1));
        later(3,()->{
            station.setType(Material.AIR,false);click(2,"PICKUP");check(empty(player.getItemOnCursor()),"Removed workbench denies pickup");combat();
        });
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if(event.getDamager().equals(player)&&mando.sabers().dealingThrowDamage(player))throwHits.add(event.getDamage());
    }
    private void combat() {
        player.closeInventory();player.getInventory().clear();player.setSneaking(false);player.setRotation(90,0);
        var saber=mando.items().create("vader");saber.addUnsafeEnchantment(Enchantment.SHARPNESS,5);
        saber.addUnsafeEnchantment(endurance,3);saber.addUnsafeEnchantment(recovery,3);
        player.getInventory().setItemInMainHand(saber);Bukkit.getPluginManager().registerEvents(this,this);
        later(25,()->{
            handle().getClass().getMethod("detectEquipmentUpdates").invoke(handle());
            check(Math.abs(player.getAttribute(Attribute.ATTACK_DAMAGE).getValue()-8)<1e-8,"Equipped native base damage");
            check(Math.abs(mando.enchantments().consumption(saber)-.8)<1e-8,"Endurance III effect bound to actual enchant");
            check(Math.abs(mando.enchantments().regeneration(saber)-1.75)<1e-8,"Recovery III effect bound to actual enchant");
            var mob=world.spawn(player.getLocation().add(-2,0,0),Husk.class);victim=mob;mob.setAI(false);mob.setGravity(false);
            mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);mob.setHealth(200);player.setFallDistance(0);player.attack(mob);
            check(Math.abs(mob.getHealth()-189)<1e-8,"Native fully charged Sharpness V melee deals eleven");
            mob.setNoDamageTicks(0);mob.teleport(player.getLocation().add(-6,0,0));
            check(mando.sabers().launch(player),"Native enchanted saber launches");
            later(45,()->{
                check(!throwHits.isEmpty(),"Actual throw contacts damage the target");
                for(double damage:throwHits)check(Math.abs(damage-15)<1e-8,"Each throw contact equals full melee critical damage");
                mando.sabers().finish(player.getUniqueId());
                var returned=player.getInventory().getItemInMainHand();check(returned.getEnchantmentLevel(Enchantment.SHARPNESS)==5&&returned.getEnchantmentLevel(endurance)==3&&returned.getEnchantmentLevel(recovery)==3,"Throw return preserves all enchants");
                finish(null);
            });
        });
    }
    private void finish(Throwable error) {
        try { if(victim!=null)victim.remove();if(player!=null)player.closeInventory();if(actors!=null)actors.close();Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve(error==null?"PASSED.txt":"FAILED.txt"),error==null?checks+" native saber assertions passed\n":error.toString());
            if(error==null)getLogger().info("SABER ENCHANTMENTS PASSED: "+checks);else getLogger().log(java.util.logging.Level.SEVERE,"SABER ENCHANTMENTS FAILED",error);
        } catch(Exception failure){getLogger().log(java.util.logging.Level.SEVERE,"Probe cleanup failed",failure);}Bukkit.shutdown();
    }
}
