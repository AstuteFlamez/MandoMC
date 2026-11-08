package net.mandomc.verification;

import java.lang.reflect.Proxy;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.saber.SaberService;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.event.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile;

/** Real world, item and vendor APIs; isolated fake-player inventories, never user gear. */
public final class SaberProbe extends JavaPlugin {
    private int checks;
    private SaberService sabers;
    private TestPlayer player;
    private ArmorStand shooter;
    private final List<WeaponProjectile> projectiles=new ArrayList<>();
    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this,()-> {
        try {verify();} catch(Throwable e) {getLogger().log(java.util.logging.Level.SEVERE,"SABER PROBE FAILED",e);cleanup();}
    },60); }
    private void verify() throws Exception {
        var plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");sabers=plugin.sabers();
        var item=plugin.items().create("vader");check(sabers.isSaber(item),"Vader has stable native shield identity");
        check(item.getMaxStackSize()==1,"saber cannot stack");
        check(item.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).iterator().next().getAmount()==9.5,"native melee adds configured damage");
        check(item.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.ATTACK_SPEED).iterator().next().getAmount()==-3,"native attack speed and charge retained");
        check(item.getItemMeta().lore().get(1).equals(net.kyori.adventure.text.Component.empty()),"badge row followed by blank line");
        var world=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        var point=new Location(world,-150,300,-150,0,0);world.getChunkAt(point).addPluginChunkTicket(this);
        player=new TestPlayer("SaberProbe",point);player.storage.setItem(0,item);
        var packConfig=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"sabers.yml")).getConfigurationSection("upstream-pack");
        var pack=new net.mandomc.integrations.packs.SaberPack(this,packConfig);
        org.bukkit.event.HandlerList.unregisterAll(pack);
        pack.request(player.player);
        check(player.packRequests==1,"join delivery works without a prior pack-loaded callback");
        pack.loaded(new org.bukkit.event.player.PlayerResourcePackStatusEvent(player.player,UUID.fromString(packConfig.getString("main-pack-id")),org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        check(player.packRequests==1 && player.forcedPack,"main pack completion requests required upstream pack");
        check(player.packUrl.equals(packConfig.getString("url")),"upstream delivered directly from pinned official URL");
        pack.loaded(new org.bukkit.event.player.PlayerResourcePackStatusEvent(player.player,UUID.fromString(packConfig.getString("id")),org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
        check(player.packRequests==1,"upstream completion cannot trigger an infinite reload loop");
        var offhand=new org.bukkit.event.player.PlayerInteractEvent(player.player,org.bukkit.event.block.Action.RIGHT_CLICK_AIR,item,null,org.bukkit.block.BlockFace.SELF,EquipmentSlot.OFF_HAND);
        sabers.interact(offhand);check(offhand.useItemInHand()==org.bukkit.event.Event.Result.DENY,"offhand cannot bypass saber guard");
        check(sabers.allowed(point),"combat permitted outside landing");
        check(!sabers.allowed(world.getSpawnLocation()),"landing blocks saber combat");
        check(!sabers.allowed(Bukkit.getWorld(NamespacedKey.minecraft("spawnworld")).getSpawnLocation()),"SpawnWorld blocks saber combat");
        check(sabers.canReflect(player.player,new Vector(0,0,-1)),"front bolt can be reflected");
        check(!sabers.canReflect(player.player,new Vector(0,0,1)),"rear bolt cannot be reflected");
        check(!sabers.canReflect(player.player,new Vector(1,0,0)),"side bolt cannot be reflected");
        player.blocking=false;check(!sabers.canReflect(player.player,new Vector(0,0,-1)),"idle saber does not reflect");player.blocking=true;
        shooter=world.spawn(point.clone().add(0,0,5),ArmorStand.class,e->{e.setPersistent(false);e.setGravity(false);});
        var def=WeaponMechanics.getInstance().getWeaponConfigurations().getObject("E11.Projectile",Projectile.class);
        var original=def.create(shooter,point.clone().add(0,1.62,4),new Vector(0,0,-2),WeaponMechanicsAPI.generateWeapon("E11"),"E11",EquipmentSlot.HAND);projectiles.add(original);
        var reflected=(WeaponProjectile)plugin.saberProjectiles().createReflection(original,player.player);projectiles.add(reflected);
        check(reflected.getShooter()==player.player,"reflected E11 belongs to defender");
        check(original.getShooter()==shooter,"original shooter identity remains immutable");
        check(reflected.getMotion().getZ()>0 && reflected.getMotion().length()==2,"reflection follows defender aim and preserves speed");
        check(reflected.getIntTag("mandomc_saber_reflections")==1,"reflection count survives new projectile");
        check(reflected.getWeaponTitle().equals("E11"),"reflection retains weapon damage configuration");
        check(sabers.launch(player.player),"sneak throw can launch");
        check(player.storage.getItem(0)!=null && sabers.isSaber(player.storage.getItem(0)),"real saber stays in inventory during throw");
        check(player.storage.getItem(0).getItemMeta().getItemModel().toString().equals("mandomc:sabers/vader_hilt"),"hand shows inactive hilt during flight");
        check(!sabers.launch(player.player),"second simultaneous throw is refused");
        check(!sabers.canReflect(player.player,new Vector(0,0,-1)),"in-flight saber cannot reflect");
        var event=new InventoryClickEvent(player.view,InventoryType.SlotType.CONTAINER,0,ClickType.SHIFT_LEFT,InventoryAction.NOTHING);
        sabers.inventory(event);check(event.isCancelled(),"reserved inventory cannot shift-transfer");
        Bukkit.getScheduler().runTaskLater(this,()-> {
            try {
                check(!sabers.busy(player.player),"throw finishes within bounded lifetime");
                check(player.storage.getItem(0).getItemMeta().getItemModel().toString().equals("mandomc:sabers/vader"),"finished throw restores existing item presentation");
                check(Arrays.stream(player.storage.getContents()).filter(Objects::nonNull).mapToInt(ItemStack::getAmount).sum()==1,"return never duplicates inventory item");
                check(sabers.launch(player.player),"throw available after cooldown");
                sabers.quit(new org.bukkit.event.player.PlayerQuitEvent(player.player,net.kyori.adventure.text.Component.empty()));
                check(!sabers.busy(player.player) && player.storage.getItem(0).getItemMeta().getItemModel().toString().equals("mandomc:sabers/vader"),"logout restores reserved item and removes flight");
                for(int i=0;i<5;i++)sabers.reflected(player.player);
                player.blocking=true;check(!sabers.canReflect(player.player,new Vector(0,0,-1)),"guard overheat disables reflection despite blocking state");
                getLogger().info("SABER PROBE PASSED: "+checks+" assertions");cleanup();
            }catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"SABER PROBE FAILED",e);cleanup();}
        },110);
    }
    private void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);checks++;getLogger().info("PASS "+message);}
    private void cleanup(){if(sabers!=null&&player!=null)sabers.finish(player.id);if(shooter!=null)shooter.remove();projectiles.forEach(WeaponProjectile::remove);if(player!=null)player.location.getChunk().removePluginChunkTicket(this);}
    @Override public void onDisable(){cleanup();}
    private static final class TestPlayer {
        private int packRequests;
        private boolean forcedPack;
        private String packUrl;
        private Location location;
        private boolean blocking=true;
        private final UUID id = UUID.randomUUID();
        private final Inventory storage = Bukkit.createInventory(null, 36);
        private boolean allowed = true;
        private boolean online = true;
        private InventoryView view;
        private final Player player;
        private TestPlayer(String name, Location location) {
            this.location=location;
            var inventory = (PlayerInventory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {PlayerInventory.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getHeldItemSlot")) return 0;
                        if (method.getName().equals("getItemInMainHand")) return storage.getItem(0);
                        if (method.getName().equals("setItemInMainHand")) {storage.setItem(0,(ItemStack) args[0]);return null;}
                        if (method.getName().equals("getStorageContents")) return storage.getContents();
                        try { return Inventory.class.getMethod(method.getName(), method.getParameterTypes()).invoke(storage, args); }
                        catch (NoSuchMethodException ignored) { return fallback(method.getReturnType()); }
                    });
            player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "addResourcePack" -> {packRequests++;packUrl=(String)args[1];forcedPack=(boolean)args[4];yield null;}
                    case "getLocation" -> this.location.clone();
                    case "getEyeLocation" -> this.location.clone().add(0,1.62,0);
                    case "getWorld" -> this.location.getWorld();
                    case "isBlocking" -> blocking;
                    case "getActiveItem" -> storage.getItem(0);
                    case "getActiveItemHand" -> EquipmentSlot.HAND;
                    case "clearActiveItem" -> {blocking=false;yield null;}
                    case "getGameMode" -> GameMode.SURVIVAL;
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
            return (InventoryView) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {InventoryView.class},
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
