package net.mandomc.gameplay.jawa;

import java.util.*;
import java.math.BigInteger;
import java.util.function.BiPredicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.credits.CreditLedger;
import net.mandomc.gameplay.ui.MenuItems;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Player-owned sell trays. Each controlled edit saves inventory, cursor custody and tray journal together. */
public final class JawaSellInventory implements Listener, AutoCloseable {
    private static final int INPUTS=27, SIZE=36;
    private final MandoPlugin plugin;
    private final ConfigurationSection text;
    private final NamespacedKey custody;
    private final BiPredicate<Player,LivingEntity> canTrade;
    private final List<Rate> rates=new ArrayList<>();
    private final Map<UUID,Tray> sessions=new HashMap<>();
    private final BukkitTask task;
    private record Rate(ItemStack template,JawaSettings.Offer offer) { }
    private record Quote(List<ItemStack> accepted,List<ItemStack> rejected,long cents,int count) { }
    public static final class Tray implements InventoryHolder {
        final UUID owner, jawa; final Inventory inventory;
        boolean locked, finished;
        Tray(Player player,LivingEntity jawa,Component title){owner=player.getUniqueId();this.jawa=jawa.getUniqueId();inventory=Bukkit.createInventory(this,SIZE,title);MenuItems.fill(inventory);for(int i=0;i<INPUTS;i++)inventory.setItem(i,null);}
        @Override public Inventory getInventory(){return inventory;}
    }
    public JawaSellInventory(MandoPlugin plugin,ConfigurationSection config,JawaSettings settings,BiPredicate<Player,LivingEntity> canTrade) {
        this.plugin=plugin;this.canTrade=canTrade;text=Objects.requireNonNull(config.getConfigurationSection("text"));
        custody=new NamespacedKey(plugin,"jawa_sell_custody_v1");
        for(var offer:settings.purchases()) {
            var stack=offer.item().startsWith("minecraft:")?new ItemStack(Objects.requireNonNull(Material.matchMaterial(offer.item()))):plugin.items().create(offer.item());
            if(stack==null)throw new IllegalArgumentException("Unknown Jawa buyback "+offer.item());
            stack.setAmount(1);
            if(rates.stream().anyMatch(r->r.template().isSimilar(stack)))throw new IllegalArgumentException("Duplicate Jawa buyback "+offer.item());
            rates.add(new Rate(stack,offer));
        }
        for(String key:List.of("buyer-info-title","buyer-sell","buyer-close","buyer-empty","buyer-mode","buyer-pending","buyer-returned","buyer-invalid","buyer-limit"))
            if(!text.isString(key))throw new IllegalArgumentException("mobs/jawas.yml: missing text."+key);
        if(text.getStringList("buyer-info-lines").isEmpty())throw new IllegalArgumentException("mobs/jawas.yml: buyer-info-lines required");
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,5,5);
    }
    private Component label(String key,String... values) {
        var tags=new ArrayList<net.kyori.adventure.text.minimessage.tag.resolver.TagResolver>();
        for(int i=0;i<values.length;i+=2)tags.add(Placeholder.unparsed(values[i],values[i+1]));
        return MiniMessage.miniMessage().deserialize(Objects.requireNonNull(text.getString(key),key),tags.toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new)).decoration(TextDecoration.ITALIC,false);
    }
    private void send(Player player,String key){player.sendMessage(label(key));}
    private static ItemStack copy(ItemStack item){return item==null || item.isEmpty()?null:item.clone();}
    private static boolean empty(ItemStack item){return item==null || item.isEmpty();}
    private boolean mode(Player player){return player.getGameMode()==GameMode.SURVIVAL || player.getGameMode()==GameMode.ADVENTURE;}
    public void open(Player player,LivingEntity jawa) {
        if(!canTrade.test(player,jawa))return;
        player.closeInventory();recover(player);
        // Unreturned items remain saved until there is room; never overwrite that custody with another tray.
        if(!saved(player).isEmpty())return;
        var tray=new Tray(player,jawa,label("gray-title"));
        var info=new ItemStack(Material.BOOK);var meta=info.getItemMeta();meta.displayName(label("buyer-info-title"));
        meta.lore(text.getStringList("buyer-info-lines").stream().map(line->MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC,false)).toList());info.setItemMeta(meta);tray.inventory.setItem(27,info);
        var close=new ItemStack(Material.BARRIER);meta=close.getItemMeta();meta.displayName(label("buyer-close"));close.setItemMeta(meta);tray.inventory.setItem(35,close);
        refresh(tray);player.openInventory(tray.inventory);
        if(player.getOpenInventory().getTopInventory().getHolder()==tray){sessions.put(player.getUniqueId(),tray);persist(player,tray);}
    }
    private List<ItemStack> saved(Player player) {
        String raw=player.getPersistentDataContainer().get(custody,PersistentDataType.STRING);
        if(raw==null || raw.isBlank())return List.of();
        return Arrays.stream(raw.split("\n")).map(s->ItemStack.deserializeBytes(Base64.getDecoder().decode(s))).toList();
    }
    private void saved(Player player,List<ItemStack> stacks) {
        if(stacks.isEmpty())player.getPersistentDataContainer().remove(custody);
        else player.getPersistentDataContainer().set(custody,PersistentDataType.STRING,stacks.stream().filter(i->!empty(i)).map(i->Base64.getEncoder().encodeToString(i.serializeAsBytes())).collect(java.util.stream.Collectors.joining("\n")));
    }
    private List<ItemStack> contents(Tray tray) {
        var items=new ArrayList<ItemStack>();for(int i=0;i<INPUTS;i++)if(!empty(tray.inventory.getItem(i)))items.add(tray.inventory.getItem(i).clone());return items;
    }
    private void journal(Player player,Tray tray) {
        var items=contents(tray);if(!empty(player.getItemOnCursor()))items.add(player.getItemOnCursor().clone());saved(player,items);
    }
    private void persist(Player player,Tray tray){journal(player,tray);player.saveData();}
    /** Remaining overflow stays in player data; no ground drops and no metadata changes. */
    public void recover(Player player) {
        if(sessions.containsKey(player.getUniqueId()))return;
        var pending=saved(player);if(pending.isEmpty())return;
        var remaining=new ArrayList<ItemStack>();
        for(var item:pending)remaining.addAll(player.getInventory().addItem(item.clone()).values());
        saved(player,remaining);player.saveData();if(!remaining.isEmpty())send(player,"buyer-pending");
    }
    private Quote quote(Tray tray) {
        var counts=new LinkedHashMap<Rate,Integer>();
        for(var item:contents(tray))for(var rate:rates)if(rate.template().isSimilar(item)){counts.merge(rate,item.getAmount(),Integer::sum);break;}
        var paying=new HashSet<Rate>();BigInteger total=BigInteger.ZERO;
        for(var entry:counts.entrySet()) {
            var offer=entry.getKey().offer();var value=BigInteger.valueOf(offer.price()).multiply(BigInteger.valueOf(entry.getValue())).divide(BigInteger.valueOf(offer.amount()));
            if(value.signum()>0){paying.add(entry.getKey());total=total.add(value);}
        }
        var accepted=new ArrayList<ItemStack>();var rejected=new ArrayList<ItemStack>();int count=0;
        for(var item:contents(tray)) {
            if(paying.stream().anyMatch(rate->rate.template().isSimilar(item))){accepted.add(item);count+=item.getAmount();}else rejected.add(item);
        }
        return new Quote(List.copyOf(accepted),List.copyOf(rejected),total.min(BigInteger.valueOf(CreditLedger.MAX+1)).longValueExact(),count);
    }
    private void refresh(Tray tray) {
        var quote=quote(tray);var button=new ItemStack(Material.LIME_DYE);var meta=button.getItemMeta();
        meta.displayName(label("buyer-sell","price",CreditLedger.format(quote.cents())));
        meta.lore(List.of(label("buyer-quote","count",String.valueOf(quote.count()),"returned",String.valueOf(quote.rejected().stream().mapToInt(ItemStack::getAmount).sum()))));
        button.setItemMeta(meta);tray.inventory.setItem(31,button);
    }
    private boolean active(Player player,Tray tray){return !tray.finished && sessions.get(player.getUniqueId())==tray && tray.owner.equals(player.getUniqueId()) && player.getOpenInventory().getTopInventory().getHolder()==tray;}
    private boolean mutable(int raw){return raw>=0 && raw<INPUTS || raw>=SIZE && raw<SIZE+36;}
    private ItemStack get(Player player,Tray tray,int raw){return copy(raw<SIZE?tray.inventory.getItem(raw):player.getInventory().getItem(player.getOpenInventory().convertSlot(raw)));}
    private void set(Player player,Tray tray,int raw,ItemStack item){if(raw<SIZE)tray.inventory.setItem(raw,item);else player.getInventory().setItem(player.getOpenInventory().convertSlot(raw),item);}
    private void sell(Player player,Tray tray) {
        if(!active(player,tray)){return;}tray.locked=false;
        var entity=Bukkit.getEntity(tray.jawa);
        if(!(entity instanceof LivingEntity jawa) || !canTrade.test(player,jawa)){player.closeInventory();return;}
        if(!mode(player)){send(player,"buyer-mode");return;}
        var quote=quote(tray);
        if(quote.cents()>CreditLedger.MAX){send(player,"buyer-limit");return;}
        if(quote.accepted().isEmpty()){send(player,contents(tray).isEmpty()?"buyer-empty":"buyer-invalid");player.closeInventory();return;}
        boolean submitted=plugin.credits().auctions().sellDeposit(player,quote.accepted(),quote.cents(),()->{
            // Custody is handed to auction escrow in the same player save; unsold items stay in this tray.
            for(int i=0;i<INPUTS;i++)tray.inventory.setItem(i,null);
            for(int i=0;i<quote.rejected().size();i++)tray.inventory.setItem(i,quote.rejected().get(i));
            journal(player,tray);
        });
        if(submitted)player.closeInventory();
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Tray tray))return;
        boolean denied=event.isCancelled();event.setCancelled(true);
        if(denied || !(event.getWhoClicked() instanceof Player player) || !active(player,tray) || tray.locked)return;
        int raw=event.getRawSlot();
        if(raw==35){tray.locked=true;Bukkit.getScheduler().runTask(plugin,()->player.closeInventory());return;}
        if(raw==31 && event.getClick()==ClickType.LEFT){tray.locked=true;Bukkit.getScheduler().runTask(plugin,()->sell(player,tray));return;}
        if(!mutable(raw))return;
        if(!mode(player)){send(player,"buyer-mode");return;}
        ItemStack current=get(player,tray,raw),cursor=copy(player.getItemOnCursor());
        switch(event.getClick()) {
            case LEFT, RIGHT -> {
                boolean right=event.getClick()==ClickType.RIGHT;
                if(empty(cursor)) {
                    if(empty(current))return;
                    int take=right?(current.getAmount()+1)/2:current.getAmount();cursor=current.clone();cursor.setAmount(take);current.setAmount(current.getAmount()-take);if(current.isEmpty())current=null;
                }else if(empty(current)) {
                    int put=right?1:Math.min(cursor.getAmount(),cursor.getMaxStackSize());current=cursor.clone();current.setAmount(put);cursor.setAmount(cursor.getAmount()-put);if(cursor.isEmpty())cursor=null;
                }else if(current.isSimilar(cursor)) {
                    int put=Math.min(current.getMaxStackSize()-current.getAmount(),right?1:cursor.getAmount());
                    if(put==0)return;current.setAmount(current.getAmount()+put);cursor.setAmount(cursor.getAmount()-put);if(cursor.isEmpty())cursor=null;
                }else{var swap=current;current=cursor;cursor=swap;}
                set(player,tray,raw,current);player.setItemOnCursor(cursor);
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                if(empty(current))return;
                if(raw<INPUTS){var left=player.getInventory().addItem(current);set(player,tray,raw,left.isEmpty()?null:left.values().iterator().next());}
                else{var left=deposit(tray,current);set(player,tray,raw,left);}
            }
            case NUMBER_KEY -> {
                int key=event.getHotbarButton();if(key<0 || key>8)return;
                var held=copy(player.getInventory().getItem(key));set(player,tray,raw,held);player.getInventory().setItem(key,current);
            }
            case SWAP_OFFHAND -> {var held=copy(player.getInventory().getItemInOffHand());set(player,tray,raw,held);player.getInventory().setItemInOffHand(current);}
            case DOUBLE_CLICK -> {
                if(empty(cursor))return;
                for(int slot=0;slot<SIZE+36 && cursor.getAmount()<cursor.getMaxStackSize();slot++)if(mutable(slot)) {
                    var candidate=get(player,tray,slot);if(candidate==null || !candidate.isSimilar(cursor))continue;
                    int take=Math.min(candidate.getAmount(),cursor.getMaxStackSize()-cursor.getAmount());cursor.setAmount(cursor.getAmount()+take);candidate.setAmount(candidate.getAmount()-take);set(player,tray,slot,candidate.isEmpty()?null:candidate);
                }
                player.setItemOnCursor(cursor);
            }
            default -> {return;}
        }
        refresh(tray);persist(player,tray);
    }
    private ItemStack deposit(Tray tray,ItemStack stack) {
        var left=stack.clone();
        for(int pass=0;pass<2;pass++)for(int i=0;i<INPUTS && !left.isEmpty();i++) {
            var target=copy(tray.inventory.getItem(i));
            if(pass==0 && target!=null && target.isSimilar(left)) {
                int put=Math.min(left.getAmount(),target.getMaxStackSize()-target.getAmount());target.setAmount(target.getAmount()+put);left.setAmount(left.getAmount()-put);tray.inventory.setItem(i,target);
            }else if(pass==1 && target==null){int put=Math.min(left.getAmount(),left.getMaxStackSize());target=left.clone();target.setAmount(put);tray.inventory.setItem(i,target);left.setAmount(left.getAmount()-put);}
        }
        return left.isEmpty()?null:left;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof Tray tray))return;
        boolean denied=event.isCancelled();event.setCancelled(true);
        if(denied || !(event.getWhoClicked() instanceof Player player) || !active(player,tray) || tray.locked || !mode(player) || event.getRawSlots().stream().anyMatch(s->!mutable(s)))return;
        event.getNewItems().forEach((slot,item)->set(player,tray,slot,item.clone()));player.setItemOnCursor(copy(event.getCursor()));refresh(tray);persist(player,tray);
    }
    @EventHandler public void closed(InventoryCloseEvent event) {
        if(!(event.getInventory().getHolder() instanceof Tray tray) || !(event.getPlayer() instanceof Player player))return;
        finish(player,tray);
    }
    private void finish(Player player,Tray tray) {
        if(tray.finished)return;tray.finished=true;sessions.remove(player.getUniqueId(),tray);
        var returned=contents(tray);for(int i=0;i<INPUTS;i++)tray.inventory.setItem(i,null);
        if(!empty(player.getItemOnCursor()))returned.add(player.getItemOnCursor().clone());player.setItemOnCursor(null);
        saved(player,returned);recover(player);
        if(!returned.isEmpty() && saved(player).isEmpty())send(player,"buyer-returned");
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hurt(EntityDamageEvent event){if(event.getFinalDamage()>0 && event.getEntity() instanceof Player player && sessions.containsKey(player.getUniqueId()))player.closeInventory();}
    @EventHandler public void quit(PlayerQuitEvent event){var tray=sessions.get(event.getPlayer().getUniqueId());if(tray!=null)finish(event.getPlayer(),tray);}
    @EventHandler public void join(PlayerJoinEvent event){Bukkit.getScheduler().runTask(plugin,()->recover(event.getPlayer()));}
    private void tick() {
        for(var tray:List.copyOf(sessions.values())) {
            var player=Bukkit.getPlayer(tray.owner);var jawa=Bukkit.getEntity(tray.jawa);
            if(player==null)continue;
            if(!(jawa instanceof LivingEntity) || !jawa.isValid() || jawa.getWorld()!=player.getWorld() || jawa.getLocation().distanceSquared(player.getLocation())>36 || plugin.combat().tagged(player))player.closeInventory();
        }
    }
    @Override public void close() {
        task.cancel();for(var tray:List.copyOf(sessions.values())){var player=Bukkit.getPlayer(tray.owner);if(player!=null){player.closeInventory();if(!tray.finished)finish(player,tray);}}
        HandlerList.unregisterAll(this);
    }
}
