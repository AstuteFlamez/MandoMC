package net.mandomc.gameplay.tutorial;

import java.util.*;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.RecipeGuide;
import net.mandomc.gameplay.ui.MenuItems;
import net.mandomc.gameplay.vehicles.VehicleModels;
import net.mandomc.integrations.mobs.MythicMobAccess;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import static net.mandomc.gameplay.tutorial.TutorialProgress.Step;

/** Owns the per-player lesson. Normal clans, shops, inventory and vehicle ownership are never mutated. */
public final class InteractiveTutorial implements Listener, AutoCloseable {
    private static final NamespacedKey ELIGIBLE = NamespacedKey.fromString("mandomc:interactive_tutorial_eligible");
    private final Set<UUID> requiredPacks = new HashSet<>();
    private final Map<UUID, Set<UUID>> loadedPacks = new HashMap<>();
    private final MandoPlugin plugin;
    private final YamlConfiguration config;
    private final TutorialWorlds pool;
    private final MythicMobAccess mobs;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, TutorialWorlds.Lease> waiting = new HashMap<>();
    private final Map<UUID, BossBar> waitBars = new HashMap<>();
    private final Set<UUID> internal = new HashSet<>(), ending = new HashSet<>(), paying = new HashSet<>(), packReady = new HashSet<>();
    private final BukkitTask ticker;
    private boolean closed;
    private long lastTick = System.nanoTime();
    private static final class Session {
        final Player player;
        final TutorialWorlds.Lease lease;
        final World world;
        final TutorialProgress progress = new TutorialProgress();
        final Set<UUID> guards = new HashSet<>();
        final List<Entity> entities = new ArrayList<>();
        final BossBar bar;
        UUID vehicle, target, brown, gray;
        TextDisplay hologram;
        String clan;
        int claimX, claimZ, eventPage;
        long pageAt, messageUntil;
        Component message;
        boolean recipeSeen, transitioning;
        int wallet = 100;
        Session(Player player, TutorialWorlds.Lease lease, World world, Component title) {
            this.player = player; this.lease = lease; this.world = world;
            bar = BossBar.bossBar(title, 0, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        }
    }
    private enum MenuKind { WARP, BUY, SELL }
    private static final class Menu implements InventoryHolder {
        final UUID owner;
        final MenuKind kind;
        final Inventory inventory;
        Menu(Player player, MenuKind kind, Component title) {
            this.owner = player.getUniqueId(); this.kind = kind;
            inventory = Bukkit.createInventory(this, kind == MenuKind.WARP ? 27 : 36, title);
            MenuItems.fill(inventory);
            if (kind == MenuKind.SELL) for (int i = 0; i < 27; i++) inventory.setItem(i, null);
        }
        @Override public Inventory getInventory() { return inventory; }
    }
    public InteractiveTutorial(MandoPlugin plugin, YamlConfiguration config) throws Exception {
        this.plugin = plugin; this.config = config;
        if (config.getInt("schema-version") != 1) throw new IllegalArgumentException("tutorial/tutorial.yml: schema 1 required");
        if (config.getInt("guards") != 3 || config.getInt("flight-seconds") < 1 || config.getInt("flight-seconds") > 300
                || config.getInt("event-page-seconds") < 3 || config.getInt("event-page-seconds") > 120
                || config.getLong("reward-credits") < 1 || config.getLong("reward-credits") > 1_000_000)
            throw new IllegalArgumentException("tutorial/tutorial.yml: invalid lesson parameters");
        for (var step : Step.values()) if (step != Step.COMPLETE && !config.isString("steps." + step.name().toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Missing tutorial instruction " + step);
        for (var id : List.of("blaster", "ammo", "saber", "junk", "vehicle-ammo"))
            if (!plugin.items().ids().contains(config.getString("items." + id))) throw new IllegalArgumentException("Unknown tutorial item " + id);
        var saberConfig = YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), "combat/sabers.yml"));
        for (String path : List.of("main-pack-id", "id", "base-pack.id"))
            requiredPacks.add(UUID.fromString(saberConfig.getString("upstream-pack." + path)));
        mobs = new MythicMobAccess();
        pool = new TutorialWorlds(plugin, config.getInt("warm-worlds"), config.getInt("maximum-worlds"));
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var command = Objects.requireNonNull(plugin.getCommand("tutorial"));
        command.setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player player)) { sender.sendMessage(text("messages.players-only")); return true; }
            if (args.length == 1 && args[0].equalsIgnoreCase("end")) end(player, false);
            else if (args.length == 0) request(player);
            else player.sendActionBar(text("messages.usage"));
            return true;
        });
        command.setTabCompleter((sender, cmd, label, args) -> args.length == 1 && "end".startsWith(args[0]) ? List.of("end") : List.of());
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }
    private Component text(String key, String... values) {
        var tags = new ArrayList<net.kyori.adventure.text.minimessage.tag.resolver.TagResolver>();
        for (int i = 0; i < values.length; i += 2) tags.add(Placeholder.unparsed(values[i], values[i + 1]));
        return MiniMessage.miniMessage().deserialize(Objects.requireNonNull(config.getString(key), key),
                tags.toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new)).decoration(TextDecoration.ITALIC, false);
    }
    private Location at(Session session, double x, double y, double z, float yaw) { return new Location(session.world, x, y, z, yaw, 0); }
    private void hint(Player player, String key) {
        var message = text("messages." + key);
        var session = sessions.get(player.getUniqueId());
        if (session != null) { session.message = message; session.messageUntil = System.currentTimeMillis() + 2000; }
        player.sendActionBar(message);
    }
    public void denied(Player player) { hint(player, "denied"); }
    public boolean lockDismount(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.vehicle != null && !internal.contains(player.getUniqueId());
    }
    private boolean teleport(Player player, Location location) {
        internal.add(player.getUniqueId());
        try { player.setFallDistance(0); return plugin.combat().correctMovement(player, location); }
        finally { internal.remove(player.getUniqueId()); }
    }
    private boolean busy(Player player) {
        if (net.mandomc.integrations.weapons.WeaponMechanicsItems.reloading(player)
                || plugin.combat().tagged(player) || player.isInsideVehicle() || plugin.vehicles().mountedType(player) != null
                || player.isDead() || net.mandomc.gameplay.ilum.ParkourInventory.stored(player)
                || plugin.credits().auctions().pending(player)) return true;
        for (String key : List.of("crate_pending", "auction_escrow_v1", "jawa_sell_custody_v1"))
            if (player.getPersistentDataContainer().has(new NamespacedKey("mandomc", key))) return true;
        return player.getPersistentDataContainer().has(net.mandomc.gameplay.crates.CrateService.PENDING);
    }
    private boolean inventoryBusy(Player player) {
        if (!player.getItemOnCursor().isEmpty()) return true;
        return player.getOpenInventory().getTopInventory() instanceof CraftingInventory crafting
                && Arrays.stream(crafting.getMatrix()).anyMatch(item -> item != null && !item.isEmpty());
    }
    public void request(Player player) {
        if (closed) return;
        if (TutorialInventory.active(player)) { if (!sessions.containsKey(player.getUniqueId())) recover(player); else hint(player, "already"); return; }
        if (waiting.containsKey(player.getUniqueId())) { hint(player, "loading"); return; }
        if (busy(player)) { hint(player, "unsafe"); return; }
        if (!packReady.contains(player.getUniqueId())) { hint(player, "pack"); return; }
        if (inventoryBusy(player)) { hint(player, "cursor"); return; }
        player.closeInventory();
        if (!player.getItemOnCursor().isEmpty()) { hint(player, "cursor"); return; }
        var lease = pool.acquire();
        if (lease == null) { hint(player, "full"); return; }
        waiting.put(player.getUniqueId(), lease);
        var bar = BossBar.bossBar(text("messages.loading-bar"), 0, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
        waitBars.put(player.getUniqueId(), bar); player.showBossBar(bar); hint(player, "loading");
        lease.ready().whenComplete((world, error) -> {
            if (!plugin.isEnabled() || closed) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (waiting.get(player.getUniqueId()) != lease) { pool.release(lease); return; }
                cancelWaiting(player, false);
                if (error != null) { pool.release(lease); fail(player, error); return; }
                if (!player.isOnline() || busy(player)) { pool.release(lease); if (player.isOnline()) hint(player, "unsafe"); return; }
                begin(player, lease, world);
            });
        });
    }
    private void cancelWaiting(Player player, boolean release) {
        var lease = waiting.remove(player.getUniqueId());
        var bar = waitBars.remove(player.getUniqueId()); if (bar != null) player.hideBossBar(bar);
        if (release && lease != null) pool.release(lease);
    }
    private void begin(Player player, TutorialWorlds.Lease lease, World world) {
        if (inventoryBusy(player)) { pool.release(lease); hint(player, "cursor"); return; }
        try {
            player.closeInventory();
            net.mandomc.integrations.weapons.WeaponMechanicsItems.stopActions(player);
            plugin.sabers().finish(player.getUniqueId());
            TutorialInventory.capture(player);
            var session = new Session(player, lease, world, text("messages.bossbar"));
            sessions.put(player.getUniqueId(), session);
            player.getInventory().clear(); player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false); player.setInvulnerable(true); player.setFoodLevel(20); player.setSaturation(20);
            player.setHealth(player.getMaxHealth()); player.setFireTicks(0); player.setWalkSpeed(.2f);
            for (var effect : List.copyOf(player.getActivePotionEffects())) player.removePotionEffect(effect.getType());
            player.showBossBar(session.bar);
            if (!teleport(player, at(session, -24.5, 65, .5, -90))) throw new IllegalStateException("Tutorial arrival was cancelled");
            enter(session);
        } catch (RuntimeException error) { fail(player, error); if (TutorialInventory.active(player)) end(player, false); else pool.release(lease); }
    }
    private void fail(Player player, Throwable error) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Tutorial failed for " + player.getUniqueId(), error);
        hint(player, "error");
    }
    private void advance(Session session, Step step) { if (session.progress.advance(step)) enter(session); }
    private void later(Session session, Step expected, long ticks, Runnable action) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!closed && sessions.get(session.player.getUniqueId()) == session && session.progress.step() == expected) {
                try { action.run(); } catch (RuntimeException error) { fail(session.player, error); end(session.player, false); }
            }
        }, ticks);
    }
    private void clearItems(Session session) { plugin.sabers().finish(session.player.getUniqueId()); session.player.getInventory().clear(); }
    private void give(Player player, String key, int amount, int slot) {
        var item = plugin.items().create(config.getString("items." + key));
        if (item == null || item.isEmpty()) throw new IllegalStateException("Missing tutorial item " + key);
        item.setAmount(amount); player.getInventory().setItem(slot, item);
    }
    private void enter(Session session) {
        var player = session.player; var step = session.progress.step(); session.transitioning = false;
        session.messageUntil = 0;
        session.bar.progress(Math.min(1, (float) step.ordinal() / Step.COMPLETE.ordinal()));
        if (session.hologram != null) { session.entities.remove(session.hologram); session.hologram.remove(); session.hologram = null; }
        switch (step) {
            case MAP -> {
                if (!teleport(player, at(session, 8.5, 65, .5, -90))) throw new IllegalStateException("Lesson teleport cancelled");
                player.sendMessage(text("messages.map-intro"));
            }
            case WILDERNESS -> player.sendMessage(text("messages.map-walk"));
            case COMBAT -> {
                clearItems(session);
                if (!teleport(player, at(session, 66.5, 65, .5, -90))) throw new IllegalStateException("Arena teleport cancelled");
                give(player, "blaster", 1, 0); give(player, "ammo", 64, 1); player.getInventory().setHeldItemSlot(0);
                for (int i = 0; i < config.getInt("guards"); i++) spawnGuard(session, i);
            }
            case SABER -> {
                clearItems(session); give(player, "saber", 1, 0); player.getInventory().setHeldItemSlot(0);
            }
            case BUY -> {
                clearItems(session);
                if (!teleport(player, at(session, 110.5, 65, -32.5, -90))) throw new IllegalStateException("Market teleport cancelled");
                for (int i = 0; i < 3; i++) {
                    var brown = mobs.spawn("JawaBrown", at(session, 120.5, 65, -35 + i * 3, 90), 1);
                    prepareNpc(session, brown); if (i == 1) session.brown = brown.getUniqueId();
                }
            }
            case SELL -> {
                give(player, "junk", 8, 1);
                if (!teleport(player, at(session, 110.5, 65, -8.5, -90))) throw new IllegalStateException("Buyer teleport cancelled");
                var gray = mobs.spawn("JawaGray", at(session, 120.5, 65, -8.5, 90), 1);
                prepareNpc(session, gray); session.gray = gray.getUniqueId();
            }
            case FLIGHT -> {
                clearItems(session); give(player, "vehicle-ammo", 64, 1);
                if (!teleport(player, at(session, 44.5, 65, 44.5, 0))) throw new IllegalStateException("Hangar teleport cancelled");
                session.vehicle = plugin.vehicles().deployTutorial(player, at(session, 48.5, 65, 48.5, 0));
                if (session.vehicle == null) throw new IllegalStateException("Training X-wing could not deploy");
            }
            case TARGET -> spawnTarget(session);
            case EVENTS -> {
                removeVehicle(session); clearItems(session);
                if (!teleport(player, at(session, 8.5, 65, .5, -90))) throw new IllegalStateException("Debrief teleport cancelled");
                session.eventPage = 0; session.pageAt = System.currentTimeMillis();
            }
            case COMPLETE -> { complete(session); return; }
            default -> { }
        }
        if (step != Step.MAP && step != Step.WILDERNESS) hologram(session);
        player.sendActionBar(instruction(session));
    }
    private void prepareNpc(Session session, LivingEntity entity) {
        if (entity == null || !entity.isValid()) throw new IllegalStateException("Tutorial NPC spawn failed");
        entity.setAI(false); entity.setInvulnerable(true); entity.setPersistent(false); entity.setRemoveWhenFarAway(false);
        session.entities.add(entity);
    }
    private void spawnGuard(Session session, int index) {
        var entity = mobs.spawn("GamorreanGuard", at(session, 77.5, 65, -5 + index * 5, 90), 1);
        if (entity == null || !entity.isValid()) throw new IllegalStateException("Training guard spawn failed");
        entity.setPersistent(false); entity.setRemoveWhenFarAway(false);
        if (entity instanceof Mob mob) mob.setTarget(session.player);
        session.guards.add(entity.getUniqueId()); session.entities.add(entity);
    }
    private void spawnTarget(Session session) {
        var target = session.world.spawn(at(session, 48.5, 82, 86, 180), Slime.class, slime -> {
            slime.setSize(5); slime.setAI(false); slime.setGravity(false); slime.setInvisible(true);
            slime.setInvulnerable(true); slime.setPersistent(false); slime.setSilent(true);
        });
        session.target = target.getUniqueId(); session.entities.add(target);
        for (int x = -2; x <= 2; x++) for (int y = 0; y < 5; y++) {
            Material color = Math.max(Math.abs(x), Math.abs(y - 2)) % 2 == 0 ? Material.RED_CONCRETE : Material.WHITE_CONCRETE;
            var display = session.world.spawn(at(session, 48 + x, 82 + y, 87, 0), BlockDisplay.class, block -> {
                block.setBlock(color.createBlockData()); block.setPersistent(false);
            }); session.entities.add(display);
        }
    }
    private Component instruction(Session session) {
        if (session.progress.step() == Step.EVENTS) return text("events." + session.eventPage + ".action");
        return text("steps." + session.progress.step().name().toLowerCase(Locale.ROOT),
                "kills", String.valueOf(session.progress.kills()), "seconds", String.valueOf(Math.min(config.getInt("flight-seconds"), session.progress.flightMillis() / 1000)));
    }
    private void hologram(Session session) {
        var location = session.progress.step() == Step.TARGET ? at(session, 48.5, 89, 86, 0)
                : session.player.getLocation().add(session.player.getLocation().getDirection().setY(0).normalize().multiply(5)).add(0, 2.8, 0);
        session.hologram = session.world.spawn(location, TextDisplay.class, display -> {
            display.setPersistent(false); display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER); display.setLineWidth(280);
            display.setSeeThrough(false); display.setShadowed(true); display.setViewRange(3);
            display.text(hologramText(session));
        }); session.entities.add(session.hologram);
    }
    private Component hologramText(Session session) {
        if (session.progress.step() == Step.EVENTS) return text("events." + session.eventPage + ".hologram");
        String key = "holograms." + session.progress.step().name().toLowerCase(Locale.ROOT);
        return config.isString(key) ? text(key) : instruction(session);
    }
    public String practiceClaim(Player player, String world, int x, int z) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.progress.step() == Step.DISBAND && session.world.getKey().toString().equals(world)
                && session.claimX == x && session.claimZ == z ? session.clan : null;
    }
    public void mapShown(Player player) {
        var session = sessions.get(player.getUniqueId()); if (session != null) advance(session, Step.MAP);
    }
    public void saberThrown(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || session.progress.step() != Step.SABER || session.transitioning) return;
        session.transitioning = true;
        later(session, Step.SABER, 35, () -> advance(session, Step.SABER));
    }
    public void board(Player player, UUID vehicle) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || !Objects.equals(session.vehicle, vehicle) || session.progress.step() != Step.FLIGHT) { denied(player); return; }
        plugin.vehicles().mount(player, vehicle, true);
    }
    public void cannonHit(Player player, UUID vehicle, Entity direct) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || session.progress.step() != Step.TARGET || !Objects.equals(session.vehicle, vehicle)
                || direct == null || !direct.getUniqueId().equals(session.target) || session.transitioning) return;
        session.transitioning = true;
        later(session, Step.TARGET, 20, () -> advance(session, Step.TARGET));
    }
    private void tick() {
        long nowNanos = System.nanoTime(), elapsed = Math.max(0, (nowNanos - lastTick) / 1_000_000); lastTick = nowNanos;
        long now = System.currentTimeMillis();
        for (var session : List.copyOf(sessions.values())) {
            try {
                var player = session.player; if (!player.isOnline()) continue;
                var step = session.progress.step();
                if (player.getWorld() != session.world) { end(player, false); continue; }
                player.setFireTicks(0); player.setFallDistance(0);
                if (step == Step.WILDERNESS && player.getLocation().getX() >= 32) advance(session, Step.WILDERNESS);
                if (step == Step.COMBAT) {
                    replenish(player, "ammo", 1);
                    for (UUID id : Set.copyOf(session.guards)) {
                        var entity = Bukkit.getEntity(id);
                        if (entity == null || !entity.isValid()) { session.guards.remove(id); spawnGuard(session, session.guards.size()); }
                    }
                }
                if (step == Step.FLIGHT || step == Step.TARGET) {
                    replenish(player, "vehicle-ammo", 1);
                    var vehicle = plugin.vehicles().entity(session.vehicle);
                    if (vehicle == null || !vehicle.isValid()) throw new IllegalStateException("Training vehicle disappeared");
                    if (session.progress.fly(plugin.vehicles().occupant(session.vehicle, player.getUniqueId()),
                            !VehicleModels.grounded(vehicle), elapsed, config.getInt("flight-seconds") * 1000L)) enter(session);
                }
                if (step == Step.EVENTS && now - session.pageAt >= config.getInt("event-page-seconds") * 1000L) {
                    if (++session.eventPage == 3) { advance(session, Step.EVENTS); continue; }
                    session.pageAt = now; if (session.hologram != null) session.hologram.text(hologramText(session));
                }
                if (Bukkit.getCurrentTick() % 5 == 0) player.sendActionBar(now < session.messageUntil ? session.message : instruction(session));
            } catch (RuntimeException error) { fail(session.player, error); end(session.player, false); }
        }
        if (Bukkit.getCurrentTick() % 100 == 0) for (var player : Bukkit.getOnlinePlayers()) {
            if (!TutorialInventory.active(player)) rewards(player);
        }
    }
    private void replenish(Player player, String item, int slot) {
        if (Bukkit.getCurrentTick() % 5 != 0) return;
        var template = plugin.items().create(config.getString("items." + item));
        if (player.getInventory().containsAtLeast(template, 1)) return;
        template.setAmount(64); player.getInventory().addItem(template);
    }
    private void removeVehicle(Session session) {
        if (session.vehicle == null) return;
        internal.add(session.player.getUniqueId());
        try { plugin.vehicles().removeTutorial(session.vehicle); session.vehicle = null; }
        finally { internal.remove(session.player.getUniqueId()); }
    }
    private void complete(Session session) {
        var player = session.player;
        if (!TutorialInventory.has(player, TutorialInventory.COMPLETED)) {
            TutorialInventory.flag(player, TutorialInventory.COMPLETED);
            TutorialInventory.flag(player, TutorialInventory.REWARD_PENDING);
            TutorialInventory.flag(player, TutorialInventory.KIT_PENDING);
            player.saveData();
        }
        end(player, true);
    }
    public void end(Player player, boolean completed) {
        if (!ending.add(player.getUniqueId())) return;
        try { endSession(player, completed); } finally { ending.remove(player.getUniqueId()); }
    }
    private void cleanupSession(Session session) {
        session.player.hideBossBar(session.bar);
        sessions.remove(session.player.getUniqueId(), session);
        try { removeVehicle(session); }
        catch (RuntimeException error) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Tutorial ship cleanup failed", error); }
        for (var entity : List.copyOf(session.entities)) {
            try { if (entity.isValid()) mobs.remove(entity); }
            catch (RuntimeException error) { entity.remove(); plugin.getLogger().log(java.util.logging.Level.WARNING, "Tutorial model cleanup failed", error); }
        }
        pool.release(session.lease);
    }
    private void endSession(Player player, boolean completed) {
        if (waiting.containsKey(player.getUniqueId())) {
            cancelWaiting(player, true); TutorialInventory.flag(player, TutorialInventory.OFFERED); player.saveData();
            hint(player, "ended"); return;
        }
        var session = sessions.get(player.getUniqueId());
        if (!TutorialInventory.active(player)) { hint(player, "outside"); return; }
        try {
            player.closeInventory(); player.setItemOnCursor(null);
            net.mandomc.integrations.weapons.WeaponMechanicsItems.stopActions(player);
            plugin.sabers().finish(player.getUniqueId());
            if (session != null) removeVehicle(session);
            plugin.combat().clear(player);
            var destination = TutorialInventory.destination(player, plugin.worlds().spawn("Batuu"));
            if (!teleport(player, destination)) throw new IllegalStateException("Tutorial exit teleport denied; inventory recovery retained");
            TutorialInventory.restore(player);
            if (session != null) cleanupSession(session);
            hint(player, completed ? "complete" : "ended");
            plugin.credits().auctions().recoverAfterTutorial(player);
            rewards(player);
        } catch (RuntimeException error) {
            fail(player, error);
            if (session != null) cleanupSession(session);
            if (player.isOnline()) player.kick(text("messages.recovery"));
        }
    }
    private void recover(Player player) {
        if (TutorialInventory.active(player)) end(player, false);
        else if (TutorialInventory.world(player.getWorld())) teleport(player, plugin.worlds().spawn("Batuu"));
    }
    private void rewards(Player player) {
        if (!player.isOnline() || TutorialInventory.active(player)) return;
        if (TutorialInventory.has(player, TutorialInventory.KIT_PENDING)) {
            // Respect the legacy starter receipt: existing players never receive a second kit.
            if (player.getPersistentDataContainer().has(new NamespacedKey("mandomc", "starter_kit_v1")) || plugin.starterKit().grant(player)) {
                player.getPersistentDataContainer().remove(TutorialInventory.KIT_PENDING); player.saveData();
            } else hint(player, "kit-space");
        }
        if (!TutorialInventory.has(player, TutorialInventory.REWARD_PENDING) || !paying.add(player.getUniqueId())) return;
        UUID id = player.getUniqueId();
        plugin.credits().auctionTask(() -> plugin.credits().ledger().rewardTutorial(id, config.getLong("reward-credits") * 100), (added, error) -> {
            paying.remove(id); var online = Bukkit.getPlayer(id);
            if (error != null) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Tutorial reward retained for retry: " + id, error); return; }
            if (online != null) {
                online.getPersistentDataContainer().remove(TutorialInventory.REWARD_PENDING); online.saveData();
                if (Boolean.TRUE.equals(added)) hint(online, "reward");
            }
        });
    }
    private ItemStack icon(Material material, String key) {
        var item = new ItemStack(material); item.editMeta(meta -> meta.displayName(text(key))); return item;
    }
    private void warp(Player player) {
        var menu = new Menu(player, MenuKind.WARP, text("menus.warp"));
        menu.inventory.setItem(13, icon(Material.DIAMOND, "menus.tutorial")); player.openInventory(menu.inventory);
    }
    public void jawa(Player player, LivingEntity entity) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || !session.entities.contains(entity) || entity.getWorld() != player.getWorld()
                || entity.getLocation().distanceSquared(player.getLocation()) > 100) { denied(player); return; }
        var step = session.progress.step();
        if (step == Step.BUY && mobs.type(entity).equals("JawaBrown")) {
            var menu = new Menu(player, MenuKind.BUY, text("menus.buy"));
            var sapling = new ItemStack(Material.OAK_SAPLING, 4);
            sapling.editMeta(meta -> { meta.displayName(text("menus.saplings")); meta.lore(List.of(text("menus.buy-hint"))); });
            menu.inventory.setItem(13, sapling); menu.inventory.setItem(31, icon(Material.GOLD_NUGGET, "menus.wallet"));
            player.openInventory(menu.inventory);
        } else if (step == Step.SELL && entity.getUniqueId().equals(session.gray)) {
            var menu = new Menu(player, MenuKind.SELL, text("menus.sell"));
            menu.inventory.setItem(27, icon(Material.BOOK, "menus.sell-hint"));
            menu.inventory.setItem(31, icon(Material.EMERALD, "menus.sell-button"));
            menu.inventory.setItem(35, icon(Material.BARRIER, "menus.close"));
            player.openInventory(menu.inventory);
        } else denied(player);
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void command(PlayerCommandPreprocessEvent event) {
        var player = event.getPlayer();
        if (!TutorialInventory.active(player) && !waiting.containsKey(player.getUniqueId())) return;
        event.setCancelled(true);
        var parts = event.getMessage().substring(1).trim().split("\\s+");
        String root = parts[0].toLowerCase(Locale.ROOT);
        if (root.startsWith("mandomc:")) root = root.substring(8);
        if (root.equals("tutorial")) {
            if (parts.length == 2 && parts[1].equalsIgnoreCase("end")) end(player, false);
            else hint(player, "already");
            return;
        }
        var session = sessions.get(player.getUniqueId());
        if (session == null) { hint(player, "loading"); return; }
        var step = session.progress.step();
        if (Set.of("warp", "warps", "travel").contains(root) && parts.length == 1 && step == Step.WARP) { warp(player); return; }
        if (Set.of("c", "cl", "clan").contains(root)) {
            if (parts.length == 2 && parts[1].equalsIgnoreCase("map") && step.ordinal() >= Step.MAP.ordinal() && step.ordinal() <= Step.DISBAND.ordinal()) {
                Objects.requireNonNull(plugin.getCommand("c")).execute(player, "c", new String[]{"map"}); return;
            }
            if (parts.length == 3 && parts[1].equalsIgnoreCase("create") && step == Step.CREATE) {
                if (!TutorialProgress.clanName(parts[2])) { hint(player, "clan-name"); return; }
                session.clan = parts[2]; advance(session, Step.CREATE); return;
            }
            if (parts.length == 2 && parts[1].equalsIgnoreCase("claim") && step == Step.CLAIM) {
                if (player.getLocation().getBlockX() < 48 || player.getLocation().getBlockX() >= 64) { hint(player, "claim-position"); return; }
                session.claimX = player.getLocation().getBlockX() >> 4; session.claimZ = player.getLocation().getBlockZ() >> 4;
                advance(session, Step.CLAIM); return;
            }
            if (parts.length == 2 && parts[1].equalsIgnoreCase("disband") && step == Step.DISBAND) {
                session.clan = null; advance(session, Step.DISBAND); return;
            }
        }
        if (root.equals("recipes") && parts.length == 1 && step == Step.RECIPES) {
            Objects.requireNonNull(plugin.getCommand("recipes")).execute(player, "recipes", new String[0]); return;
        }
        denied(player);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void opened(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !TutorialInventory.active(player)) return;
        var session = sessions.get(player.getUniqueId());
        if (session == null) { event.setCancelled(true); return; }
        if (event.getInventory().getHolder() instanceof Menu menu && menu.owner.equals(player.getUniqueId())) return;
        if (session.progress.step() == Step.RECIPES && RecipeGuide.isMenu(event.getInventory())) {
            session.recipeSeen = true; return;
        }
        event.setCancelled(true); denied(player);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void clicked(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !TutorialInventory.active(player)) return;
        var session = sessions.get(player.getUniqueId());
        if (session == null) { event.setCancelled(true); return; }
        var holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof Menu menu && menu.owner.equals(player.getUniqueId())) {
            event.setCancelled(true);
            if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.SHIFT_LEFT) return;
            int slot = event.getRawSlot();
            if (menu.kind == MenuKind.WARP && slot == 13 && session.progress.step() == Step.WARP) {
                Bukkit.getScheduler().runTask(plugin, () -> { if (sessions.get(player.getUniqueId()) == session) { player.closeInventory(); advance(session, Step.WARP); } });
            } else if (menu.kind == MenuKind.BUY && slot == 13 && session.progress.step() == Step.BUY && session.wallet >= 40) {
                session.wallet -= 40; player.getInventory().setItem(0, new ItemStack(Material.OAK_SAPLING, 4));
                Bukkit.getScheduler().runTask(plugin, () -> { if (sessions.get(player.getUniqueId()) == session) { player.closeInventory(); advance(session, Step.BUY); } });
            } else if (menu.kind == MenuKind.SELL && session.progress.step() == Step.SELL) sellClick(session, menu, event);
            return;
        }
        if (RecipeGuide.isMenu(event.getView().getTopInventory())) return;
        // Player storage rearrangement is allowed, but crafting/creative transfers and dropping are not.
        if (event.getView().getTopInventory().getType() != InventoryType.CRAFTING || event.getRawSlot() < 5
                || event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP
                || event.getClick() == ClickType.DOUBLE_CLICK || event.getClick() == ClickType.MIDDLE) event.setCancelled(true);
    }
    private void sellClick(Session session, Menu menu, InventoryClickEvent event) {
        var player = session.player; int raw = event.getRawSlot();
        if (raw == 35) { Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory()); return; }
        if (raw == 31) {
            int found = 0;
            for (int i = 0; i < 27; i++) {
                var item = menu.inventory.getItem(i);
                if (config.getString("items.junk").equals(plugin.items().identify(item))) found += item.getAmount();
            }
            if (found < 8) { hint(player, "sell-junk"); return; }
            for (int i = 0; i < 27; i++) menu.inventory.setItem(i, null);
            session.wallet += 6;
            Bukkit.getScheduler().runTask(plugin, () -> { if (sessions.get(player.getUniqueId()) == session) { player.closeInventory(); advance(session, Step.SELL); } });
            return;
        }
        if (raw >= menu.inventory.getSize() && event.getClickedInventory() == player.getInventory()) {
            var item = event.getCurrentItem();
            if (!config.getString("items.junk").equals(plugin.items().identify(item))) { hint(player, "sell-junk"); return; }
            if (menu.inventory.firstEmpty() < 0 || menu.inventory.firstEmpty() >= 27) return;
            menu.inventory.setItem(menu.inventory.firstEmpty(), item.clone()); event.setCurrentItem(null);
        } else if (raw >= 0 && raw < 27) {
            var item = menu.inventory.getItem(raw);
            if (item != null && !item.isEmpty() && player.getInventory().firstEmpty() >= 0) {
                player.getInventory().addItem(item.clone()); menu.inventory.setItem(raw, null);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && TutorialInventory.active(player)) event.setCancelled(true);
    }
    @EventHandler public void closed(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        var session = sessions.get(player.getUniqueId()); if (session == null) return;
        if (event.getInventory().getHolder() instanceof Menu menu && menu.kind == MenuKind.SELL) {
            for (int i = 0; i < 27; i++) {
                var item = menu.inventory.getItem(i);
                if (item != null && !item.isEmpty()) player.getInventory().addItem(item.clone());
                menu.inventory.setItem(i, null);
            }
        }
        if (session.recipeSeen && session.progress.step() == Step.RECIPES) later(session, Step.RECIPES, 1, () -> {
            if (!RecipeGuide.isMenu(player.getOpenInventory().getTopInventory())) advance(session, Step.RECIPES);
        });
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void interact(PlayerInteractEvent event) {
        if (!TutorialInventory.active(event.getPlayer())) return;
        // Block containers/blocks, but keep the weapon's item action and reload controls available.
        event.setUseInteractedBlock(Event.Result.DENY);
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || !Set.of(Step.COMBAT, Step.SABER, Step.FLIGHT, Step.TARGET).contains(session.progress.step()))
            event.setUseItemInHand(Event.Result.DENY);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interactEntity(PlayerInteractEntityEvent event) {
        if (!TutorialInventory.active(event.getPlayer())) return;
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) { event.setCancelled(true); return; }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (session.vehicle != null && session.vehicle.equals(plugin.vehicles().id(event.getRightClicked()))) {
            board(event.getPlayer(), session.vehicle); return;
        }
        if (event.getRightClicked() instanceof LivingEntity living && session.entities.contains(living)) jawa(event.getPlayer(), living);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && TutorialInventory.active(player)) { event.setCancelled(true); return; }
        if (!TutorialInventory.world(event.getEntity().getWorld())) return;
        // Only the current player's owned guards are damageable, and only during combat.
        Entity attacker = event.getDamageSource().getCausingEntity();
        var session = attacker instanceof Player player ? sessions.get(player.getUniqueId()) : null;
        if (session == null || session.progress.step() != Step.COMBAT || !session.guards.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void died(EntityDeathEvent event) {
        if (!TutorialInventory.world(event.getEntity().getWorld())) return;
        event.getDrops().clear(); event.setDroppedExp(0);
        for (var session : List.copyOf(sessions.values())) if (session.guards.remove(event.getEntity().getUniqueId())) {
            if (session.player.equals(event.getDamageSource().getCausingEntity()) || session.player.equals(event.getEntity().getKiller())) {
                if (session.progress.kill(config.getInt("guards"))) later(session, Step.SABER, 1, () -> enter(session));
            } else later(session, Step.COMBAT, 1, () -> spawnGuard(session, session.guards.size()));
            break;
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void food(FoodLevelChangeEvent event) { if (event.getEntity() instanceof Player p && TutorialInventory.active(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent event) { if (TutorialInventory.active(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void pickup(EntityPickupItemEvent event) { if (TutorialInventory.world(event.getEntity().getWorld())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void breakBlock(BlockBreakEvent event) { if (TutorialInventory.world(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(BlockPlaceEvent event) { if (TutorialInventory.world(event.getBlock().getWorld())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void explode(EntityExplodeEvent event) { if (TutorialInventory.world(event.getLocation().getWorld())) { event.blockList().clear(); event.setCancelled(true); } }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void blockExplosion(BlockExplodeEvent event) { if (TutorialInventory.world(event.getBlock().getWorld())) { event.blockList().clear(); event.setCancelled(true); } }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void portal(PlayerPortalEvent event) { if (TutorialInventory.active(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void travel(PlayerTeleportEvent event) {
        if (internal.contains(event.getPlayer().getUniqueId())) return;
        if (TutorialInventory.active(event.getPlayer()) || event.getTo() != null && TutorialInventory.world(event.getTo().getWorld())) {
            event.setCancelled(true); denied(event.getPlayer());
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null) return;
        var session = sessions.get(event.getPlayer().getUniqueId()); if (session == null) return;
        var to = event.getTo(); var step = session.progress.step();
        if (step == Step.FLIGHT || step == Step.TARGET) return;
        boolean outside = to.getY() < 64 || to.getY() > 77 || switch (step) {
            case WARP -> to.getX() < -32 || to.getX() >= -12 || Math.abs(to.getZ()) > 10;
            case MAP -> to.getX() < 0 || to.getX() >= 30 || Math.abs(to.getZ()) > 10;
            case WILDERNESS, CREATE, CLAIM, DISBAND -> to.getX() < 0 || to.getX() >= 64 || Math.abs(to.getZ()) > 10;
            case COMBAT, SABER -> to.getX() < 64 || to.getX() > 90 || Math.abs(to.getZ()) > 16;
            case BUY -> to.getX() < 100 || to.getX() > 132 || to.getZ() < -43 || to.getZ() > -21;
            case SELL, RECIPES -> to.getX() < 100 || to.getX() > 132 || to.getZ() < -20 || to.getZ() > 3;
            default -> to.getX() < 0 || to.getX() > 30 || Math.abs(to.getZ()) > 10;
        };
        if (outside) { event.setTo(event.getFrom()); denied(event.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void dismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && lockDismount(player)) { event.setCancelled(true); denied(player); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void advancement(com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent event) {
        if (TutorialInventory.active(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void statistic(PlayerStatisticIncrementEvent event) {
        if (TutorialInventory.active(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void experience(PlayerExpChangeEvent event) { if (TutorialInventory.active(event.getPlayer())) event.setAmount(0); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void join(PlayerJoinEvent event) {
        var player = event.getPlayer();
        // Recovery is synchronous so other next-tick delivery systems see the real inventory.
        recover(player);
        if (!player.hasPlayedBefore()) TutorialInventory.flag(player, ELIGIBLE);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline()) rewards(player); }, 20);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void pack(PlayerResourcePackStatusEvent event) {
        if (event.getStatus() != PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) return;
        var player = event.getPlayer();
        loadedPacks.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(event.getID());
        if (!loadedPacks.get(player.getUniqueId()).containsAll(requiredPacks)) return;
        packReady.add(player.getUniqueId());
        if (TutorialInventory.has(player, ELIGIBLE) && !TutorialInventory.has(player, TutorialInventory.OFFERED))
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline() && !TutorialInventory.has(player, TutorialInventory.OFFERED)) request(player); }, 20);
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void quit(PlayerQuitEvent event) {
        var player = event.getPlayer(); cancelWaiting(player, true);
        if (TutorialInventory.active(player)) end(player, false);
        packReady.remove(player.getUniqueId()); loadedPacks.remove(player.getUniqueId());
    }
    @Override public void close() {
        closed = true; ticker.cancel();
        for (var player : Bukkit.getOnlinePlayers()) {
            cancelWaiting(player, true); if (TutorialInventory.active(player)) end(player, false);
        }
        pool.close(); HandlerList.unregisterAll(this);
    }
}
