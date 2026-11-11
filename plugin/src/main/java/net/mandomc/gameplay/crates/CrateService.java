package net.mandomc.gameplay.crates;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.gameplay.ui.MenuItems;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Container;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Crate inventory, key consumption and pending delivery share Paper's atomic player-data save. */
public final class CrateService implements Listener, CommandExecutor, AutoCloseable {
    public static final NamespacedKey PENDING = new NamespacedKey("mandomc", "crate_pending_reward");
    private static final NamespacedKey PENDING_ID = new NamespacedKey("mandomc", "crate_pending_id");
    private static final NamespacedKey MAIL = new NamespacedKey("mandomc", "crate_mail");
    private record Menu(Inventory inventory, CrateDefinition crate, boolean rolling, int frame, long due,
                        String transaction, ItemStack result) { }
    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final Predicate<Player> restricted;
    private final Predicate<Player> combat;
    private final YamlConfiguration config;
    private final ConfigurationSection language;
    private final Map<String, CrateDefinition> crates = new java.util.LinkedHashMap<>();
    private final Map<String, String> blocks = new HashMap<>();
    private final Map<UUID, Menu> menus = new HashMap<>();
    private final List<org.bukkit.entity.TextDisplay> labels=new ArrayList<>();
    private final java.util.Set<org.bukkit.Chunk> labelChunks=new java.util.HashSet<>();
    private final SecureRandom random = new SecureRandom();
    private final BukkitTask ticker;
    private long tick;
    private boolean closed;

    public CrateService(JavaPlugin plugin, ItemCatalog items, YamlConfiguration config,
                        Predicate<Player> restricted, Predicate<Player> combat) {
        this.plugin = plugin; this.items = items; this.config = config; this.restricted = restricted; this.combat = combat;
        if (config.getInt("schema-version") != 1) throw new IllegalArgumentException("crates.yml: schema-version 1 required");
        language = config.getConfigurationSection("messages");
        var definitions = config.getConfigurationSection("crates");
        if (definitions == null || language == null) throw new IllegalArgumentException("crates.yml: missing crates/messages");
        for (String id : definitions.getKeys(false)) {
            var crate = CrateDefinition.read(id, definitions.getConfigurationSection(id));
            requireItem(crate.keyItem());
            for (var entry : crate.rewards().entries()) requireItem(entry.value().item());
            crates.put(id, crate);
        }
        if (crates.isEmpty()) throw new IllegalArgumentException("No crates configured");
        registerBlocks();
        var command = java.util.Objects.requireNonNull(plugin.getCommand("crates"));
        command.setExecutor(this);
        command.setTabCompleter((sender, cmd, label, args) -> args.length == 1
                ? List.of("preview", "open", "claim", "keys", "list", "key", "setblock").stream()
                    .filter(s -> s.startsWith(args[0])).toList()
                : args.length == 2 ? crates.keySet().stream().filter(s -> s.startsWith(args[1])).toList() : List.of());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private void requireItem(String id) {
        if (items.create(id) == null) throw new IllegalArgumentException("Crate references missing catalog item " + id);
    }
    public Map<String, CrateDefinition> definitions() { return Map.copyOf(crates); }
    private static NamespacedKey virtualKey(String id) { return new NamespacedKey("mandomc", "crate_keys_" + id); }
    private static String point(Location at) { return at.getWorld().getKey()+","+at.getBlockX()+","+at.getBlockY()+","+at.getBlockZ(); }
    private void registerBlocks() {
        labels.forEach(Entity->Entity.remove());labels.clear();
        for(var chunk:labelChunks)chunk.removePluginChunkTicket(plugin);labelChunks.clear();
        blocks.clear();
        for (String id : crates.keySet()) for (var entry : config.getMapList("crates."+id+".blocks")) {
            var key = NamespacedKey.fromString(String.valueOf(entry.get("world")));
            var world = key == null ? null : Bukkit.getWorld(key);
            if (world == null) throw new IllegalArgumentException("Crate block world unavailable: " + entry);
            var block = world.getBlockAt(((Number)entry.get("x")).intValue(), ((Number)entry.get("y")).intValue(),
                    ((Number)entry.get("z")).intValue());
            if (Boolean.TRUE.equals(entry.get("place-if-air")) && block.isEmpty()) {
                if (!block.getRelative(0,-1,0).getType().isSolid() || !block.getRelative(0,1,0).isPassable()) {
                    throw new IllegalArgumentException("Crate block lacks support/clearance: " + entry);
                }
                block.setType(Material.ENDER_CHEST, false);
            }
            if (!(block.getState() instanceof Container) && block.getType() != Material.ENDER_CHEST) {
                throw new IllegalArgumentException("Crate block must be an existing container: " + entry);
            }
            if (blocks.put(point(block.getLocation()), id) != null) throw new IllegalArgumentException("Two crates share a block");
            var at=block.getLocation().add(.5,1.8,.5);var chunk=at.getChunk();
            if(chunk.addPluginChunkTicket(plugin))labelChunks.add(chunk);
            labels.add(world.spawn(at,org.bukkit.entity.TextDisplay.class,entity->{
                entity.text(text(crates.get(id).name()).append(Component.newline()).append(message("block-hint")));
                entity.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);entity.setShadowed(true);
                entity.setPersistent(false);entity.setViewRange(.5f);
            }));
        }
    }

    private Component text(String value) { return MiniMessage.miniMessage().deserialize(value).decoration(TextDecoration.ITALIC, false); }
    private Component message(String key, String... args) {
        String line = java.util.Objects.requireNonNull(language.getString(key), "Missing crate message " + key);
        for (int i=0;i<args.length;i+=2) line = line.replace("{"+args[i]+"}", args[i+1]);
        return text(line);
    }
    private void send(CommandSender sender, String key, String... args) { sender.sendMessage(message(key,args)); }
    private CrateDefinition crate(String id) {
        var crate = crates.get(id.toLowerCase(java.util.Locale.ROOT));
        if (crate == null) throw new IllegalArgumentException("Unknown crate: " + id);
        return crate;
    }
    private boolean allowed(Player player) {
        if (restricted.test(player)) { send(player,"parkour"); return false; }
        if (combat.test(player) || player.isInsideVehicle()) { send(player,"unsafe"); return false; }
        return true;
    }

    public int keys(Player player, CrateDefinition crate) {
        if (crate.virtual()) return player.getPersistentDataContainer().getOrDefault(virtualKey(crate.id()), PersistentDataType.INTEGER,0);
        int count=0;
        for (var item : player.getInventory().getStorageContents()) if (crate.keyItem().equals(items.identify(item))) count += item.getAmount();
        return count;
    }
    private void consume(Player player, CrateDefinition crate) {
        if (crate.virtual()) {
            int count=keys(player,crate);
            if (count<1) throw new IllegalStateException("Key disappeared");
            player.getPersistentDataContainer().set(virtualKey(crate.id()),PersistentDataType.INTEGER,count-1);
        } else {
            for (int slot=0;slot<36;slot++) {
                var stack=player.getInventory().getItem(slot);
                if (!crate.keyItem().equals(items.identify(stack))) continue;
                if (stack.getAmount()==1) player.getInventory().setItem(slot,null); else stack.setAmount(stack.getAmount()-1);
                return;
            }
            throw new IllegalStateException("Key disappeared");
        }
    }
    private static boolean fits(Player player, ItemStack reward) {
        int remaining=reward.getAmount();
        for (var stack : player.getInventory().getStorageContents()) {
            if (stack==null || stack.isEmpty()) remaining-=reward.getMaxStackSize();
            else if (stack.isSimilar(reward)) remaining-=Math.max(0,stack.getMaxStackSize()-stack.getAmount());
            if (remaining<=0) return true;
        }
        return false;
    }

    /** Enqueue only; the caller must save the player's whole inventory/PDC transaction afterward. */
    public void enqueueKey(Player player, String id, int amount) {
        if (amount<1 || amount>64) throw new IllegalArgumentException("Key amount must be 1–64");
        var crate=crate(id);
        if (crate.virtual()) {
            var data=player.getPersistentDataContainer();
            data.set(virtualKey(id),PersistentDataType.INTEGER,Math.addExact(keys(player,crate),amount));
        } else {
            var key=items.create(crate.keyItem());key.setAmount(amount);
            var mail=new ArrayList<>(List.of(mail(player)));
            if (mail.size()>=256) throw new IllegalArgumentException("Claim pending crate deliveries first");
            mail.add(key);
            player.getPersistentDataContainer().set(MAIL,PersistentDataType.BYTE_ARRAY,ItemStack.serializeItemsAsBytes(mail.toArray(ItemStack[]::new)));
        }
    }
    private static ItemStack[] mail(Player player) {
        var bytes=player.getPersistentDataContainer().get(MAIL,PersistentDataType.BYTE_ARRAY);
        return bytes==null ? new ItemStack[0] : ItemStack.deserializeItemsFromBytes(bytes);
    }
    public void claim(Player player) {
        if (restricted.test(player)) return;
        var viewing=menus.get(player.getUniqueId());
        if(viewing!=null&&viewing.rolling())return;
        finishPending(player);
        var leftovers=new ArrayList<ItemStack>();
        int delivered=0;
        for (var item : mail(player)) {
            if (!fits(player,item)) { leftovers.add(item); continue; }
            var remainder=player.getInventory().addItem(item.clone());
            if (!remainder.isEmpty()) throw new IllegalStateException("Crate delivery capacity changed on server thread");
            delivered+=item.getAmount();
        }
        var data=player.getPersistentDataContainer();
        if (leftovers.isEmpty()) data.remove(MAIL);
        else data.set(MAIL,PersistentDataType.BYTE_ARRAY,ItemStack.serializeItemsAsBytes(leftovers.toArray(ItemStack[]::new)));
        player.saveData();
        if (delivered>0) send(player,"claimed","amount",Integer.toString(delivered));
        if (!leftovers.isEmpty()) send(player,"mail-full");
    }

    private ItemStack reward(CrateDefinition.Reward reward) {
        var item=items.create(reward.item());item.setAmount(reward.amount());return item;
    }
    public void open(Player player, CrateDefinition crate) {
        if (!allowed(player)) return;
        var current=menus.get(player.getUniqueId());
        if (current!=null && current.rolling()) return;
        var data=player.getPersistentDataContainer();
        if (data.has(PENDING)) { finishPending(player); if (data.has(PENDING)) send(player,"mail-full"); return; }
        if (keys(player,crate)<1) { send(player,"no-key"); return; }
        var selected=crate.rewards().choose(random.nextInt(crate.rewards().total()));
        var reward=reward(selected);
        if (!fits(player,reward)) { send(player,"full"); return; }
        // Inventory and the fixed reward decision are persisted together before any animation is shown.
        consume(player,crate);
        data.set(PENDING,PersistentDataType.BYTE_ARRAY,reward.serializeAsBytes());
        String transaction=UUID.randomUUID().toString();
        data.set(PENDING_ID,PersistentDataType.STRING,transaction);
        player.saveData();
        plugin.getLogger().info("Crate opened: "+player.getUniqueId()+" crate="+crate.id()+" reward="+selected.item()+" amount="+selected.amount());
        var inventory=Bukkit.createInventory(null,27,message("opening-title","crate",crate.name()));
        MenuItems.fill(inventory);
        inventory.setItem(4,button(Material.HOPPER,"winner"));
        inventory.setItem(22,button(Material.ARROW,"skip"));
        // Remove the preview before close events fire; they must not claim this new transaction prematurely.
        menus.remove(player.getUniqueId());
        player.openInventory(inventory);
        menus.put(player.getUniqueId(),new Menu(inventory,crate,true,0,tick+1,transaction,reward.clone()));
    }
    private ItemStack button(Material material,String message) {
        var item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(message(message));item.setItemMeta(meta);return item;
    }
    public void preview(Player player, CrateDefinition crate) {
        if (!allowed(player)) return;
        var current=menus.get(player.getUniqueId());
        if (current!=null && current.rolling()) return;
        if (player.getPersistentDataContainer().has(PENDING)) { finishPending(player); return; }
        int rows=crate.rewards().entries().size()>7 ? 6 : 3;
        var inventory=Bukkit.createInventory(null,rows*9,message("preview-title","crate",crate.name()));
        int slot=10;
        for (var entry : crate.rewards().entries()) {
            var item=reward(entry.value());var meta=item.getItemMeta();
            var lore=new ArrayList<>(meta.hasLore()?meta.lore():List.<Component>of());
            lore.add(Component.empty());
            lore.add(message("chance","chance",String.format(java.util.Locale.ROOT,"%.2f",100.0*entry.weight()/crate.rewards().total())));
            meta.lore(lore);item.setItemMeta(meta);inventory.setItem(slot++,item);
            if (slot%9==8) slot+=2;
        }
        int control=inventory.getSize()-5;
        var key=items.create(crate.keyItem());var meta=key.getItemMeta();
        meta.lore(List.of(message("key-count","amount",Integer.toString(keys(player,crate))),message("open-hint")));
        key.setItemMeta(meta);inventory.setItem(control,key);
        MenuItems.fill(inventory);
        menus.remove(player.getUniqueId());
        player.openInventory(inventory);
        menus.put(player.getUniqueId(),new Menu(inventory,crate,false,0,0,null,null));
    }
    private void tick() {
        tick++;
        if(tick%40==0)for(var label:labels)if(label.isValid())label.getWorld().spawnParticle(org.bukkit.Particle.DUST,
                label.getLocation().clone().subtract(0,.6,0),3,.35,.15,.35,0,
                new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(95,205,255),.8f));
        for (var entry : List.copyOf(menus.entrySet())) {
            var menu=entry.getValue();
            if (!menu.rolling() || tick<menu.due()) continue;
            var player=Bukkit.getPlayer(entry.getKey());
            if (player==null) { menus.remove(entry.getKey()); continue; }
            if (menu.frame()>=37) {
                menus.remove(entry.getKey());player.closeInventory();claim(player);continue;
            }
            if (menu.frame()==36) {
                menu.inventory().setItem(13,menu.result().clone());
                finishTransaction(player,menu.transaction());
                menus.put(entry.getKey(),new Menu(menu.inventory(),menu.crate(),true,37,tick+25,menu.transaction(),menu.result()));
                continue;
            }
            for (int slot=9;slot<=17;slot++) menu.inventory().setItem(slot,reward(menu.crate().rewards().choose(random.nextInt(menu.crate().rewards().total()))));
            player.playSound(player.getLocation(),Sound.BLOCK_NOTE_BLOCK_XYLOPHONE,.45f,1.2f);
            menus.put(entry.getKey(),new Menu(menu.inventory(),menu.crate(),true,menu.frame()+1,tick+1+menu.frame()/7,menu.transaction(),menu.result()));
        }
    }
    public void finishPending(Player player) {
        if (restricted.test(player)) return;
        var data=player.getPersistentDataContainer();
        var bytes=data.get(PENDING,PersistentDataType.BYTE_ARRAY);
        if (bytes==null) return;
        var reward=ItemStack.deserializeBytes(bytes);
        if (!fits(player,reward)) { send(player,"mail-full"); return; }
        var remainder=player.getInventory().addItem(reward.clone());
        if (!remainder.isEmpty()) throw new IllegalStateException("Pending reward capacity changed");
        data.remove(PENDING);data.remove(PENDING_ID);
        player.saveData();
        player.sendMessage(message("won").append(reward.effectiveName()));
        player.playSound(player.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.8f,1.2f);
    }
    private void finishTransaction(Player player,String transaction) {
        if(transaction!=null&&transaction.equals(player.getPersistentDataContainer().get(PENDING_ID,PersistentDataType.STRING)))
            finishPending(player);
    }

    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        try {
            String action=args.length==0 ? "list" : args[0].toLowerCase(java.util.Locale.ROOT);
            if (action.equals("key")) {
                if (!sender.hasPermission("mandomc.crates.admin")) { send(sender,"denied"); return true; }
                if (args.length<4 || args.length>5 || !args[1].equals("give")) { send(sender,"admin-usage"); return true; }
                var target=Bukkit.getPlayerExact(args[3]);
                if (target==null) { send(sender,"offline"); return true; }
                int amount=args.length==5 ? Integer.parseInt(args[4]) : 1;
                enqueueKey(target,args[2],amount);target.saveData();claim(target);
                send(sender,"key-given","player",target.getName(),"amount",Integer.toString(amount));return true;
            }
            if (!(sender instanceof Player player)) { send(sender,"players-only"); return true; }
            if (action.equals("list")) { send(player,"list","crates",String.join(", ",crates.keySet()));return true; }
            if (action.equals("claim")) { if (allowed(player)) claim(player);return true; }
            if (action.equals("keys")) {
                for (var crate:crates.values()) send(player,"keys","crate",crate.id(),"amount",Integer.toString(keys(player,crate)));return true;
            }
            if (action.equals("setblock")) {
                if (!player.hasPermission("mandomc.crates.admin") || args.length!=2) { send(player,"admin-usage");return true; }
                var crate=crate(args[1]);var block=player.getTargetBlockExact(5);
                if (block==null || (!(block.getState() instanceof Container) && block.getType()!=Material.ENDER_CHEST)) { send(player,"container");return true; }
                var positions=new ArrayList<>(config.getMapList("crates."+crate.id()+".blocks"));
                positions.add(Map.of("world",block.getWorld().getKey().toString(),"x",block.getX(),"y",block.getY(),"z",block.getZ()));
                config.set("crates."+crate.id()+".blocks",positions);
                config.save(new File(plugin.getDataFolder(),"crates/crates.yml"));registerBlocks();send(player,"bound");return true;
            }
            if (args.length==2 && (action.equals("preview") || action.equals("open"))) {
                if (action.equals("preview")) preview(player,crate(args[1]));else open(player,crate(args[1]));return true;
            }
            send(sender,"usage");
        } catch (IllegalArgumentException | java.io.IOException error) { sender.sendMessage(Component.text(error.getMessage())); }
        return true;
    }

    @EventHandler(priority=EventPriority.LOWEST) public void interact(PlayerInteractEvent event) {
        if (event.getClickedBlock()==null || event.getHand()!=EquipmentSlot.HAND) return;
        String id=blocks.get(point(event.getClickedBlock().getLocation()));
        if (id==null) return;
        event.setCancelled(true);
        if (event.getAction()!=Action.RIGHT_CLICK_BLOCK && event.getAction()!=Action.LEFT_CLICK_BLOCK) return;
        var player=event.getPlayer();boolean preview=event.getAction()==Action.LEFT_CLICK_BLOCK || player.isSneaking();
        Bukkit.getScheduler().runTask(plugin,()->{ if (player.isOnline()) { if (preview) preview(player,crate(id));else open(player,crate(id)); } });
    }
    @EventHandler public void click(InventoryClickEvent event) {
        var menu=menus.get(event.getWhoClicked().getUniqueId());
        if (menu==null || !event.getView().getTopInventory().equals(menu.inventory())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.isShiftClick() || !event.isLeftClick()) return;
        if (event.getRawSlot()!=menu.inventory().getSize()-5) return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if (menus.get(player.getUniqueId())!=menu) return;
            if (menu.rolling()) { menus.remove(player.getUniqueId()); player.closeInventory();finishTransaction(player,menu.transaction()); }
            else open(player,menu.crate());
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        var menu=menus.get(event.getWhoClicked().getUniqueId());
        if (menu!=null && event.getView().getTopInventory().equals(menu.inventory())) event.setCancelled(true);
    }
    @EventHandler public void inventoryClosed(InventoryCloseEvent event) {
        var menu=menus.get(event.getPlayer().getUniqueId());
        if(menu==null||!event.getInventory().equals(menu.inventory()))return;
        menus.remove(event.getPlayer().getUniqueId(),menu);
        if (menu!=null && menu.rolling() && event.getPlayer() instanceof Player player && !closed) {
            Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline())finishTransaction(player,menu.transaction());});
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event) { menus.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(priority=EventPriority.HIGHEST) public void protectCrate(org.bukkit.event.block.BlockBreakEvent event) {
        if(blocks.containsKey(point(event.getBlock().getLocation())))event.setCancelled(true);
    }
    @EventHandler public void joined(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(event.getPlayer().isOnline())claim(event.getPlayer());},40);
    }
    @Override public void close() {
        closed=true;ticker.cancel();
        for (var entry:List.copyOf(menus.entrySet())) {
            var player=Bukkit.getPlayer(entry.getKey());
            if (player!=null) { player.closeInventory();finishPending(player); }
        }
        menus.clear();HandlerList.unregisterAll(this);
        labels.forEach(Entity->Entity.remove());labels.clear();
        for(var chunk:labelChunks)chunk.removePluginChunkTicket(plugin);labelChunks.clear();
    }
}
