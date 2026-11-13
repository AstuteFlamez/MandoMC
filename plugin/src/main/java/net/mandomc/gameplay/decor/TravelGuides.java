package net.mandomc.gameplay.decor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.territory.TerritoryService;
import org.bukkit.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Native guide NPCs, under the existing decor lifecycle; commands retain menu/book authority. */
public final class TravelGuides implements Listener, AutoCloseable {
    public static final NamespacedKey MARKER = new NamespacedKey("mandomc", "travel_guide_id");
    public static final NamespacedKey DAILY = new NamespacedKey("mandomc", "cassian_handbook_day");
    private static final NamespacedKey BOOK = new NamespacedKey("mandomc", "tutorial_handbook");
    private record Guide(String id, Location at, String title, String texture, String signature) {}
    private record Live(Guide guide, Mannequin body, TextDisplay title) {}
    private final MandoPlugin plugin;
    private final YamlConfiguration config;
    private final Clock clock;
    private final List<Guide> guides = new ArrayList<>();
    private final Map<String, Live> live = new LinkedHashMap<>();
    private final Set<Chunk> chunks = new HashSet<>();
    private final Set<UUID> internal = new HashSet<>();
    private record Click(UUID player, String guide) {}
    private final Map<Click, Integer> clicked = new HashMap<>();
    private final Map<UUID, Integer> lastHint = new HashMap<>();
    private final List<String> hints;
    private final PluginCommand warp;
    private final PluginCommand tutorial;
    private final BukkitTask task;
    private boolean closed;

    public TravelGuides(MandoPlugin plugin, YamlConfiguration config) {
        this(plugin, config, Clock.system(ZoneId.of(config.getString("time-zone", "America/Chicago"))));
    }
    TravelGuides(MandoPlugin plugin, YamlConfiguration config, Clock clock) {
        this.plugin = plugin; this.config = config; this.clock = clock;
        if (config.getInt("schema-version") != 1) throw new IllegalArgumentException("travel-guides schema-version 1 required");
        hints = config.getStringList("hints");
        if (hints.size() < 2) throw new IllegalArgumentException("travel-guides requires at least two hints");
        warp = Objects.requireNonNull(plugin.getCommand("warp"));
        tutorial = Objects.requireNonNull(plugin.getCommand("tutorial"));
        if (warp.getPlugin() != plugin || tutorial.getPlugin() != plugin) throw new IllegalStateException("Guide commands must retain native owners");
        for (String id : List.of("rebel_pilot", "cassian_andor")) {
            ConfigurationSection c = Objects.requireNonNull(config.getConfigurationSection("npcs." + id));
            World world = Objects.requireNonNull(Bukkit.getWorld(Objects.requireNonNull(NamespacedKey.fromString(c.getString("world", "")))));
            for (String key : List.of("x", "y", "z", "yaw", "pitch"))
                if (!c.isDouble(key) && !c.isInt(key)) throw new IllegalArgumentException("Missing guide coordinate " + id + "." + key);
            Location at = new Location(world, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"), (float)c.getDouble("yaw"), (float)c.getDouble("pitch"));
            if (!Double.isFinite(at.getX()) || !Double.isFinite(at.getY()) || !Double.isFinite(at.getZ())
                    || !Float.isFinite(at.getYaw()) || !Float.isFinite(at.getPitch()) || Math.abs(at.getPitch()) > 90
                    || !world.getWorldBorder().isInside(at) || at.getY() < world.getMinHeight() || at.getY() + 3 >= world.getMaxHeight())
                throw new IllegalArgumentException("Invalid guide location " + id);
            for (String key : List.of("title", "texture", "signature"))
                if (c.getString(key, "").isBlank()) throw new IllegalArgumentException("Missing guide " + id + "." + key);
            guides.add(new Guide(id, at, c.getString("title"), c.getString("texture"), c.getString("signature")));
        }
        try {
            for (Guide guide : guides) {
                Chunk chunk = guide.at.getChunk();
                if (chunk.addPluginChunkTicket(plugin)) chunks.add(chunk);
            }
            for (World world : Bukkit.getWorlds()) for (Entity entity : List.copyOf(world.getEntities())) removeStale(entity);
            guides.forEach(this::spawn);
        } catch (RuntimeException error) { removeAll(); throw error; }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2, 2);
        plugin.getLogger().info("Travel guides ready: Rebel pilot and Cassian Andor at saved anchors.");
    }
    public static String day(Clock clock) { return LocalDate.now(clock).toString(); }
    public Map<String, Mannequin> bodies() {
        Map<String, Mannequin> result = new LinkedHashMap<>(); live.forEach((id, v) -> result.put(id, v.body)); return Map.copyOf(result);
    }
    public Map<String, TextDisplay> titles() {
        Map<String, TextDisplay> result = new LinkedHashMap<>(); live.forEach((id, v) -> result.put(id, v.title)); return Map.copyOf(result);
    }
    private void mark(Entity entity, String id) { entity.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, id); }
    private void spawn(Guide guide) {
        TerritoryService territory = Objects.requireNonNull(Bukkit.getServicesManager().load(TerritoryService.class));
        Mannequin body = territory.spawnNpc(EntityType.MANNEQUIN, guide.at, () -> guide.at.getWorld().spawn(guide.at, Mannequin.class, e -> {
            var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("mandomc:guide:" + guide.id).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", guide.texture, guide.signature));
            e.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(profile));
            e.setDescription(Component.empty()); e.setImmovable(true); e.setGravity(false); e.setInvulnerable(true);
            e.setSilent(true); e.setPersistent(false); e.setRemoveWhenFarAway(false); e.setCollidable(false);
            e.setCanPickupItems(false); e.setCustomNameVisible(false); mark(e, guide.id);
        }));
        try {
            TextDisplay title = guide.at.getWorld().spawn(guide.at.clone().add(0, 2.25, 0), TextDisplay.class, e -> {
                mark(e, guide.id + "_title"); e.text(Component.text(guide.title, NamedTextColor.YELLOW));
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); e.setDefaultBackground(false);
                e.setBillboard(Display.Billboard.CENTER); e.setShadowed(true); e.setPersistent(false);
                e.setInvulnerable(true); e.setGravity(false); e.setViewRange(.6f);
            });
            live.put(guide.id, new Live(guide, body, title));
        } catch (RuntimeException error) { body.remove(); throw error; }
    }
    private void tick() {
        if (closed) return;
        for (Guide guide : guides) {
            Live value = live.get(guide.id);
            if (value == null || !value.body.isValid() || !value.title.isValid()) {
                if (value != null) { value.body.remove(); value.title.remove(); }
                spawn(guide); continue;
            }
            Mannequin body = value.body;
            if (!body.getWorld().equals(guide.at.getWorld()) || body.getLocation().distanceSquared(guide.at) > .000001) {
                internal.add(body.getUniqueId()); try { body.teleport(guide.at); } finally { internal.remove(body.getUniqueId()); }
            }
            Player viewer = guide.at.getWorld().getPlayers().stream()
                    .filter(p -> p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR)
                    .filter(p -> p.getMetadata("vanished").stream().noneMatch(v -> v.asBoolean()))
                    .filter(p -> p.getLocation().distanceSquared(guide.at) < 25)
                    .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(guide.at))).orElse(null);
            Location direction = viewer == null ? guide.at : guide.at.clone().setDirection(viewer.getEyeLocation().toVector().subtract(body.getEyeLocation().toVector()));
            var pose = DecorLook.step(body.getBodyYaw(), body.getLocation().getYaw(), body.getLocation().getPitch(), direction.getYaw(), direction.getPitch(), viewer != null);
            body.setRotation(pose.head(), pose.pitch()); body.setBodyYaw(pose.body()); body.setVelocity(new org.bukkit.util.Vector());
            body.setFireTicks(0);
        }
        clicked.entrySet().removeIf(e -> Bukkit.getCurrentTick() - e.getValue() > 1200);
    }
    private boolean owned(Entity entity) { return entity.getPersistentDataContainer().has(MARKER, PersistentDataType.STRING); }
    private void removeStale(Entity entity) {
        if (owned(entity) && live.values().stream().noneMatch(v -> v.body.getUniqueId().equals(entity.getUniqueId()) || v.title.getUniqueId().equals(entity.getUniqueId()))) entity.remove();
    }
    @EventHandler public void chunk(ChunkLoadEvent event) { for (Entity entity : event.getChunk().getEntities()) removeStale(entity); }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(PlayerInteractEntityEvent event) {
        if (!owned(event.getRightClicked())) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        Live guide = live.values().stream().filter(v -> v.body.getUniqueId().equals(event.getRightClicked().getUniqueId())).findFirst().orElse(null);
        Player player = event.getPlayer();
        if (guide == null || player.getGameMode() == GameMode.SPECTATOR || !player.getWorld().equals(guide.body.getWorld())
                || player.getLocation().distanceSquared(guide.body.getLocation()) > 36) return;
        int now = Bukkit.getCurrentTick();
        var click = new Click(player.getUniqueId(), guide.guide.id);
        int delay = guide.guide.id.equals("cassian_andor") ? 60 : 10;
        if (now - clicked.getOrDefault(click, now - delay) < delay) return;
        clicked.put(click, now);
        if (guide.guide.id.equals("rebel_pilot")) {
            if (warp.testPermission(player)) warp.execute(player, "warp", new String[0]);
        } else cassian(player);
    }
    private boolean hasBook(Player player) {
        for (var item : player.getInventory().getContents())
            if (item != null && item.getType() == Material.WRITTEN_BOOK && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(BOOK, PersistentDataType.BYTE)) return true;
        return false;
    }
    private void cassian(Player player) {
        String today = day(clock);
        if (!today.equals(player.getPersistentDataContainer().get(DAILY, PersistentDataType.STRING))) {
            if (!player.getItemOnCursor().isEmpty() || player.getOpenInventory().getTopInventory() instanceof org.bukkit.inventory.CraftingInventory crafting
                    && Arrays.stream(crafting.getMatrix()).anyMatch(i -> i != null && !i.isEmpty())) { say(player, config.getString("messages.busy")); return; }
            if (!tutorial.testPermission(player)) return;
            // Execute the actual registered tutorial owner. Its refresh/full-inventory policy remains authoritative.
            tutorial.execute(player, "tutorial", new String[0]);
            if (hasBook(player)) {
                player.getPersistentDataContainer().set(DAILY, PersistentDataType.STRING, today);
                player.saveData(); say(player, config.getString("messages.delivered"));
            } else say(player, config.getString("messages.full"));
            return;
        }
        int previous = lastHint.getOrDefault(player.getUniqueId(), -1);
        int next = java.util.concurrent.ThreadLocalRandom.current().nextInt(previous < 0 ? hints.size() : hints.size() - 1);
        if (previous >= 0 && next >= previous) next++;
        lastHint.put(player.getUniqueId(), next); say(player, hints.get(next));
    }
    private void say(Player player, String line) {
        Component text = Component.text("Cassian Andor", NamedTextColor.GOLD).append(Component.text(" » ", NamedTextColor.DARK_GRAY)).append(Component.text(line, NamedTextColor.GRAY));
        try {
            Class<?> type = Class.forName("net.mandomc.gameplay.messaging.MessageService", true, plugin.getClass().getClassLoader());
            Object service = Bukkit.getServicesManager().load(type);
            if (service != null) text = (Component)type.getMethod("known", String.class, String.class, Component.class).invoke(service, "decor/travel-guides.yml", "cassian-line", text);
        } catch (ClassNotFoundException absent) { /* Optional presentation integration. */ }
        catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot format Cassian dialogue", error); }
        player.sendMessage(text);
    }
    @EventHandler public void quit(PlayerQuitEvent e) { clicked.keySet().removeIf(c -> c.player.equals(e.getPlayer().getUniqueId())); lastHint.remove(e.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.LOWEST) public void damage(EntityDamageEvent e) { if (owned(e.getEntity())) e.setCancelled(true); }
    @EventHandler public void death(EntityDeathEvent e) { if (owned(e.getEntity())) { e.getDrops().clear(); e.setDroppedExp(0); } }
    @EventHandler(priority = EventPriority.HIGHEST) public void mount(EntityMountEvent e) { if (owned(e.getMount()) || owned(e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void teleport(EntityTeleportEvent e) { if (owned(e.getEntity()) && !internal.contains(e.getEntity().getUniqueId())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void combust(EntityCombustEvent e) { if (owned(e.getEntity())) e.setCancelled(true); }
    @EventHandler public void target(EntityTargetEvent e) { if (owned(e.getEntity()) || e.getTarget() != null && owned(e.getTarget())) e.setCancelled(true); }
    private void removeAll() {
        live.values().forEach(v -> { v.body.remove(); v.title.remove(); }); live.clear();
        chunks.forEach(c -> c.removePluginChunkTicket(plugin)); chunks.clear();
    }
    @Override public void close() { closed = true; task.cancel(); HandlerList.unregisterAll(this); removeAll(); clicked.clear(); lastHint.clear(); }
}
