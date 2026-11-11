package net.mandomc.verification;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

/** Run only in a disposable runtime: real native mining, isolated players and recipe/view assertions. */
public final class ProgressionProbe extends JavaPlugin implements Listener {
    private int checks;
    private MandoPlugin mando;
    private World world;
    private Player actor;
    private org.bukkit.block.Block block;
    private boolean denyBreak, denyDrops;
    private int nativeDiamonds;
    private int nativeExperience;
    private boolean litOre, lastOreLit;
    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); getLogger().info("PROGRESSION PROBE PASSED " + checks + " assertions"); }
            catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"PROGRESSION PROBE FAILED",error); }
            finally { Bukkit.shutdown(); }
        }, 80);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void beforeBreak(BlockBreakEvent event) {
        if (event.getPlayer() == actor && denyBreak) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void experience(BlockBreakEvent event) {
        if (event.getPlayer() == actor) nativeExperience = event.getExpToDrop();
    }
    @EventHandler(priority=EventPriority.LOWEST) public void nativeDrops(BlockDropItemEvent event) {
        if (event.getPlayer() != actor) return;
        lastOreLit = event.getBlockState().getBlockData() instanceof org.bukkit.block.data.Lightable light && light.isLit();
        nativeDiamonds = event.getItems().stream().map(Item::getItemStack)
                .filter(i -> java.util.Set.of(Material.DIAMOND,Material.REDSTONE,Material.RAW_IRON,Material.GOLD_NUGGET,Material.ANCIENT_DEBRIS).contains(i.getType())).mapToInt(ItemStack::getAmount).sum();
        if (denyDrops) event.setCancelled(true);
    }
    private void verify() throws Exception {
        mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(mando.isEnabled(), "complete combined MandoMC initialized");
        world = Bukkit.getWorlds().getFirst();
        block = world.getBlockAt(1000, 280, 1000);
        block.getChunk().load();
        actor = nativeTestPlayer(world, block.getLocation().add(0,1,2));
        actor.setGameMode(GameMode.SURVIVAL);
        for (Material ore : List.of(Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE)) {
            mine(ore, new ItemStack(Material.IRON_PICKAXE), "durasteel_scrap", false);
            check(nativeDiamonds==1 && nativeExperience>=3 && nativeExperience<=7, "native single loot and XP " + ore);
            for (int i=0;i<12;i++) {
                var fortune=new ItemStack(Material.DIAMOND_PICKAXE);fortune.addUnsafeEnchantment(Enchantment.FORTUNE,3);
                mine(ore, fortune, "durasteel_scrap", false);
                check(nativeDiamonds>=1 && nativeDiamonds<=4, "native Fortune III range");
            }
            var silk=new ItemStack(Material.DIAMOND_PICKAXE);silk.addUnsafeEnchantment(Enchantment.SILK_TOUCH,1);
            mine(ore,silk,null,false);
            var drops=drops();check(drops.size()==1 && drops.getFirst().getItemStack().getType()==ore,"Silk Touch retains ore " + ore);
            check(nativeExperience==0,"Silk Touch retains zero XP");
            mine(ore,new ItemStack(Material.STONE_PICKAXE),null,false);check(drops().isEmpty(),"wrong tool has no loot");
            actor.setGameMode(GameMode.CREATIVE);mine(ore,new ItemStack(Material.DIAMOND_PICKAXE),null,false);
            check(drops().isEmpty(),"Creative has no loot");actor.setGameMode(GameMode.SURVIVAL);
            denyBreak=true;mine(ore,new ItemStack(Material.IRON_PICKAXE),null,true);
            check(block.getType()==ore && drops().isEmpty(),"protected/cancelled break retains ore and no loot");denyBreak=false;
            denyDrops=true;mine(ore,new ItemStack(Material.IRON_PICKAXE),null,false);
            check(drops().isEmpty(),"cancelled drop event creates no loot");denyDrops=false;
        }
        mine(Material.EMERALD_ORE,new ItemStack(Material.IRON_PICKAXE),null,false);
        check(drops().size()==1 && drops().getFirst().getItemStack().getType()==Material.EMERALD,"other ores unaffected");
        check(mando.items().create("durasteel_scrap").getItemMeta().getItemModel().toString().equals("mandomc:legacy/durasteel_scrap"),"scrap uses existing model");
        recipes(); armor(); metalChain(); approvedRecipes(); electrumMining(); guide(); desertSpawns(); saberRecipes(); expandedMetals(); utilities();
        drops().forEach(Entity::remove);block.setType(Material.AIR,false);
    }
    private void saberRecipes() throws Exception {
        var field=MandoPlugin.class.getDeclaredField("craftingRecipes");field.setAccessible(true);var recipes=(CraftingRecipes)field.get(mando);
        int assembled=0, styles=0;
        for(var recipe:recipes.definitions()) {
            if(recipe.stonecutting()) {
                var nativeRecipe=Bukkit.getRecipe(new NamespacedKey("mandomc","refinish_"+recipe.id()));
                check(nativeRecipe instanceof StonecuttingRecipe,"native stonecutter recipe "+recipe.id());
                var stone=(StonecuttingRecipe)nativeRecipe;var input=recipes.ingredient(recipe.ingredients().get('I'));
                check(stone.getInputChoice().test(input) && recipe.result().equals(mando.items().identify(stone.getResult())),"exact saber input/style output "+recipe.id());
                var worn=input.clone();var meta=(Damageable)worn.getItemMeta();meta.setDamage(10);worn.setItemMeta(meta);
                check(!stone.getInputChoice().test(worn),"worn saber cannot receive free repair "+recipe.id());styles++;
            } else if(recipe.ingredients().values().stream().anyMatch(i->i.endsWith("_kyber")) && recipe.category().equals("weapons")
                    || recipe.id().endsWith("_kyber") || recipe.id().equals("precision_emitter_coil")) {
                var matrix=new ItemStack[9];
                for(int y=0;y<recipe.shape().size();y++)for(int x=0;x<recipe.shape().get(y).length();x++) {
                    char symbol=recipe.shape().get(y).charAt(x);if(symbol!=' ')matrix[y*3+x]=recipes.ingredient(recipe.ingredients().get(symbol));
                }
                var actual=Bukkit.craftItemResult(matrix,world,actor);
                check(recipe.result().equals(mando.items().identify(actual.getResult())),"native saber-chain crafting "+recipe.id());
                check(Arrays.stream(actual.getResultingMatrix()).allMatch(i->i==null||i.isEmpty()),"saber chain costs consumed "+recipe.id());
                if(recipe.category().equals("weapons"))assembled++;
            }
        }
        check(assembled==7 && styles==71,"all 78 saber identities have an assembly or style route");
        var viewer=new TestPlayer("SaberGuide");Bukkit.getPluginCommand("recipes").execute(viewer.player,"recipes",new String[]{"yoda_lightsaber"});
        check(viewer.view.getTopInventory().getItem(23).getType()==Material.STONECUTTER,"guide names actual saber workstation");
        check("yoda_lightsaber".equals(mando.items().identify(viewer.view.getTopInventory().getItem(25))),"guide style output remains genuine saber");
    }
    private List<Item> drops() { return new ArrayList<>(world.getNearbyEntitiesByType(Item.class,block.getLocation(),4)); }
    private void mine(Material material, ItemStack tool, String id, boolean cancelled) {
        drops().forEach(Entity::remove);block.setType(material,false);
        if(litOre && block.getBlockData() instanceof org.bukkit.block.data.Lightable light) {light.setLit(true);block.setBlockData(light,false);}
        actor.getInventory().setItemInMainHand(tool);nativeDiamonds=0;
        boolean broken=actor.breakBlock(block);
        check(broken != cancelled,"native break result " + material);
        if (id!=null) {
            var loot=drops();check(!loot.isEmpty(),"native mining produced loot");
            check(loot.stream().allMatch(i -> id.equals(mando.items().identify(i.getItemStack()))),"all native material drops replaced exactly once");
            check(loot.size()==1 && loot.getFirst().getItemStack().getAmount()>=1 && loot.getFirst().getItemStack().getAmount()<=5,"one final 1–5 scrap stack regardless of native/Fortune quantity");
        }
    }
    private void recipes() {
        var recipe=Bukkit.getRecipe(new NamespacedKey("mandomc","craft_plastoid"));
        check(recipe instanceof ShapelessRecipe,"plastoid registered shapeless");
        check(recipe.getResult().getAmount()==2 && "plastoid".equals(mando.items().identify(recipe.getResult())),"two catalog plastoid output");
        var units=List.of(Material.CLAY_BALL,Material.CLAY_BALL,Material.SAND,Material.COAL);
        for(int shift=0;shift<9;shift++) {
            ItemStack[] grid=new ItemStack[9];for(int i=0;i<4;i++)grid[(i*2+shift)%9]=new ItemStack(units.get(i));
            var found=Bukkit.getCraftingRecipe(grid,world);
            check(found!=null && "plastoid".equals(mando.items().identify(found.getResult())),"plastoid arbitrary placement " + shift);
        }
        for (Material wrong:List.of(Material.CHARCOAL,Material.RED_SAND,Material.CLAY)) {
            var grid=new ItemStack[]{new ItemStack(Material.CLAY_BALL),new ItemStack(Material.CLAY_BALL),new ItemStack(Material.SAND),new ItemStack(Material.COAL),null,null,null,null,null};
            grid[wrong==Material.CHARCOAL?3:wrong==Material.RED_SAND?2:0]=new ItemStack(wrong);
            check(Bukkit.getCraftingRecipe(grid,world)==null,"reject substitute " + wrong);
        }
        var grid=new ItemStack[]{new ItemStack(Material.CLAY_BALL,2),new ItemStack(Material.SAND),new ItemStack(Material.COAL),null,null,null,null,null,null};
        check(Bukkit.getCraftingRecipe(grid,world)==null,"two clay units require two slots");
        grid[0]=new ItemStack(Material.CLAY_BALL);grid[4]=new ItemStack(Material.CLAY_BALL);grid[8]=new ItemStack(Material.STICK);
        check(Bukkit.getCraftingRecipe(grid,world)==null,"extra ingredient rejected");
        for(String id:List.of("speederbike_chassis","speederbike_repulsor","uwing_cockpit","uwing_wing","speederbike","uwing")) {
            check(Bukkit.getRecipe(new NamespacedKey("mandomc","craft_"+id))!=null,"vehicle recipe registered " + id);
        }
        for(int row=0;row<3;row++) {
            var matrix=new ItemStack[9];matrix[row*3]=mando.items().create("tiefighter_wing");matrix[row*3+1]=mando.items().create("tiefighter_cockpit");matrix[row*3+2]=mando.items().create("tiefighter_wing");
            check("tiefighter".equals(mando.items().identify(Bukkit.getCraftingRecipe(matrix,world).getResult())),"TIE native matching unchanged row " + row);
        }
    }
    private void armor() {
        var view=MenuType.CRAFTING.create(actor,Component.text("Progression craft checks"));
        var inv=(CraftingInventory)view.getTopInventory();
        for(Material type:Material.values()) if(net.mandomc.gameplay.rules.SurvivalRules.armorMaterial(type) && type.isItem()) {
            inv.setResult(new ItemStack(type));mando.rules().craft(new PrepareItemCraftEvent(inv,view,false));
            check(inv.getResult()==null,"vanilla armor craft denied " + type);
            inv.setResult(new ItemStack(type));mando.rules().craft(new PrepareItemCraftEvent(inv,view,true));
            check(inv.getResult()!=null,"vanilla armor repair allowed " + type);
        }
        for(String family:List.of("robes","plastoid","durasteel","beskar")) for(String slot:List.of("helmet","chestplate","leggings","boots")) {
            String id=family+"_"+slot;
            var item=mando.items().create(id);inv.clear();inv.setResult(item);
            mando.rules().craft(new PrepareItemCraftEvent(inv,view,false));
            check(id.equals(mando.items().identify(inv.getResult())),"custom armor craft retained " + id);
            var repaired=item.clone();if(repaired.getItemMeta() instanceof Damageable meta){meta.setDamage(10);repaired.setItemMeta(meta);}
            inv.setMatrix(new ItemStack[]{item,item.clone(),null,null,null,null,null,null,null});inv.setResult(repaired);
            mando.rules().craft(new PrepareItemCraftEvent(inv,view,true));
            check(id.equals(mando.items().identify(inv.getResult())),"custom crafting repair identity " + id);
            var anvil=MenuType.ANVIL.create(actor,Component.text("Repair"));anvil.getTopInventory().setItem(0,item);
            var event=new PrepareAnvilEvent(anvil,repaired);mando.rules().anvil(event);
            check(id.equals(mando.items().identify(event.getResult())),"custom anvil repair retained " + id);
        }
        var smith=MenuType.SMITHING.create(actor,Component.text("Smithing checks"));
        var vanillaSmith=new PrepareSmithingEvent(smith,new ItemStack(Material.NETHERITE_CHESTPLATE));mando.rules().smith(vanillaSmith);check(vanillaSmith.getResult()==null,"vanilla smithing armor creation denied");
        var customSmith=new PrepareSmithingEvent(smith,mando.items().create("durasteel_chestplate"));mando.rules().smith(customSmith);check("durasteel_chestplate".equals(mando.items().identify(customSmith.getResult())),"custom smithing presentation remains allowed");
        var crafter=new CrafterCraftEvent(block,(CraftingRecipe)Bukkit.getRecipe(new NamespacedKey("minecraft","diamond_helmet")),new ItemStack(Material.DIAMOND_HELMET));
        Bukkit.getPluginManager().callEvent(crafter);check(crafter.isCancelled(),"automated vanilla armor craft denied");
        crafter=new CrafterCraftEvent(block,(CraftingRecipe)Bukkit.getRecipe(new NamespacedKey("mandomc","craft_durasteel_helmet")),mando.items().create("durasteel_helmet"));
        Bukkit.getPluginManager().callEvent(crafter);check(!crafter.isCancelled(),"automated custom armor craft allowed");
    }
    private void guide() throws Exception {
        var viewer=new TestPlayer("ProgressionGuide");
        Bukkit.getPluginCommand("recipes").execute(viewer.player,"recipes",new String[]{"plastoid"});
        var inventory=viewer.view.getTopInventory();
        var initial=mando.items().create("plastoid").serializeAsBytes();
        var ingredient=inventory.getItem(10);var lore=ingredient.getItemMeta().lore();
        check(PlainTextComponentSerializer.plainText().serialize(lore.getLast()).equals("Click to see how to obtain."),"ingredient preview has exact bottom obtain hint");
        check(lore.getLast().decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC)==net.kyori.adventure.text.format.TextDecoration.State.FALSE,"obtain hint is nonitalic");
        check(Arrays.equals(initial,mando.items().create("plastoid").serializeAsBytes()),"preview leaves catalog templates untouched");
        check(mando.items().create("plastoid").getItemMeta().lore().stream().noneMatch(line->PlainTextComponentSerializer.plainText().serialize(line).contains("Click to see")),"real items never gain UI hint");
        check(inventory.getItem(10).getType()==Material.CLAY_BALL && inventory.getItem(11).getType()==Material.CLAY_BALL,"guide shows separate clay units");
        check(inventory.getItem(12).getType()==Material.SAND && inventory.getItem(19).getType()==Material.COAL,"guide shows sand and coal");
        check(inventory.getItem(25).getAmount()==2 && "plastoid".equals(mando.items().identify(inventory.getItem(25))),"guide shows two custom output");
        check(PlainTextComponentSerializer.plainText().serialize(inventory.getItem(23).getItemMeta().displayName()).contains("any arrangement"),"guide labels shapeless arrangement");
        check(inventory.getItem(20)==null && inventory.getItem(30)==null,"unused guide cells empty");
        Bukkit.getPluginCommand("recipes").execute(viewer.player,"recipes",new String[]{"tiefighter"});inventory=viewer.view.getTopInventory();
        check(inventory.getItem(10)==null && inventory.getItem(11)==null && inventory.getItem(12)==null,"TIE top row empty");
        check("tiefighter_wing".equals(mando.items().identify(inventory.getItem(19))) && "tiefighter_cockpit".equals(mando.items().identify(inventory.getItem(20))) && "tiefighter_wing".equals(mando.items().identify(inventory.getItem(21))),"TIE preview one row down");
        Bukkit.getPluginCommand("recipes").execute(viewer.player,"recipes",new String[0]);inventory=viewer.view.getTopInventory();
        check(PlainTextComponentSerializer.plainText().serialize(inventory.getItem(11).getItemMeta().displayName()).contains("Materials"),"Materials fills exact slot between All and Armor");
        check(inventory.getItem(10).getType()==Material.KNOWLEDGE_BOOK && PlainTextComponentSerializer.plainText().serialize(inventory.getItem(12).getItemMeta().displayName()).contains("Armor"),"adjacent category selections retained");
        activate(viewer,11);inventory=viewer.view.getTopInventory();
        var guideField=MandoPlugin.class.getDeclaredField("recipeGuide");guideField.setAccessible(true);var guide=(RecipeGuide)guideField.get(mando);
        var transfer=new InventoryClickEvent(viewer.view,InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.MOVE_TO_OTHER_INVENTORY);guide.click(transfer);check(transfer.isCancelled(),"material-list shift transfer blocked");
        var recipesField=MandoPlugin.class.getDeclaredField("craftingRecipes");recipesField.setAccessible(true);var recipes=(CraftingRecipes)recipesField.get(mando);
        check(recipes.guideDefinitions().stream().noneMatch(r->r.category().equals("fuel")),"unused fuel category hidden");
        check(recipes.guideDefinitions().stream().noneMatch(r->Set.of("aurodium","cortosis","phrik","songsteel","lommite","neuranium","mandalorian_iron").contains(r.result())),"unused metals hidden from recipe guide");
        for(String id:List.of("agrinium","agrinium_sheet","durasteel","durasteel_sheet","electrum_sheet","beskar","beskar_sheet"))check(recipes.guideDefinitions().stream().anyMatch(r->r.result().equals(id)),"used metal route visible " + id);
        Bukkit.getPluginCommand("recipes").execute(viewer.player,"recipes",new String[]{"barrel_liner"});activate(viewer,10);inventory=viewer.view.getTopInventory();
        check(inventory.getItem(23).getType()==Material.CRAFTING_TABLE && "agrinium_scrap".equals(mando.items().identify(inventory.getItem(10))),"ingredient navigation opens actual Agrinium scrap refining route");
        activate(viewer,45);check("barrel_liner".equals(mando.items().identify(viewer.view.getTopInventory().getItem(25))),"ingredient navigation returns to parent recipe");
        for (String id : List.of("agrinium_pickaxe","agrinium_shovel")) {
            var item=mando.items().create(id);check(item.getType()==(id.endsWith("pickaxe")?Material.IRON_PICKAXE:Material.IRON_SHOVEL),"Agrinium tools use native iron behavior " + id);
        }
        check(!mando.items().ids().contains("agrinium_wrench"),"no unrequested Agrinium wrench");
    }
    @SuppressWarnings("unchecked") private void activate(TestPlayer viewer,int slot) throws Exception {
        var holder=viewer.view.getTopInventory().getHolder();var field=holder.getClass().getDeclaredField("actions");field.setAccessible(true);
        ((Map<Integer,java.util.function.Consumer<Player>>)field.get(holder)).get(slot).accept(viewer.player);
    }
    private void metalChain() throws Exception {
        var materials=new MetalMaterials(mando.items());
        for (var pair : Map.of(Material.IRON_INGOT,"agrinium",Material.REDSTONE,"electrum").entrySet()) {
            var plain=new ItemStack(pair.getKey(),7);var canonical=materials.canonical(plain);
            check(pair.getValue().equals(mando.items().identify(canonical)) && canonical.getAmount()==7,"native ingot migration keeps amount " + pair.getValue());
            var named=plain.clone();var meta=named.getItemMeta();meta.displayName(Component.text(pair.getValue()));named.setItemMeta(meta);
            check(materials.canonical(named)==named,"named lookalike not silently accepted " + pair.getValue());
            var old=mando.items().create(pair.getValue());old.setType(Material.ECHO_SHARD);
            check(materials.canonical(old).getType()==pair.getKey(),"old catalog base migrates " + pair.getValue());
        }
        for (Material ore:List.of(Material.COPPER_ORE,Material.DEEPSLATE_COPPER_ORE)) {
            mine(ore,new ItemStack(Material.IRON_PICKAXE),null,false);
            Material raw=ore.name().contains("IRON")?Material.RAW_IRON:Material.RAW_COPPER;
            check(drops().stream().allMatch(i->i.getItemStack().getType()==raw),"native raw material mining retained " + ore);
        }
        for (Material furnace:List.of(Material.FURNACE,Material.BLAST_FURNACE)) {
            smeltNative(furnace,Material.RAW_IRON,"agrinium");
        }
        for (var pair:Map.of(Material.IRON_BLOCK,"agrinium",Material.REDSTONE_BLOCK,"electrum").entrySet()) {
            var ingot=mando.items().create(pair.getValue());var matrix=new ItemStack[9];Arrays.setAll(matrix,i->ingot.clone());
            var packed=Bukkit.craftItemResult(matrix,world,actor);
            check(packed.getResult().getType()==pair.getKey(),"nine ingots retain storage route " + pair.getValue());
            matrix=new ItemStack[9];matrix[4]=mando.items().create(pair.getValue()+"_block");var unpacked=Bukkit.craftItemResult(matrix,world,actor).getResult();
            check(unpacked.getAmount()==9 && pair.getValue().equals(mando.items().identify(unpacked)),"storage unpacks nine canonical ingots " + pair.getValue());
        }
    }
    private void smeltNative(Material furnaceType,Material raw,String id) throws Exception {
        var pos=block.getRelative(2,0,0);pos.setType(furnaceType,false);
        var furnace=(org.bukkit.block.Furnace)pos.getState();furnace.getInventory().setSmelting(new ItemStack(raw));furnace.getInventory().setFuel(new ItemStack(Material.COAL));
        Object level=world.getClass().getMethod("getHandle").invoke(world);
        Class<?> bp=Class.forName("net.minecraft.core.BlockPos");Object at=bp.getConstructor(int.class,int.class,int.class).newInstance(pos.getX(),pos.getY(),pos.getZ());
        Object tile=level.getClass().getMethod("getBlockEntity",bp).invoke(level,at);
        Object state=level.getClass().getMethod("getBlockState",bp).invoke(level,at);
        Class<?> base=Class.forName("net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity");
        var tick=base.getMethod("serverTick",Class.forName("net.minecraft.server.level.ServerLevel"),bp,Class.forName("net.minecraft.world.level.block.state.BlockState"),base);
        for(int i=0;i<205;i++)tick.invoke(null,level,at,state,tile);
        var result=furnace.getInventory().getResult();
        check(result!=null && id.equals(mando.items().identify(result)),"native furnace tick produces catalog ingot " + furnaceType + " " + id);
        check(furnace.getInventory().getSmelting()==null,"native smelting consumes one raw item");
        furnace.getInventory().clear();pos.setType(Material.AIR,false);
    }
    private void approvedRecipes() throws Exception {
        var field=MandoPlugin.class.getDeclaredField("craftingRecipes");field.setAccessible(true);var recipes=(CraftingRecipes)field.get(mando);
        var ids=Set.of("plastoid","barrel_liner","alloy_plating","wiring_coil","energy_conduits","fusion_coil_stabilizer","plasma_capacitor_array","electrum_sheet","transparisteel_shard","circuit_board","precision_crafted_lens","straight_hilt","great_hilt","whip_hilt","pike_hilt","fang_hilt","speederbike_chassis","speederbike_repulsor","uwing_cockpit","uwing_wing","speederbike","uwing","agrinium_pickaxe","agrinium_shovel");
        for(var recipe:recipes.definitions()) if(ids.contains(recipe.id())) {
            var matrix=new ItemStack[9];
            for(int y=0;y<recipe.shape().size();y++)for(int x=0;x<recipe.shape().get(y).length();x++) {
                char symbol=recipe.shape().get(y).charAt(x);if(symbol!=' ')matrix[y*3+x]=recipes.ingredient(recipe.ingredients().get(symbol));
            }
            var found=Bukkit.getCraftingRecipe(matrix,world);
            check(found!=null && recipe.result().equals(mando.items().identify(found.getResult())),"native matching " + recipe.id());
            var actual=Bukkit.craftItemResult(matrix,world,actor);
            check(recipe.result().equals(mando.items().identify(actual.getResult())) && actual.getResult().getAmount()==recipe.amount(),"native crafting output " + recipe.id());
            check(Arrays.stream(actual.getResultingMatrix()).allMatch(i->i==null || i.isEmpty()),"one unit consumed from every occupied slot " + recipe.id());
        }
        var matrix=new ItemStack[]{null,mando.items().create("electrum"),null,mando.items().create("electrum"),mando.items().create("plastoid"),mando.items().create("electrum"),null,mando.items().create("electrum"),null};
        matrix[1]=new ItemStack(Material.REDSTONE);check(Bukkit.getCraftingRecipe(matrix,world)==null,"untagged redstone cannot match exact Electrum ingredient");
        matrix[1]=mando.items().create("electrum");var fake=mando.items().create("plastoid");var meta=fake.getItemMeta();meta.getPersistentDataContainer().remove(ItemCatalog.ITEM_ID);fake.setItemMeta(meta);matrix[4]=fake;
        check(Bukkit.getCraftingRecipe(matrix,world)==null,"lookalike plastoid cannot match exact ingredient");
        check(Bukkit.getRecipe(new NamespacedKey("mandomc","smelt_electrum_sheet"))==null,"old one-ingot sheet shortcut absent");
    }
    private void electrumMining() {
        for(Material ore:List.of(Material.REDSTONE_ORE,Material.DEEPSLATE_REDSTONE_ORE)) {
            for(boolean lit:List.of(false,true)) {
                litOre=lit;mine(ore,new ItemStack(Material.IRON_PICKAXE),"electrum_scrap",false);
                check(lastOreLit==lit && nativeDiamonds>=4 && nativeDiamonds<=5,"lit/unlit Electrum retains native quantity " + lit);
            }
            litOre=false;
            for(int i=0;i<12;i++) {
                var pickaxe=new ItemStack(Material.IRON_PICKAXE);pickaxe.addUnsafeEnchantment(Enchantment.FORTUNE,3);
                mine(ore,pickaxe,"electrum_scrap",false);check(nativeDiamonds>=4 && nativeDiamonds<=8,"native redstone Fortune quantity retained");
            }
            var silk=new ItemStack(Material.IRON_PICKAXE);silk.addUnsafeEnchantment(Enchantment.SILK_TOUCH,1);mine(ore,silk,null,false);
            check(drops().size()==1 && drops().getFirst().getItemStack().getType()==ore,"Electrum Silk Touch retains ore");
            mine(ore,new ItemStack(Material.STONE_PICKAXE),null,false);check(drops().isEmpty(),"Electrum wrong tool no drops");
            denyBreak=true;mine(ore,new ItemStack(Material.IRON_PICKAXE),null,true);check(drops().isEmpty() && block.getType()==ore,"protected Electrum untouched");denyBreak=false;
            denyDrops=true;mine(ore,new ItemStack(Material.IRON_PICKAXE),null,false);check(drops().isEmpty(),"cancelled Electrum drop event creates no loot");denyDrops=false;
            actor.setGameMode(GameMode.CREATIVE);mine(ore,new ItemStack(Material.IRON_PICKAXE),null,false);check(drops().isEmpty(),"Creative Electrum no loot");actor.setGameMode(GameMode.SURVIVAL);
        }
        var copper=new ItemStack(Material.COPPER_INGOT);check(new MetalMaterials(mando.items()).canonical(copper)==copper,"ordinary copper is no longer Electrum");
    }
    private void expandedMetals() throws Exception {
        for(Material ore:List.of(Material.IRON_ORE,Material.DEEPSLATE_IRON_ORE,Material.NETHER_GOLD_ORE,Material.ANCIENT_DEBRIS)) {
            String id=ore==Material.IRON_ORE||ore==Material.DEEPSLATE_IRON_ORE?"agrinium_scrap":"beskar_scrap";
            Material right=ore==Material.ANCIENT_DEBRIS?Material.DIAMOND_PICKAXE:ore==Material.NETHER_GOLD_ORE?Material.WOODEN_PICKAXE:Material.STONE_PICKAXE;
            mine(ore,new ItemStack(right),id,false);
            check(nativeDiamonds>0,"native ordinary loot exists for "+ore);
            check(ore!=Material.ANCIENT_DEBRIS||nativeDiamonds==1,"ordinary debris native self-drop recognized without mistaking it for Silk Touch");
            for(int n=0;n<16;n++) {
                var fortune=new ItemStack(right);fortune.addUnsafeEnchantment(Enchantment.FORTUNE,3);mine(ore,fortune,id,false);
                int min=ore==Material.NETHER_GOLD_ORE?2:1,max=ore==Material.NETHER_GOLD_ORE?24:ore==Material.ANCIENT_DEBRIS?1:4;
                check(nativeDiamonds>=min&&nativeDiamonds<=max,"native Fortune quantity "+ore+" count="+nativeDiamonds);
            }
            var silk=new ItemStack(right);silk.addUnsafeEnchantment(Enchantment.SILK_TOUCH,1);mine(ore,silk,null,false);
            check(drops().size()==1 && drops().getFirst().getItemStack().getType()==ore,"Silk Touch keeps correct ore "+ore);
            check(MetalMaterials.FORMS.get(ore).equals(mando.items().identify(drops().getFirst().getItemStack())),"Silk ore gains standard catalog tooltip "+ore);
            var wrong=new ItemStack(ore==Material.ANCIENT_DEBRIS?Material.IRON_PICKAXE:ore==Material.NETHER_GOLD_ORE?Material.WOODEN_SHOVEL:Material.WOODEN_PICKAXE);
            mine(ore,wrong,null,false);check(drops().isEmpty(),"wrong native tool still yields no scrap "+ore);
            denyBreak=true;mine(ore,new ItemStack(right),null,true);check(drops().isEmpty(),"cancelled break gives no scrap "+ore);denyBreak=false;
            denyDrops=true;mine(ore,new ItemStack(right),null,false);check(drops().isEmpty(),"cancelled native drop gives no scrap "+ore);denyDrops=false;
            actor.setGameMode(GameMode.CREATIVE);mine(ore,new ItemStack(right),null,false);check(drops().isEmpty(),"Creative gives no scrap "+ore);actor.setGameMode(GameMode.SURVIVAL);
        }
        var canonical=new MetalMaterials(mando.items());
        for(var form:MetalMaterials.FORMS.entrySet()) {
            var item=canonical.canonical(new ItemStack(form.getKey(),2));
            check(form.getValue().equals(mando.items().identify(item))&&item.getAmount()==2,"native themed form normalizes without quantity change "+form.getValue());
            check(item.getItemMeta().lore().size()>=4,"form has badge/spacing/functional lore "+form.getValue());
            var named=new ItemStack(form.getKey());var meta=named.getItemMeta();meta.displayName(Component.text("Third party"));named.setItemMeta(meta);check(canonical.canonical(named)==named,"named form preserved "+form.getValue());
        }
        for(String metal:List.of("agrinium","durasteel","electrum","beskar")) {
            var matrix=new ItemStack[9];Arrays.setAll(matrix,i->mando.items().create(metal));
            var packed=Bukkit.craftItemResult(matrix,world,actor);check((metal+"_block").equals(mando.items().identify(packed.getResult()))&&packed.getResult().getAmount()==1,"nine canonical ingots yield one standard storage block "+metal);
            check(Arrays.stream(packed.getResultingMatrix()).allMatch(i->i==null||i.isEmpty()),"nine ingots consumed "+metal);
            matrix=new ItemStack[9];matrix[4]=packed.getResult();var unpacked=Bukkit.craftItemResult(matrix,world,actor);
            check(metal.equals(mando.items().identify(unpacked.getResult()))&&unpacked.getResult().getAmount()==9,"storage round trip returns exactly nine matching ingots "+metal);
            placedBlock(metal+"_block");
            shiftStorage(metal);
            mine(mando.items().create(metal+"_block").getType(),new ItemStack(Material.NETHERITE_PICKAXE),null,false);
            check(drops().size()==1&&(metal+"_block").equals(mando.items().identify(drops().getFirst().getItemStack())),"placed native block mining restores correct catalog form "+metal);
        }
        var viewer=new TestPlayer("StorageGuide");Bukkit.getPluginCommand("recipes").execute(viewer.player,"recipes",new String[]{"beskar_from_block"});
        check(viewer.view.getTopInventory().getItem(25).getAmount()==9&&"beskar_block".equals(mando.items().identify(viewer.view.getTopInventory().getItem(10))),"guide shows block-to-nine conversion");
        activate(viewer,10);check("beskar_block".equals(mando.items().identify(viewer.view.getTopInventory().getItem(25))),"clicking block opens reciprocal packing recipe");
    }
    private void placedBlock(String id) throws Exception {
        var base=block.getRelative(0,-1,0);base.setType(Material.STONE,false);block.setType(Material.AIR,false);
        var held=mando.items().create(id);held.setAmount(2);actor.getInventory().setItemInMainHand(held);
        var handle=actor.getClass().getMethod("getHandle").invoke(actor);var mode=handle.getClass().getField("gameMode").get(handle);
        var level=world.getClass().getMethod("getHandle").invoke(world);
        var vecType=Class.forName("net.minecraft.world.phys.Vec3");var vec=vecType.getConstructor(double.class,double.class,double.class).newInstance(block.getX()+.5,(double)block.getY(),block.getZ()+.5);
        var posType=Class.forName("net.minecraft.core.BlockPos");var pos=posType.getConstructor(int.class,int.class,int.class).newInstance(base.getX(),base.getY(),base.getZ());
        var direction=Class.forName("net.minecraft.core.Direction");var hitType=Class.forName("net.minecraft.world.phys.BlockHitResult");
        var hit=hitType.getConstructor(vecType,direction,posType,boolean.class).newInstance(vec,direction.getField("UP").get(null),pos,false);
        var handType=Class.forName("net.minecraft.world.InteractionHand");var nativeItem=handle.getClass().getMethod("getMainHandItem").invoke(handle);
        mode.getClass().getMethod("useItemOn",Class.forName("net.minecraft.server.level.ServerPlayer"),Class.forName("net.minecraft.world.level.Level"),Class.forName("net.minecraft.world.item.ItemStack"),handType,hitType).invoke(mode,handle,level,nativeItem,handType.getField("MAIN_HAND").get(null),hit);
        check(block.getType()==held.getType(),"native use places catalog storage block "+id);
        check(actor.getInventory().getItemInMainHand().getAmount()==1,"native placement consumes exactly one catalog block "+id);
        actor.getInventory().clear();
    }
    private void shiftStorage(String metal) throws Exception {
        actor.getInventory().clear();
        var table=block.getRelative(3,0,0);table.setType(Material.CRAFTING_TABLE,false);
        var view=actor.openWorkbench(table.getLocation(),false);var crafting=(CraftingInventory)view.getTopInventory();
        var matrix=new ItemStack[9];Arrays.setAll(matrix,i->{var item=mando.items().create(metal);item.setAmount(3);return item;});crafting.setMatrix(matrix);
        var menu=view.getClass().getMethod("getHandle").invoke(view);var handle=actor.getClass().getMethod("getHandle").invoke(actor);
        menu.getClass().getMethod("slotsChanged",Class.forName("net.minecraft.world.Container")).invoke(menu,menu.getClass().getField("craftSlots").get(menu));
        menu.getClass().getMethod("clicked",int.class,int.class,Class.forName("net.minecraft.world.inventory.ContainerInput"),Class.forName("net.minecraft.world.entity.player.Player")).invoke(menu,0,0,Class.forName("net.minecraft.world.inventory.ContainerInput").getField("QUICK_MOVE").get(null),handle);
        check(Arrays.stream(actor.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->(metal+"_block").equals(mando.items().identify(i))).mapToInt(ItemStack::getAmount).sum()==3,"native shift craft packs 27 ingots into three blocks "+metal);
        check(Arrays.stream(crafting.getMatrix()).allMatch(i->i==null||i.isEmpty()),"shift packing consumes all 27 inputs "+metal);
        actor.getInventory().clear();matrix=new ItemStack[9];var blocks=mando.items().create(metal+"_block");blocks.setAmount(3);matrix[4]=blocks;crafting.setMatrix(matrix);
        menu.getClass().getMethod("slotsChanged",Class.forName("net.minecraft.world.Container")).invoke(menu,menu.getClass().getField("craftSlots").get(menu));
        menu.getClass().getMethod("clicked",int.class,int.class,Class.forName("net.minecraft.world.inventory.ContainerInput"),Class.forName("net.minecraft.world.entity.player.Player")).invoke(menu,0,0,Class.forName("net.minecraft.world.inventory.ContainerInput").getField("QUICK_MOVE").get(null),handle);
        check(Arrays.stream(actor.getInventory().getStorageContents()).filter(Objects::nonNull).filter(i->metal.equals(mando.items().identify(i))).mapToInt(ItemStack::getAmount).sum()==27,"native shift craft unpacks three blocks into exactly 27 ingots "+metal);
        actor.closeInventory();actor.getInventory().clear();
    }
    private void utilities() throws Exception {
        var field=MandoPlugin.class.getDeclaredField("craftingRecipes");field.setAccessible(true);var recipes=(CraftingRecipes)field.get(mando);int count=0;
        for(var recipe:recipes.definitions())if(recipe.category().equals("utilities")) {
            var matrix=new ItemStack[9];
            for(int y=0;y<recipe.shape().size();y++)for(int x=0;x<recipe.shape().get(y).length();x++){char c=recipe.shape().get(y).charAt(x);if(c!=' ')matrix[y*3+x]=recipes.ingredient(recipe.ingredients().get(c));}
            var result=Bukkit.craftItemResult(matrix,world,actor);check(result.getResult().getType()==Material.matchMaterial(recipe.result())&&result.getResult().getAmount()==recipe.amount(),"native sheet utility matching/output "+recipe.id());
            check(Arrays.stream(result.getResultingMatrix()).allMatch(i->i==null||i.isEmpty()),"sheet utility exact slot costs consumed "+recipe.id());
            for(int i=0;i<9;i++)if(matrix[i]!=null&&"agrinium_sheet".equals(mando.items().identify(matrix[i])))matrix[i]=mando.items().create("agrinium");
            check(Bukkit.getCraftingRecipe(matrix,world)==null,"old ingot shortcut absent "+recipe.id());count++;
        }
        check(count==26,"all 26 iron utility recipes use Agrinium sheets");
    }
    private void desertSpawns() throws Exception {
        var location=Bukkit.getWorld(NamespacedKey.minecraft("devworld")).getBlockAt(1000,280,1000).getLocation();
        location.getChunk().load();
        for(EntityType type:List.of(EntityType.SLIME,EntityType.HUSK,EntityType.RABBIT,EntityType.CAMEL,EntityType.CAMEL_HUSK,EntityType.PARCHED)) {
            for(var reason:CreatureSpawnEvent.SpawnReason.values()) {
                boolean expected=reason==CreatureSpawnEvent.SpawnReason.NATURAL || reason==CreatureSpawnEvent.SpawnReason.CHUNK_GEN;
                check(mando.rules().disabledNaturalSpawn(type,reason)==expected,"natural-only blacklist " + type + " " + reason);
            }
            var before=new com.destroystokyo.paper.event.entity.PreCreatureSpawnEvent(location,type,CreatureSpawnEvent.SpawnReason.NATURAL);
            Bukkit.getPluginManager().callEvent(before);check(before.isCancelled() && before.shouldAbortSpawn(),"pre-spawn stops initialization " + type);
            var natural=(LivingEntity)location.getWorld().spawnEntity(location,type,CreatureSpawnEvent.SpawnReason.NATURAL);
            check(!natural.isValid(),"real natural spawn refused " + type);
            for(var reason:List.of(CreatureSpawnEvent.SpawnReason.CUSTOM,CreatureSpawnEvent.SpawnReason.COMMAND,CreatureSpawnEvent.SpawnReason.SPAWNER,CreatureSpawnEvent.SpawnReason.BREEDING)) {
                var explicit=location.getWorld().spawnEntity(location,type,reason);check(explicit.isValid(),"explicit spawn retained " + type + " " + reason);explicit.remove();
            }
        }
        check(!mando.rules().disabledNaturalSpawn(EntityType.PIG,CreatureSpawnEvent.SpawnReason.NATURAL)
                && !mando.rules().disabledNaturalSpawn(EntityType.ZOMBIE,CreatureSpawnEvent.SpawnReason.NATURAL),"custom model hosts and generic mobs not globally banned");
        var generated=location.getWorld().spawnEntity(location,EntityType.CAMEL,CreatureSpawnEvent.SpawnReason.DEFAULT);
        var custom=location.getWorld().spawnEntity(location.clone().add(1,0,0),EntityType.CAMEL,CreatureSpawnEvent.SpawnReason.CUSTOM);
        var named=location.getWorld().spawnEntity(location.clone().add(2,0,0),EntityType.CAMEL,CreatureSpawnEvent.SpawnReason.DEFAULT);named.customName(Component.text("Keep named fixture"));
        mando.rules().generatedChunk(new org.bukkit.event.world.ChunkLoadEvent(location.getChunk(),false));check(generated.isValid(),"existing chunk/entity not swept");
        mando.rules().generatedChunk(new org.bukkit.event.world.ChunkLoadEvent(location.getChunk(),true));
        check(!generated.isValid(),"new-chunk initial DEFAULT camel suppressed without loot");
        check(custom.isValid() && named.isValid(),"new-chunk custom/named entities preserved");custom.remove();named.remove();
        var mobs=new net.mandomc.integrations.mobs.MythicMobAccess();
        for(String id:net.mandomc.integrations.mobs.MythicMobAccess.TYPES) {
            var mob=mobs.spawn(id,location.clone().add(5,0,0),1);
            check(mob!=null && mob.isValid() && mobs.managed(mob),"custom sandy encounter still spawns " + id);mobs.remove(mob);
        }
    }
    private void check(boolean value,String label) {if(!value)throw new IllegalStateException(label);checks++;getLogger().info("PASS "+label);}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "CombatTest");
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
