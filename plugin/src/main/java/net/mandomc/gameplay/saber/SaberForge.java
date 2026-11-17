package net.mandomc.gameplay.saber;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.ui.MenuItems;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** Read-only upgrades: the actual saber never leaves its inventory slot; main-thread exchange consumes exact materials. */
public final class SaberForge implements Listener,AutoCloseable {
    private final MandoPlugin plugin;
    private final YamlConfiguration config;
    private final int budget,maximum;
    private static final class Menu implements InventoryHolder {
        final UUID owner;final Location station;final int held;final ItemStack expected;final Inventory inventory;
        Menu(Player player,Block block,Component title){owner=player.getUniqueId();station=block.getLocation();held=player.getInventory().getHeldItemSlot();expected=player.getInventory().getItem(held).clone();inventory=Bukkit.createInventory(this,27,title);MenuItems.fill(inventory);}
        @Override public Inventory getInventory(){return inventory;}
    }
    public SaberForge(MandoPlugin plugin,YamlConfiguration config){
        this.plugin=plugin;this.config=config;budget=config.getInt("point-budget");maximum=config.getInt("maximum-level");
        if(config.getInt("schema-version")!=1 || budget<1 || budget>10 || maximum<1 || maximum>budget)throw new IllegalArgumentException("forge: invalid point bounds");
        for(String track:SaberUpgrades.TRACKS){
            if(plugin.items().create(config.getString("upgrades."+track+".material",""))==null || config.getInt("upgrades."+track+".amount")<1 || config.getInt("upgrades."+track+".amount")>64
                    || !Double.isFinite(config.getDouble("upgrades."+track+".per-level")) || config.getDouble("upgrades."+track+".per-level")<=0)
                throw new IllegalArgumentException("forge: invalid upgrade "+track);
        }
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    public double bonus(ItemStack item,String track){return SaberUpgrades.level(item,track)*config.getDouble("upgrades."+track+".per-level");}
    private Component text(String key,String...values){var tags=new ArrayList<net.kyori.adventure.text.minimessage.tag.resolver.TagResolver>();for(int i=0;i<values.length;i+=2)tags.add(Placeholder.unparsed(values[i],values[i+1]));return MiniMessage.miniMessage().deserialize(config.getString("text."+key,key),tags.toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new));}
    private NamespacedKey marker(Block block){return new NamespacedKey("mandomc","forge_"+block.getX()+"_"+block.getY()+"_"+block.getZ());}
    public boolean station(Block block){return block.getType()==Material.SMITHING_TABLE && block.getChunk().getPersistentDataContainer().has(marker(block),PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void place(BlockPlaceEvent event){event.getBlockPlaced().getChunk().getPersistentDataContainer().remove(marker(event.getBlockPlaced()));if("saber_forge".equals(plugin.items().identify(event.getItemInHand())))event.getBlockPlaced().getChunk().getPersistentDataContainer().set(marker(event.getBlockPlaced()),PersistentDataType.BYTE,(byte)1);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void drops(BlockDropItemEvent event){
        var block=event.getBlock();if(!block.getChunk().getPersistentDataContainer().has(marker(block),PersistentDataType.BYTE))return;
        for(var drop:event.getItems())if(drop.getItemStack().getType()==Material.SMITHING_TABLE)drop.setItemStack(plugin.items().create("saber_forge"));
        block.getChunk().getPersistentDataContainer().remove(marker(block));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void broken(BlockBreakEvent event){var block=event.getBlock();if(station(block))Bukkit.getScheduler().runTask(plugin,()->{if(block.getType()!=Material.SMITHING_TABLE)block.getChunk().getPersistentDataContainer().remove(marker(block));});}
    @EventHandler(ignoreCancelled=true) public void push(BlockPistonExtendEvent event){if(event.getBlocks().stream().anyMatch(this::station))event.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void pull(BlockPistonRetractEvent event){if(event.getBlocks().stream().anyMatch(this::station))event.setCancelled(true);}
    private boolean allowed(Player player,Block block){return station(block) && player.getWorld()==block.getWorld() && player.getLocation().distanceSquared(block.getLocation().add(.5,.5,.5))<=36
            && !player.isDead() && !player.isInsideVehicle() && !plugin.combat().tagged(player) && !plugin.sabers().busy(player)
            && !net.mandomc.gameplay.admin.StaffMode.active(player) && !net.mandomc.gameplay.tutorial.TutorialInventory.active(player)
            && (player.getGameMode()==GameMode.SURVIVAL || player.getGameMode()==GameMode.ADVENTURE);}
    @EventHandler(priority=EventPriority.HIGHEST) public void use(PlayerInteractEvent event){
        if(event.getAction()!=Action.RIGHT_CLICK_BLOCK || event.getHand()!=EquipmentSlot.HAND || event.getClickedBlock()==null || !station(event.getClickedBlock()))return;
        if(event.useInteractedBlock()==Event.Result.DENY)return;
        event.setCancelled(true);var player=event.getPlayer();
        if(!allowed(player,event.getClickedBlock())){player.sendMessage(text("unsafe"));return;}
        if(!plugin.sabers().isSaber(player.getInventory().getItemInMainHand())){player.sendMessage(text("hold"));return;}
        open(player,event.getClickedBlock());
    }
    public void open(Player player,Block block){
        var menu=new Menu(player,block,text("title"));menu.inventory.setItem(4,menu.expected.clone());
        for(int i=0;i<SaberUpgrades.TRACKS.size();i++){
            String track=SaberUpgrades.TRACKS.get(i);int level=SaberUpgrades.level(menu.expected,track);String path="upgrades."+track;
            var icon=plugin.items().create(config.getString(path+".material"));int cost=config.getInt(path+".amount")*(level+1);
            var materialName=icon.getItemMeta().displayName();var meta=icon.getItemMeta();meta.displayName(text(track,"level",""+level,"maximum",""+maximum));
            meta.lore(List.of(text("cost","amount",""+cost,"material",net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(materialName)),text("points","spent",""+SaberUpgrades.spent(menu.expected),"budget",""+budget),text("effect","value",config.getString(path+".description","")),text("confirm")));icon.setItemMeta(meta);menu.inventory.setItem(10+i*2,icon);
        }
        var close=new ItemStack(Material.BARRIER);close.editMeta(m->m.displayName(text("close")));menu.inventory.setItem(26,close);player.openInventory(menu.inventory);
    }
    @EventHandler public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu menu))return;event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player) || !menu.owner.equals(player.getUniqueId()) || !event.isLeftClick())return;
        int raw=event.getRawSlot();if(raw==26){Bukkit.getScheduler().runTask(plugin,()->player.closeInventory());return;}
        int index=(raw-10)/2;if(raw<10 || raw>16 || raw%2!=0)return;
        Bukkit.getScheduler().runTask(plugin,()->upgrade(player,menu,SaberUpgrades.TRACKS.get(index)));
    }
    private void upgrade(Player player,Menu menu,String track){
        if(!player.isOnline() || player.getOpenInventory().getTopInventory()!=menu.inventory || !allowed(player,menu.station.getBlock())
                || player.getInventory().getHeldItemSlot()!=menu.held || !menu.expected.equals(player.getInventory().getItem(menu.held))){player.closeInventory();return;}
        int level=SaberUpgrades.level(menu.expected,track);
        if(!SaberUpgrades.eligible(SaberUpgrades.spent(menu.expected),level,budget,maximum)){player.sendMessage(text("limit"));return;}
        String path="upgrades."+track;var required=plugin.items().create(config.getString(path+".material"));int remaining=config.getInt(path+".amount")*(level+1);
        var contents=player.getInventory().getStorageContents();for(int i=0;i<contents.length;i++)if(contents[i]!=null)contents[i]=contents[i].clone();
        for(int i=0;i<contents.length && remaining>0;i++)if(i!=menu.held && contents[i]!=null && contents[i].isSimilar(required)){
            int amount=Math.min(remaining,contents[i].getAmount());remaining-=amount;contents[i].setAmount(contents[i].getAmount()-amount);
        }
        if(remaining>0){player.sendMessage(text("missing"));return;}
        contents[menu.held]=SaberUpgrades.upgraded(menu.expected,track,config.getDouble(path+".per-level"),config.getString("text.stats-label","Forge:"));
        player.getInventory().setStorageContents(contents);player.saveData();player.playSound(player.getLocation(),Sound.BLOCK_ANVIL_USE,.5f,1.2f);open(player,menu.station.getBlock());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void style(InventoryClickEvent event){
        if(event.getView().getTopInventory().getType()==InventoryType.STONECUTTER && event.getRawSlot()==1
                && SaberUpgrades.spent(event.getView().getTopInventory().getItem(0))>0){event.setCancelled(true);event.getWhoClicked().sendMessage(text("style-first"));}
    }
    @EventHandler public void drag(InventoryDragEvent event){if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);}
    @Override public void close(){HandlerList.unregisterAll(this);for(var player:Bukkit.getOnlinePlayers())if(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu)player.closeInventory();}
}
