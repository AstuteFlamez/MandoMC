package net.mandomc.verification;

import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.*;
import net.mandomc.gameplay.saber.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Real Paper checks on a disposable snapshot; never install this probe on shared development or production. */
public final class GameplayUpgradesProbe extends JavaPlugin {
    private MandoPlugin mando;private NativeProbePlayers actors;private Player player;private int checks;
    private List<String> searchBefore;private org.bukkit.block.Block forgeBlock;private List<LivingEntity> browns;
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->run(this::start),120);}
    private interface Work{void run()throws Exception;}
    private void run(Work work){try{work.run();}catch(Throwable error){finish(error);}}
    private void later(int ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->run(work),ticks);}
    private void check(boolean value,String reason){if(!value)throw new IllegalStateException(reason);checks++;}
    private Object field(Object value,String name)throws Exception{var f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private Object invoke(Object owner,String name,Class<?>[] types,Object...args)throws Exception{var m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(owner,args);}
    private String plain(Component value){return PlainTextComponentSerializer.plainText().serialize(value);}
    private void click(int slot){var event=new InventoryClickEvent(player.getOpenInventory(),org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"menu transfer cancelled "+slot);}
    private List<String> ids(){var values=new ArrayList<String>();for(int i=0;i<45;i++){var stack=player.getOpenInventory().getTopInventory().getItem(i);var id=mando.items().identify(stack);if(id!=null)values.add(id);}return values;}
    private void start()throws Exception{
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando!=null && mando.isEnabled(),"gameplay enabled");
        actors=new NativeProbePlayers("Upgrade");player=actors.create(new Location(mando.worlds().resolve("DevWorld"),-201.5,200,-199.5));player.setGameMode(GameMode.SURVIVAL);mando.ranks().apply(player);
        var guide=field(mando,"recipeGuide");
        invoke(guide,"open",new Class[]{Player.class,String.class,int.class,String.class,boolean.class},player,null,0,"beskar",false);
        searchBefore=ids();check(!searchBefore.isEmpty(),"output search has results");check(searchBefore.stream().allMatch(s->s.contains("beskar")),"output search filters names/IDs");click(0);
        later(2,()->{check(player.getOpenInventory().getTopInventory().getItem(25)!=null,"result opens from search");click(45);later(2,this::searchBack);});
    }
    private void searchBack()throws Exception{
        check(ids().equals(searchBefore),"Back retains filtered search");var guide=field(mando,"recipeGuide");
        invoke(guide,"open",new Class[]{Player.class,String.class,int.class,String.class,boolean.class},player,null,0,"wiring coil",true);
        check(!ids().isEmpty(),"ingredient-use search has outputs");check(ids().stream().anyMatch(s->!s.equals("wiring_coil")),"ingredient search finds consuming recipes");
        invoke(guide,"open",new Class[]{Player.class,String.class,int.class,String.class,boolean.class},player,null,0,"no_such_recipe_6291",false);
        check(ids().isEmpty(),"no match yields no arbitrary output");check(player.getOpenInventory().getTopInventory().getItem(22).getType()==Material.PAPER,"no-results control present");
        invoke(guide,"search",new Class[]{Player.class,String.class,String.class,boolean.class},player,null,"beskar",false);
        later(3,this::afterSearchDialog);
    }
    private void afterSearchDialog()throws Exception{
        actors.drain(player);check(actors.otherPackets(player).stream().anyMatch(s->s.contains("ShowDialog")),"native search text dialog sent");
        player.closeInventory();recipes();jawas();
    }
    private void recipes()throws Exception{
        var recipes=(CraftingRecipes)field(mando,"craftingRecipes");var dark=recipes.definitions().stream().filter(r->r.result().equals("dark_saber")).toList();
        check(dark.size()==1 && !dark.getFirst().stonecutting(),"Darksaber conversion removed");check(dark.getFirst().ingredients().containsValue("black_kyber"),"Black Kyber required");
        var grid=new ItemStack[9];grid[1]=mando.items().create("beskar");grid[3]=mando.items().create("phrik");grid[4]=mando.items().create("black_kyber");grid[5]=mando.items().create("phrik");grid[7]=mando.items().create("energy_conduits");
        var craft=Bukkit.getCraftingRecipe(grid,player.getWorld());check(craft!=null && "dark_saber".equals(mando.items().identify(craft.getResult())),"actual native Darksaber recipe works");
        grid[4]=new ItemStack(Material.AMETHYST_SHARD);check(Bukkit.getCraftingRecipe(grid,player.getWorld())==null,"ordinary shard cannot replace Black Kyber");
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"crates/crates.yml"));var rows=config.getMapList("crates.koth.rewards");int total=0;
        for(var row:rows){String id=row.get("item").toString();var item=mando.items().create(id);check(item!=null,"KOTH item generates "+id);check(!id.endsWith("_helmet") && !id.endsWith("_chestplate") && !id.endsWith("_leggings") && !id.endsWith("_boots"),"no armor reward "+id);total+=((Number)row.get("weight")).intValue();}
        check(total==100 && rows.size()==12,"exact 100-ticket equipment pool");
    }
    private void jawas()throws Exception{
        var settings=mando.jawas().settings();check(settings.categories().size()==7,"seven gray categories");
        var grayConfig=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"mobs/jawas.yml"));var categories=new HashSet<String>();
        for(String id:grayConfig.getConfigurationSection("npcs").getKeys(false)){categories.add(grayConfig.getString("npcs."+id+".category"));}
        check(categories.size()==7 && !categories.contains(null),"all saved anchors have distinct valid categories");
        var base=player.getLocation();base.clone().add(3,0,0).getChunk().load();browns=mando.jawas().spawnGroup(List.of(base.clone().add(3,0,0),base.clone().add(5,0,0)));
        for(var jawa:browns){var first=mando.jawas().stock(jawa);for(int i=0;i<20;i++)check(first.equals(mando.jawas().stock(jawa)),"brown stock fixed across repeated interactions");check(first.containsAll(settings.basicComponents()),"all basic parts stocked");check(first.size()>=7 && first.size()<=9,"one to three advanced parts");check(jawa.getPersistentDataContainer().has(new NamespacedKey("mandomc","jawa_stock_v2"),PersistentDataType.STRING),"stock saved on native entity");}
        var at=mando.worlds().spawn("Batuu");for(String id:grayConfig.getConfigurationSection("npcs").getKeys(false)){
            var home=new Location(at.getWorld(),grayConfig.getDouble("npcs."+id+".x"),grayConfig.getDouble("npcs."+id+".y"),grayConfig.getDouble("npcs."+id+".z"));home.getChunk().load();}
        later(10,this::grayMenus);
    }
    private void grayMenus()throws Exception{
        var grayConfig=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"mobs/jawas.yml"));int count=0;
        for(var entity:mando.worlds().resolve("Batuu").getLivingEntities())if(mando.jawas().gray(entity)){
            player.teleport(entity.getLocation().clone().add(1,0,0));mando.jawas().open(player,entity);var menu=player.getOpenInventory().getTopInventory();
            check(menu.getSize()==36 && menu.getItem(32).getType()==Material.EMERALD,"gray buy menu and Sell Goods control");
            check(menu.getItem(0).getType()!=Material.GRAY_STAINED_GLASS_PANE,"gray stock visible");
            String anchor=entity.getPersistentDataContainer().get(new NamespacedKey("mandomc","jawa_npc_v1"),PersistentDataType.STRING);
            String name=mando.jawas().settings().category(grayConfig.getString("npcs."+anchor+".category")).name();
            check(plain(player.getOpenInventory().title()).contains(name),"gray title matches category "+anchor);count++;
        }
        check(count==7,"seven existing gray Jawas retained");
        long labels=mando.worlds().resolve("Batuu").getEntitiesByClass(TextDisplay.class).stream().filter(t->mando.jawas().settings().categories().stream().anyMatch(c->plain(t.text()).equals(c.name()))).count();
        check(labels==7,"seven readable floating category titles");
        player.closeInventory();player.teleport(new Location(mando.worlds().resolve("DevWorld"),-201.5,200,-199.5));forge();
    }
    private void forge()throws Exception{
        forgeBlock=player.getLocation().clone().add(1,0,0).getBlock();forgeBlock.setType(Material.SMITHING_TABLE);
        forgeBlock.getChunk().getPersistentDataContainer().set(new NamespacedKey("mandomc","forge_"+forgeBlock.getX()+"_"+forgeBlock.getY()+"_"+forgeBlock.getZ()),PersistentDataType.BYTE,(byte)1);
        check(mando.forge().station(forgeBlock),"existing station opens Saber Workbench");
        mando.forge().open(player,forgeBlock);var menu=player.getOpenInventory().getTopInventory();
        check(menu.getItem(10).getType()==Material.CRAFTING_TABLE&&menu.getItem(13).getType()==Material.STONECUTTER&&menu.getItem(16).getType()==Material.ENCHANTED_BOOK,"workbench has crafting, style and enchanting");
        mando.forge().function(player,forgeBlock,16);check(player.getOpenInventory() instanceof org.bukkit.inventory.view.AnvilView,"native anvil-style upgrades replace material points");
        droid();
    }
    private void droid()throws Exception{
        player.closeInventory();var location=mando.droid().location();check(location!=null,"M4-ND spawned");
        var guides=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"decor/travel-guides.yml"));
        check(Math.abs(location.getX()-guides.getDouble("npcs.cassian_andor.x"))<1e-9 && Math.abs(location.getZ()-guides.getDouble("npcs.cassian_andor.z"))<1e-9,"M4-ND occupies Cassian's exact anchor");
        check(location.getWorld().getEntities().stream().noneMatch(e->"cassian_andor".equals(e.getPersistentDataContainer().get(new NamespacedKey("mandomc","travel_guide_id"),PersistentDataType.STRING))),"Cassian NPC removed");
        player.teleport(location.clone().add(1,0,0));actors.drain(player);mando.droid().prompt(player);later(3,this::afterDroidDialog);
    }
    private void afterDroidDialog()throws Exception{
        actors.drain(player);check(actors.otherPackets(player).stream().anyMatch(s->s.contains("ShowDialog")),"M4-ND native private question dialog");
        mando.droid().ask(player,"What is the capital of France?");later(2,()->{
            check(actors.drain(player).stream().map(this::plain).anyMatch(s->s.contains("databanks")),"off-topic question refused locally");
            mando.droid().ask(player,"Who are you and what do brown Jawas sell?");waitForAnswer(0);
        });
    }
    private void waitForAnswer(int ticks)throws Exception{
        var messages=actors.drain(player).stream().map(this::plain).toList();
        if(messages.stream().anyMatch(s->s.contains("M4-ND") && !s.contains("Consulting"))){check(messages.stream().noneMatch(s->s.contains("unavailable")),"real Groq path available");getLogger().info("Verified live Groq explanation without exposing key.");finish(null);return;}
        if(ticks>800)throw new IllegalStateException("Timed out waiting for Groq explanation");later(10,()->waitForAnswer(ticks+10));
    }
    private void finish(Throwable error){try{if(browns!=null)browns.forEach(Entity::remove);if(actors!=null)actors.close();Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(error==null?"PASSED.txt":"FAILED.txt"),error==null?checks+" native gameplay upgrade assertions passed\n":error.toString());if(error==null)getLogger().info("GAMEPLAY UPGRADES PASSED: "+checks);else getLogger().log(java.util.logging.Level.SEVERE,"GAMEPLAY UPGRADES FAILED",error);}catch(Exception failure){getLogger().log(java.util.logging.Level.SEVERE,"Probe cleanup failed",failure);}Bukkit.shutdown();}
}
