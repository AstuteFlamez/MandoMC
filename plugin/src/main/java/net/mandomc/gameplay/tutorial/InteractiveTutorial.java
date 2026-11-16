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
    private static final NamespacedKey SECTIONS = NamespacedKey.fromString("mandomc:tutorial_sections_v2");
    private static final NamespacedKey PAID = NamespacedKey.fromString("mandomc:tutorial_sections_paid_v2");
    private static final NamespacedKey HUB_CHOICE = NamespacedKey.fromString("mandomc:tutorial_hub_choice");
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
        UUID vehicle, brown, gray;
        TextDisplay hologram;
        String clan;
        int claimX, claimZ, eventPage, eventSeconds = -1;
        long pageAt, messageUntil, saberHeldMillis, controlMillis;
        int controlTick = -1;
        boolean correctControl;
        Location targetCenter;
        final Set<org.bukkit.block.Block> targetBlocks = new LinkedHashSet<>();
        final Map<org.bukkit.block.Block, org.bukkit.block.BlockState> practiceBlocks = new LinkedHashMap<>();
        Component message;
        boolean recipeSeen, transitioning, saberHit;
        final Set<org.bukkit.block.Block> ores = new HashSet<>();
        org.bukkit.block.Block furnace, bench;
        int mined, routeIndex;
        final List<Entity> hubEntities = new ArrayList<>();
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
        final int continueSlot;
        Menu(Player player, MenuKind kind, Component title, net.mandomc.gameplay.travel.WarpService warps) {
            this.owner = player.getUniqueId(); this.kind = kind;
            inventory = kind == MenuKind.WARP ? warps.createMenu(this) : Bukkit.createInventory(this, 36, title);
            continueSlot = ((inventory.getSize() / 9 - 1) / 2) * 9 + 4;
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
        for (var id : List.of("blaster", "ammo", "saber", "junk", "vehicle-ammo", "vehicle"))
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
    public void vehicleMessage(Player player, String key) {
        hint(player, switch (key) {
            case "cannon-controls" -> "cannons";
            case "occupied" -> "seat-busy";
            case "unavailable" -> "vehicle-unavailable";
            default -> "denied";
        });
    }
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
        if (net.mandomc.gameplay.admin.StaffMode.active(player) || net.mandomc.integrations.weapons.WeaponMechanicsItems.reloading(player)
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
            if (!teleport(player, at(session, 8.5, 65, .5, 180))) throw new IllegalStateException("Tutorial arrival was cancelled");
            enter(session);
        } catch (RuntimeException error) { fail(player, error); if (TutorialInventory.active(player)) end(player, false); else pool.release(lease); }
    }
    private void fail(Player player, Throwable error) {
        plugin.getLogger().log(java.util.logging.Level.SEVERE, "Tutorial failed for " + player.getUniqueId(), error);
        hint(player, "error");
    }
    private int sections(Player player) { return player.getPersistentDataContainer().getOrDefault(SECTIONS, PersistentDataType.INTEGER, 0); }
    private void hub(Session session) {
        session.player.closeInventory(); removeVehicle(session); clearItems(session);
        for (var entity : List.copyOf(session.entities)) { if (entity.isValid()) mobs.remove(entity); }
        session.entities.clear(); session.hubEntities.clear(); session.hologram = null; session.guards.clear();
        for (var state : session.practiceBlocks.values()) state.update(true, false);
        session.practiceBlocks.clear(); session.targetBlocks.clear(); session.ores.clear();
        session.furnace = null; session.bench = null; session.clan = null;
        session.progress.hub(); session.player.setGameMode(GameMode.ADVENTURE);
        if (!teleport(session.player, at(session, 8.5, 65, .5, 180))) throw new IllegalStateException("Hub teleport denied");
        enter(session);
    }
    private void hubDisplays(Session session) {
        int done = sections(session.player);
        session.bar.name(text("messages.hub-bar", "done", Integer.toString(Integer.bitCount(done))));
        var choices = TutorialProgress.Section.values();
        for (int i = 0; i <= choices.length; i++) {
            double angle = Math.PI * 2 * i / 6 - Math.PI / 2;
            var at = at(session, 8.5 + Math.cos(angle) * 7, 65, .5 + Math.sin(angle) * 7, 0);
            String label = i == choices.length ? "exit" : choices[i].name().toLowerCase(Locale.ROOT);
            String state = i == choices.length ? "" : config.getString((done & (1 << i)) != 0 ? "hub.completed" : "hub.available");
            var display = session.world.spawn(at.clone().add(0, 2.3, 0), TextDisplay.class, e -> {
                e.setPersistent(false); e.setBillboard(Display.Billboard.CENTER); e.setLineWidth(180);
                e.text(text("hub." + label).append(Component.newline()).append(MiniMessage.miniMessage().deserialize(state)));
            });
            var click = session.world.spawn(at.clone().add(0, .3, 0), Interaction.class, e -> {
                e.setPersistent(false); e.setInteractionWidth(3); e.setInteractionHeight(3); e.setResponsive(true);
                e.getPersistentDataContainer().set(HUB_CHOICE, PersistentDataType.STRING, label);
            });
            session.entities.add(display); session.entities.add(click); session.hubEntities.add(display); session.hubEntities.add(click);
        }
    }
    private void choose(Session session, Entity entity) {
        if (session.progress.step() != Step.HUB || !session.hubEntities.contains(entity)
                || entity.getLocation().distanceSquared(session.player.getLocation()) > 64) return;
        String choice = entity.getPersistentDataContainer().get(HUB_CHOICE, PersistentDataType.STRING);
        if (choice == null) return;
        if (choice.equals("exit")) { end(session.player, false); session.player.sendMessage(text("messages.start-naboo")); return; }
        for (var e : session.hubEntities) { e.remove(); session.entities.remove(e); }
        session.hubEntities.clear(); session.recipeSeen = false; session.mined = 0; session.saberHit = false;
        session.wallet = 100;
        session.progress.start(TutorialProgress.Section.valueOf(choice.toUpperCase(Locale.ROOT))); enter(session);
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
        session.messageUntil = 0; session.controlMillis = 0; session.correctControl = false;
        session.bar.progress(Math.min(1, (float) step.ordinal() / Step.COMPLETE.ordinal()));
        if (session.hologram != null) { session.entities.remove(session.hologram); session.hologram.remove(); session.hologram = null; }
        switch (step) {
            case HUB -> hubDisplays(session);
            case WARP -> { if (!teleport(player, at(session, 8.5, 65, .5, 180))) throw new IllegalStateException("Planet arrival cancelled"); }
            case MINING -> {
                clearItems(session); player.setGameMode(GameMode.SURVIVAL);
                player.getInventory().setItem(0, new ItemStack(Material.STONE_PICKAXE)); player.getInventory().setHeldItemSlot(0);
                player.getInventory().setItem(1, new ItemStack(Material.STICK, 2));
                player.getInventory().setItem(2, new ItemStack(Material.COAL, 1));
                if (!teleport(player, at(session, 43.5, 65, 22.5, 0))) throw new IllegalStateException("Mine arrival cancelled");
                for (int x = 42; x <= 46; x += 2) { var block = session.world.getBlockAt(x, 65, 27); practiceBlock(session, block, Material.IRON_ORE); session.ores.add(block); }
                session.furnace = session.world.getBlockAt(40, 65, 23); practiceBlock(session, session.furnace, Material.FURNACE);
                session.bench = session.world.getBlockAt(40, 65, 25); practiceBlock(session, session.bench, Material.CRAFTING_TABLE);
            }
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
                if (!teleport(player, at(session, 66.5, 65, .5, -90))) throw new IllegalStateException("Saber arrival cancelled");
                clearItems(session); give(player, "saber", 1, 0); player.getInventory().setHeldItemSlot(0);
                spawnGuard(session, 0); session.saberHit = false;
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
                session.routeIndex = 0;
                if (!teleport(player, plugin.worlds().spawn("Batuu"))) throw new IllegalStateException("Batuu teleport cancelled");
                player.sendMessage(text("messages.shop-route"));
            }
            case RECIPES -> {
                clearItems(session);
                if (!teleport(player, at(session, 8.5, 65, .5, -90))) throw new IllegalStateException("Recipe lesson teleport cancelled");
            }
            case PLACE -> {
                clearItems(session); give(player, "vehicle", 1, 0); give(player, "vehicle-ammo", 64, 1);
                player.getInventory().setHeldItemSlot(0);
                if (!teleport(player, at(session, 44.5, 65, 44.5, 0))) throw new IllegalStateException("Hangar teleport cancelled");
            }
            case TARGET -> spawnTarget(session);
            case LAND -> landingPad(session);
            case EVENTS -> {
                removeVehicle(session); clearItems(session);
                if (!teleport(player, at(session, 8.5, 65, .5, -90))) throw new IllegalStateException("Debrief teleport cancelled");
                session.eventPage = 0; session.pageAt = System.currentTimeMillis();
            }
            case COMPLETE -> { complete(session); return; }
            default -> { }
        }
        if (step != Step.HUB && step != Step.SELL && step != Step.MAP && step != Step.WILDERNESS && !flightStep(step)) hologram(session);
        if (step == Step.EVENTS) eventTimer(session);
        plugin.territories().announce(player);
        player.sendActionBar(instruction(session));
    }
    private void prepareNpc(Session session, LivingEntity entity) {
        if (entity == null || !entity.isValid()) throw new IllegalStateException("Tutorial NPC spawn failed");
        entity.setAI(false); entity.setInvulnerable(true); entity.setPersistent(false); entity.setRemoveWhenFarAway(false);
        session.entities.add(entity);
    }
    private void spawnGuard(Session session, int index) {
        String type = session.progress.step() == Step.SABER ? "GamorreanGuard" : config.getStringList("guard-types").get(index % 3);
        var entity = mobs.spawn(type, at(session, 77.5, 65, -5 + index * 5, 90), 1);
        if (entity == null || !entity.isValid()) throw new IllegalStateException("Training guard spawn failed");
        entity.setPersistent(false); entity.setRemoveWhenFarAway(false);
        entity.setAI(false);
        if (entity instanceof Mob mob) mob.setTarget(null);
        session.guards.add(entity.getUniqueId()); session.entities.add(entity);
    }
    private void spawnTarget(Session session) {
        var ship = Objects.requireNonNull(plugin.vehicles().entity(session.vehicle));
        var facing = session.player.getEyeLocation();
        facing.setPitch((float) Math.clamp(facing.getPitch(), -30, 30));
        var direction = facing.getDirection();
        session.targetCenter = ship.getLocation().add(0, 3, 0).add(direction.clone().multiply(28));
        session.targetCenter.setY(Math.clamp(session.targetCenter.getY(), 74, session.world.getMaxHeight() - 12));
        boolean alongX = Math.abs(direction.getZ()) >= Math.abs(direction.getX());
        // Solid blocks are both the visible bullseye and the native cannon collision surface.
        for (int across = -8; across <= 8; across++) for (int up = -8; up <= 8; up++) {
            var block = session.targetCenter.clone().add(alongX ? across : 0, up, alongX ? 0 : across).getBlock();
            int ring = Math.max(Math.abs(across), Math.abs(up)) / 2;
            practiceBlock(session, block, ring % 2 == 0 ? Material.SEA_LANTERN : Material.RED_CONCRETE);
            session.targetBlocks.add(block);
        }
    }
    private void practiceBlock(Session session, org.bukkit.block.Block block, Material material) {
        session.practiceBlocks.putIfAbsent(block, block.getState());
        block.setType(material, false);
    }
    private void landingPad(Session session) {
        var ship = Objects.requireNonNull(plugin.vehicles().entity(session.vehicle));
        // The flight area is larger than the original walking scene. Give every landing a solid surface,
        // including older generated chunks that were void before the flight area was expanded.
        int x = ship.getLocation().getBlockX(), z = ship.getLocation().getBlockZ();
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
            var block = session.world.getBlockAt(x + dx, 64, z + dz);
            practiceBlock(session, block, Math.abs(dx) == 4 || Math.abs(dz) == 4 ? Material.SEA_LANTERN : Material.SMOOTH_STONE);
        }
    }
    private static boolean flightStep(Step step) {
        return step.ordinal() >= Step.BOARD.ordinal() && step.ordinal() <= Step.LAND.ordinal();
    }
    /** Filter the actual ModelEngine inputs before motion; only the taught control can affect the ship. */
    public net.mandomc.gameplay.vehicles.VehicleMotion.Input flightInput(Player player,
            net.mandomc.gameplay.vehicles.VehicleMotion.Input raw, LivingEntity vehicle) {
        var session = sessions.get(player.getUniqueId());
        if (session == null) return raw;
        var step = session.progress.step();
        session.controlTick = Bukkit.getCurrentTick();
        session.correctControl = switch (step) {
            case FLIGHT -> raw.forward() > 0;
            case RIGHT -> raw.side() < 0;
            case LEFT -> raw.side() > 0;
            // Normal aircraft braking stays engaged after S is released.
            case STOP -> session.correctControl || raw.forward() < 0;
            case LAND -> raw.sneak();
            default -> false;
        };
        return TutorialFlight.controls(step, raw, VehicleModels.grounded(vehicle));
    }
    public boolean mayFire(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.progress.step() == Step.TARGET && !session.transitioning;
    }
    private Component instruction(Session session) {
        if (session.progress.step() == Step.SELL && insideShop(session.player.getLocation())) return text("messages.sell-inside");
        if (session.progress.step() == Step.EVENTS) return text("events." + session.eventPage + ".action",
                "seconds", Integer.toString(remainingSeconds(session)));
        if (session.progress.step() == Step.SABER && session.transitioning && (plugin.sabers().busy(session.player) || session.saberHit)) return text(
                plugin.sabers().busy(session.player) ? "messages.saber-return" : "messages.saber-hold");
        if (session.progress.step() == Step.FLIGHT) {
            var vehicle = plugin.vehicles().entity(session.vehicle);
            if (vehicle != null && VehicleModels.grounded(vehicle)) return text("steps.takeoff");
        }
        return text("steps." + session.progress.step().name().toLowerCase(Locale.ROOT),
                "kills", String.valueOf(session.progress.kills()),
                "seconds", String.valueOf(Math.min(config.getInt("flight-seconds"), session.progress.flightMillis() / 1000)),
                "total", Integer.toString(config.getInt("flight-seconds")));
    }
    private int remainingSeconds(Session session) {
        long remaining = config.getInt("event-page-seconds") * 1000L - (System.currentTimeMillis() - session.pageAt);
        return (int) Math.max(0, (remaining + 999) / 1000);
    }
    private Component countdown(Session session) {
        return text(session.eventPage == 2 ? "messages.event-finish" : "messages.event-next",
                "seconds", Integer.toString(remainingSeconds(session)));
    }
    private void eventTimer(Session session) {
        int seconds = remainingSeconds(session);
        if (seconds == session.eventSeconds) return;
        session.eventSeconds = seconds;
        session.bar.name(text("messages.event-bar", "page", Integer.toString(session.eventPage + 1),
                "seconds", Integer.toString(seconds)));
        session.bar.progress(Math.max(0, Math.min(1, (float) seconds / config.getInt("event-page-seconds"))));
        if (session.hologram != null) session.hologram.text(hologramText(session));
    }
    private void hologram(Session session) {
        var location = switch (session.progress.step()) {
            case SABER, COMBAT -> at(session, 74.5, 68, .5, 0);
            case MINING, SMELTING, CRAFTING -> at(session, 44, 68, 27, 0);
            case CREATE, CLAIM, DISBAND -> at(session, 53, 68, .5, 0);
            case BUY -> at(session, 116, 68, -32.5, 0);
            case SELL -> at(session, 116, 68, -8.5, 0);
            case PLACE -> at(session, 50, 68, 50, 0);
            case WARP -> at(session, 14, 68, .5, 0);
            default -> at(session, 14, 68, .5, 0);
        };
        session.hologram = session.world.spawn(location, TextDisplay.class, display -> {
            display.setPersistent(false); display.setBillboard(Display.Billboard.CENTER);
            display.setAlignment(TextDisplay.TextAlignment.CENTER); display.setLineWidth(280);
            display.setSeeThrough(false); display.setShadowed(true); display.setViewRange(session.progress.step() == Step.TARGET ? 8 : 3);
            display.text(hologramText(session));
        }); session.entities.add(session.hologram);
    }
    private Component hologramText(Session session) {
        if (session.progress.step() == Step.EVENTS) return text("events." + session.eventPage + ".hologram")
                .append(Component.newline()).append(countdown(session));
        String key = "holograms." + session.progress.step().name().toLowerCase(Locale.ROOT);
        return config.isString(key) ? text(key) : instruction(session);
    }
    public String practiceClaim(Player player, String world, int x, int z) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.progress.step() == Step.DISBAND && session.world.getKey().toString().equals(world)
                && session.claimX == x && session.claimZ == z ? session.clan : null;
    }
    public boolean territoryTitles(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.progress.step().ordinal() >= Step.MAP.ordinal()
                && session.progress.step().ordinal() <= Step.DISBAND.ordinal();
    }
    /** Uses normal placement input, but the vehicle remains owned only by this tutorial session. */
    public void placeVehicle(Player player, ItemStack item, Location location) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || session.progress.step() != Step.PLACE || session.vehicle != null
                || !config.getString("items.vehicle").equals(plugin.items().identify(item))
                || location.getWorld() != session.world || location.getX() < 36 || location.getX() > 64
                || location.getZ() < 36 || location.getZ() > 64 || location.getY() != 65
                || location.distanceSquared(player.getLocation()) > 36
                || !location.getBlock().isPassable() || !location.clone().add(0, 1, 0).getBlock().isPassable()
                || location.clone().subtract(0, 1, 0).getBlock().isPassable()) {
            hint(player, "place-position"); return;
        }
        try {
            session.vehicle = plugin.vehicles().deployTutorial(player, config.getString("items.vehicle"), location);
            if (session.vehicle == null) throw new IllegalStateException("Training ship could not deploy");
            item.subtract(1);
            advance(session, Step.PLACE);
        } catch (RuntimeException error) { fail(player, error); end(player, false); }
    }
    public void mapShown(Player player) {
        var session = sessions.get(player.getUniqueId()); if (session != null) advance(session, Step.MAP);
    }
    public void saberThrown(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || session.progress.step() != Step.SABER) return;
        session.transitioning = true;
        session.saberHeldMillis = 0;
    }
    public boolean saberPractice(Player player) {
        var session = sessions.get(player.getUniqueId()); return session != null && session.progress.step() == Step.SABER;
    }
    public void saberHit(Player player, LivingEntity victim) {
        var session = sessions.get(player.getUniqueId());
        if (session != null && session.progress.step() == Step.SABER && session.entities.contains(victim)) session.saberHit = true;
    }
    public void board(Player player, UUID vehicle) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || !Objects.equals(session.vehicle, vehicle) || session.progress.step() != Step.BOARD) { denied(player); return; }
        plugin.vehicles().mount(player, vehicle, true);
    }
    public void cannonHit(Player player, UUID vehicle, org.bukkit.block.Block block) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || session.progress.step() != Step.TARGET || !Objects.equals(session.vehicle, vehicle)
                || block == null || !session.targetBlocks.contains(block) || session.transitioning) return;
        session.transitioning = true;
        for (var part : session.targetBlocks) {
            session.world.spawnParticle(Particle.BLOCK, part.getLocation().add(.5, .5, .5), 3, .4, .4, .4, .1, part.getBlockData());
            part.setType(Material.AIR, false);
        }
        session.targetBlocks.clear();
        session.world.spawnParticle(Particle.EXPLOSION_EMITTER, session.targetCenter, 3);
        session.world.playSound(session.targetCenter, Sound.ENTITY_GENERIC_EXPLODE, 1, .8f);
        later(session, Step.TARGET, 60, () -> advance(session, Step.TARGET));
    }
    private void tick() {
        long nowNanos = System.nanoTime(), elapsed = Math.max(0, (nowNanos - lastTick) / 1_000_000); lastTick = nowNanos;
        long now = System.currentTimeMillis();
        for (var session : List.copyOf(sessions.values())) {
            try {
                var player = session.player; if (!player.isOnline()) continue;
                var step = session.progress.step();
                if (player.getWorld() != session.world && !(step == Step.SELL && player.getWorld() == plugin.worlds().spawn("Batuu").getWorld())) { end(player, false); continue; }
                player.setFireTicks(0); player.setFallDistance(0);
                if (step == Step.WILDERNESS && player.getLocation().getX() >= 32) advance(session, Step.WILDERNESS);
                if (step == Step.COMBAT) {
                    replenish(player, "ammo", 1);
                    for (UUID id : Set.copyOf(session.guards)) {
                        var entity = Bukkit.getEntity(id);
                        if (entity == null || !entity.isValid()) { session.guards.remove(id); spawnGuard(session, session.guards.size()); }
                    }
                }
                if (step == Step.SMELTING && player.getInventory().containsAtLeast(plugin.items().create("agrinium"), 3)) advance(session, Step.SMELTING);
                if (step == Step.SABER && session.saberHit && session.transitioning) {
                    boolean held = !plugin.sabers().busy(player)
                            && config.getString("items.saber").equals(plugin.items().identify(player.getInventory().getItemInMainHand()));
                    session.saberHeldMillis = held ? session.saberHeldMillis + Math.max(0, Math.min(250, elapsed)) : 0;
                    if (session.saberHeldMillis >= 1000) advance(session, Step.SABER);
                }
                if (flightStep(step)) {
                    replenish(player, "vehicle-ammo", 1);
                    var vehicle = plugin.vehicles().entity(session.vehicle);
                    if (vehicle == null || !vehicle.isValid()) throw new IllegalStateException("Training vehicle disappeared");
                    boolean pilot = plugin.vehicles().occupant(session.vehicle, player.getUniqueId());
                    boolean grounded = VehicleModels.grounded(vehicle);
                    boolean observed = pilot && session.correctControl && Bukkit.getCurrentTick() - session.controlTick <= 2;
                    if (step == Step.BOARD && pilot) advance(session, Step.BOARD);
                    if (session.progress.fly(pilot, !grounded && vehicle.getVelocity().lengthSquared() > .01, elapsed, config.getInt("flight-seconds") * 1000L)) enter(session);
                    if (step == Step.RIGHT || step == Step.LEFT || step == Step.STOP) {
                        boolean correct = observed && !grounded && (step != Step.STOP || vehicle.getVelocity().lengthSquared() < .01);
                        session.controlMillis = correct ? session.controlMillis + Math.min(250, elapsed) : 0;
                        if (session.controlMillis >= 600) advance(session, step);
                    }
                    if (step == Step.LAND && pilot && grounded) advance(session, Step.LAND);
                }
                if (step == Step.EVENTS && now - session.pageAt >= config.getInt("event-page-seconds") * 1000L) {
                    if (++session.eventPage == 3) { advance(session, Step.EVENTS); continue; }
                    session.pageAt = now; session.eventSeconds = -1;
                }
                if (step == Step.EVENTS) eventTimer(session);
                if (Bukkit.getCurrentTick() % 10 == 0) guidance(session);
                if (Bukkit.getCurrentTick() % 5 == 0) player.sendActionBar(now < session.messageUntil ? session.message : instruction(session));
            } catch (RuntimeException error) { fail(session.player, error); end(session.player, false); }
        }
        if (Bukkit.getCurrentTick() % 100 == 0) for (var player : Bukkit.getOnlinePlayers()) {
            sectionRewards(player);
            if (!TutorialInventory.active(player)) rewards(player);
        }
    }
    private void guidance(Session session) {
        var player = session.player; var step = session.progress.step();
        if (territoryTitles(player)) {
            int cx = player.getLocation().getBlockX() >> 4, cz = player.getLocation().getBlockZ() >> 4;
            for (int x = cx; x <= cx; x++) for (int z = cz; z <= cz; z++) {
                var center = at(session, x * 16 + 8, 65.15, z * 16 + 8, 0);
                var identity = plugin.territories().identity(player, center);
                var color = identity.relation() == net.mandomc.gameplay.territory.TerritoryIdentity.Relation.OWN
                        ? Color.LIME : identity.zone() == net.mandomc.gameplay.territory.TerritoryPolicy.Zone.SAFEZONE ? Color.ORANGE : Color.GRAY;
                for (int n = 0; n <= 16; n += 2) {
                    dust(player, at(session, x * 16 + n, 65.15, z * 16, 0), color);
                    dust(player, at(session, x * 16 + n, 65.15, z * 16 + 16, 0), color);
                    dust(player, at(session, x * 16, 65.15, z * 16 + n, 0), color);
                    dust(player, at(session, x * 16 + 16, 65.15, z * 16 + n, 0), color);
                }
            }
        }
        if (step == Step.SELL) { shopGuidance(session); return; }
        Location goal = switch (step) {
            case WILDERNESS -> at(session, 36, 65.25, .5, 0);
            case CLAIM -> at(session, 55, 65.25, .5, 0);
            case BUY -> at(session, 119, 65.25, -32.5, 0);
            case SELL -> at(session, 119, 65.25, -8.5, 0);
            case PLACE -> at(session, 50, 65.25, 50, 0);
            case BOARD -> plugin.vehicles().entity(session.vehicle).getLocation().add(0, .3, 0);
            default -> null;
        };
        if (goal == null) return;
        var from = player.getLocation().clone(); from.setY(goal.getY());
        var delta = goal.toVector().subtract(from.toVector()); double length = delta.length();
        if (length < .2) return; delta.normalize();
        double phase = (Bukkit.getCurrentTick() % 20) / 20.0;
        for (double n = phase; n < Math.min(20, length); n += .9) dust(player, from.clone().add(delta.clone().multiply(n)), Color.AQUA);
        // Arrowhead at the destination makes direction unambiguous even while stationary.
        var side = new org.bukkit.util.Vector(-delta.getZ(), 0, delta.getX());
        for (int sign : new int[]{-1, 1}) for (double n = 0; n < 2; n += .4)
            dust(player, goal.clone().subtract(delta.clone().multiply(n)).add(side.clone().multiply(n * sign * .6)), Color.AQUA);
    }
    private void dust(Player player, Location at, Color color) {
        player.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1));
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
        var player = session.player; int bit = 1 << session.progress.section().ordinal();
        player.getPersistentDataContainer().set(SECTIONS, PersistentDataType.INTEGER, sections(player) | bit);
        player.saveData(); sectionRewards(player); hub(session);
    }
    private void sectionRewards(Player player) {
        if (!player.isOnline() || !paying.add(player.getUniqueId())) return;
        int done = sections(player), paid = player.getPersistentDataContainer().getOrDefault(PAID, PersistentDataType.INTEGER, 0);
        var pending = Arrays.stream(TutorialProgress.Section.values()).filter(section -> (done & ~paid & (1 << section.ordinal())) != 0).findFirst();
        if (pending.isEmpty()) { paying.remove(player.getUniqueId()); return; }
        var section = pending.get(); UUID id = player.getUniqueId();
        plugin.credits().auctionTask(() -> plugin.credits().ledger().rewardTutorialSection(id, section.name(), config.getLong("reward-credits") * 100), (added, error) -> {
            paying.remove(id); var online = Bukkit.getPlayer(id);
            if (error != null) { plugin.getLogger().log(java.util.logging.Level.SEVERE, "Section reward retained for retry: " + id, error); return; }
            if (online != null) {
                int receipts = online.getPersistentDataContainer().getOrDefault(PAID, PersistentDataType.INTEGER, 0);
                online.getPersistentDataContainer().set(PAID, PersistentDataType.INTEGER, receipts | (1 << section.ordinal())); online.saveData();
                if (Boolean.TRUE.equals(added)) hint(online, "reward");
                sectionRewards(online);
            }
        });
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
        for (var state : session.practiceBlocks.values()) state.update(true, false);
        session.practiceBlocks.clear(); session.targetBlocks.clear();
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
            TutorialInventory.flag(player, TutorialInventory.KIT_PENDING); player.saveData();
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
        plugin.credits().auctionTask(() -> plugin.credits().ledger().rewardTutorial(id, 5000 * 100L), (added, error) -> {
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
        var menu = new Menu(player, MenuKind.WARP, text("menus.warp"), plugin.warps());
        menu.inventory.setItem(menu.continueSlot, icon(Material.DIAMOND, "menus.tutorial")); player.openInventory(menu.inventory);
    }
    public boolean selling(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.progress.step() == Step.SELL && player.getWorld() == plugin.worlds().spawn("Batuu").getWorld();
    }
    public void sold(Player player) {
        var session = sessions.get(player.getUniqueId());
        if (session != null && selling(player)) later(session, Step.SELL, 1, () -> advance(session, Step.SELL));
    }
    public void jawa(Player player, LivingEntity entity) {
        var session = sessions.get(player.getUniqueId());
        if (session == null || entity.getWorld() != player.getWorld() || entity.getLocation().distanceSquared(player.getLocation()) > 36) { denied(player); return; }
        if (selling(player) && plugin.jawas().gray(entity)) {
            plugin.jawas().openTutorialBuyer(player, entity); return;
        }
        if (session.progress.step() == Step.BUY && session.entities.contains(entity) && mobs.type(entity).equals("JawaBrown")) {
            var menu = new Menu(player, MenuKind.BUY, text("menus.buy"), plugin.warps());
            var item = new ItemStack(Material.STRING, 16);
            item.editMeta(meta -> { meta.displayName(text("menus.saplings")); meta.lore(List.of(text("menus.buy-hint"))); });
            menu.inventory.setItem(13, item); menu.inventory.setItem(31, icon(Material.GOLD_NUGGET, "menus.wallet"));
            player.openInventory(menu.inventory);
        } else denied(player);
    }
    private boolean insideShop(Location location) {
        return location.getWorld() == plugin.worlds().spawn("Batuu").getWorld() && location.getX() >= -59 && location.getX() <= -37
                && location.getZ() >= -25 && location.getZ() <= -5 && location.getY() >= 95 && location.getY() <= 100;
    }
    private void shopGuidance(Session session) {
        var player = session.player;
        if (insideShop(player.getLocation())) player.sendActionBar(text("messages.sell-inside"));
        var points = config.getMapList("shop-route");
        if (points.size() < 2) return;
        var route = new ArrayList<Location>();
        for (var point : points) route.add(new Location(player.getWorld(), ((Number) point.get("x")).doubleValue(),
                ((Number) point.get("y")).doubleValue(), ((Number) point.get("z")).doubleValue()));
        while (session.routeIndex < route.size() - 1 && player.getLocation().distanceSquared(route.get(session.routeIndex)) < 2.25) session.routeIndex++;
        // Follow only surveyed walking segments. Never draw a direct line through shop walls.
        int start = Math.max(0, session.routeIndex - 1);
        for (int i = start; i < Math.min(route.size() - 1, session.routeIndex + 3); i++) {
            var a = route.get(i); var delta = route.get(i + 1).toVector().subtract(a.toVector()); double length = delta.length();
            if (length < .01) continue;
            for (double n = (Bukkit.getCurrentTick() % 20) / 20.0; n < length; n += .6) {
                var point = a.clone().add(delta.clone().multiply(n / length)).add(0, .35, 0);
                if (player.getLocation().distanceSquared(point) < 225) dust(player, point, Color.AQUA);
            }
        }
        // Keep the endpoint arrow visible inside the shop until the sale completes.
        var tip = route.getLast().clone().add(0, .35, 0);
        if (player.getLocation().distanceSquared(tip) >= 225) return;
        var forward = tip.toVector().subtract(route.get(route.size() - 2).toVector()).setY(0);
        if (forward.lengthSquared() < .01) return;
        forward.normalize();
        var side = new org.bukkit.util.Vector(-forward.getZ(), 0, forward.getX());
        for (double n = 0; n <= 3; n += .2)
            dust(player, tip.clone().subtract(forward.clone().multiply(n)), Color.AQUA);
        for (int sign : new int[]{-1, 1}) for (double n = .2; n <= 1.6; n += .2)
            dust(player, tip.clone().subtract(forward.clone().multiply(n))
                    .add(side.clone().multiply(n * sign * .7)), Color.AQUA);
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
            else { var current = sessions.get(player.getUniqueId()); if (current != null) hub(current); else hint(player, "loading"); }
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
                session.clan = null; plugin.territories().announce(player); advance(session, Step.DISBAND); return;
            }
        }
        if (root.equals("recipes") && parts.length == 1 && Set.of(Step.RECIPES, Step.MINING, Step.SMELTING, Step.CRAFTING).contains(step)) {
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
        if (Set.of(Step.RECIPES, Step.MINING, Step.SMELTING, Step.CRAFTING).contains(session.progress.step()) && RecipeGuide.isMenu(event.getInventory())) {
            if (session.progress.step() != Step.RECIPES || RecipeGuide.isCategory(event.getInventory(), "weapons")) session.recipeSeen = true;
            return;
        }
        if (session.progress.step() == Step.SELL && event.getInventory().getHolder() instanceof net.mandomc.gameplay.jawa.JawaSellInventory.Tray) return;
        var location = event.getInventory().getLocation();
        if (location != null && location.getWorld() == session.world && (location.getBlock().equals(session.furnace) || location.getBlock().equals(session.bench))) return;
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
            if (menu.kind == MenuKind.WARP && slot == menu.continueSlot && session.progress.step() == Step.WARP) {
                Bukkit.getScheduler().runTask(plugin, () -> { if (sessions.get(player.getUniqueId()) == session) { player.closeInventory(); advance(session, Step.WARP); } });
            } else if (menu.kind == MenuKind.BUY && slot == 13 && session.progress.step() == Step.BUY && session.wallet >= 24) {
                session.wallet -= 24; player.getInventory().setItem(0, new ItemStack(Material.STRING, 16));
                Bukkit.getScheduler().runTask(plugin, () -> { if (sessions.get(player.getUniqueId()) == session) { player.closeInventory(); advance(session, Step.BUY); } });
            } else if (menu.kind == MenuKind.SELL && session.progress.step() == Step.SELL) sellClick(session, menu, event);
            return;
        }
        if (RecipeGuide.isMenu(event.getView().getTopInventory())) return;
        if (session.progress.step() == Step.SELL && holder instanceof net.mandomc.gameplay.jawa.JawaSellInventory.Tray) return;
        if (Set.of(Step.MINING, Step.SMELTING, Step.CRAFTING).contains(session.progress.step())
                && (event.getView().getTopInventory().getType() == InventoryType.FURNACE || event.getView().getTopInventory().getType() == InventoryType.WORKBENCH)) {
            if (event.getClick() == ClickType.DROP || event.getClick() == ClickType.CONTROL_DROP) event.setCancelled(true);
            return;
        }
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
        if (event.getWhoClicked() instanceof Player player && TutorialInventory.active(player)) {
            var session = sessions.get(player.getUniqueId()); var type = event.getView().getTopInventory().getType();
            if (session != null && ((session.progress.step() == Step.SELL && event.getView().getTopInventory().getHolder() instanceof net.mandomc.gameplay.jawa.JawaSellInventory.Tray)
                    || Set.of(Step.MINING, Step.SMELTING, Step.CRAFTING).contains(session.progress.step()) && (type == InventoryType.FURNACE || type == InventoryType.WORKBENCH))) return;
            event.setCancelled(true);
        }
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
        var session = sessions.get(event.getPlayer().getUniqueId());
        // Let the normal vehicle placement handler validate this one training item and clicked surface.
        if (session != null && session.progress.step() == Step.PLACE
                && config.getString("items.vehicle").equals(plugin.items().identify(event.getItem()))) {
            event.setUseItemInHand(Event.Result.DENY); return;
        }
        if (session != null && Set.of(Step.MINING, Step.SMELTING, Step.CRAFTING).contains(session.progress.step())) {
            if (event.getClickedBlock() != null && (event.getClickedBlock().equals(session.furnace) || event.getClickedBlock().equals(session.bench))) return;
            if (session.ores.contains(event.getClickedBlock())) return;
        }
        // Block containers/blocks, but keep the weapon's item action and reload controls available.
        event.setUseInteractedBlock(Event.Result.DENY);
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
        if (session.progress.step() == Step.HUB) { choose(session, event.getRightClicked()); return; }
        if (session.vehicle != null && session.vehicle.equals(plugin.vehicles().id(event.getRightClicked()))) {
            board(event.getPlayer(), session.vehicle); return;
        }
        if (event.getRightClicked() instanceof LivingEntity living && (session.entities.contains(living) || session.progress.step() == Step.SELL)) jawa(event.getPlayer(), living);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent hit && hit.getDamager() instanceof Player player) {
            var session = sessions.get(player.getUniqueId());
            if (session != null && session.progress.step() == Step.HUB) { event.setCancelled(true); choose(session, event.getEntity()); return; }
        }
        if (event.getEntity() instanceof Player player && TutorialInventory.active(player)) { event.setCancelled(true); return; }
        if (!TutorialInventory.world(event.getEntity().getWorld())) return;
        // Only the current player's owned guards are damageable, and only during combat.
        Entity attacker = event.getDamageSource().getCausingEntity();
        var session = attacker instanceof Player player ? sessions.get(player.getUniqueId()) : null;
        if (session != null && session.progress.step() == Step.SABER && !plugin.sabers().dealingThrowDamage(session.player)) { event.setCancelled(true); return; }
        if (session == null || !Set.of(Step.COMBAT, Step.SABER).contains(session.progress.step()) || !session.guards.contains(event.getEntity().getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void died(EntityDeathEvent event) {
        if (!TutorialInventory.world(event.getEntity().getWorld())) return;
        event.getDrops().clear(); event.setDroppedExp(0);
        for (var session : List.copyOf(sessions.values())) if (session.guards.remove(event.getEntity().getUniqueId())) {
            if (session.progress.step() == Step.SABER) break;
            if (session.player.equals(event.getDamageSource().getCausingEntity()) || session.player.equals(event.getEntity().getKiller())) {
                if (session.progress.kill(config.getInt("guards"))) later(session, Step.RECIPES, 1, () -> enter(session));
            } else later(session, Step.COMBAT, 1, () -> spawnGuard(session, session.guards.size()));
            break;
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void food(FoodLevelChangeEvent event) { if (event.getEntity() instanceof Player p && TutorialInventory.active(p)) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent event) { if (TutorialInventory.active(event.getPlayer())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void pickup(EntityPickupItemEvent event) { if (event.getEntity() instanceof Player player && TutorialInventory.active(player)) { var session = sessions.get(player.getUniqueId()); if (session == null || session.progress.step() != Step.MINING) event.setCancelled(true); } }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void breakBlock(BlockBreakEvent event) {
        if (!TutorialInventory.active(event.getPlayer()) && !TutorialInventory.world(event.getBlock().getWorld())) return;
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.progress.step() == Step.MINING && session.ores.contains(event.getBlock())
                && event.getPlayer().getInventory().getItemInMainHand().getType() == Material.STONE_PICKAXE && !event.isCancelled()) return;
        event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mined(BlockBreakEvent event) {
        var session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null || session.progress.step() != Step.MINING || !session.ores.contains(event.getBlock())) return;
        later(session, Step.MINING, 1, () -> {
            if (event.getBlock().getType().isAir() && session.ores.remove(event.getBlock()) && ++session.mined == 3) {
                // Collect genuine native ore drops before changing the lesson's pickup permissions.
                for (var entity : session.world.getNearbyEntities(event.getBlock().getLocation(), 6, 3, 6))
                    if (entity instanceof Item item && "agrinium_scrap".equals(plugin.items().identify(item.getItemStack()))) {
                        session.player.getInventory().addItem(item.getItemStack()); item.remove();
                    }
                advance(session, Step.MINING);
            }
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void requireRecipe(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var session = sessions.get(player.getUniqueId());
        if (session != null && Set.of(Step.MINING, Step.SMELTING, Step.CRAFTING).contains(session.progress.step())
                && (session.progress.step() != Step.CRAFTING || !session.recipeSeen)) { event.setCancelled(true); hint(player, "open-recipes"); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void crafted(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var session = sessions.get(player.getUniqueId());
        if (session == null || session.progress.step() != Step.CRAFTING || !session.recipeSeen
                || !"agrinium_pickaxe".equals(plugin.items().identify(event.getCurrentItem()))) return;
        later(session, Step.CRAFTING, 1, () -> {
            if (player.getInventory().containsAtLeast(plugin.items().create("agrinium_pickaxe"), 1)
                    || "agrinium_pickaxe".equals(plugin.items().identify(player.getItemOnCursor()))) advance(session, Step.CRAFTING);
        });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(BlockPlaceEvent event) { if (TutorialInventory.active(event.getPlayer()) || TutorialInventory.world(event.getBlock().getWorld())) event.setCancelled(true); }
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
        if (flightStep(step) || step == Step.SELL) return;
        boolean outside = to.getY() < 64 || to.getY() > 77 || switch (step) {
            case HUB, WARP -> to.getX() < -3 || to.getX() > 30 || Math.abs(to.getZ()) > 12;
            case MINING, SMELTING, CRAFTING -> to.getX() < 36 || to.getX() > 50 || to.getZ() < 18 || to.getZ() > 31;
            case MAP -> to.getX() < 0 || to.getX() >= 30 || Math.abs(to.getZ()) > 10;
            case WILDERNESS, CREATE, CLAIM, DISBAND -> to.getX() < 0 || to.getX() >= 64 || Math.abs(to.getZ()) > 10;
            case COMBAT, SABER -> to.getX() < 64 || to.getX() > 90 || Math.abs(to.getZ()) > 16;
            case PLACE -> to.getX() < 34 || to.getX() > 66 || to.getZ() < 34 || to.getZ() > 66;
            case BUY -> to.getX() < 100 || to.getX() > 132 || to.getZ() < -43 || to.getZ() > -21;
            case SELL -> to.getX() < 100 || to.getX() > 132 || to.getZ() < -20 || to.getZ() > 3;
            default -> to.getX() < 0 || to.getX() > 30 || Math.abs(to.getZ()) > 10;
        };
        if (outside) { event.setTo(event.getFrom()); hint(event.getPlayer(), "follow-particles"); }
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
