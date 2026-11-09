package net.mandomc.verification;

import java.util.*;
import java.lang.reflect.Proxy;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.combat.CombatService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.damage.*;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.core.compatibility.HitBox;

/** Isolated native players and temporary entities; never accesses a real player's inventory. */
public final class CombatProbe extends JavaPlugin {
    private int checks;
    private MandoPlugin plugin;
    private CombatService combat;
    private World world;
    private Location point;
    private final List<Player> players = new ArrayList<>();
    private final List<Entity> entities = new ArrayList<>();
    private final Listener observer = new Listener() {};
    private Set<UUID> beforeDrops;
    private Player timed;
    private org.bukkit.scoreboard.Scoreboard previous;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); } catch (Throwable error) { fail(error); }
        }, 60);
    }
    private void verify() throws Exception {
        plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        combat = plugin.combat();
        check(combat != null && plugin.rules() != null && plugin.starterKit() != null, "all combat basics initialized");
        check(Bukkit.getWorlds().stream().allMatch(w -> w.getEnvironment() == World.Environment.NORMAL), "Nether and End are not loaded");
        world = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        point = new Location(world, -170, 300, -170, 0, 0);
        point.getChunk().addPluginChunkTicket(this);
        beforeDrops = new HashSet<>();
        world.getEntitiesByClass(Item.class).forEach(item -> beforeDrops.add(item.getUniqueId()));
        verifyKit();
        verifyRules();
        verifyLogout();
        verifyCombat();
        timed = player(point);
        previous = Bukkit.getScoreboardManager().getNewScoreboard();
        var team = previous.registerNewTeam("probe_team");team.addEntry(timed.getName());team.setAllowFriendlyFire(false);
        timed.setScoreboard(previous);
        plugin.hud().attach(timed);
        combat.tag(timed);
        check(!timed.getScoreboard().getTeam("probe_team").allowFriendlyFire(), "combat sidebar preserves team friendly-fire policy");
        team.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        check(combat.remainingTenths(timed) == 150, "new combat tag starts at 15.0 seconds");
        check(timed.getScoreboard().getObjective("mandomc_hud") != null, "combat retains full sidebar");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                check(timed.getScoreboard().getTeam("probe_team").getOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE)
                        == org.bukkit.scoreboard.Team.OptionStatus.NEVER, "active scoreboard follows original team policy updates");
                int left = combat.remainingTenths(timed);
                check(left < 150 && left > 130, "timer decreases with elapsed real time");
                check(text(timed.getScoreboard().getObjective("mandomc_hud").getScore("mmc_line_8").customName()).matches("⚔ ɪɴ ᴄᴏᴍʙᴀᴛ: [0-9]+\\.[0-9]s"), "sidebar updates a single decimal timer line");
            } catch (Throwable error) { fail(error); }
        }, 20);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                check(!combat.tagged(timed), "tag expires after fifteen seconds");
                check(timed.getScoreboard().getObjective("mandomc_hud") != null, "expiry preserves full sidebar");
                check(text(timed.getScoreboard().getObjective("mandomc_hud").getScore("mmc_line_8").customName()).contains("ᴋɪʟʟѕ"), "expiry restores kills row");
                var safeQuit = player(point);
                combat.quit(new PlayerQuitEvent(safeQuit, Component.empty()));
                check(!safeQuit.isDead(), "safe logout does not kill");
                var closing = player(point);
                closing.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 3));
                combat.tag(closing);
                combat.close();
                combat.quit(new PlayerQuitEvent(closing, Component.empty()));
                check(!closing.isDead() && closing.getInventory().getItem(0).getAmount() == 3, "disabled service preserves gear on shutdown");
                getLogger().info("COMBAT PROBE PASSED: " + checks + " assertions");
                cleanup();
            } catch (Throwable error) { fail(error); }
        }, 325);
    }
    private void verifyKit() throws Exception {
        Player fresh = player(point);
        fresh.getInventory().setItem(20, new ItemStack(Material.DIRT, 7));
        check(plugin.starterKit().grant(fresh), "complete starter kit grants");
        check(fresh.getInventory().getItem(20).getAmount() == 7, "kit preserves existing inventory");
        for (Material type : List.of(Material.STONE_PICKAXE, Material.STONE_AXE, Material.STONE_SHOVEL, Material.STONE_HOE))
            check(count(fresh, item -> item.getType() == type) == 1, "kit gives one " + type);
        check(count(fresh, item -> item.getType() == Material.COOKED_BEEF) == 64, "kit gives 64 steak");
        for (String id : List.of("robes_helmet", "robes_chestplate", "robes_leggings", "robes_boots"))
            check(count(fresh, item -> id.equals(plugin.items().identify(item))) == 1, "kit gives " + id);
        check(count(fresh, item -> "E11".equals(WeaponMechanicsAPI.getWeaponTitle(item))) == 1, "kit gives vendor E11");
        ItemStack ammo = plugin.items().create("standard_plasma_cells");
        check(count(fresh, item -> item.isSimilar(ammo)) == 64, "kit gives 64 correct E11 ammo");
        check(!plugin.starterKit().grant(fresh), "repeated grant refused");
        fresh.getInventory().clear();
        check(!plugin.starterKit().grant(fresh), "losing all gear does not reset kit marker");
        Player full = player(point);
        for (int i=0;i<36;i++) full.getInventory().setItem(i, new ItemStack(Material.DIRT, 64));
        check(!plugin.starterKit().grant(full), "full inventory refuses whole kit");
        check(count(full, item -> item.getType() == Material.DIRT) == 2304, "refused delivery changes no inventory");
        full.getInventory().clear();
        check(plugin.starterKit().grant(full), "failed delivery never marks kit granted");
        Player existing = player(point);
        Player played = (Player) Proxy.newProxyInstance(getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
            if (method.getName().equals("hasPlayedBefore")) return true;
            return method.invoke(existing, args);
        });
        plugin.starterKit().join(new PlayerJoinEvent(played, Component.empty()));
        check(!existing.getPersistentDataContainer().has(new NamespacedKey(plugin, "starter_kit_pending_v1")), "existing players are not backfilled on join");
        Player newcomer = player(point);
        Player online = (Player) Proxy.newProxyInstance(getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
            if (method.getName().equals("isOnline")) return true;
            return method.invoke(newcomer, args);
        });
        plugin.starterKit().join(new PlayerJoinEvent(online, Component.empty()));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                check(count(newcomer, item -> item.isSimilar(ammo)) == 64, "first-join event delivers 64 ammo after join");
                check(newcomer.getPersistentDataContainer().has(new NamespacedKey(plugin,"starter_kit_v1")), "join persists once-only kit marker");
                check(!newcomer.getPersistentDataContainer().has(new NamespacedKey(plugin,"starter_kit_pending_v1")), "completed join removes pending delivery marker");
            } catch (Throwable error) {fail(error);}
        }, 3);
    }
    private void verifyRules() throws Exception {
        var rules = plugin.rules();
        for (Material type : List.of(Material.ELYTRA, Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION,
                Material.DIAMOND_SWORD, Material.BOW, Material.CROSSBOW, Material.SHIELD))
            check(rules.banned(new ItemStack(type)), "banned use: " + type);
        check(!rules.banned(plugin.items().create("vader")), "custom saber exempt from shield restriction");
        check(!rules.banned(plugin.items().create("e11")), "E11 remains usable");
        check(!rules.banned(new ItemStack(Material.STONE_AXE)), "ordinary tools remain usable");
        Player actor = player(point);
        var glide = new EntityToggleGlideEvent(actor, true); rules.glide(glide);
        check(glide.isCancelled(), "elytra glide denied");
        var drink = new PlayerItemConsumeEvent(actor, new ItemStack(Material.POTION)); rules.consume(drink);
        check(drink.isCancelled(), "potion drinking denied");
        var effect = new PotionEffect(PotionEffectType.SPEED, 100, 0);
        var potion = new EntityPotionEffectEvent(actor, null, effect, EntityPotionEffectEvent.Cause.POTION_SPLASH, EntityPotionEffectEvent.Action.ADDED, false);
        rules.effects(potion); check(potion.isCancelled(), "splash potion effects denied");
        var skill = new EntityPotionEffectEvent(actor, null, effect, EntityPotionEffectEvent.Cause.PLUGIN, EntityPotionEffectEvent.Action.ADDED, false);
        rules.effects(skill); check(!skill.isCancelled(), "plugin ability effects are retained");
        var portal = new org.bukkit.event.world.PortalCreateEvent(List.of(), world, actor, org.bukkit.event.world.PortalCreateEvent.CreateReason.FIRE);
        rules.portal(portal); check(portal.isCancelled(), "Nether portal formation denied");
        var end = new PlayerTeleportEvent(actor, point, point.clone().add(1,0,0), PlayerTeleportEvent.TeleportCause.END_PORTAL);
        rules.teleport(end); check(end.isCancelled(), "End portal use denied");
        var regular = new PlayerTeleportEvent(actor, point, point.clone().add(1,0,0), PlayerTeleportEvent.TeleportCause.PLUGIN);
        rules.teleport(regular); check(!regular.isCancelled(), "ordinary world travel retained");
        verifyRepairs(actor);
    }
    private void verifyRepairs(Player actor) {
        var view = MenuType.ANVIL.create(actor, Component.text("Repair test"));
        var inv = view.getTopInventory();
        var robe = plugin.items().create("robes_chestplate");
        var meta = (Damageable) robe.getItemMeta();meta.setDamage(50);robe.setItemMeta(meta);
        inv.setItem(0, robe);inv.setItem(1, new ItemStack(Material.LEATHER));
        var result = robe.clone();meta = (Damageable) result.getItemMeta();meta.setDamage(10);result.setItemMeta(meta);
        var repair = new PrepareAnvilEvent(view, result);plugin.rules().anvil(repair);
        check(repair.getResult() != null && "robes_chestplate".equals(plugin.items().identify(repair.getResult())), "anvil repair preserves custom robe identity");
        var book = new ItemStack(Material.ENCHANTED_BOOK);var bookMeta = (EnchantmentStorageMeta) book.getItemMeta();
        bookMeta.addStoredEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 1, true);book.setItemMeta(bookMeta);
        inv.setItem(1, book);var enchant = new PrepareAnvilEvent(view, result);plugin.rules().anvil(enchant);
        check(enchant.getResult() == null, "anvil enchant books denied");
        inv.setItem(1, robe);var enchanted = result.clone();enchanted.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1);
        enchant = new PrepareAnvilEvent(view, enchanted);plugin.rules().anvil(enchant);
        check(enchant.getResult() == null, "anvil enchant transfer denied");
        var craft = (CraftingInventory) actor.getOpenInventory().getTopInventory();
        craft.setMatrix(new ItemStack[] {robe, robe.clone(), null, null});craft.setResult(result);
        plugin.rules().craft(new PrepareItemCraftEvent(craft, actor.getOpenInventory(), true));
        check("robes_chestplate".equals(plugin.items().identify(craft.getResult())) && ((Damageable) craft.getResult().getItemMeta()).getDamage() == 10, "craft repair keeps custom identity and repaired wear");
        craft.setMatrix(new ItemStack[] {robe, new ItemStack(Material.LEATHER_CHESTPLATE), null, null});craft.setResult(result);
        plugin.rules().craft(new PrepareItemCraftEvent(craft, actor.getOpenInventory(), true));
        check(craft.getResult() == null, "mixed custom and vanilla craft repair denied");
        craft.clear();
    }
    private void verifyCombat() throws Exception {
        Player a = player(point), b = player(point.clone().add(0,0,3));
        Cow mob = world.spawn(point.clone().add(2,0,0), Cow.class, c -> {c.setAI(false);c.setGravity(false);c.setPersistent(false);}); entities.add(mob);
        var cancelled = hit(mob, a, 2); cancelled.setCancelled(true); Bukkit.getPluginManager().callEvent(cancelled);
        check(!combat.tagged(a), "cancelled hit creates no combat tag");
        Bukkit.getPluginManager().callEvent(hit(mob, a, 0));check(!combat.tagged(a), "zero damage creates no combat tag");
        Bukkit.getPluginManager().callEvent(hit(mob, a, 2));check(combat.tagged(a), "mob hit tags player");combat.clear(a);
        Bukkit.getPluginManager().callEvent(hit(a, mob, 2));check(combat.tagged(a), "attacking mob tags player");combat.clear(a);
        Bukkit.getPluginManager().callEvent(hit(a,b,2));check(combat.tagged(a) && combat.tagged(b), "PvP tags both participants");
        var teleport = new PlayerTeleportEvent(a, point, point.clone().add(4,0,0), PlayerTeleportEvent.TeleportCause.COMMAND);
        Bukkit.getPluginManager().callEvent(teleport);check(teleport.isCancelled(), "tag blocks external command teleports");
        var travelField = MandoPlugin.class.getDeclaredField("travel");travelField.setAccessible(true);
        var travel = (net.mandomc.gameplay.travel.TeleportService) travelField.get(plugin);
        check(!travel.request(a, () -> point), "MandoMC travel shares combat tag");
        combat.clear(a);combat.clear(b);
        var arrow = world.spawn(point.clone().add(0,1,0), Arrow.class);entities.add(arrow);arrow.setShooter(mob);
        Bukkit.getPluginManager().callEvent(hit(arrow,a,2));check(combat.tagged(a), "mob projectile tags its victim");combat.clear(a);
        var weapon = plugin.items().create("e11");
        var definition = WeaponMechanics.getInstance().getWeaponConfigurations().getObject("E11.Projectile", Projectile.class);
        var origin = a.getLocation().add(0,.5,0);
        var direction = b.getLocation().add(0,.5,0).toVector().subtract(origin.toVector()).normalize();
        var bolt = definition.create(a, origin, direction, weapon, "E11", EquipmentSlot.HAND);
        try {
            double health = b.getHealth();
            var box = HitBox.getHitbox(b.getBoundingBox());
            box.setLivingEntity(b);
            var collision = box.rayTrace(origin.toVector(), direction);
            check(collision != null, "E11 intersects native player hitbox");
            WeaponMechanics.getInstance().getWeaponHandler().getHitHandler().handleHit(collision, bolt);
            check(b.getHealth() < health, "E11 applies real player damage");
            check(combat.tagged(a) && combat.tagged(b), "WeaponMechanics damage tags both players");
        } finally { bolt.remove();combat.clear(a);combat.clear(b); }
        Player safe = player(world.getSpawnLocation());
        safe.damage(2, mob);
        check(!combat.tagged(safe), "safe-zone damage denial creates no tag");
    }
    private EntityDamageByEntityEvent hit(Entity source, Entity target, double amount) {
        return new EntityDamageByEntityEvent(source,target,EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(source).withDirectEntity(source).build(),amount);
    }
    private void verifyLogout() throws Exception {
        Player owner = player(point.clone().add(5,0,5));
        owner.getInventory().setItem(0, plugin.items().create("vader"));
        owner.getInventory().setHelmet(plugin.items().create("robes_helmet"));
        owner.getInventory().setItemInOffHand(new ItemStack(Material.DIAMOND, 9));
        owner.getInventory().setItem(7, new ItemStack(Material.COOKED_BEEF, 64));
        check(plugin.sabers().launch(owner), "saber launches before logout test");
        check(owner.getInventory().getItemInMainHand().isEmpty(), "airborne saber leaves hand empty");
        List<ItemStack> drops = new ArrayList<>();int[] deaths = {0};
        Bukkit.getPluginManager().registerEvent(PlayerDeathEvent.class, observer, EventPriority.LOWEST, (listener,event) -> {
            var death = (PlayerDeathEvent) event;
            if (death.getEntity() == owner) death.setKeepInventory(true);
        },this);
        Bukkit.getPluginManager().registerEvent(PlayerDeathEvent.class, observer, EventPriority.MONITOR, (listener,event) -> {
            var death = (PlayerDeathEvent) event;
            if (death.getEntity() == owner) { deaths[0]++;death.getDrops().forEach(item -> drops.add(item.clone())); }
        },this);
        combat.tag(owner);
        combat.quit(new PlayerQuitEvent(owner, Component.empty()));
        check(owner.isDead() && deaths[0] == 1, "tagged logout causes exactly one real death");
        check(!plugin.sabers().busy(owner) && !owner.getPersistentDataContainer().has(new NamespacedKey(plugin,"saber_flight_item")), "logout clears airborne saber ownership");
        check(drops.stream().filter(plugin.sabers()::isSaber).mapToInt(ItemStack::getAmount).sum() == 1, "airborne saber drops exactly once");
        check(drops.stream().filter(item -> item.getType() == Material.DIAMOND).mapToInt(ItemStack::getAmount).sum() == 9, "offhand stack drops");
        check(drops.stream().filter(item -> "robes_helmet".equals(plugin.items().identify(item))).count() == 1, "armor drops");
        check(drops.stream().filter(item -> item.getType() == Material.COOKED_BEEF).mapToInt(ItemStack::getAmount).sum() == 64, "storage stack drops");
        check(Arrays.stream(owner.getInventory().getContents()).allMatch(item -> item == null || item.isEmpty()), "logout inventory cleared even if keepInventory was set");
        check(!combat.tagged(owner), "death clears combat state");
        var actual = world.getEntitiesByClass(Item.class).stream().filter(item -> !beforeDrops.contains(item.getUniqueId())).toList();
        check(actual.stream().filter(item -> plugin.sabers().isSaber(item.getItemStack())).count() == 1, "world contains exactly one dropped saber");
        actual.forEach(Entity::remove);
        HandlerList.unregisterAll(observer);
    }
    private Player player(Location location) throws Exception { var p = nativeTestPlayer(location.getWorld(), location); players.add(p);return p; }
    private int count(Player player, java.util.function.Predicate<ItemStack> predicate) { return Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(predicate).mapToInt(ItemStack::getAmount).sum(); }
    private String text(Component value) { return PlainTextComponentSerializer.plainText().serialize(value); }
    private void check(boolean result, String description) { if (!result) throw new IllegalStateException(description);checks++;getLogger().info("PASS " + description); }
    private void fail(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"COMBAT PROBE FAILED",error);Bukkit.getScheduler().cancelTasks(this);cleanup(); }
    private void cleanup() {
        HandlerList.unregisterAll(observer);
        for (Player player : players) {if (combat != null) combat.clear(player); if (plugin != null && plugin.hud() != null) plugin.hud().detach(player);if (plugin != null) plugin.sabers().finish(player.getUniqueId());}
        entities.forEach(Entity::remove);
        if (world != null && beforeDrops != null) world.getEntitiesByClass(Item.class).stream().filter(item -> !beforeDrops.contains(item.getUniqueId()) && item.getLocation().distanceSquared(point) < 400).forEach(Entity::remove);
        if (point != null) point.getChunk().removePluginChunkTicket(this);
    }
    @Override public void onDisable() { cleanup(); }
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "CombatTest");
        Object handle = playerClass.getConstructor(serverClass, levelClass, profileClass, infoClass)
                .newInstance(server, level, profile, infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object flow = flowClass.getField("SERVERBOUND").get(null);
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Object connection = connectionClass.getConstructor(flowClass).newInstance(flow);
        Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie = cookieClass.getMethod("createInitial", profileClass, boolean.class).invoke(null, profile, false);
        Class<?> listenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object packetListener = listenerClass.getConstructor(serverClass, connectionClass, playerClass, cookieClass)
                .newInstance(server, connection, handle, cookie);
        playerClass.getField("connection").set(handle, packetListener);
        // Isolated players have no network client to send its loaded acknowledgement.
        var loadTimer = listenerClass.getDeclaredField("clientLoadedTimeoutTimer");
        loadTimer.setAccessible(true);loadTimer.setInt(packetListener, 0);
        var waiting = listenerClass.getDeclaredField("waitingForRespawn");
        waiting.setAccessible(true);waiting.setBoolean(packetListener, false);
        playerClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }

}
