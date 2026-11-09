package net.mandomc.gameplay.vehicles;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.config.AtomicData;
import net.mandomc.gameplay.combat.CombatService;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.gameplay.ui.MenuItems;
import net.mandomc.integrations.regions.VehicleProtection;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Owns vehicle identity, health persistence and seats. Native living damage owns hit application.
 */
public final class VehicleService implements Listener, AutoCloseable {
    public static final NamespacedKey ID = new NamespacedKey("mandomc", "vehicle_id");
    private static final NamespacedKey TOKEN = new NamespacedKey("mandomc", "vehicle_token");

    private enum State {
        PACKED,
        DEPLOYED,
        DESTROYED
    }

    private static final class Record {
        UUID id, owner, token;
        String type;
        State state;
        double health;
        Location location;
        long hitUntil;
    }

    private record Live(Record record, LivingEntity entity, VehicleModels.Handle model) {}

    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final CombatService combat;
    private final VehicleModels models;
    private final VehicleProtection protection;
    private final net.mandomc.gameplay.explosions.TemporaryExplosions explosions;
    private final ConfigurationSection messages;
    private final Map<String, VehicleDefinition> definitions = new LinkedHashMap<>();
    private final Map<UUID, Record> records = new LinkedHashMap<>();
    private final Map<UUID, Live> active = new LinkedHashMap<>();
    private final Map<UUID, UUID> entities = new HashMap<>();
    private final Map<UUID, Entity> attackers = new HashMap<>();
    private final Map<UUID, Integer> interactions = new HashMap<>();

    private record Ticket(World world, int x, int z) {}

    private final Set<Ticket> tickets = new HashSet<>();
    private final Path journal;
    private final BukkitTask task, restoreTask, menuTask;
    private final Map<String, ItemStack> emptySeatHeads = new HashMap<>();
    private final Map<String, String> emptySeatTitles = new HashMap<>();
    private boolean closed;
    private final int maxActive, combatSeconds;
    private final double packSpeedSquared;

    public VehicleService(
            JavaPlugin plugin,
            ItemCatalog items,
            CombatService combat,
            VehicleModels models,
            VehicleProtection protection,
            net.mandomc.gameplay.explosions.TemporaryExplosions explosions,
            ConfigurationSection messages)
            throws Exception {
        this.plugin = plugin;
        this.items = items;
        this.combat = combat;
        this.models = models;
        this.protection = protection;
        this.explosions = explosions;
        this.messages = Objects.requireNonNull(messages);
        for (String id : List.of("speederbike", "xwing", "tiefighter", "uwing")) {
            String path = "vehicles/" + id + ".yml";
            if (!new java.io.File(plugin.getDataFolder(), path).exists())
                plugin.saveResource(path, false);
            var data = new YamlConfiguration();
            data.load(new java.io.File(plugin.getDataFolder(), path));
            if (id.equals("uwing") && !data.contains("gunner-seats")) {
                var seats = new ArrayList<>(data.getStringList("passenger-seats"));
                if (seats.remove("seatone")) {
                    data.set("passenger-seats", seats);
                    data.set("gunner-seats", List.of("seatone"));
                    data.save(new java.io.File(plugin.getDataFolder(), path));
                }
            }
            definitions.put(id, VehicleDefinition.read(id, data));
        }
        String settingsPath = "vehicles/settings.yml";
        if (!new java.io.File(plugin.getDataFolder(), settingsPath).exists())
            plugin.saveResource(settingsPath, false);
        var settings = new YamlConfiguration();
        settings.load(new java.io.File(plugin.getDataFolder(), settingsPath));
        maxActive = settings.getInt("max-deployed");
        combatSeconds = settings.getInt("combat-seconds");
        double packSpeed = settings.getDouble("packing-max-horizontal-speed");
        packSpeedSquared = packSpeed * packSpeed;
        if (settings.getInt("schema-version") != 1
                || maxActive < 1
                || maxActive > 128
                || combatSeconds < 1
                || combatSeconds > 300
                || !Double.isFinite(packSpeed)
                || packSpeed < 0
                || packSpeed > .2)
            throw new IllegalArgumentException("vehicles/settings.yml: invalid limits");
        String menuPath = "vehicles/menu.yml";
        if (!new java.io.File(plugin.getDataFolder(), menuPath).exists()) plugin.saveResource(menuPath, false);
        var menu = YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(), menuPath));
        if (menu.getInt("schema-version") != 1) throw new IllegalArgumentException("vehicles/menu.yml: expected schema 1");
        for (String key : List.of("rebel-pilot", "tie-pilot", "speeder-pilot", "rebel-gunner")) {
            String texture = menu.getString("heads." + key + ".texture", "");
            String title = menu.getString("heads." + key + ".title", "");
            if (!texture.matches("[0-9a-f]{32,64}") || title.isBlank())
                throw new IllegalArgumentException("vehicles/menu.yml: invalid head " + key);
            var head = new ItemStack(Material.PLAYER_HEAD);
            var meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
            var profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/" + texture + "\"}}}";
            profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures",
                    Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            meta.setPlayerProfile(profile);
            head.setItemMeta(meta);
            emptySeatHeads.put(key, head);
            emptySeatTitles.put(key, title);
        }
        journal = plugin.getDataFolder().toPath().resolve("data/vehicles.yml");
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        restoreTask = Bukkit.getScheduler().runTaskLater(plugin, this::restore, 20);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
        menuTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshSeatMenus, 2, 2);
    }

    public Set<String> types() {
        return Collections.unmodifiableSet(definitions.keySet());
    }

    public boolean managed(Entity entity) {
        return entity != null && entities.containsKey(entity.getUniqueId());
    }

    public int activeCount() {
        return active.size();
    }

    public LivingEntity entity(UUID id) {
        var live = active.get(id);
        return live == null ? null : live.entity;
    }

    public UUID identity(Entity e) {
        return entities.get(e.getUniqueId());
    }

    public boolean mayDamage(Entity victim, Entity attacker) {
        var live = live(victim);
        if (live == null) return true;
        return protection.damage(victim.getLocation())
                && (attacker == null || protection.damage(attacker.getLocation()))
                && (attacker == null || !live.model.riders().contains(attacker));
    }

    private Live live(Entity entity) {
        return entity == null ? null : active.get(entities.get(entity.getUniqueId()));
    }

    private void message(Player player, String key) {
        player.sendMessage(
                MiniMessage.miniMessage()
                        .deserialize(
                                Objects.requireNonNull(
                                        messages.getString(key), "vehicles." + key)));
    }

    private void save() {
        var data = new YamlConfiguration();
        data.set("schema-version", 1);
        for (Record r : records.values()) {
            String p = "vehicles." + r.id + ".";
            data.set(p + "owner", r.owner.toString());
            data.set(p + "token", r.token.toString());
            data.set(p + "type", r.type);
            data.set(p + "state", r.state.name());
            data.set(p + "health", r.health);
            data.set(p + "hit-until", r.hitUntil);
            if (r.location != null) {
                data.set(p + "world", r.location.getWorld().getUID().toString());
                data.set(p + "x", r.location.getX());
                data.set(p + "y", r.location.getY());
                data.set(p + "z", r.location.getZ());
                data.set(p + "yaw", r.location.getYaw());
            }
        }
        try {
            AtomicData.write(journal, data.saveToString());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot save vehicle lifecycle journal", e);
        }
    }

    private void load() throws Exception {
        if (!Files.exists(journal)) return;
        var data = new YamlConfiguration();
        data.load(journal.toFile());
        if (data.getInt("schema-version") != 1)
            throw new IllegalArgumentException("Invalid vehicle journal schema");
        var section = data.getConfigurationSection("vehicles");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            var c = section.getConfigurationSection(id);
            var r = new Record();
            r.id = UUID.fromString(id);
            r.owner = UUID.fromString(c.getString("owner"));
            r.token = UUID.fromString(c.getString("token"));
            r.type = c.getString("type");
            r.state = State.valueOf(c.getString("state"));
            r.health = c.getDouble("health");
            r.hitUntil = c.getLong("hit-until");
            var d = definitions.get(r.type);
            if (d == null || !Double.isFinite(r.health) || r.health < 0 || r.health > d.health())
                throw new IllegalArgumentException("Invalid vehicle record: " + id);
            if (c.contains("world")) {
                World world = Bukkit.getWorld(UUID.fromString(c.getString("world")));
                if (world == null)
                    throw new IllegalStateException("Vehicle world not loaded for " + id);
                r.location =
                        new Location(
                                world,
                                c.getDouble("x"),
                                c.getDouble("y"),
                                c.getDouble("z"),
                                (float) c.getDouble("yaw"),
                                0);
                r.location.checkFinite();
            }
            if (r.state == State.DEPLOYED && (r.location == null || r.health <= 0))
                throw new IllegalArgumentException("Invalid deployed vehicle " + id);
            records.put(r.id, r);
        }
    }

    private void restore() {
        try {
            for (Record r : records.values()) if (r.state == State.DEPLOYED) spawn(r);
        } catch (RuntimeException error) {
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Vehicle restoration failed; repair models and restart",
                            error);
        }
    }

    private void spawn(Record r) {
        var d = definitions.get(r.type);
        holdChunks(r.location);
        var pig =
                r.location
                        .getWorld()
                        .spawn(
                                r.location,
                                Pig.class,
                                p -> {
                                    p.setAdult();
                                    p.setAgeLock(true);
                                    p.setAI(true);
                                    Bukkit.getMobGoals().removeAllGoals(p);
                                    p.setSilent(true);
                                    p.setPersistent(false);
                                    p.setRemoveWhenFarAway(false);
                                    p.setCollidable(false);
                                    p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(d.health());
                                    p.setHealth(r.health);
                                    p.getAttribute(Attribute.MOVEMENT_SPEED)
                                            .setBaseValue(d.speed());
                                    p.setMaximumNoDamageTicks(0);
                                    p.getPersistentDataContainer()
                                            .set(ID, PersistentDataType.STRING, r.id.toString());
                                });
        try {
            var handle =
                    models.attach(
                            pig,
                            d,
                            new VehicleModels.Access() {
                                @Override
                                public boolean mayPilot(Player player) {
                                    return !closed
                                            && r.state == State.DEPLOYED
                                            && r.owner.equals(player.getUniqueId());
                                }

                                @Override
                                public boolean mayMove(Location from, Location to) {
                                    return movement(r.id, from, to);
                                }

                                @Override
                                public void interact(Player p) {
                                    interactWith(p, pig);
                                }
                            });
            active.put(r.id, new Live(r, pig, handle));
            entities.put(pig.getUniqueId(), r.id);
        } catch (RuntimeException | LinkageError error) {
            pig.remove();
            throw error;
        }
    }

    private boolean movement(UUID id, Location from, Location to) {
        var live = active.get(id);
        if (live == null || !from.getWorld().equals(to.getWorld())) return false;
        boolean tagged = live.model.riders().stream().anyMatch(combat::tagged);
        int samples = Math.max(1, (int) Math.ceil(from.distance(to) * 4));
        for (int i = 1; i <= samples; i++) {
            var point =
                    from.clone()
                            .add(
                                    to.toVector()
                                            .subtract(from.toVector())
                                            .multiply((double) i / samples));
            if (!point.getWorld().isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4)
                    || !point.getWorld().getWorldBorder().isInside(point)
                    || point.getY() < point.getWorld().getMinHeight()
                    || point.getY() >= point.getWorld().getMaxHeight() - 2
                    || (tagged && !protection.damage(point))) return false;
            if (tagged)
                for (Player rider : live.model.riders()) {
                    var riderPoint =
                            rider.getLocation().add(point.toVector().subtract(from.toVector()));
                    if (!protection.damage(riderPoint)) return false;
                }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND
                || e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        String type = items.identify(item);
        if (!definitions.containsKey(type)) return;
        // A denied block interaction must never be used to place a vehicle through protection.
        if (e.useInteractedBlock() == Event.Result.DENY) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        var location =
                e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(.5, 0, .5);
        location.setYaw(p.getLocation().getYaw());
        deploy(p, item, location);
    }

    public UUID deploy(Player p, ItemStack item, Location location) {
        String type = items.identify(item);
        if (!definitions.containsKey(type)) return null;
        if (p.isInsideVehicle()
                || combat.tagged(p)
                || !protection.blocks(location)
                || !protection.damage(location)
                || !location.getBlock().isPassable()
                || !location.clone().add(0, 1, 0).getBlock().isPassable()) {
            message(p, "placement-denied");
            return null;
        }
        if (active.size() >= maxActive
                || records.values().stream()
                        .anyMatch(
                                r ->
                                        r.state == State.DEPLOYED
                                                && r.owner.equals(p.getUniqueId()))) {
            message(p, "already-deployed");
            return null;
        }
        Record r;
        var tags = item.getItemMeta().getPersistentDataContainer();
        String raw = tags.get(ID, PersistentDataType.STRING);
        if (raw != null) {
            try {
                r = records.get(UUID.fromString(raw));
            } catch (IllegalArgumentException ex) {
                message(p, "invalid");
                return null;
            }
            if (r == null
                    || r.state != State.PACKED
                    || !r.type.equals(type)
                    || !r.token.toString().equals(tags.get(TOKEN, PersistentDataType.STRING))) {
                message(p, "invalid");
                return null;
            }
        } else {
            r = new Record();
            r.id = UUID.randomUUID();
            r.owner = p.getUniqueId();
            r.token = UUID.randomUUID();
            r.type = type;
            r.state = State.PACKED;
            r.health = definitions.get(type).health();
            records.put(r.id, r);
        }
        UUID previousOwner = r.owner;
        r.owner = p.getUniqueId();
        r.location = location.clone();
        try {
            spawn(r);
            r.state = State.DEPLOYED;
            save();
        } catch (RuntimeException error) {
            var spawned = active.remove(r.id);
            if (spawned != null) {
                entities.remove(spawned.entity.getUniqueId());
                spawned.model.remove();
                spawned.entity.remove();
            }
            r.state = State.PACKED;
            r.owner = previousOwner;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Cannot deploy " + type, error);
            message(p, "unavailable");
            return null;
        }
        item.subtract(1);
        p.saveData();
        message(p, "deployed");
        return r.id;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent e) {
        if (!managed(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) interactWith(e.getPlayer(), e.getRightClicked());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interactAt(PlayerInteractAtEntityEvent e) {
        if (!managed(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) interactWith(e.getPlayer(), e.getRightClicked());
    }

    public void interactWith(Player p, Entity entity) {
        var live = live(entity);
        if (live == null
                || p.getWorld() != entity.getWorld()
                || p.getLocation().distanceSquared(entity.getLocation()) > 400) return;
        if (interactions.getOrDefault(p.getUniqueId(), -1) == Bukkit.getCurrentTick()) return;
        interactions.put(p.getUniqueId(), Bukkit.getCurrentTick());
        if (p.isSneaking()) {
            pack(p, live.record.id);
            return;
        }
        p.openInventory(new Seats(live.record.id).getInventory());
    }

    public boolean mount(Player p, UUID id, boolean driver) {
        return mount(p, id, driver ? VehicleModels.Seat.PILOT : VehicleModels.Seat.PASSENGER);
    }

    public record FiringPlatform(UUID id, String type, LivingEntity entity, List<Location> muzzles,
            org.bukkit.util.Vector direction, Set<UUID> excluded) {}

    public String mountedType(Player player) {
        for (Live live : active.values()) if (live.model.riders().contains(player)) return live.record.type;
        return null;
    }

    public FiringPlatform firingPlatform(Player player, List<String> bones) {
        for (Live live : active.values()) {
            if (!live.entity.isValid() || live.entity.isDead() || !live.model.riders().contains(player)) continue;
            if (!player.equals(live.model.driver()) && !live.model.mayShoot(player)) return null;
            var excluded = new HashSet<UUID>();
            live.model.riders().forEach(p -> excluded.add(p.getUniqueId()));
            excluded.add(live.entity.getUniqueId());
            excluded.add(live.record.owner);
            var muzzles = new ArrayList<Location>();
            for (String bone : bones) {
                var location = live.model.muzzle(bone);
                if (location == null) return null; // Missing cannon bones fail closed.
                muzzles.add(location);
            }
            return new FiringPlatform(live.record.id, live.record.type, live.entity,
                    List.copyOf(muzzles), live.model.aim(), Set.copyOf(excluded));
        }
        return null;
    }

    public boolean occupant(UUID id, UUID player) {
        var live = active.get(id);
        return live != null && live.model.riders().stream().anyMatch(p -> p.getUniqueId().equals(player));
    }

    public UUID id(Entity entity) {
        var live = live(entity);
        return live == null ? null : live.record.id;
    }

    public boolean mayShoot(Player player) {
        for (Live live : active.values()) {
            if (live.model.riders().contains(player)) return live.model.mayShoot(player);
        }
        return true;
    }

    public boolean mount(Player p, UUID id, VehicleModels.Seat seat) {
        return mount(p, id, seat, null);
    }

    private boolean mount(Player p, UUID id, VehicleModels.Seat seat, String seatId) {
        boolean driver = seat == VehicleModels.Seat.PILOT;
        var live = active.get(id);
        // ModelEngine seats are virtual: Bukkit's isInsideVehicle alone cannot detect them.
        if (active.values().stream().anyMatch(v -> v != live && v.model.riders().contains(p))) {
            message(p, "occupied");
            return false;
        }
        if (live == null
                || p.isDead()
                || (p.isInsideVehicle() && !live.model.riders().contains(p))
                || p.getWorld() != live.entity.getWorld()
                || p.getLocation().distanceSquared(live.entity.getLocation()) > 400) return false;
        if (driver && !live.record.owner.equals(p.getUniqueId())) {
            message(p, "owner-only");
            return false;
        }
        if (driver && live.model.driver() != null) {
            message(p, "occupied");
            return false;
        }
        if (combat.tagged(p) && !protection.damage(live.entity.getLocation())) {
            message(p, "combat");
            return false;
        }
        boolean mounted = live.model.mount(p, seat, seatId);
        message(
                p,
                mounted
                        ? (seat == VehicleModels.Seat.GUNNER
                                ? "gunner-controls"
                                : seat == VehicleModels.Seat.PASSENGER
                                        ? "passenger-controls"
                                        : definitions.get(live.record.type).aerial()
                                                ? "flight-controls"
                                                : "ground-controls")
                        : "occupied");
        if (mounted && seat != VehicleModels.Seat.PASSENGER) message(p, "cannon-controls");
        return mounted;
    }

    public boolean pack(Player p, UUID id) {
        var live = active.get(id);
        if (live == null) return false;
        if (!live.record.owner.equals(p.getUniqueId())) {
            message(p, "owner-only");
            return false;
        }
        if (!live.model.riders().isEmpty()
                || live.entity.getVelocity().setY(0).lengthSquared() > packSpeedSquared
                || !VehicleModels.grounded(live.entity)) {
            message(p, "pack-stationary");
            return false;
        }
        if (combat.tagged(p) || live.record.hitUntil > System.currentTimeMillis()) {
            message(p, "combat");
            return false;
        }
        if (p.getInventory().firstEmpty() < 0) {
            message(p, "inventory-full");
            return false;
        }
        var r = live.record;
        r.health = live.entity.getHealth();
        r.location = live.entity.getLocation();
        r.token = UUID.randomUUID();
        r.state = State.PACKED;
        try {
            save();
        } catch (RuntimeException ex) {
            r.state = State.DEPLOYED;
            throw ex;
        }
        remove(live);
        p.getInventory().addItem(packed(r));
        p.saveData();
        message(p, "packed");
        return true;
    }

    private ItemStack packed(Record r) {
        var item = Objects.requireNonNull(items.create(r.type));
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, r.id.toString());
        meta.getPersistentDataContainer().set(TOKEN, PersistentDataType.STRING, r.token.toString());
        var lore = new ArrayList<>(Objects.requireNonNullElse(meta.lore(), List.of()));
        lore.add(
                MiniMessage.miniMessage()
                        .deserialize(
                                messages.getString("packed-health"),
                                Placeholder.unparsed(
                                        "health", Integer.toString((int) Math.ceil(r.health))),
                                Placeholder.unparsed(
                                        "max",
                                        Integer.toString((int) definitions.get(r.type).health()))));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent e) {
        if (!managed(e.getEntity())) return;
        var cause = e.getCause();
        Entity attacker = e.getDamageSource().getCausingEntity();
        if (!(e instanceof EntityDamageByEntityEvent)
                && cause != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                && cause != EntityDamageEvent.DamageCause.VOID) e.setCancelled(true);
        else if (!mayDamage(e.getEntity(), attacker)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void hit(EntityDamageEvent e) {
        var live = live(e.getEntity());
        if (live == null || e.getFinalDamage() <= 0) return;
        Entity attacker = e.getDamageSource().getCausingEntity();
        if (attacker == null) attackers.remove(live.record.id);
        else attackers.put(live.record.id, attacker);
        live.record.hitUntil = System.currentTimeMillis() + combatSeconds * 1000L;
        live.record.health = Math.max(0, live.entity.getHealth() - e.getFinalDamage());
        for (Player rider : live.model.riders()) combat.tag(rider);
        if (attacker instanceof Player player) combat.tag(player);
        // Lethal hits are journaled by death() in this same native damage call.
        if (live.record.health > 0) save();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent e) {
        var live = live(e.getEntity());
        if (live == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        Entity attacker = e.getDamageSource().getCausingEntity();
        if (attacker == null) attacker = attackers.get(live.record.id);
        var location = live.entity.getLocation();
        live.record.health = 0;
        live.record.state = State.DESTROYED;
        save();
        remove(live);
        // Remove ownership/indexes first so chained native explosions cannot kill this identity
        // twice.
        if (protection.damage(location)) explosions.explode(location, attacker);
    }

    private void remove(Live live) {
        active.remove(live.record.id);
        attackers.remove(live.record.id);
        entities.remove(live.entity.getUniqueId());
        live.model.remove();
        live.entity.remove();
    }

    private void leave(Player p) {
        for (Live live : List.copyOf(active.values()))
            if (live.model.riders().contains(p)) live.model.eject(p);
        interactions.remove(p.getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        leave(e.getPlayer());
    }

    @EventHandler
    public void playerDeath(PlayerDeathEvent e) {
        leave(e.getPlayer());
    }

    @EventHandler
    public void world(PlayerChangedWorldEvent e) {
        leave(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void leash(PlayerLeashEntityEvent e) {
        if (managed(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void target(EntityTargetEvent e) {
        if (managed(e.getEntity())) e.setCancelled(true);
    }

    private void holdChunks(Location location) {
        int x = location.getBlockX() >> 4, z = location.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                var ticket = new Ticket(location.getWorld(), x + dx, z + dz);
                if (tickets.add(ticket))
                    ticket.world.addPluginChunkTicket(ticket.x, ticket.z, plugin);
            }
    }

    private void refreshTickets() {
        Set<Ticket> needed = new HashSet<>();
        for (Live live : active.values()) {
            var location = live.entity.getLocation();
            holdChunks(location);
            int x = location.getBlockX() >> 4, z = location.getBlockZ() >> 4;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    needed.add(new Ticket(location.getWorld(), x + dx, z + dz));
        }
        for (Ticket ticket : Set.copyOf(tickets))
            if (!needed.contains(ticket)) {
                ticket.world.removePluginChunkTicket(ticket.x, ticket.z, plugin);
                tickets.remove(ticket);
            }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void nativeMount(EntityMountEvent e) {
        if (managed(e.getMount())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void unload(org.bukkit.event.world.WorldUnloadEvent e) {
        if (active.values().stream().anyMatch(v -> v.entity.getWorld().equals(e.getWorld())))
            e.setCancelled(true);
    }

    private void refreshSeatMenus() {
        if (closed) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Seats seats) seats.refresh();
        }
    }

    private void tick() {
        if (closed) return;
        refreshTickets();
        for (Live live : List.copyOf(active.values())) {
            if (!live.entity.isValid()) continue;
            live.record.location = live.entity.getLocation();
            live.record.health = live.entity.getHealth();
            for (Player p : live.model.riders())
                p.sendActionBar(
                        MiniMessage.miniMessage()
                                .deserialize(
                                        messages.getString("hud"),
                                        Placeholder.unparsed(
                                                "vehicle",
                                                definitions.get(live.record.type).name()),
                                        Placeholder.unparsed(
                                                "health",
                                                Integer.toString(
                                                        (int) Math.ceil(live.entity.getHealth()))),
                                        Placeholder.unparsed(
                                                "max",
                                                Integer.toString(
                                                        (int)
                                                                definitions
                                                                        .get(live.record.type)
                                                                        .health()))));
        }
        if (!active.isEmpty() && Bukkit.getCurrentTick() % 100 < 20) save();
    }

    private final class Seats implements InventoryHolder {
        final UUID id;
        final Inventory inventory;
        final Map<Integer, VehicleModels.SeatStatus> entries = new HashMap<>();
        List<VehicleModels.SeatStatus> displayed;

        Seats(UUID id) {
            this.id = id;
            inventory = Bukkit.createInventory(this, 27,
                    MiniMessage.miniMessage().deserialize(messages.getString("menu-title")));
            refresh();
        }

        void refresh() {
            var live = active.get(id);
            var seats = live == null ? List.<VehicleModels.SeatStatus>of() : live.model.seats();
            if (seats.equals(displayed)) return;
            displayed = seats;
            MenuItems.fill(inventory);
            entries.clear();
            int spacing = seats.size() <= 5 ? 2 : 1;
            int start = seats.size() <= 5 ? 13 - (seats.size() - 1) : 9 + (9 - seats.size()) / 2;
            for (int i = 0; i < seats.size(); i++) {
                var seat = seats.get(i);
                int slot = start + i * spacing;
                entries.put(slot, seat);
                String headKey = seat.role() != VehicleModels.Seat.PILOT ? "rebel-gunner"
                        : live.record.type.equals("tiefighter") ? "tie-pilot"
                        : live.record.type.equals("speederbike") ? "speeder-pilot" : "rebel-pilot";
                var item = seat.occupant() == null ? emptySeatHeads.get(headKey).clone() : new ItemStack(Material.PLAYER_HEAD);
                var meta = item.getItemMeta();
                if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull && seat.occupant() != null)
                    skull.setPlayerProfile(seat.occupant().getPlayerProfile());
                meta.displayName(MiniMessage.miniMessage().deserialize(
                        messages.getString(seat.occupant() == null ? "seat-open" : "seat-occupied"),
                        Placeholder.component("role", seat.occupant() == null && seat.role() != VehicleModels.Seat.PASSENGER
                                ? net.kyori.adventure.text.Component.text(emptySeatTitles.get(headKey), net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                                : MiniMessage.miniMessage().deserialize(messages.getString(seat.role().name().toLowerCase(Locale.ROOT)))),
                        Placeholder.unparsed("player", seat.occupant() == null ? "" : seat.occupant().getName())));
                item.setItemMeta(meta);
                inventory.setItem(slot, item);
            }
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Seats seats)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // Re-read occupancy so a stale menu cannot board a different or already occupied seat.
        seats.refresh();
        var seat = seats.entries.get(e.getRawSlot());
        if (seat == null) return;
        if (seat.occupant() != null) { message(p, "occupied"); return; }
        if (mount(p, seats.id, seat.role(), seat.id())) p.closeInventory();
        seats.refresh();
    }

    @EventHandler
    public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Seats) e.setCancelled(true);
    }

    @Override
    public void close() {
        closed = true;
        task.cancel();
        restoreTask.cancel();
        menuTask.cancel();
        for (Live live : active.values()) {
            live.record.location = live.entity.getLocation();
            live.record.health = live.entity.getHealth();
        }
        try {
            save();
        } finally {
            for (Live live : List.copyOf(active.values())) remove(live);
            refreshTickets();
            models.close();
            HandlerList.unregisterAll(this);
        }
    }
}
