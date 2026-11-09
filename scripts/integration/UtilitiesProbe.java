package net.mandomc.verification;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.commands.CommandMessages;
import net.mandomc.gameplay.arrival.*;
import net.mandomc.gameplay.travel.*;
import net.mandomc.gameplay.admin.AdminService;
import org.bukkit.*;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;

/** Real Paper worlds/commands with isolated actor data. Never changes online players or their gear. */
public final class UtilitiesProbe extends JavaPlugin {
    private int checks;
    private MandoPlugin plugin;
    private TeleportService travel;
    private TeleportRequests requests;
    private final List<Actor> actors = new ArrayList<>();
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { run(); getLogger().info("UTILITIES PROBE PASSED: " + checks + " assertions"); }
            catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "UTILITIES PROBE FAILED", error); }
            finally { if (requests != null) requests.close(); if (travel != null) travel.close();
                for (var actor : actors) plugin.combat().clear(actor.player); }
        }, 80);
    }
    private void check(boolean value, String why) {
        if (!value) throw new IllegalStateException(why);
        checks++; getLogger().info("PASS " + why);
    }
    private void denied(Runnable action, String why) {
        try { action.run(); throw new AssertionError(why); }
        catch (IllegalArgumentException | IllegalStateException expected) { check(true, why); }
    }
    private void run() throws Exception {
        plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(plugin.worlds() != null && plugin.admin() != null && plugin.warps() != null, "replacement fully initialized");
        check(Bukkit.getPluginManager().getPlugin("Essentials") == null, "Essentials absent");
        check(Bukkit.getPluginManager().getPlugin("Multiverse-Core") == null, "Multiverse absent");
        check(Bukkit.getWorld(NamespacedKey.minecraft("the_nether")) == null && Bukkit.getWorld(NamespacedKey.minecraft("the_end")) == null, "disabled dimensions stay unloaded");
        for (String world : List.of("spawnworld", "overworld", "hoth", "devworld")) {
            check(Bukkit.getWorld(NamespacedKey.minecraft(world)) != null, world + " loaded without Multiverse");
        }
        for (String id : List.of("spawn", "earth", "hoth")) {
            var location = plugin.warps().destination(id);
            check(location.getWorld() != null, id + " public arrival validated");
        }
        var hoth = plugin.warps().destination("hoth");
        check(hoth.getX() == -47.0 && hoth.getZ() == -153.0
                && hoth.getYaw() == -18.75f && hoth.getPitch() == -1.65f, "Hoth arrival retains exact position and facing");
        check(plugin.worlds().resolve("SurvivalWorld").getKey().equals(NamespacedKey.minecraft("overworld")), "old world alias preserved");
        denied(() -> plugin.worlds().resolve("the_nether"), "disabled world unavailable through alias resolver");
        var publicNames = List.of("home", "homes", "sethome", "delhome", "back", "tpa", "tpahere", "tpaccept", "tpdeny", "tpacancel", "tpcancel", "spawn", "survival", "warp");
        for (String name : publicNames) {
            var command = Bukkit.getPluginCommand(name);
            check(command != null && command.getPlugin() == plugin, "/" + name + " owned by MandoMC");
            check(Bukkit.getPluginManager().getPermission(command.getPermission()).getDefault().getValue(false), "/" + name + " allowed for non-ops");
        }
        var adminNames = List.of("flyspeed", "fly", "speed", "gm", "gm0", "gm1", "gm2", "gm3", "heal", "feed", "god", "vanish", "invsee", "enderchest", "time", "weather", "tp", "tppos", "tphere", "tpall", "mvtp");
        for (String name : adminNames) {
            var command = Bukkit.getPluginCommand(name);
            check(command != null && command.getPlugin() == plugin, "/" + name + " owned by MandoMC");
            check(!Bukkit.getPluginManager().getPermission(command.getPermission()).getDefault().getValue(false), "/" + name + " denies non-ops");
        }
        for (String alias : List.of("gms", "gmc", "gma", "gmsp", "gamemode", "teleport", "ec", "tpyes", "tpno", "return", "travel")) {
            check(Bukkit.getPluginCommand(alias).getPlugin() == plugin, "/" + alias + " alias resolves correctly");
        }
        var earth = plugin.warps().destination("earth");
        var a = actor("UtilityA", earth); var b = actor("UtilityB", earth.clone().add(1,0,0)); var c = actor("UtilityC", earth.clone().add(0,0,1));
        var personal = plugin.personal();
        personal.set(a.player, "base");
        check(personal.home(a.player, "base").equals(SavedLocation.of(earth)), "home stores exact location");
        personal.set(a.player, "second"); personal.set(a.player, "third");
        denied(() -> personal.set(a.player, "fourth"), "non-op home limit enforced");
        personal.set(a.player, "base"); check(personal.names(a.player).size() == 3, "overwriting a home does not spend another slot");
        var saved = a.data.serializeToBytes(); a.data.readFromBytes(saved);
        check(personal.home(a.player, "base").resolve().equals(earth), "homes survive native PDC serialization");
        personal.delete(a.player, "second"); denied(() -> personal.home(a.player, "second"), "deleted home unavailable");
        var cancelled = new PlayerTeleportEvent(a.player, earth, hoth, PlayerTeleportEvent.TeleportCause.COMMAND);
        cancelled.setCancelled(true); personal.teleport(cancelled); denied(() -> personal.back(a.player), "cancelled teleport does not create back history");
        personal.teleport(new PlayerTeleportEvent(a.player, earth, hoth, PlayerTeleportEvent.TeleportCause.COMMAND));
        check(personal.back(a.player).equals(SavedLocation.of(earth)), "successful teleport records departure");
        personal.death(new org.bukkit.event.entity.PlayerDeathEvent(a.player, org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(), new ArrayList<>(), 0, Component.empty(), false));
        check(personal.back(a.player).equals(SavedLocation.of(earth)), "death records return location");
        var language = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(plugin.getResource("language/messages.yml")));
        var config = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(plugin.getResource("travel/teleport.yml")));
        config.set("effects.enabled", false);
        travel = new TeleportService(this, config, language, plugin.combat());
        var utilityConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(plugin.getResource("admin/utilities.yml")));
        var clock = new AtomicLong(1_000_000_000L);
        var directory = new HashMap<UUID, Player>(); for (var actor : actors) directory.put(actor.id, actor.player);
        requests = new TeleportRequests(this, travel, plugin.combat(), new CommandMessages(language), utilityConfig, directory::get, clock::get);
        check(!requests.send(a.player, a.player, false), "self teleport requests rejected");
        check(requests.send(a.player, b.player, false), "ordinary player can send tpa");
        check(!requests.send(a.player, c.player, false), "one outgoing request per player");
        check(requests.send(c.player, b.player, true), "several senders can request same recipient");
        denied(() -> requests.accept(b.player, null), "ambiguous acceptance requires sender name");
        requests.deny(b.player, c.name); check(requests.incoming(b.player).size() == 1, "deny removes only selected request");
        check(requests.accept(b.player, a.name), "accept starts shared warmup");
        check(a.teleports == 0, "accept never teleports immediately");
        tick(59); check(a.teleports == 0, "warmup waits full sixty ticks");
        tick(1); check(a.teleports == 1 && a.location.equals(b.location), "tpa completes exactly once at recipient");
        denied(() -> requests.accept(b.player, a.name), "accepted request cannot be replayed");
        clock.addAndGet(6_000_000_000L);
        check(requests.send(a.player, b.player, true), "tpahere request created");
        check(requests.accept(b.player, a.name), "tpahere consent accepted");
        a.online = false; tick(60); a.online = true;
        check(b.teleports == 0, "anchor disconnect during warmup prevents teleport");
        clock.addAndGet(6_000_000_000L);
        requests.send(a.player, b.player, false); clock.addAndGet(61_000_000_000L);
        denied(() -> requests.accept(b.player, a.name), "expired requests cannot be accepted");
        check(requests.send(a.player, b.player, false), "new request after expiry accepted");
        requests.cancel(a.player); check(requests.incoming(b.player).isEmpty(), "outgoing cancellation clears recipient request");
        clock.addAndGet(6_000_000_000L);
        plugin.combat().tag(a.player);
        check(!requests.send(a.player, b.player, false), "combat blocks teleport requests");
        check(!plugin.admin().teleport(a.player, hoth), "combat blocks direct admin teleports too");
        plugin.combat().clear(a.player);
        check(travel.request(a.player, () -> hoth), "public trip starts out of combat");
        a.location.add(.1,0,0); tick(60); check(a.teleports == 1, "movement cancels shared warmup");
        check(travel.request(a.player, () -> hoth), "cancelled trip can be retried"); travel.cancelRequest(a.player); tick(60);
        check(a.teleports == 1, "tpcancel clears countdown");
        a.location = earth.clone();
        a.admin = true;
        cmd(a, "gm1"); check(a.mode == GameMode.CREATIVE, "creative shortcut executes");
        cmd(a, "gm3"); check(a.mode == GameMode.SPECTATOR, "spectator shortcut executes");
        cmd(a, "gm0"); check(a.mode == GameMode.SURVIVAL, "survival shortcut executes");
        cmd(a, "gm", "adventure"); check(a.mode == GameMode.ADVENTURE, "named game mode executes");
        cmd(a, "flyspeed", "4"); check(a.flySpeed == .4f, "flyspeed maps 4 to native .4");
        cmd(a, "flyspeed", "NaN"); check(a.flySpeed == .4f, "invalid speed leaves current speed intact");
        cmd(a, "speed", "walk", "1"); check(a.walkSpeed == .2f, "walk speed preserves vanilla default");
        cmd(a, "fly", "on"); check(a.flight && a.flying, "fly on enables actual flight");
        cmd(a, "fly", "off"); check(!a.flight && !a.flying, "fly off clears both flags");
        cmd(a, "god", "on"); check(a.invulnerable && plugin.admin().god(a.player), "god mode sets native and saved state");
        cmd(a, "god", "off"); check(!a.invulnerable && !plugin.admin().god(a.player), "god mode can be disabled");
        cmd(a, "vanish", "on"); check(plugin.admin().vanished(a.player), "vanish state persisted");
        cmd(a, "vanish", "off"); check(!plugin.admin().vanished(a.player), "vanish toggles off");
        a.health = 3; a.food = 2; cmd(a, "heal"); check(a.health == 20 && a.food == 20, "heal restores health and food");
        a.food = 1; cmd(a, "feed"); check(a.food == 20, "feed restores food");
        cmd(a, "tp", "~1", "~", "~"); check(a.location.getX() == earth.getX()+1, "admin tp supports relative coordinates");
        cmd(a, "mvtp", "Hoth"); check(a.location.equals(hoth), "mvtp reaches exact saved world spawn");
        a.admin = false; var before = a.mode; cmd(a, "gm1"); check(a.mode == before, "non-op cannot execute admin command");
        var arrival = new ArrivalListener(this, new ArrivalSettings("minecraft:spawnworld", "minecraft:overworld"),
                new ArrivalDestinations(new ArrivalSettings("minecraft:spawnworld", "minecraft:overworld"),
                new net.mandomc.integrations.regions.WorldGuardArrivalProtection(), plugin.worlds()::spawn), language);
        var first = new io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent(null, earth, true);
        arrival.firstSpawn(first); check(first.getSpawnLocation().equals(plugin.warps().destination("spawn")), "new players arrive at Batuu");
        a.played = true; var returning = new io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent(null, hoth, false);
        arrival.firstSpawn(returning); check(returning.getSpawnLocation().equals(hoth), "returning players keep their position");
        var death = new PlayerRespawnEvent(a.player, earth, false, false, false, PlayerRespawnEvent.RespawnReason.DEATH);
        arrival.respawn(death); check(death.getRespawnLocation().equals(plugin.warps().destination("spawn")), "bedless death returns to Batuu");
        var bed = new PlayerRespawnEvent(a.player, earth, true, false, false, PlayerRespawnEvent.RespawnReason.DEATH);
        arrival.respawn(bed); check(bed.getRespawnLocation().equals(earth), "valid bed keeps priority over Batuu");
        denied(() -> ArrivalDestinations.safeSpawn(earth.clone().add(0, 5, 0)), "unsupported player destination rejected");
        var configMissing = new YamlConfiguration(); configMissing.set("schema-version", 1);
        configMissing.set("worlds.missing.key", "minecraft:utility_missing_dimension");
        denied(() -> new net.mandomc.gameplay.worlds.WorldService(this, configMissing), "missing dimension fails without generating terrain");
        check(Bukkit.getWorld(NamespacedKey.minecraft("utility_missing_dimension")) == null, "missing dimension not generated");
        clock.addAndGet(6_000_000_000L);
        a.location = earth.clone(); b.location = earth.clone().add(1,0,0);
        check(requests.send(a.player, b.player, true) && requests.accept(b.player, a.name), "tpahere accepted with recipient as traveller");
        tick(60); check(b.teleports == 1 && b.location.equals(a.location), "tpahere moves recipient to sender");
        var visibility = AdminService.class.getDeclaredMethod("visibility", Player.class, Player.class); visibility.setAccessible(true);
        a.admin = true; b.admin = false; plugin.admin().vanish(a.player, true); visibility.invoke(plugin.admin(), b.player, a.player);
        check(b.hidden == 1, "vanish hides staff entity and tab entry from ordinary viewer");
        b.admin = true; visibility.invoke(plugin.admin(), b.player, a.player); check(b.shown == 1, "authorized staff can see vanished player");
        plugin.admin().vanish(a.player, false);
        inventory(a, b);
        a.mode = GameMode.CREATIVE; a.location = earth.clone(); int beforeCreative = a.teleports;
        check(travel.request(a.player, () -> hoth) && a.teleports == beforeCreative + 1, "Creative public teleport is immediate");
        check(a.location.equals(hoth), "instant Creative teleport uses exact destination");
        plugin.combat().tag(a.player);
        check(!travel.request(a.player, () -> earth) && a.teleports == beforeCreative + 1, "Creative bypass never bypasses combat");
        plugin.combat().clear(a.player);
        check(!travel.request(a.player, () -> { throw new IllegalStateException("missing"); }), "Creative still validates destination");
        a.mode = GameMode.SURVIVAL; travel.request(a.player, () -> earth); check(a.teleports == beforeCreative + 1, "Survival still starts a countdown");
        a.mode = GameMode.CREATIVE; tick(1); check(a.teleports == beforeCreative + 2, "switching into Creative completes pending trip promptly");


    }
    private void inventory(Actor viewer, Actor target) throws Exception {
        viewer.admin = true; target.storage[0] = new ItemStack(Material.DIAMOND, 7);
        target.storage[40] = new ItemStack(Material.EMERALD, 3);
        var field = AdminService.class.getDeclaredField("inventories"); field.setAccessible(true);
        var inspector = (net.mandomc.gameplay.admin.InventoryInspection) field.get(plugin.admin());
        plugin.admin().inspect(viewer.player, target.player, false);
        check(viewer.opened.getItem(40).getAmount() == 3, "inspection includes real offhand slot");
        check(viewer.opened.getItem(50).getType() == Material.GRAY_STAINED_GLASS_PANE, "inspection fills decorative slots");
        var view = (InventoryView) Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{InventoryView.class},(p,m,args)->{
            return switch(m.getName()) {
                case "getTopInventory" -> viewer.opened; case "getBottomInventory" -> viewer.inventory; case "getPlayer" -> viewer.player;
                case "getType" -> org.bukkit.event.inventory.InventoryType.CHEST; case "getCursor" -> viewer.cursor;
                case "getInventory" -> (int)args[0] < viewer.opened.getSize() ? viewer.opened : viewer.inventory;
                case "convertSlot" -> args[0]; case "getSlotType" -> org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER;
                default -> null;
            };
        });
        var click = new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        inspector.click(click);
        check(click.isCancelled() && target.storage[0] == null && viewer.cursor.getAmount() == 7, "inspection takes once from authoritative slot");
        var put = new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                1,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PLACE_ALL);
        inspector.click(put);
        check(target.storage[1].getAmount() == 7 && viewer.cursor.isEmpty(), "inspection places once without duplication");
        var shift = new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                1,org.bukkit.event.inventory.ClickType.SHIFT_LEFT,org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY);
        inspector.click(shift); check(shift.isCancelled() && target.storage[1].getAmount() == 7, "shift transfer cannot duplicate preview");
        inspector.close(new org.bukkit.event.inventory.InventoryCloseEvent(view));
        target.ender.setItem(0,new ItemStack(Material.GOLD_INGOT,4));
        plugin.admin().inspect(viewer.player,target.player,true);
        check(viewer.opened.getItem(0).getAmount() == 4, "ender chest inspection uses target storage");
        viewer.admin=false;
        var denied = new org.bukkit.event.inventory.InventoryClickEvent(view,org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                0,org.bukkit.event.inventory.ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
        inspector.click(denied); check(target.ender.getItem(0).getAmount()==4 && viewer.closed, "permission loss closes view without moving items");
        inspector.close(new org.bukkit.event.inventory.InventoryCloseEvent(view));
    }
    private void cmd(Actor actor, String name, String... args) { Bukkit.getPluginCommand(name).execute(actor.player, name, args); }
    private void tick(int count) throws Exception {
        var method = TeleportService.class.getDeclaredMethod("tick"); method.setAccessible(true);
        for (int i=0;i<count;i++) method.invoke(travel);
    }
    private Actor actor(String name, Location location) { var actor = new Actor(name,location); actors.add(actor); return actor; }
    private final class Actor {
        final UUID id = UUID.randomUUID(); final String name; Location location;
        boolean admin, online=true, played, flight, flying, invulnerable; int teleports, food=20;
        int hidden, shown; boolean closed; ItemStack cursor = new ItemStack(Material.AIR); Inventory opened;
        final ItemStack[] storage = new ItemStack[41]; final Inventory ender = Bukkit.createInventory(null,27); PlayerInventory inventory;
        double health=20; float flySpeed=.1f, walkSpeed=.2f; GameMode mode=GameMode.SURVIVAL;
        final PersistentDataContainer data = new ItemStack(Material.STONE).getItemMeta().getPersistentDataContainer();
        org.bukkit.scoreboard.Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        final Player player;
        Actor(String name, Location start) {
            this.name=name;location=start.clone();
            inventory = (PlayerInventory)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{PlayerInventory.class},(p,m,args)->{
                return switch(m.getName()) {
                    case "getSize" -> 41; case "getItem" -> storage[(int)args[0]];
                    case "setItem" -> { storage[(int)args[0]] = (ItemStack)args[1]; yield null; }
                    default -> null;
                };
            });
            player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(proxy,m,args)->{
                switch(m.getName()) {
                    case "hidePlayer":hidden++;return null;case "showPlayer":shown++;return null;
                    case "getInventory":return inventory;case "getEnderChest":return ender;
                    case "getItemOnCursor":return cursor;case "setItemOnCursor":cursor=(ItemStack)args[0];return null;
                    case "openInventory":opened=(Inventory)args[0];closed=false;return null;case "closeInventory":closed=true;return null;
                    case "getUniqueId":return id;case "getName":return name;case "getLocation":return location.clone();case "getWorld":return location.getWorld();
                    case "isOnline":return online;case "isDead":return false;case "hasPlayedBefore":return played;case "canSee":return true;
                    case "hasPermission":return args[0].toString().startsWith("mandomc.travel.") && !args[0].toString().endsWith("unlimited") || admin;
                    case "isOp":return admin;case "getPersistentDataContainer":return data;case "getPassengers":return List.of();
                    case "getScoreboard":return scoreboard;case "setScoreboard":scoreboard=(org.bukkit.scoreboard.Scoreboard)args[0];return null;
                    case "getGameMode":return mode;case "setGameMode":mode=(GameMode)args[0];return null;
                    case "setFlySpeed":flySpeed=(float)args[0];return null;case "setWalkSpeed":walkSpeed=(float)args[0];return null;
                    case "getFlySpeed":return flySpeed;case "getWalkSpeed":return walkSpeed;
                    case "getAllowFlight":return flight;case "setAllowFlight":flight=(boolean)args[0];return null;
                    case "isFlying":return flying;case "setFlying":flying=(boolean)args[0];return null;
                    case "setInvulnerable":invulnerable=(boolean)args[0];return null;
                    case "setHealth":health=(double)args[0];return null;case "getHealth":return health;case "setFoodLevel":food=(int)args[0];return null;
                    case "getAttribute":return Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{AttributeInstance.class},(p,method,arguments)->method.getName().equals("getValue")?20d:null);
                    case "getActivePotionEffects":return List.of();
                    case "teleport":location=((Location)args[0]).clone();teleports++;return true;
                    case "getEffectivePermissions":return Set.of();case "equals":return proxy==args[0];case "hashCode":return id.hashCode();case "toString":return name;
                    default:if(m.getReturnType()==boolean.class)return false;if(m.getReturnType()==int.class)return 0;if(m.getReturnType()==double.class)return 0d;if(m.getReturnType()==float.class)return 0f;return null;
                }
            });
        }
    }
}
