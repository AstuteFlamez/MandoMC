package net.mandomc.gameplay.boba;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mandomc.commands.RootCommand;
import net.mandomc.gameplay.ui.MenuItems;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.joml.Vector3f;

/** Three documented backpack rendering patterns, independently selectable for visual playtesting. */
public final class JetpackVisualTrials implements Listener, AutoCloseable {
    public enum Method { HMC, MYTHIC, MAGIC }
    private static final NamespacedKey METHOD = new NamespacedKey("mandomc","jetpack_visual_method");
    private static final NamespacedKey HIDDEN = new NamespacedKey("mandomc","jetpack_visual_hide_self");
    private static final NamespacedKey OFFSET = new NamespacedKey("mandomc","jetpack_visual_offset");
    private final JavaPlugin plugin;
    private final YamlConfiguration language;
    private final Map<UUID, Trial> trials = new HashMap<>();
    private final Set<Inventory> menus = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final class Trial {
        final Method method;
        final ItemDisplay original;
        final List<Entity> owned = new ArrayList<>();
        ArmorStand viewer, wearer;
        ItemDisplay selfDisplay;
        Trial(Method method, ItemDisplay original) { this.method=method;this.original=original; }
    }
    public JetpackVisualTrials(JavaPlugin plugin) {
        this.plugin=plugin;
        var file=new java.io.File(plugin.getDataFolder(),"equipment/jetpack-visual-tests.yml");
        if(file.isFile())language=YamlConfiguration.loadConfiguration(file);
        else try(var reader=new InputStreamReader(Objects.requireNonNull(
                plugin.getResource("equipment/jetpack-visual-tests.yml")),StandardCharsets.UTF_8)) {
            language=YamlConfiguration.loadConfiguration(reader);
        } catch(java.io.IOException error) { throw new IllegalStateException("Cannot read visual test language",error); }
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    public Method method(Player player) {
        try { return Method.valueOf(player.getPersistentDataContainer().getOrDefault(METHOD,PersistentDataType.STRING,"MYTHIC")); }
        catch(IllegalArgumentException error) { return Method.MYTHIC; }
    }
    public boolean hidden(Player player) { return player.getPersistentDataContainer().getOrDefault(HIDDEN,PersistentDataType.BYTE,(byte)0)!=0; }
    public int offset(Player player) { return Math.clamp(player.getPersistentDataContainer().getOrDefault(OFFSET,PersistentDataType.INTEGER,0),0,2); }
    public void select(Player player, Method method) {
        remove(player);
        player.getPersistentDataContainer().set(METHOD,PersistentDataType.STRING,method.name());
    }
    public void hideSelf(Player player, boolean hide) { player.getPersistentDataContainer().set(HIDDEN,PersistentDataType.BYTE,(byte)(hide?1:0)); }
    public void offset(Player player, int offset) { player.getPersistentDataContainer().set(OFFSET,PersistentDataType.INTEGER,Math.clamp(offset,0,2)); }
    public void register(RootCommand root) {
        root.register("jetpacktest","mandomc.equipment",(sender,args)-> {
            if(!(sender instanceof Player player))return;
            if(args.length>0) {
                try { select(player,Method.valueOf(args[0].toUpperCase(Locale.ROOT))); }
                catch(IllegalArgumentException ignored) { }
            }
            open(player);
        },(sender,args)->Arrays.stream(Method.values()).map(m->m.name().toLowerCase(Locale.ROOT))
                .filter(m->args.length==0||m.startsWith(args[0].toLowerCase(Locale.ROOT))).toList());
    }
    private ItemStack model(String suffix) {
        var item=new ItemStack(Material.ECHO_SHARD);var meta=item.getItemMeta();
        meta.setItemModel(new NamespacedKey("mandomc","equipment/jetpack_test_"+suffix));item.setItemMeta(meta);return item;
    }
    private ArmorStand stand(Player player,Trial trial,boolean self) {
        var stand=player.getWorld().spawn(player.getLocation(),ArmorStand.class,e->{
            e.setVisible(false);e.setMarker(true);e.setGravity(false);e.setPersistent(false);e.setInvulnerable(true);
            e.setSilent(true);e.setBasePlate(false);e.setArms(false);e.setCanTick(false);e.setCanMove(false);
            e.setVisibleByDefault(!self);
            e.getAttribute(Attribute.SCALE).setBaseValue(player.getAttribute(Attribute.SCALE).getValue());
            for(var slot:EquipmentSlot.values())if(slot.isArmor())e.addEquipmentLock(slot,ArmorStand.LockType.ADDING_OR_CHANGING);
        });
        trial.owned.add(stand);
        if(self)player.showEntity(plugin,stand);else player.hideEntity(plugin,stand);
        return stand;
    }
    private Trial create(Player player,ItemDisplay original,Method method) {
        var trial=new Trial(method,original);
        try {
            if(method==Method.MYTHIC) {
                trial.selfDisplay=player.getWorld().spawn(player.getLocation(),ItemDisplay.class,e->{
                    e.setItemStack(original.getItemStack());e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                    e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setVisibleByDefault(false);
                    e.setInterpolationDuration(1);e.setTeleportDuration(0);e.setShadowRadius(0);
                });
                trial.owned.add(trial.selfDisplay);mount(player,trial.selfDisplay);player.showEntity(plugin,trial.selfDisplay);
                player.hideEntity(plugin,original);
            } else {
                original.setItemStack(new ItemStack(Material.AIR));
                trial.viewer=stand(player,trial,false);mount(player,trial.viewer);
                trial.viewer.getEquipment().setHelmet(model("carrier_0"));
                trial.wearer=stand(player,trial,true);
                Entity parent=player;
                if(method==Method.HMC) {
                    // The documented HMC self-view pattern elevates a carrier through invisible cloud mounts.
                    // A separately lowered helmet model compensates; the client's frustum decides visibility.
                    for(int i=0;i<5;i++) {
                        var cloud=player.getWorld().spawn(player.getLocation(),AreaEffectCloud.class,e->{
                            e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setVisibleByDefault(false);
                            e.setRadius(0);e.setDuration(Integer.MAX_VALUE);e.setWaitTime(Integer.MAX_VALUE);
                            e.setParticle(Particle.BLOCK,Bukkit.createBlockData(Material.AIR));
                        });
                        trial.owned.add(cloud);mount(parent,cloud);player.showEntity(plugin,cloud);parent=cloud;
                    }
                }
                mount(parent,trial.wearer);
            }
            return trial;
        } catch(RuntimeException error) { destroy(trial);throw error; }
    }
    private void mount(Entity parent,Entity child) {
        if(!parent.addPassenger(child))throw new IllegalStateException("Could not mount jetpack visual test entity");
    }
    public void update(Player player,ItemDisplay original,JetpackPose.Pose pose) {
        var method=method(player);var trial=trials.get(player.getUniqueId());
        if(trial!=null&&(trial.original!=original||trial.method!=method||trial.owned.stream().anyMatch(e->!e.isValid()))) {
            remove(player);trial=null;
        }
        if(trial==null) { trial=create(player,original,method);trials.put(player.getUniqueId(),trial); }
        if(method==Method.MYTHIC) {
            var transform=original.getTransformation();
            var extra=new Vector3f(0,0,offset(player)*.15f).rotate(pose.modelRotation());
            transform.getTranslation().add(extra);
            if(!transform.equals(trial.selfDisplay.getTransformation())) {
                trial.selfDisplay.setTransformation(transform);trial.selfDisplay.setInterpolationDelay(0);
            }
            trial.selfDisplay.setRotation(0,0);
            if(hidden(player))player.hideEntity(plugin,trial.selfDisplay);else player.showEntity(plugin,trial.selfDisplay);
        } else {
            // These carrier patterns rotate around yaw, as in their public backpack documentation/source.
            // Deliberately retain that difference from the full torso-matrix display for an honest comparison.
            for(var stand:List.of(trial.viewer,trial.wearer)) {
                stand.setRotation(player.getBodyYaw(),0);stand.setBodyYaw(player.getBodyYaw());
            }
            var desired=model((method==Method.HMC?"wearer_":"carrier_")+offset(player));
            if(!desired.equals(trial.wearer.getEquipment().getHelmet()))trial.wearer.getEquipment().setHelmet(desired);
            if(hidden(player))player.hideEntity(plugin,trial.wearer);else player.showEntity(plugin,trial.wearer);
        }
    }
    private void destroy(Trial trial) {
        for(int i=trial.owned.size()-1;i>=0;i--)trial.owned.get(i).remove();
        if(trial.original.isValid()) {
            var item=new ItemStack(Material.ECHO_SHARD);var meta=item.getItemMeta();
            meta.setItemModel(new NamespacedKey("mandomc","equipment/boba_jetpack_attached"));item.setItemMeta(meta);
            trial.original.setItemStack(item);
        }
    }
    public void remove(Player player) {
        var trial=trials.remove(player.getUniqueId());if(trial==null)return;
        destroy(trial);if(trial.original.isValid()&&player.isOnline())player.showEntity(plugin,trial.original);
    }
    /** Read-only diagnostics for the disposable native visual/lifecycle probe. */
    public List<Entity> entities(Player player) { var trial=trials.get(player.getUniqueId());return trial==null?List.of():List.copyOf(trial.owned); }
    private ItemStack button(Material material,String key) {
        var item=new ItemStack(material);var meta=item.getItemMeta();var mm=MiniMessage.miniMessage();
        meta.displayName(mm.deserialize(language.getString(key+".name")));
        meta.lore(language.getStringList(key+".lore").stream().map(mm::deserialize).toList());item.setItemMeta(meta);return item;
    }
    public void open(Player player) {
        var inventory=Bukkit.createInventory(null,27,MiniMessage.miniMessage().deserialize(language.getString("title")));
        MenuItems.fill(inventory);
        inventory.setItem(10,button(Material.LEATHER_HELMET,"hmc"));
        inventory.setItem(13,button(Material.ECHO_SHARD,"mythic"));
        inventory.setItem(16,button(Material.ARMOR_STAND,"magic"));
        var selected=inventory.getItem(switch(method(player)){case HMC->10;case MYTHIC->13;case MAGIC->16;});
        var meta=selected.getItemMeta();meta.setEnchantmentGlintOverride(true);selected.setItemMeta(meta);
        inventory.setItem(20,button(hidden(player)?Material.ENDER_EYE:Material.ENDER_PEARL,hidden(player)?"show":"hide"));
        var offsetButton=button(Material.COMPARATOR,"offset");var offsetMeta=offsetButton.getItemMeta();
        var lore=new ArrayList<>(Objects.requireNonNull(offsetMeta.lore()));
        lore.add(MiniMessage.miniMessage().deserialize(language.getString("offset-current"),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("distance",String.format(Locale.ROOT,"%.2f",offset(player)*.15))));
        offsetMeta.lore(lore);offsetButton.setItemMeta(offsetMeta);inventory.setItem(22,offsetButton);
        inventory.setItem(24,button(Material.BARRIER,"reset"));menus.add(inventory);player.openInventory(inventory);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!menus.contains(event.getView().getTopInventory()))return;event.setCancelled(true);
        if(event.getClick()!=ClickType.LEFT||!(event.getWhoClicked() instanceof Player player))return;
        boolean change=true;
        switch(event.getRawSlot()) {
            case 10->select(player,Method.HMC);case 13->select(player,Method.MYTHIC);case 16->select(player,Method.MAGIC);
            case 20->hideSelf(player,!hidden(player));case 22->offset(player,(offset(player)+1)%3);
            case 24->{select(player,Method.MYTHIC);hideSelf(player,false);offset(player,0);}
            default->change=false;
        }
        if(change)Bukkit.getScheduler().runTask(plugin,()->{
            if(player.isOnline()&&player.getOpenInventory().getTopInventory()==event.getView().getTopInventory())open(player);
        });
    }
    @EventHandler public void drag(InventoryDragEvent event) { if(menus.contains(event.getView().getTopInventory()))event.setCancelled(true); }
    @EventHandler public void closed(InventoryCloseEvent event) { menus.remove(event.getInventory()); }
    @Override public void close() {
        for(var trial:trials.values())destroy(trial);trials.clear();
        for(var menu:List.copyOf(menus))for(var viewer:List.copyOf(menu.getViewers()))viewer.closeInventory();
        menus.clear();HandlerList.unregisterAll(this);
    }
}
