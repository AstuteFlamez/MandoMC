package net.mandomc.gameplay.events;

import java.io.File;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.format.TextDecoration;
import net.mandomc.gameplay.travel.WarpService;
import net.mandomc.gameplay.ui.MenuItems;
import org.bukkit.Material;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Legacy event rotation/menu/admin surface; owns the sole scheduler and active event. */
public final class EventManager implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final WarpService warps;
    private Map<String,ManagedEvent> events=Map.of();
    private Map<String,EventRotation.Entry> entries=Map.of();
    private EventRotation rotation=new EventRotation();
    private String active;
    private String queued;
    private YamlConfiguration config;
    private ZoneId zone;
    private HourlySchedule schedule;
    private int startMinute;
    private int endMinute;
    private EventEndCountdown endingCountdown;
    private final Map<UUID,Inventory> menus=new HashMap<>();
    private final BukkitTask ticker;
    public EventManager(JavaPlugin plugin,WarpService warps) throws Exception {
        this.plugin=plugin;this.warps=warps;reload();
        var command=Objects.requireNonNull(plugin.getCommand("event"));
        command.setExecutor((sender,ignored,label,args)->command(sender,args));
        command.setTabCompleter((sender,ignored,label,args)-> {
            if(args.length==1) return List.of("gui","status","list","start","stop","forceend","next","reroll","reload","enable","disable")
                    .stream().filter(s->s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .filter(s->s.equals("gui") || sender.hasPermission("mandomc.event.admin")).toList();
            return args.length==2 && sender.hasPermission("mandomc.event.admin")
                    ?events.keySet().stream().filter(s->s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList():List.of();
        });
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        ticker=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,10,10);
    }
    private YamlConfiguration read(String path) throws Exception {
        var file=new File(plugin.getDataFolder(),path);
        if(!file.isFile()) plugin.saveResource(path,false);
        var yaml=new YamlConfiguration();yaml.load(file);
        if(yaml.getInt("schema-version")!=1) throw new IllegalArgumentException(path+": expected schema-version 1");
        return yaml;
    }
    /** Idle-only replacement validates all data before releasing the previous configuration. */
    public void reload() throws Exception {
        if(active!=null) throw new IllegalStateException("An event is active");
        var nextConfig=read("events/schedule.yml");
        ZoneId nextZone=ZoneId.of(nextConfig.getString("scheduler.timezone",""));
        int nextMinute=nextConfig.getInt("scheduler.announcements.start-minute",-1);
        var nextSchedule=new HourlySchedule(ZonedDateTime.now(nextZone),
                nextConfig.getInt("scheduler.announcements.end-warning-minute",-1),
                nextConfig.getInt("scheduler.announcements.end-minute",-1),
                nextConfig.getInt("scheduler.announcements.start-warning-minute",-1),nextMinute);
        for(String key:List.of("current","next","chances","chance-entry","none","idle","click","starts-in","ending","starting",
                "denied","players-only","busy","reloaded","invalid","started","stopped","queued","enabled","disabled","usage"))
            if(!nextConfig.isString("messages."+key)) throw new IllegalArgumentException("events/schedule.yml: missing messages."+key);
        if(!nextConfig.isString("gui.title")) throw new IllegalArgumentException("events/schedule.yml: missing gui.title");
        var nextCountdown = new EventEndCountdown(
                nextConfig.getString("ending-countdown.title", EventEndCountdown.DEFAULT_TITLE),
                nextConfig.getString("ending-countdown.subtitle", EventEndCountdown.DEFAULT_SUBTITLE));
        // Register the next event type here when its playable slice lands; do not load old unused definitions.
        var nextEvents=new LinkedHashMap<String,ManagedEvent>();
        var nextEntries=new LinkedHashMap<String,EventRotation.Entry>();
        var kothConfig=read("events/koth.yml");
        if(!"koth".equals(kothConfig.getString("id"))) throw new IllegalArgumentException("events/koth.yml: expected id koth");
        var koth=new KothEvent(plugin,kothConfig);
        nextEvents.put(koth.id(),koth);
        nextEntries.put(koth.id(),new EventRotation.Entry(koth.id(),kothConfig.getInt("weight",-1),
                kothConfig.getInt("cooldown-cycles",-1),kothConfig.getBoolean("enabled",false)));
        try {
            var jabbaConfig=read("events/jabba.yml");
            var catalog=((net.mandomc.bootstrap.MandoPlugin)plugin).items();
            var jabba=new net.mandomc.gameplay.dungeon.JabbaEvent(plugin,catalog,jabbaConfig);
            nextEvents.put(jabba.id(),jabba);
            nextEntries.put(jabba.id(),new EventRotation.Entry(jabba.id(),jabbaConfig.getInt("weight",-1),jabbaConfig.getInt("cooldown-cycles",-1),jabbaConfig.getBoolean("enabled",false)));
        } catch(Exception error) {nextEvents.values().forEach(ManagedEvent::close);throw error;}
        events.values().forEach(ManagedEvent::close);
        config=nextConfig;zone=nextZone;schedule=nextSchedule;startMinute=nextMinute;
        endMinute=nextConfig.getInt("scheduler.announcements.end-minute");endingCountdown=nextCountdown;
        events=Map.copyOf(nextEvents);entries=Map.copyOf(nextEntries);rotation=new EventRotation();
        reroll();refreshMenus();
    }
    public ManagedEvent event(String id) {return events.get(id);}
    public String activeId() {return active;}
    public String queuedId() {return queued;}
    public boolean start(String id) {
        if(active!=null || !entries.containsKey(id) || !entries.get(id).enabled()) return false;
        try {events.get(id).start();}
        catch(RuntimeException error) {events.get(id).stop(false);plugin.getLogger().warning("Cannot start event "+id+": "+error.getMessage());return false;}
        endingCountdown.reset();active=id;if(id.equals(queued)) queued=null;
        rotation.started(entries.get(id));return true;
    }
    public void stop(boolean announce) {
        endingCountdown.reset();
        if(active!=null) {events.get(active).stop(announce);active=null;}
    }
    public boolean queue(String id) {
        if(!entries.containsKey(id) || !entries.get(id).enabled()) return false;
        queued=id;return true;
    }
    public void reroll() {queued=rotation.choose(List.copyOf(entries.values()),ThreadLocalRandom.current());}
    public long startsIn() {
        var now=ZonedDateTime.now(zone);
        var next=now.truncatedTo(ChronoUnit.HOURS).plusMinutes(startMinute);
        if(!next.isAfter(now)) next=next.plusHours(1);
        return java.time.Duration.between(now,next).getSeconds();
    }
    public void phase(HourlySchedule.Phase phase) {
        switch(phase) {
            case END_WARNING -> {if(active!=null) plugin.getServer().broadcast(text("ending",name(active),""));}
            case STOP -> {stop(true);if(queued==null) reroll();}
            case START_WARNING -> {if(queued==null) reroll();if(queued!=null) plugin.getServer().broadcast(text("starting",name(queued),""));}
            case START -> {if(queued==null) reroll();if(queued!=null) start(queued);}
        }
    }
    private void tick() {
        if(config.getBoolean("scheduler.enabled")) for(var phase:schedule.poll(ZonedDateTime.now(zone))) phase(phase);
        if(active!=null) {
            var event=events.get(active);
            try {event.tick();}
            catch(RuntimeException error) {plugin.getLogger().log(java.util.logging.Level.SEVERE,"Event stopped after gameplay error",error);event.stop(false);}
            if(!event.running()) {active=null;endingCountdown.reset();}
            else if(config.getBoolean("ending-countdown.enabled",true)) {
                long scheduled=config.getBoolean("scheduler.enabled")
                        ?EventEndCountdown.secondsUntilStop(ZonedDateTime.now(zone),endMinute):Long.MAX_VALUE;
                endingCountdown.send(event,scheduled,Bukkit.getOnlinePlayers());
            } else endingCountdown.reset();
        }
        refreshMenus();
    }
    private String name(String id) {return id==null?config.getString("messages.none"):events.get(id).displayName();}
    public static String formatDuration(long seconds) {
        long remaining=Math.max(0,seconds);
        long hours=remaining/3600,minutes=remaining%3600/60,secs=remaining%60;
        var parts=new java.util.ArrayList<String>();
        if(hours>0) parts.add(hours+(hours==1?" Hour":" Hours"));
        if(minutes>0) parts.add(minutes+(minutes==1?" Min":" Mins"));
        if(secs>0 || parts.isEmpty()) parts.add(secs+(secs==1?" Second":" Seconds"));
        return String.join(" ",parts);
    }
    private Component text(String key,String event,String chance) {
        return MiniMessage.miniMessage().deserialize(config.getString("messages."+key),
                Placeholder.unparsed("event",event),Placeholder.unparsed("chance",chance),
                Placeholder.unparsed("time",formatDuration(startsIn())),
                Placeholder.unparsed("seconds",Long.toString(startsIn()))).decoration(TextDecoration.ITALIC,false);
    }
    private void send(CommandSender sender,String key) {sender.sendMessage(text(key,"",""));}
    private boolean command(CommandSender sender,String[] args) {
        if(args.length==0 || args.length==1 && args[0].equalsIgnoreCase("gui")) {
            if(sender instanceof Player p) open(p);else send(sender,"players-only");return true;
        }
        if(!sender.hasPermission("mandomc.event.admin")) {send(sender,"denied");return true;}
        String sub=args[0].toLowerCase(Locale.ROOT),id=args.length==2?args[1].toLowerCase(Locale.ROOT):"";
        if(args.length>2) {send(sender,"usage");return true;}
        try {
            switch(sub) {
                case "start" -> send(sender,start(id)?"started":"invalid");
                case "stop","forceend" -> {stop(true);send(sender,"stopped");}
                case "next" -> send(sender,queue(id)?"queued":"invalid");
                case "reroll" -> {reroll();send(sender,queued!=null?"queued":"invalid");}
                case "reload" -> {if(active!=null) send(sender,"busy");else {reload();send(sender,"reloaded");}}
                case "status" -> {sender.sendMessage(text("current",name(active),""));sender.sendMessage(text("next",name(queued),""));sender.sendMessage(text("starts-in","",""));}
                case "list" -> sender.sendMessage(Component.text(String.join(", ",events.keySet())));
                case "enable","disable" -> {
                    if(!events.containsKey(id)) {send(sender,"invalid");break;}
                    if(active!=null) {send(sender,"busy");break;}
                    var file=new File(plugin.getDataFolder(),"events/"+id+".yml");
                    var yaml=new YamlConfiguration();yaml.load(file);yaml.set("enabled",sub.equals("enable"));yaml.save(file);
                    reload();send(sender,sub.equals("enable")?"enabled":"disabled");
                }
                default -> send(sender,"usage");
            }
        } catch(Exception error) {plugin.getLogger().warning("Event action failed: "+error.getMessage());send(sender,"invalid");}
        return true;
    }
    public void open(Player p) {
        var inventory=plugin.getServer().createInventory(null,27,MiniMessage.miniMessage().deserialize(config.getString("gui.title")));
        decorate(inventory);p.openInventory(inventory);menus.put(p.getUniqueId(),inventory);
    }
    private ItemStack icon(Material material,Component name,List<Component> lore) {
        var item=new ItemStack(material);var meta=item.getItemMeta();meta.displayName(name);meta.lore(lore);item.setItemMeta(meta);return item;
    }
    private void decorate(Inventory inventory) {
        inventory.setItem(11,icon(Material.BEACON,text("current",name(active),""),List.of(text(active==null?"idle":"click","",""))));
        inventory.setItem(13,icon(Material.CLOCK,text("next",name(queued),""),List.of(text("starts-in","",""),text("click","",""))));
        int total=entries.values().stream().filter(EventRotation.Entry::enabled).mapToInt(EventRotation.Entry::weight).sum();
        var chances=entries.values().stream().filter(EventRotation.Entry::enabled)
                .map(e->text("chance-entry",name(e.id()),String.format(Locale.ROOT,"%.1f",total==0?0:100d*e.weight()/total))).toList();
        inventory.setItem(15,icon(Material.PAPER,text("chances","",""),chances));MenuItems.fill(inventory);
    }
    private void refreshMenus() {menus.values().forEach(this::decorate);}
    @EventHandler public void click(InventoryClickEvent e) {
        if(!menus.containsValue(e.getView().getTopInventory()))return;
        e.setCancelled(true);
        if(e.getWhoClicked() instanceof Player p && e.isLeftClick() && !e.isShiftClick()
                && (e.getRawSlot()==11 || e.getRawSlot()==13)) {String target=e.getRawSlot()==11?active:queued;p.closeInventory();if(target!=null)warps.request(p,target.equals("jabba")?"tatooine":"hoth");}
    }
    @EventHandler public void drag(InventoryDragEvent e) {if(menus.containsValue(e.getView().getTopInventory()))e.setCancelled(true);}
    @EventHandler public void closed(InventoryCloseEvent e) {menus.remove(e.getPlayer().getUniqueId(),e.getInventory());}
    public void close() {
        ticker.cancel();stop(false);events.values().forEach(ManagedEvent::close);
        for(var inventory:List.copyOf(menus.values())) for(var viewer:List.copyOf(inventory.getViewers())) viewer.closeInventory();
        menus.clear();HandlerList.unregisterAll(this);
    }
}
