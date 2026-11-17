package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.tutorial.*;
import net.mandomc.gameplay.items.*;
import org.bukkit.*;
import org.bukkit.block.Furnace;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable native walkthrough: actual world blocks, recipes, menus, models and reward persistence. */
public final class TutorialHubProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private MandoPlugin mando;
    private net.mandomc.integrations.mobs.MythicMobAccess mobs;
    private NativeProbePlayers actors;
    private Player player, observer;
    private int checks;
    private final List<Integer> beskarLoot = new ArrayList<>();
    private boolean checkingBeskar, shopsOnly;
    private boolean finished, brakePressed;
    private ItemStack[] original;
    private long balance;
    @FunctionalInterface interface Work { void run() throws Exception; }
    private void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
    private void later(long ticks, Work action) { Bukkit.getScheduler().runTaskLater(this, () -> { if (!finished) try { action.run(); } catch (Throwable e) { fail(e); } }, ticks); }
    private Object field(Object target, String name) throws Exception { var f=target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    private Object session() throws Exception { return ((Map<?,?>)field(mando.tutorial(),"sessions")).get(player.getUniqueId()); }
    private TutorialProgress progress() throws Exception { return (TutorialProgress)field(session(),"progress"); }
    private void stage(String name) throws Exception { check(progress().step().name().equals(name), "Expected "+name+", got "+progress().step()); getLogger().info("HUB CHECK: "+name); }
    private void command(String text) { var event=new PlayerCommandPreprocessEvent(player,text); Bukkit.getPluginManager().callEvent(event); check(event.isCancelled(),"Lesson owns command "+text); }
    private void click(int slot) { Bukkit.getPluginManager().callEvent(new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL)); }
    private void put(Player p, Location at) throws Exception { var m=InteractiveTutorial.class.getDeclaredMethod("teleport",Player.class,Location.class);m.setAccessible(true);check((boolean)m.invoke(mando.tutorial(),p,at),"Native teleport"); }
    private void choose(String section) throws Exception {
        stage("HUB");
        var target=player.getWorld().getEntitiesByClass(Interaction.class).stream().filter(e->section.equals(e.getPersistentDataContainer().get(NamespacedKey.fromString("mandomc:tutorial_hub_choice"),PersistentDataType.STRING))).findFirst().orElseThrow();
        put(player,target.getLocation().clone().add(0,0,2));
        Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,target,EquipmentSlot.HAND));
    }
    private void packs(Player p) { var c=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"combat/sabers.yml"));for(String k:List.of("main-pack-id","id","base-pack.id"))mando.tutorial().pack(new PlayerResourcePackStatusEvent(p,UUID.fromString(c.getString("upstream-pack."+k)),PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED)); }
    @Override public void onEnable() { Bukkit.getPluginManager().registerEvents(this,this); later(320,this::start); }
    private void start() throws Exception {
        check(Bukkit.getPort()==25590,"Disposable localhost fixture only");
        mobs=new net.mandomc.integrations.mobs.MythicMobAccess();
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando.tutorial()!=null,"Tutorial initialized");
        var weaponType = Class.forName("me.deecaad.weaponmechanics.WeaponMechanics",true,
                Bukkit.getPluginManager().getPlugin("WeaponMechanics").getClass().getClassLoader());
        var weapons = weaponType.getMethod("getInstance").invoke(null);
        var weaponConfig = weaponType.getMethod("getWeaponConfigurations").invoke(weapons);
        check(((Number)weaponConfig.getClass().getMethod("getDouble",String.class)
                .invoke(weaponConfig,"E11.Damage.Base_Damage")).doubleValue() == 2.0, "Loaded E-11 base damage is 2.0");
        var massiff = mobs.spawn("Massiff",mando.worlds().spawn("Tatooine").clone().add(0,2,0),1);
        check(massiff.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() == 30,
                "Native Massiff maximum health is 30");
        check(massiff.getHealth() == 30, "New Massiff starts at 30 HP");mobs.remove(massiff);
        getDataFolder().mkdirs();actors=new NativeProbePlayers("HubCheck");
        if (Files.exists(getDataFolder().toPath().resolve("balance-only"))) { pass(); return; }
        shopsOnly = Files.exists(getDataFolder().toPath().resolve("shops-only"));
        if (!shopsOnly && Files.exists(getDataFolder().toPath().resolve("beskar-only"))) { observer=actors.create(mando.worlds().spawn("Batuu"));beskar();return; }
        if (!shopsOnly && Files.exists(getDataFolder().toPath().resolve("restart-ready"))) { restartCheck(); return; }
        player=actors.create(mando.worlds().spawn("Batuu"));observer=actors.create(mando.worlds().spawn("Batuu"));
        player.getPersistentDataContainer().set(NamespacedKey.fromString("mandomc:starter_kit_v1"),PersistentDataType.BYTE,(byte)1);
        player.getInventory().setItem(0,new ItemStack(Material.EMERALD,13));original=player.getInventory().getContents();balance=mando.credits().ledger().balance(player.getUniqueId());
        if (shopsOnly) { packs(player);mando.tutorial().request(player);later(40,this::shops);return; }
        exportTemplates();checkMetals();packs(player);mando.tutorial().request(player);later(40,this::hub);
    }
    private void exportTemplates() throws Exception {
        var c=new org.bukkit.configuration.file.YamlConfiguration();
        for(String id:new HashSet<>(MetalMigration.SHEETS.values()))c.set(id,Base64.getEncoder().encodeToString(mando.items().create(id).serializeAsBytes()));
        c.save(getDataFolder().toPath().resolve("ingot-templates.yml").toFile());
        var legacy=mando.items().create("beskar");legacy.setAmount(17);legacy.editMeta(m->m.getPersistentDataContainer().set(ItemCatalog.ITEM_ID,PersistentDataType.STRING,"beskar_sheet"));
        Files.write(getDataFolder().toPath().resolve("legacy-sheet.bin"),legacy.serializeAsBytes());
        Files.write(getDataFolder().toPath().resolve("legacy-inventory.bin"),ItemStack.serializeItemsAsBytes(new ItemStack[]{legacy,null,new ItemStack(Material.DIAMOND,3)}));
    }
    private void checkMetals() throws Exception {
        var normalization=new MetalMaterials(mando.items());
        for(var e:MetalMigration.SHEETS.entrySet()) {
            check(!mando.items().ids().contains(e.getKey()),"No sheet catalog entry "+e.getKey());
            check(mando.items().create(e.getKey()).isSimilar(mando.items().create(e.getValue())),"Old queued reward resolves to ingot");
            var old=mando.items().create(e.getValue());old.setAmount(37);old.editMeta(m->m.getPersistentDataContainer().set(ItemCatalog.ITEM_ID,PersistentDataType.STRING,e.getKey()));
            var migrated=normalization.canonical(old);check(migrated.isSimilar(mando.items().create(e.getValue()))&&migrated.getAmount()==37,"Sheet stack migrates once");
            check(normalization.canonical(migrated).equals(migrated),"Migration idempotent");
            var recipe=Bukkit.getRecipe(new NamespacedKey(mando,"smelt_"+e.getValue()+"_from_scrap"));
            check(recipe instanceof FurnaceRecipe,"Normal furnace recipe "+e.getValue());
            check(((FurnaceRecipe)recipe).getInputChoice().test(mando.items().create(e.getValue()+"_scrap")),"Scrap matches exact input");
            check(recipe.getResult().isSimilar(mando.items().create(e.getValue())),"Correct ingot output");
        }
        check(Bukkit.getRecipe(new NamespacedKey(mando,"craft_agrinium"))==null,"No nine-scrap shortcut");
        var old=mando.items().create("beskar");old.editMeta(m->m.getPersistentDataContainer().set(ItemCatalog.ITEM_ID,PersistentDataType.STRING,"beskar_sheet"));
        var shulker=new ItemStack(Material.SHULKER_BOX);shulker.editMeta(org.bukkit.inventory.meta.BlockStateMeta.class,m->{var box=(org.bukkit.block.ShulkerBox)m.getBlockState();box.getInventory().setItem(4,old);m.setBlockState(box);});
        var converted=normalization.canonical(shulker);var box=(org.bukkit.block.ShulkerBox)((org.bukkit.inventory.meta.BlockStateMeta)converted.getItemMeta()).getBlockState();
        check("beskar".equals(mando.items().identify(box.getInventory().getItem(4))),"Nested shulker migration");
    }
    private void hub() throws Exception {
        stage("HUB");check(player.getWorld()!=observer.getWorld(),"Private training world");
        check(player.getWorld().getEntitiesByClass(Interaction.class).size()==6,"Five lessons and exit");
        choose("survival");stage("WARP");command("/warp");check(player.getOpenInventory().getTopInventory().getSize()==54,"Real galaxy menu");click(22);later(3,this::mine);
    }
    private void mine() throws Exception {
        stage("MINING");check(player.getGameMode()==GameMode.SURVIVAL,"Native mining mode");
        var world=player.getWorld();
        for(int x=42;x<=46;x+=2){var block=world.getBlockAt(x,65,27);put(player,block.getLocation().add(.5,0,-2));check(player.breakBlock(block),"Native ore break "+x);}
        later(5,this::smelt);
    }
    private void smelt() throws Exception {
        stage("SMELTING");check(player.getInventory().containsAtLeast(mando.items().create("agrinium_scrap"),3),"Three ores yield three scraps");
        check(!player.getInventory().containsAtLeast(mando.items().create("agrinium_scrap"),4),"No extra scrap");
        command("/recipes");check(RecipeGuide.isMenu(player.getOpenInventory().getTopInventory()),"Recipes browsable");player.closeInventory();
        var block=(org.bukkit.block.Block)field(session(),"furnace");put(player,block.getLocation().add(2,0,0));
        var furnace=(Furnace)block.getState();player.openInventory(furnace.getInventory());check(player.getOpenInventory().getTopInventory().getType()==InventoryType.FURNACE,"Actual furnace opens");
        var scraps=mando.items().create("agrinium_scrap");scraps.setAmount(3);player.getInventory().removeItem(scraps);player.getInventory().removeItem(new ItemStack(Material.COAL));
        furnace.getInventory().setSmelting(scraps);furnace.getInventory().setFuel(new ItemStack(Material.COAL));player.closeInventory();
        waitSmelt(block,0);
    }
    private void waitSmelt(org.bukkit.block.Block block,int ticks) throws Exception {
        var furnace=(Furnace)block.getState();var result=furnace.getInventory().getResult();
        if(result==null||result.getAmount()!=3){check(ticks<750,"Furnace finishes in bounded time");later(20,()->waitSmelt(block,ticks+20));return;}
        check(result.isSimilar(mando.items().create("agrinium")),"Native furnace yields three ingots");player.getInventory().addItem(result);furnace.getInventory().setResult(null);later(5,this::craft);
    }
    private void craft() throws Exception {
        stage("CRAFTING");var grid=new ItemStack[9];for(int i=0;i<3;i++)grid[i]=mando.items().create("agrinium");grid[4]=new ItemStack(Material.STICK);grid[7]=new ItemStack(Material.STICK);
        var crafted=Bukkit.craftItemResult(grid,player.getWorld(),player).getResult();check("agrinium_pickaxe".equals(mando.items().identify(crafted)),"Native ingot pickaxe recipe");
        var bench=(org.bukkit.block.Block)field(session(),"bench");put(player,bench.getLocation().add(2,0,0));player.openWorkbench(bench.getLocation(),false);
        var inv=(CraftingInventory)player.getOpenInventory().getTopInventory();inv.setMatrix(grid);inv.setResult(crafted);
        var event=new CraftItemEvent(Bukkit.getRecipe(new NamespacedKey(mando,"craft_agrinium_pickaxe")),player.getOpenInventory(),InventoryType.SlotType.RESULT,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(event);check(!event.isCancelled(),"Craft permitted after browsing recipes");
        // Simulate the native result-slot transfer after the uncancelled craft callback.
        inv.setMatrix(new ItemStack[9]);inv.setResult(null);player.getInventory().setItem(5,crafted);later(6,this::clans);
    }
    private void clans() throws Exception {
        stage("HUB");choose("clans");stage("MAP");command("/c map");stage("WILDERNESS");
        put(player,new Location(player.getWorld(),36.5,65,.5));later(3,()->{stage("CREATE");command("/c create Learners");stage("CLAIM");
            put(player,new Location(player.getWorld(),53.5,65,.5));command("/c claim");stage("DISBAND");command("/c disband");stage("HUB");combat();});
    }
    private void combat() throws Exception {
        choose("combat");stage("COMBAT");
        @SuppressWarnings("unchecked") var guards=(Set<UUID>)field(session(),"guards");var types=new HashSet<String>();var mobs=new net.mandomc.integrations.mobs.MythicMobAccess();
        var api=Class.forName("me.deecaad.weaponmechanics.WeaponMechanicsAPI",true,Bukkit.getPluginManager().getPlugin("WeaponMechanics").getClass().getClassLoader());
        int delay=12;
        for(UUID id:List.copyOf(guards)){
            var target=(LivingEntity)Bukkit.getEntity(id);types.add(mobs.type(target));check(!target.hasAI(),"Stationary target");target.setHealth(1);
            for(int shot=0;shot<3;shot++) later(delay+=8,()->{if(target.isValid()&&!target.isDead()) api.getMethod("shoot",LivingEntity.class,String.class,Location.class).invoke(null,player,"SE14C",target.getEyeLocation());});
        }
        check(types.equals(Set.of("GamorreanGuard","QuarrenGuard","TwilekGuard")),"One of each requested target");later(110,this::sabers);
    }
    private void sabers() throws Exception {
        stage("RECIPES");command("/recipes");
        var inv=player.getOpenInventory().getTopInventory();int weapons=-1;
        for(int i=0;i<inv.getSize();i++){var it=inv.getItem(i);if(it!=null&&it.hasItemMeta()&&it.getItemMeta().displayName()!=null&&net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it.getItemMeta().displayName()).contains("Weapons"))weapons=i;}
        check(weapons>=0,"Weapons category visible");click(weapons);later(3,()->{check(RecipeGuide.isCategory(player.getOpenInventory().getTopInventory(),"weapons"),"Weapons actually opened");player.closeInventory();later(4,()->{
            stage("SABER");
            @SuppressWarnings("unchecked") var guards=(Set<UUID>)field(session(),"guards");var target=(LivingEntity)Bukkit.getEntity(guards.iterator().next());
            var at=player.getLocation();at.setDirection(target.getEyeLocation().toVector().subtract(player.getEyeLocation().toVector()));put(player,at);
            check(mando.sabers().launch(player),"Native saber throw starts");waitSaber(0);
        });});
    }
    private void waitSaber(int ticks) throws Exception {
        if(progress().step()==TutorialProgress.Step.HUB){shops();return;}
        check(ticks<220,"Saber target hit and return completes");later(5,()->waitSaber(ticks+5));
    }
    private void shops() throws Exception {
        choose("shops");stage("BUY");
        var brown=player.getWorld().getLivingEntities().stream().filter(e->mobs.type(e).equals("JawaBrown")).findFirst().orElseThrow();
        put(player,brown.getLocation().clone().add(-2,0,0));mando.tutorial().jawa(player,brown);click(13);later(5,this::batuu);
    }
    private void batuu() throws Exception {
        stage("SELL");check(player.getWorld()==observer.getWorld(),"Real public Batuu");check(player.getInventory().contains(Material.STRING,16),"Practice String supplied for selling");check("wiring_coil".equals(mando.items().identify(player.getInventory().getItem(0))),"Purchased Wiring Coil retained");
        var cfg=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"tutorial/tutorial.yml"));
        var route = new ArrayList<Location>();
        for (var point : cfg.getMapList("shop-route")) route.add(new Location(player.getWorld(),
                ((Number) point.get("x")).doubleValue(), ((Number) point.get("y")).doubleValue(),
                ((Number) point.get("z")).doubleValue()));
        for (int i = 0; i < route.size() - 1; i++) {
            var start = route.get(i); var delta = route.get(i + 1).toVector().subtract(start.toVector());
            for (double n = 0; n <= delta.length(); n += .2) {
                var anchor = start.clone().add(delta.clone().normalize().multiply(n));
                var at = surface(anchor);
                check(at != null, "Every route sample has a walking surface");
                var floor = at.getWorld().rayTraceBlocks(at.clone().add(0, 1.5, 0), new org.bukkit.util.Vector(0,-1,0),
                        3, FluidCollisionMode.NEVER, true);
                check(floor != null && at.getY() > floor.getHitPosition().getY()
                        && at.getY() - floor.getHitPosition().getY() < 1,
                        "Trail clears actual walking surface: " + at.toVector());
            }
        }
        for (double[] point : new double[][] {
                {-16.5,93,-8.5,93.25}, {-19.5,94,-8.5,93.75}, {-20.5,94,-8.5,94.25},
                {-21.5,94,-8.5,94.75}, {-22.5,95,-8.5,95.25}, {-22.5,95.5,-12.5,95.75},
                {-22.5,96,-14.5,96.25}, {-41.5,96,-14.5,96.25}}) {
            var at = surface(new Location(player.getWorld(),point[0],point[1],point[2]));
            check(at != null && Math.abs(at.getY()-point[3]) < .001,
                    "Measured slab/stair/landing height at " + Arrays.toString(point) + ": " + at);
        }
        var guidance = InteractiveTutorial.class.getDeclaredMethod("shopGuidance",session().getClass());
        guidance.setAccessible(true);
        put(player,route.get(1));actors.drain(player);actors.otherPackets(player);
        guidance.invoke(mando.tutorial(),session());
        later(12, () -> {
        var stairs = particlePositions(player).stream().filter(at -> at.getX() < -19 && at.getX() > -22
                && Math.abs(at.getZ()+8.5)<.01).toList();
        check(stairs.size() >= 4 && stairs.stream().anyMatch(at -> at.getY() < 94)
                && stairs.stream().anyMatch(at -> at.getY() > 94.7), "Actual particle packets climb the stair treads: " + stairs);
        // Jump ahead without visiting earlier waypoints: the trail must still lead through the doorway.
        put(player,new Location(player.getWorld(),-32.5,96.1,-14.5));
        actors.drain(player);actors.otherPackets(player);guidance.invoke(mando.tutorial(),session());
        later(12, () -> {
        var doorwayParticles = particlePositions(player);
        check(doorwayParticles.stream().anyMatch(at -> at.getX() < -33 && at.getX() > -36
                && Math.abs(at.getZ()+14.5)<.01), "Cutting corners cannot leave the trail stuck behind the stairs: " + doorwayParticles);
        var buyers = player.getWorld().getLivingEntities().stream().filter(mando.jawas()::gray).toList();
        check(buyers.size() >= 7, "All seven public gray Jawas are loaded");
        for (var gray : buyers) {
            put(player, gray.getLocation().clone().add(0,0,2));mando.tutorial().jawa(player,gray);
            check(player.getOpenInventory().getTopInventory().getHolder() instanceof net.mandomc.gameplay.jawa.JawaSellInventory.Tray,
                    "Public gray Jawa opens tutorial salvage at " + gray.getLocation().toVector());
            player.closeInventory();
        }
        // Inside the building, and beyond the old early return, the endpoint arrow must still render privately.
        put(player,route.getLast().clone().add(2,0,0));put(observer,player.getLocation().clone().add(0,0,2));
        actors.drain(player);actors.drain(observer);actors.otherPackets(player);actors.otherPackets(observer);later(12,()->{
            actors.drain(player);actors.drain(observer);var own=actors.otherPackets(player);var others=actors.otherPackets(observer);
            check(own!=null&&own.stream().anyMatch(x->x.contains("LevelParticles")),"Player receives endpoint arrow inside the shop");
            check(others==null||others.stream().noneMatch(x->x.contains("LevelParticles")),"Observer receives no tutorial particles");sell();
        });
        });
        });
    }
    private Location surface(Location anchor) throws Exception {
        var method = InteractiveTutorial.class.getDeclaredMethod("shopSurface",Location.class);
        method.setAccessible(true);return (Location)method.invoke(mando.tutorial(),anchor);
    }
    private List<org.bukkit.util.Vector> particlePositions(Player target) throws Exception {
        Object channel = ((Map<?,?>)field(actors,"channels")).get(target.getUniqueId());
        channel.getClass().getMethod("runPendingTasks").invoke(channel);
        var read = channel.getClass().getMethod("readOutbound");
        var points = new ArrayList<org.bukkit.util.Vector>();
        Object packet;
        while ((packet = read.invoke(channel)) != null) {
            var type = packet.getClass();
            if (type.getSimpleName().equals("ClientboundLevelParticlesPacket")) points.add(new org.bukkit.util.Vector(
                    ((Number)type.getMethod("getX").invoke(packet)).doubleValue(),
                    ((Number)type.getMethod("getY").invoke(packet)).doubleValue(),
                    ((Number)type.getMethod("getZ").invoke(packet)).doubleValue()));
        }
        return points;
    }
    private void sell() throws Exception {
        var gray=player.getWorld().getLivingEntities().stream().filter(e->mando.jawas().gray(e)&&e.getLocation().getX()<-37).findFirst().orElseThrow();
        put(player,gray.getLocation().clone().add(0,0,2));mando.tutorial().jawa(player,gray);
        check(player.getOpenInventory().getTopInventory().getHolder() instanceof net.mandomc.gameplay.jawa.JawaSellInventory.Tray,"Actual gray Jawa sell tray");
        check(!player.getPersistentDataContainer().has(NamespacedKey.fromString("mandomc:jawa_sell_custody_v1")),"Practice tray cannot enter durable real custody");
        int stringSlot=-1;for(int i=0;i<36;i++)if(player.getInventory().getItem(i)!=null && player.getInventory().getItem(i).getType()==Material.STRING){stringSlot=i;break;}
        check(stringSlot>=0,"Practice salvage is available");click(stringSlot<9?63+stringSlot:36+stringSlot-9);click(0);click(31);later(8,()->{stage("HUB");if(shopsOnly) { command("/tutorial end");pass(); } else replay();});
    }
    private void replay() throws Exception {
        check(player.getPersistentDataContainer().getOrDefault(NamespacedKey.fromString("mandomc:tutorial_sections_v2"),PersistentDataType.INTEGER,0)==15,"Four section completions persisted");
        long before=mando.credits().ledger().balance(player.getUniqueId());choose("clans");command("/tutorial");stage("HUB");
        check(mando.credits().ledger().balance(player.getUniqueId())==before,"Leaving replay pays nothing");
        choose("vehicles");stage("PLACE");check(player.getInventory().containsAtLeast(mando.items().create("xwing"),1),"Vehicle lesson retains X-wing");
        flight();
    }
    private void afterFlight() throws Exception {
        stage("HUB");
        check(player.getPersistentDataContainer().getOrDefault(NamespacedKey.fromString("mandomc:tutorial_sections_v2"),PersistentDataType.INTEGER,0)==31,"All five section receipts");
        command("/tutorial");stage("HUB");command("/tutorial end");
        check(!TutorialInventory.active(player),"Exit restores escrow");
        check(Arrays.equals(original,player.getInventory().getContents()),"Original inventory restored exactly");
        later(15,()->{
            check(mando.credits().ledger().balance(player.getUniqueId())==balance+250000,"Exactly five $500 rewards, no shop money");
            packs(player);mando.tutorial().request(player);later(30,()->{stage("HUB");choose("shops");
                var brown=player.getWorld().getLivingEntities().stream().filter(e->mobs.type(e).equals("JawaBrown")).findFirst().orElseThrow();
                put(player,brown.getLocation().clone().add(-2,0,0));mando.tutorial().jawa(player,brown);click(13);later(4,()->{
                    stage("SELL");prepareRestart();command("/tutorial end");check(Arrays.equals(original,player.getInventory().getContents()),"Exit on public Batuu restores original inventory");beskar();
                });
            });
        });
    }
    private void flight() throws Exception {
        stage("PLACE");
        check(field(session(), "vehicle") == null, "No X-wing blocks arrival instructions");
        check("xwing".equals(mando.items().identify(player.getInventory().getItemInMainHand())), "Training X-wing supplied in inventory");
        check(progress().flightMillis() == 0, "Placement has no flight progress");
        // Drive the ordinary vehicle block-interaction event; only a valid placement advances.
        var block = player.getWorld().getBlockAt(48, 64, 46);
        var denied = new PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        denied.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        Bukkit.getPluginManager().callEvent(denied); stage("PLACE");
        var placed = new PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placed); stage("BOARD");
        check(player.getInventory().getItemInMainHand().isEmpty(), "Placement consumes training X-wing item");
        UUID id = (UUID) field(session(), "vehicle");
        var entity = mando.vehicles().entity(id); check(entity != null, "Real modeled X-wing created");
        var movement = mando.vehicles().getClass().getDeclaredMethod("movement", UUID.class, Location.class, Location.class);
        movement.setAccessible(true);
        for (var destination : List.of(new Location(entity.getWorld(), -35, 90, -80), new Location(entity.getWorld(), 130, 150, 80)))
            check((boolean) movement.invoke(mando.vehicles(), id, entity.getLocation(), destination), "Full training world permits flight outside former runway");
        check(new net.mandomc.integrations.regions.VehicleProtection().use(new Location(entity.getWorld(), 8, 100, 0)), "Training cannon use includes former lesson safezone");
        var journal = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(), "data/vehicles.yml"));
        check(!journal.contains("vehicles." + id), "Training ship is not real vehicle ownership");
        check(instruction().contains("board"), "Flight first shows only boarding instruction");
        mando.tutorial().board(player, id); check(mando.vehicles().occupant(id, player.getUniqueId()), "Player boards real ModelEngine pilot seat");
        check(mando.tutorial().lockDismount(player), "Dismount locked during flight");
        later(25, () -> {
            stage("FLIGHT"); check(progress().flightMillis() == 0, "Grounded idle time does not count");
            check(instruction().contains("take off"), "Grounded pilot sees takeoff instruction");
            check(!mando.vehicleCannons().fire(player), "Cannons locked before gunnery lesson");
            // Native test actors do not have a real client chunk subscription. Prepare their flight corridor.
            var chunks = new ArrayList<java.util.concurrent.CompletableFuture<?>>();
            for (int x = 0; x <= 10; x++) for (int z = 2; z <= 24; z++) chunks.add(player.getWorld().getChunkAtAsync(x, z, true));
            java.util.concurrent.CompletableFuture.allOf(chunks.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .thenRun(() -> later(1, () -> {
                        for (int x = 0; x <= 10; x++) for (int z = 2; z <= 24; z++)
                            player.getWorld().addPluginChunkTicket(x, z, this);
                        driveFlight(0);
                    }));
        });
    }
    private void driveFlight(int ticks) throws Exception {
        check(ticks < 700, "Flight must finish within native test deadline");
        var step = progress().step();
        if (step == TutorialProgress.Step.TARGET) { target(); return; }
        var id = (UUID) field(session(), "vehicle"); var ship = mando.vehicles().entity(id);
        var controller = com.ticxo.modelengine.api.ModelEngineAPI.getMountPairManager().getController(player.getUniqueId());
        check(controller != null, "Training pilot has native controller");
        double side = step == TutorialProgress.Step.RIGHT ? -1 : step == TutorialProgress.Step.LEFT ? 1 : 0;
        double forward = step == TutorialProgress.Step.FLIGHT && ticks == 0 ? 1 : step == TutorialProgress.Step.STOP && !brakePressed ? -1 : 0;
        if (step == TutorialProgress.Step.STOP) brakePressed = true;
        player.setRotation(ticks < 60 ? -20 : 0, ticks < 35 ? -20 : 0);
        controller.setInput(new com.ticxo.modelengine.api.mount.controller.MountController.MountInput((float) side, (float) forward, false, false));
        var model = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(ship);
        controller.updateDriverMovement(model.getBase().getMoveController(), model.getModels().values().iterator().next());
        if (ticks == 50) {
            // Read the controller output now; Entity#getVelocity is sampled after native drag/interpolation.
            var requested = model.getBase().getMoveController().getVelocity();
            check(Math.abs(requested.length() - 2.1) < .001, "Training requests normal X-wing speed after W release: " + requested);
            check(requested.getX() > .3 && requested.getZ() > .3, "Native flight follows mouse heading on both axes");
        }
        later(1, () -> driveFlight(ticks + 1));
    }
    private void target() throws Exception {
        stage("TARGET"); var session = session(); UUID vehicle = (UUID) field(session, "vehicle");
        @SuppressWarnings("unchecked") var blocks = Set.copyOf((Set<org.bukkit.block.Block>) field(session, "targetBlocks"));
        check(blocks.size() == 289, "Target is a solid 17-by-17 block structure");
        check(blocks.stream().allMatch(block -> block.getType() == Material.RED_CONCRETE || block.getType() == Material.SEA_LANTERN), "Target uses actual world blocks");
        check(player.getWorld().getEntitiesByClass(Slime.class).isEmpty(), "Target has no hidden slime hitbox");
        check(player.getWorld().getEntitiesByClass(BlockDisplay.class).isEmpty(), "Target has no display-entity substitute");
        mando.tutorial().cannonHit(player, UUID.randomUUID(), blocks.iterator().next()); stage("TARGET");
        var platform = mando.vehicles().firingPlatform(player, List.of("weapon_one", "weapon_two"));
        check(platform != null, "Native X-wing muzzles resolve");
        var ray = player.getWorld().rayTraceBlocks(platform.muzzles().getFirst(), platform.direction(), 60,
                FluidCollisionMode.NEVER, true);
        check(ray != null && blocks.contains(ray.getHitBlock()), "Actual spawned block target intersects native cannon ray without moving it");
        check(mando.vehicleCannons().fire(player), "Native cannons emit and consume supplied ammo");
        later(30, () -> {
            stage("TARGET"); check(blocks.stream().allMatch(block -> block.getType().isAir()), "Cannon destroyed the actual target blocks before explosion delay completes");
        });
        later(90, () -> land(vehicle));
    }
    private void land(UUID vehicle) throws Exception {
        stage("LAND"); var ship = mando.vehicles().entity(vehicle);
        var controller = com.ticxo.modelengine.api.ModelEngineAPI.getMountPairManager().getController(player.getUniqueId());
        controller.setInput(new com.ticxo.modelengine.api.mount.controller.MountController.MountInput(0, 0, false, true));
        var model = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(ship);
        controller.updateDriverMovement(model.getBase().getMoveController(), model.getModels().values().iterator().next());
        later(1, () -> { if (progress().step() == TutorialProgress.Step.LAND) land(vehicle); else { check(mando.vehicles().entity(vehicle) == null, "Training ship cleaned up"); afterFlight(); } });
    }
    private String instruction() throws Exception {
        var session=session();var method=InteractiveTutorial.class.getDeclaredMethod("instruction",session.getClass());method.setAccessible(true);
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize((net.kyori.adventure.text.Component)method.invoke(mando.tutorial(),session));
    }
    @org.bukkit.event.EventHandler public void spawned(org.bukkit.event.entity.ItemSpawnEvent event) {
        if (checkingBeskar && "beskar_scrap".equals(mando.items().identify(event.getEntity().getItemStack()))) beskarLoot.add(event.getEntity().getItemStack().getAmount());
    }
    private void beskar() throws Exception {
        mando.events().stop(false);
        var event=(net.mandomc.gameplay.events.beskar.BeskarEvent)mando.events().event("beskar");
        check(event.hosts().size()==10,"Exactly ten configured Beskar nodes");
        check(mando.events().start("beskar"),"Scheduler starts Beskar Rush");waitBeskar(event,0);
    }
    private void waitBeskar(net.mandomc.gameplay.events.beskar.BeskarEvent event,int tries) throws Exception {
        if (!event.miningActive()) { check(tries<150,"Beskar preparation completes");later(2,()->waitBeskar(event,tries+1));return; }
        var world=Bukkit.getWorld(NamespacedKey.minecraft("mandalore_review"));
        observer.setGameMode(GameMode.SURVIVAL);observer.getInventory().setItemInMainHand(new ItemStack(Material.WOODEN_PICKAXE));
        put(observer,event.hosts().getFirst().stand().block(world).getLocation().add(.5,1,.5));
        check(!observer.breakBlock(event.hosts().getFirst().ore().block(world)),"Weak tool cannot consume a Beskar node");check(event.remainingCount()==10,"Denied mining preserves count");
        checkingBeskar=true;
        for (var host : event.hosts()) {
            put(observer,host.stand().block(world).getLocation().add(.5,1,.5));
            var pick=new ItemStack(Material.DIAMOND_PICKAXE);pick.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FORTUNE,100);observer.getInventory().setItemInMainHand(pick);
            check(observer.breakBlock(host.ore().block(world)),"Native event node mined");
        }
        later(5,()->{
            checkingBeskar=false;check(event.remainingCount()==0,"Ten nodes deplete completely");check(beskarLoot.size()==10&&beskarLoot.stream().allMatch(n->n==1),"Exactly ten scraps, Fortune cannot multiply supply");
            mando.events().stop(false);
            for (var host:event.hosts())check(host.ore().block(world).getType()==Material.DEEPSLATE,"Event cleans up each mined host");
            pass();
        });
    }
    private void prepareRestart() throws Exception {
        var folder=getDataFolder().toPath();Files.write(folder.resolve("expected-inventory.bin"),ItemStack.serializeItemsAsBytes(original));
        // Simulate a lost callback after the ledger committed the section reward.
        player.getPersistentDataContainer().set(NamespacedKey.fromString("mandomc:tutorial_sections_paid_v2"),PersistentDataType.INTEGER,0);
        player.saveData();var saved=Path.of("dev_world/players/data",player.getUniqueId()+".dat");
        Files.copy(saved,folder.resolve("crash-player.dat"),StandardCopyOption.REPLACE_EXISTING);
        var properties=new Properties();properties.setProperty("uuid",player.getUniqueId().toString());properties.setProperty("balance",Long.toString(mando.credits().ledger().balance(player.getUniqueId())));
        try(var out=Files.newOutputStream(folder.resolve("restart.properties"))){properties.store(out,"Isolated recovery fixture");}
    }
    private void restartCheck() throws Exception {
        var folder=getDataFolder().toPath();var properties=new Properties();try(var in=Files.newInputStream(folder.resolve("restart.properties"))){properties.load(in);}
        player=actors.create(mando.worlds().spawn("Batuu"),UUID.fromString(properties.getProperty("uuid")));player.loadData();
        check(TutorialInventory.active(player),"Public-shop escrow survives actual JVM restart");
        mando.tutorial().join(new PlayerJoinEvent(player,net.kyori.adventure.text.Component.empty()));
        var expected=ItemStack.deserializeItemsFromBytes(Files.readAllBytes(folder.resolve("expected-inventory.bin")));
        var actual=player.getInventory().getContents();check(expected.length==actual.length,"Recovered inventory size");
        for(int slot=0;slot<expected.length;slot++) {
            var want=expected[slot];var got=actual[slot];
            check(Objects.equals(want==null||want.isEmpty()?null:want,got==null||got.isEmpty()?null:got),"Recovered slot "+slot+" expected="+want+" actual="+got);
        }
        check(!TutorialInventory.active(player),"Recovery clears escrow");
        if (Files.exists(folder.resolve("migrated-native-inventory.bin"))) {
            var migrated=ItemStack.deserializeItemsFromBytes(Files.readAllBytes(folder.resolve("migrated-native-inventory.bin")));
            check(migrated[0].getAmount()==17&&"beskar".equals(mando.items().identify(migrated[0])),"Offline migration produces native ingot stack");
            check((migrated[1]==null||migrated[1].isEmpty())&&migrated[2].getType()==Material.DIAMOND&&migrated[2].getAmount()==3,"Offline migration preserves other slots");
        }
        later(120,()->{
            check(mando.credits().ledger().balance(player.getUniqueId())==Long.parseLong(properties.getProperty("balance")),"Restart cannot duplicate five section rewards");
            check(player.getPersistentDataContainer().getOrDefault(NamespacedKey.fromString("mandomc:tutorial_sections_paid_v2"),PersistentDataType.INTEGER,0)==31,"All lost callbacks reconcile");
            Files.writeString(folder.resolve("RESTART-PASSED.txt"),checks+" actual JVM recovery checks passed\n");pass();
        });
    }
    private void pass() throws Exception { finished=true;actors.close();Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),checks+" native assertions passed\n");getLogger().info("TUTORIAL HUB PASSED "+checks);Bukkit.shutdown(); }
    private void fail(Throwable error) { if(finished)return;finished=true;getLogger().log(java.util.logging.Level.SEVERE,"TUTORIAL HUB FAILED",error);try{getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());if(actors!=null)actors.close();}catch(Exception e){getLogger().warning(e.toString());}Bukkit.shutdown(); }
}
