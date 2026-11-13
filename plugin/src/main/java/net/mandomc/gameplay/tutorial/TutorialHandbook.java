package net.mandomc.gameplay.tutorial;

import java.io.File;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Explicit /tutorial delivery only. First-join chat and tutorial-seen state remain arrival-owned. */
public final class TutorialHandbook {
    public enum Delivery { GIVEN, UPDATED, PREVIEW_ONLY, WAIT_FOR_ITEMS }
    public static final NamespacedKey MARKER=new NamespacedKey("mandomc","tutorial_handbook");
    private final HandbookDefinition definition;
    private final YamlConfiguration language;
    private final ItemStack template;

    public TutorialHandbook(JavaPlugin plugin) {
        this(load(plugin));
    }
    public TutorialHandbook(YamlConfiguration config) {
        language=config;definition=HandbookDefinition.read(config);
        for(String key:new String[]{"given","updated","full","busy","players-only"})
            if(!config.isString("messages."+key))throw new IllegalArgumentException("language/handbook.yml: missing messages."+key);
        template=new ItemStack(Material.WRITTEN_BOOK);
        var meta=(BookMeta)template.getItemMeta();
        meta.title(Component.text(definition.title()));meta.author(Component.text(definition.author()));
        meta.displayName(Component.text(definition.title(),NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false));
        meta.setGeneration(BookMeta.Generation.ORIGINAL);
        for(var page:definition.pages()) {
            String heading=String.join("\n",BookPageLayout.wrap(page.heading(),true));
            String body=String.join("\n",BookPageLayout.wrap(page.body(),false));
            meta.addPages(Component.text(heading,NamedTextColor.BLACK).decorate(TextDecoration.BOLD)
                    .append(Component.text("\n\n"+body,NamedTextColor.BLACK).decoration(TextDecoration.BOLD,false))
                    .decoration(TextDecoration.ITALIC,false));
        }
        meta.lore(definition.lore().stream().map(line->Component.text(line,NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC,false)).toList());
        meta.getPersistentDataContainer().set(MARKER,PersistentDataType.BYTE,(byte)1);
        template.setItemMeta(meta);
    }
    private static YamlConfiguration load(JavaPlugin plugin) {
        File file=new File(plugin.getDataFolder(),"language/handbook.yml");
        if(!file.isFile())plugin.saveResource("language/handbook.yml",false);
        try {var config=new YamlConfiguration();config.load(file);return config;}
        catch(Exception error){throw new IllegalStateException("Cannot load language/handbook.yml",error);}
    }
    public HandbookDefinition definition(){return definition;}
    public ItemStack create(){return template.clone();}
    public boolean isHandbook(ItemStack item) {
        return item!=null&&item.getType()==Material.WRITTEN_BOOK&&item.hasItemMeta()
                &&item.getItemMeta().getPersistentDataContainer().has(MARKER,PersistentDataType.BYTE);
    }
    public boolean command(CommandSender sender) {
        if(!(sender instanceof Player player)){sender.sendMessage(message("players-only"));return true;}
        Delivery result=give(player);
        player.sendMessage(message(switch(result){case GIVEN->"given";case UPDATED->"updated";case PREVIEW_ONLY->"full";case WAIT_FOR_ITEMS->"busy";}));
        if(result!=Delivery.WAIT_FOR_ITEMS)player.openBook(create());
        return true;
    }
    public Delivery give(Player player) {
        // Closing a full crafting/cursor view can cause vanilla drops. Leave those items untouched.
        var cursor=player.getItemOnCursor();
        if(cursor!=null&&!cursor.isEmpty())return Delivery.WAIT_FOR_ITEMS;
        if(player.getOpenInventory().getTopInventory() instanceof org.bukkit.inventory.CraftingInventory crafting
                &&java.util.Arrays.stream(crafting.getMatrix()).anyMatch(item->item!=null&&!item.isEmpty()))
            return Delivery.WAIT_FOR_ITEMS;
        // Settle other menu returns before checking room. Never drop or overwrite unrelated items.
        player.closeInventory();
        var inventory=player.getInventory();
        for(int slot=0;slot<36;slot++)if(isHandbook(inventory.getItem(slot))) {
            var book=create();book.setAmount(inventory.getItem(slot).getAmount());inventory.setItem(slot,book);return Delivery.UPDATED;
        }
        if(isHandbook(inventory.getItemInOffHand())) {
            var book=create();book.setAmount(inventory.getItemInOffHand().getAmount());inventory.setItemInOffHand(book);return Delivery.UPDATED;
        }
        for(int slot=0;slot<36;slot++) {
            var item=inventory.getItem(slot);
            if(item==null||item.isEmpty()){inventory.setItem(slot,create());return Delivery.GIVEN;}
        }
        return Delivery.PREVIEW_ONLY;
    }
    private Component message(String key){return MiniMessage.miniMessage().deserialize(language.getString("messages."+key));}
}
