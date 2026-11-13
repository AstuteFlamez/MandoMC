package net.mandomc.verification;

import java.util.ArrayList;
import java.util.List;
import net.mandomc.integrations.paper.RedstoneCircuitBoundary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Powerable;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable isolated worlds only; never install in the shared runtime. */
public final class RedstoneProbe extends JavaPlugin implements Listener {
    private int checks;
    private int events;
    private int settledEvents;
    private final java.util.Map<Material,Integer> eventTypes=new java.util.EnumMap<>(Material.class);
    private final List<Block> targets = new ArrayList<>();
    private final List<Block> sources = new ArrayList<>();
    private World world;
    private ServerLevel level;
    private Block dispenser;
    private Block dropper;
    private Block hopper;
    private Block manualDoor;
    private net.mandomc.gameplay.items.ItemCatalog catalog;
    private Block crafter;

    private void check(boolean valid, String reason) {
        if (!valid) throw new AssertionError(reason);
        checks++;
    }

    @Override public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            try { begin(); }
            catch (Throwable failure) { fail(failure); }
        });
    }

    private Block set(int x, int y, int z, String data, boolean physics) {
        Block block = world.getBlockAt(x,y,z);
        block.getChunk().addPluginChunkTicket(this);
        block.setBlockData(Bukkit.createBlockData(data), physics);
        return block;
    }

    private void begin() throws Exception {
        world = Bukkit.getWorlds().getFirst();
        level = ((CraftWorld) world).getHandle();
        // Existing powered state at enable, with no terrain removal.
        set(0,80,0,"minecraft:stone",false);
        var old = set(0,81,0,"minecraft:redstone_wire[power=15]",false);
        var locked = set(2,81,0,"minecraft:hopper[enabled=false]",false);
        // Run the exact deployed SurvivalRules constructor with the scoped registration overlay.
        catalog=new net.mandomc.gameplay.items.ItemCatalog(getDataFolder().toPath());
        catalog.reload();
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.InputStreamReader(getResource("rules/survival.yml"),java.nio.charset.StandardCharsets.UTF_8));
        var rules=new net.mandomc.gameplay.rules.SurvivalRules(this,config,
                new org.bukkit.configuration.file.YamlConfiguration(),item->false,catalog);
        for(String id:List.of("electrum","electrum_scrap","electrum_block","electrum_ore","deepslate_electrum_ore")) {
            var item=catalog.create(id);
            check(item!=null && id.equals(catalog.identify(item)),"Electrum catalog identity "+id);
            check(!rules.banned(item),"Electrum material remains usable "+id);
        }
        var allRecipes=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                getDataFolder().toPath().resolve("items/recipes.yml").toFile());
        var metalRecipes=new org.bukkit.configuration.file.YamlConfiguration();
        metalRecipes.set("schema-version",1);
        for(String id:allRecipes.getConfigurationSection("recipes").getKeys(false)) {
            if(allRecipes.getString("recipes."+id+".result","").startsWith("electrum"))
                metalRecipes.set("recipes."+id,allRecipes.getConfigurationSection("recipes."+id));
        }
        new net.mandomc.gameplay.items.CraftingRecipes(this,catalog,metalRecipes);
        ItemStack[] grid=new ItemStack[9];
        java.util.Arrays.setAll(grid,i->catalog.create("electrum_scrap"));
        var recipe=Bukkit.getCraftingRecipe(grid,world);
        check(recipe!=null && "electrum".equals(catalog.identify(recipe.getResult())),"native Electrum crafting retains item identity");
        check(old.getBlockData().getAsString().contains("power=0"), "existing dust depowered");
        check(locked.getBlockData().getAsString().contains("enabled=true"), "existing hopper unlocked");
        check(old.getType()==Material.REDSTONE_WIRE, "existing dust identity retained");
        Bukkit.getPluginManager().registerEvents(this,this);

        String[] input = {"redstone_block", "redstone_torch[lit=true]", "redstone_wall_torch[lit=true,facing=east]",
                "redstone_wire[power=15]", "repeater[powered=true,facing=east]", "comparator[powered=true,facing=east]",
                "observer[powered=true,facing=west]", "lever[powered=true,face=floor]", "stone_button[powered=true,face=floor]",
                "oak_pressure_plate[powered=true]", "heavy_weighted_pressure_plate[power=15]", "daylight_detector[power=15]",
                "sculk_sensor[power=15,sculk_sensor_phase=active]", "calibrated_sculk_sensor[power=15,sculk_sensor_phase=active]",
                "detector_rail[powered=true]", "tripwire_hook[powered=true]"};
        for (int i=0;i<input.length;i++) {
            int x = i*5;
            for(int j=0;j<4;j++) set(x+j,80,8,"minecraft:stone",false);
            Block source = set(x,81,8,"minecraft:"+input[i],false);
            sources.add(source);
            Block lamp = set(x+1,81,8,"minecraft:redstone_lamp",true);
            targets.add(lamp);
            var pos = new BlockPos(x,81,8);
            for(Direction direction:Direction.values()) {
                check(level.getSignal(pos,direction)==0,input[i]+" weak output "+direction);
                check(level.getDirectSignal(pos,direction)==0,input[i]+" strong output "+direction);
                check(level.getBlockState(pos).getSignal(level,pos,direction)==0,input[i]+" state output "+direction);
            }
            check(!level.hasNeighborSignal(pos.relative(Direction.EAST)), input[i]+" neighboring consumer");
        }
        // Constant source attached directly to actual native actuator blocks.
        String[] output = {"piston[facing=east]", "sticky_piston[facing=east]", "iron_door", "oak_trapdoor",
                "copper_bulb", "powered_rail", "activator_rail", "tnt", "crafter", "dispenser", "dropper"};
        for(int i=0;i<output.length;i++) {
            int x=i*5;
            set(x,80,16,"minecraft:stone",false);
            Block target=set(x,81,16,"minecraft:"+output[i],false);
            targets.add(target);
            set(x+1,81,16,"minecraft:redstone_block",true);
            // Run the actual native neighbor callback, not a fabricated Bukkit event.
            var pos=new BlockPos(x,81,16);
            level.neighborChanged(pos,net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK,null);
        }
        dispenser = set(0,85,24,"minecraft:dispenser[facing=east,triggered=true]",false);
        dropper = set(5,85,24,"minecraft:dropper[facing=east,triggered=true]",false);
        for(Block block:List.of(dispenser,dropper)) {
            var inventory=((org.bukkit.block.Container)block.getState()).getInventory();
            inventory.setItem(0,new ItemStack(Material.DIAMOND,3));
            BlockPos pos=new BlockPos(block.getX(),block.getY(),block.getZ());
            // A tick already queued before the cutoff must be inert, regardless of TRIGGERED.
            level.scheduleTick(pos,level.getBlockState(pos).getBlock(),1);
        }
        crafter=set(25,85,24,"minecraft:crafter[triggered=true]",false);
        var crafterInventory=((org.bukkit.block.Container)crafter.getState()).getInventory();
        for(int i=0;i<9;i++)crafterInventory.setItem(i,new ItemStack(Material.IRON_NUGGET));
        var crafterPos=new BlockPos(25,85,24);
        level.scheduleTick(crafterPos,level.getBlockState(crafterPos).getBlock(),1);
        var queuedPiston=set(30,85,24,"minecraft:piston[facing=east]",false);
        set(31,85,24,"minecraft:stone",false);
        var pistonPos=new BlockPos(30,85,24);
        level.blockEvent(pistonPos,level.getBlockState(pistonPos).getBlock(),0,Direction.EAST.get3DDataValue());
        targets.add(queuedPiston);
        for(int i=0;i<2;i++) {
            int x=40+i*5;
            set(x,85,24,"minecraft:stone",false);
            set(x,85,26,"minecraft:command_block",false);
            var commandPos=new BlockPos(x,85,26);
            var command=(net.minecraft.world.level.block.entity.CommandBlockEntity)level.getBlockEntity(commandPos);
            command.getCommandBlock().setCommand("setblock "+x+" 85 24 minecraft:diamond_block");
            command.setAutomatic(i==1);
            command.setPowered(true);
            command.markConditionMet();
            level.scheduleTick(commandPos,level.getBlockState(commandPos).getBlock(),1);
        }
        hopper=set(10,85,24,"minecraft:hopper",false);
        set(10,84,24,"minecraft:chest",false);
        ((org.bukkit.block.Container)hopper.getState()).getInventory().setItem(0,new ItemStack(Material.DIAMOND,3));
        manualDoor=set(15,85,24,"minecraft:oak_trapdoor[open=true]",false);
        var ore=set(20,85,24,"minecraft:deepslate_redstone_ore",false);
        check(ore.getDrops(new ItemStack(Material.DIAMOND_PICKAXE)).stream()
                .anyMatch(item->item.getType()==Material.REDSTONE),"Electrum backing ore still drops redstone");
        var oreState=ore.getState();
        var drops=new ArrayList<org.bukkit.entity.Item>();
        drops.add(world.dropItem(ore.getLocation(),new ItemStack(Material.REDSTONE,5)));
        ore.setType(Material.AIR,false);
        Bukkit.getPluginManager().callEvent(new org.bukkit.event.block.BlockDropItemEvent(ore,oreState,null,drops));
        check("electrum_scrap".equals(catalog.identify(drops.getFirst().getItemStack())),"Electrum mining event retains scrap identity");
        int quantity=drops.getFirst().getItemStack().getAmount();
        check(quantity>=1 && quantity<=5,"Electrum mining retains deployed one-to-five scrap quantity");
        drops.forEach(org.bukkit.entity.Entity::remove);
        check(Bukkit.getRecipesFor(new ItemStack(Material.REDSTONE_TORCH)).size()>0,"redstone recipes retained");
        // Dense loaded wire sheet over constant sources: exercise the actual wire evaluator in a ticking chunk.
        for(int x=0;x<16;x++)for(int z=32;z<48;z++) {
            set(x,80,z,"minecraft:redstone_block",false);
            set(x,81,z,"minecraft:redstone_wire[power=15]",false);
        }
        for(int x=0;x<16;x++)for(int z=32;z<48;z++)
            level.neighborChanged(new BlockPos(x,81,z),net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK,null);
        // Exercise chunk load normalization synchronously, before native ticks are allowed.
        World remoteWorld=Bukkit.createWorld(new org.bukkit.WorldCreator("redstone-reload")
                .type(org.bukkit.WorldType.FLAT).generatorSettings("{\"layers\":[{\"block\":\"minecraft:bedrock\",\"height\":1}],\"biome\":\"minecraft:plains\"}"));
        var remote=remoteWorld.getChunkAt(20,20);
        Block remoteWire=remote.getBlock(1,81,1);
        remote.getBlock(1,80,1).setType(Material.STONE,false);
        remoteWire.setBlockData(Bukkit.createBlockData("minecraft:redstone_wire[power=15]"),false);
        check(Bukkit.unloadWorld(remoteWorld,true),"test world and chunk unloaded");
        remoteWorld=Bukkit.createWorld(new org.bukkit.WorldCreator("redstone-reload"));
        remoteWorld.getChunkAt(20,20);
        check(remoteWorld.getBlockAt(321,81,321).getBlockData().getAsString().contains("power=0"),"saved circuit inert on load");
        Bukkit.getScheduler().runTaskLater(this,()->settledEvents=events,100);
        Bukkit.getScheduler().runTaskLater(this,()->{
            try { finish(); } catch(Throwable failure) { fail(failure); }
        },200);
    }

    private void finish() {
        for(int x=0;x<16;x++)for(int z=32;z<48;z++)
            check(world.getBlockAt(x,81,z).getBlockData().getAsString().contains("power=0"),"dense wire sheet inert");
        for(Block target:targets) {
            var data=target.getBlockData();
            check(!(data instanceof Powerable power) || !power.isPowered(), "native target not powered: "+data);
            check(!(data instanceof org.bukkit.block.data.Lightable light) || !light.isLit(),"native target not lit: "+data);
            check(!(data instanceof org.bukkit.block.data.type.Piston piston) || !piston.isExtended(),"native piston inert");
            check(target.getType()!=Material.AIR,"native target retained: "+target.getLocation());
        }
        for(Block block:List.of(dispenser,dropper)) {
            check(((org.bukkit.block.Container)block.getState()).getInventory().getItem(0).getAmount()==3,
                    "queued dispense/dropper tick inert");
        }
        var crafterInventory=((org.bukkit.block.Container)crafter.getState()).getInventory();
        for(int i=0;i<9;i++)check(crafterInventory.getItem(i)!=null
                && crafterInventory.getItem(i).getType()==Material.IRON_NUGGET,"queued crafter tick inert "+i);
        check(world.getBlockAt(40,85,24).getType()==Material.STONE,"queued redstone command block inert");
        check(world.getBlockAt(45,85,24).getType()==Material.DIAMOND_BLOCK,"independent automatic command block works");
        check(((org.bukkit.block.Container)hopper.getRelative(0,-1,0).getState()).getInventory()
                .contains(Material.DIAMOND),"ordinary hopper transfer works");
        check(((org.bukkit.block.data.Openable)manualDoor.getBlockData()).isOpen(),"independent manual door state retained");
        check(events<1000,"no redstone retry storm: "+events+" events over 200 ticks");
        check(events==settledEvents,"no recurring power updates during the final 100 ticks: "+events+"/"+settledEvents+" "+eventTypes);
        getLogger().info("REDSTONE PROBE PASSED: "+checks+" assertions; "+events
                +" power events over 200 ticks; "+(events-settledEvents)+" during final 100 ticks");
        Bukkit.shutdown();
    }

    @EventHandler public void power(BlockRedstoneEvent event) {
        events++;
        eventTypes.merge(event.getBlock().getType(),1,Integer::sum);
    }
    private void fail(Throwable failure) {
        getLogger().log(java.util.logging.Level.SEVERE,"REDSTONE PROBE FAILED",failure);
        Bukkit.shutdown();
    }
}
