package net.mandomc.gameplay.boba;

import net.mandomc.commands.RootCommand;
import net.mandomc.gameplay.items.ItemCatalog;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/** Existing equipment entry now explains the crafting upgrade; never moves inventory items. */
public final class JetpackEquipmentMenu implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final org.bukkit.configuration.file.YamlConfiguration language;
    public JetpackEquipmentMenu(JavaPlugin plugin,ItemCatalog items,BobaEquipment equipment,ConfigurationSection ignored) {
        this.plugin=plugin;this.items=items;
        try(var reader=new java.io.InputStreamReader(java.util.Objects.requireNonNull(plugin.getResource("equipment/jetpack-chestplate.yml")),java.nio.charset.StandardCharsets.UTF_8)) {
            language=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(reader);
        } catch(java.io.IOException error) { throw new IllegalStateException(error); }
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    public void register(RootCommand root) {
        root.register("equipment","mandomc.equipment",(sender,args)->{if(sender instanceof Player p)open(p);},(sender,args)->java.util.List.of());
    }
    public void open(Player player) { player.sendMessage(MiniMessage.miniMessage().deserialize(language.getString("guide"))); }
    public boolean change(Player player) { open(player);return false; }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void use(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND||!event.getAction().isRightClick()
                ||!BobaEquipment.JETPACK.equals(items.identify(event.getItem())))return;
        event.setUseItemInHand(Event.Result.DENY);open(event.getPlayer());
    }
    @Deprecated public void click(org.bukkit.event.inventory.InventoryClickEvent event) { }
    @Deprecated public void drag(org.bukkit.event.inventory.InventoryDragEvent event) { }
    @Deprecated public void close(org.bukkit.event.inventory.InventoryCloseEvent event) { }
    @Override public void close() { HandlerList.unregisterAll(this); }
}
