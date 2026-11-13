package net.mandomc.review.ilum;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Isolated review content; does not replace or overlay the deployed MandoMC binary. */
public final class IlumReview extends JavaPlugin implements Listener {
    private record Room(int id, String chapter, String title, boolean puzzle, int floor,
                        int x0, int z0, boolean east, Location checkpoint) { }
    private record Button(int room, int panel, int answer, int correct) { }
    private final List<Room> rooms = new ArrayList<>();
    private final Map<String, Button> buttons = new HashMap<>();
    private final Map<UUID, Long> noticeTimes = new HashMap<>();
    private final Map<UUID, Long> clickTimes = new HashMap<>();
    private World world;
    private Location crystal;
    private YamlConfiguration map;
    private YamlConfiguration messages;
    private NamespacedKey roomKey, sealsKey, modeKey, returnKey, startedKey, teamKey, collisionKey;
    private boolean ready;

    @Override public void onEnable() {
        saveResource("map.yml", false);
        saveResource("messages.yml", false);
        map = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "map.yml"));
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));
        roomKey = new NamespacedKey(this, "room");
        sealsKey = new NamespacedKey(this, "seals");
        modeKey = new NamespacedKey(this, "previous-mode");
        returnKey = new NamespacedKey(this, "return-location");
        startedKey = new NamespacedKey(this, "started");
        teamKey = new NamespacedKey(this, "previous-team");
        collisionKey = new NamespacedKey(this, "previous-collision");
        world = Bukkit.getWorld(NamespacedKey.minecraft("ilumreview"));
        if (world == null) throw new IllegalStateException("Existing Ilum dimension must be loaded by worlds.yml");
        world.setPVP(false);
        world.setDifficulty(Difficulty.PEACEFUL);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.getWorldBorder().setCenter(280, 240);
        world.getWorldBorder().setSize(590);
        for (var rule : List.of(org.bukkit.GameRules.ADVANCE_TIME, org.bukkit.GameRules.ADVANCE_WEATHER,
                org.bukkit.GameRules.SPAWN_MOBS, org.bukkit.GameRules.FALL_DAMAGE,
                org.bukkit.GameRules.DROWNING_DAMAGE, org.bukkit.GameRules.FIRE_DAMAGE,
                org.bukkit.GameRules.FREEZE_DAMAGE, org.bukkit.GameRules.MOB_GRIEFING,
                org.bukkit.GameRules.TNT_EXPLODES)) world.setGameRule(rule, false);
        world.setGameRule(org.bukkit.GameRules.KEEP_INVENTORY, true);
        world.setGameRule(org.bukkit.GameRules.RANDOM_TICK_SPEED, 0);
        world.setGameRule(org.bukkit.GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        for (Map<?, ?> data : map.getMapList("rooms")) {
            var cp = (List<?>) data.get("checkpoint");
            int id = number(data, "id");
            Room room = new Room(id, (String) data.get("chapter"), (String) data.get("title"),
                    data.get("kind").equals("puzzle"), number(data, "floor"), number(data, "x0"),
                    number(data, "z0"), (Boolean) data.get("east"),
                    new Location(world, dec(cp.get(0)), dec(cp.get(1)), dec(cp.get(2)), (float) dec(cp.get(3)), 0));
            if (id != rooms.size()) throw new IllegalArgumentException("Nonlinear room manifest");
            rooms.add(room);
            for (Object raw : (List<?>) data.get("stations")) {
                Map<?, ?> station = (Map<?, ?>) raw;
                List<?> locations = (List<?>) station.get("buttons");
                for (int answer = 0; answer < 3; answer++) {
                    List<?> pos = (List<?>) locations.get(answer);
                    buttons.put(key(((Number) pos.get(0)).intValue(), ((Number) pos.get(1)).intValue(),
                            ((Number) pos.get(2)).intValue()),
                            new Button(id, number(station, "index"), answer, number(station, "correct")));
                }
            }
        }
        if (rooms.size() != 30 || buttons.size() != 162) throw new IllegalArgumentException("Incomplete adventure");
        var crystalPos = map.getIntegerList("crystal");
        crystal = new Location(world, crystalPos.get(0), crystalPos.get(1), crystalPos.get(2));
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("ilumreview").setExecutor(this);
        getCommand("ilumreview").setTabCompleter((sender, cmd, alias, args) ->
                args.length == 1 ? List.of("start", "resume", "retry", "status", "leave", "reset", "help")
                        .stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList() : List.of());
        Bukkit.getScheduler().runTaskLater(this, this::initializeSigns, 20);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!ready) return;
            for (Player player : world.getPlayers()) {
                player.setFoodLevel(20);
                player.setSaturation(20);
                player.setFireTicks(0);
                player.setFallDistance(0);
                if (player.getGameMode() != GameMode.ADVENTURE) continue;
                Room room = room(player);
                player.sendActionBar(text("actionbar", "checkpoint", Integer.toString(room.id()+1),
                        "title", room.title(), "seals", Integer.toString(Integer.bitCount(progress(player).seals())),
                        "task", room.puzzle() ? "Solve 3 clue panels" : "Cross the wide platforms"));
            }
        }, 40, 40);
    }

    @Override public void onDisable() {
        if (world != null) for (Player player : world.getPlayers()) collisionPolicy(player, false);
        Bukkit.getScheduler().cancelTasks(this);
        org.bukkit.event.HandlerList.unregisterAll((Listener) this);
    }

    private void collisionPolicy(Player player, boolean inside) {
        var data = player.getPersistentDataContainer();
        var board = Bukkit.getScoreboardManager().getMainScoreboard();
        var team = board.getTeam("ilum_safe");
        if (inside) {
            if (team == null) team = board.registerNewTeam("ilum_safe");
            team.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE, org.bukkit.scoreboard.Team.OptionStatus.NEVER);
            if (!data.has(teamKey)) {
                var previous = board.getEntryTeam(player.getName());
                data.set(teamKey, PersistentDataType.STRING, previous == null ? "" : previous.getName());
                data.set(collisionKey, PersistentDataType.BYTE, (byte) (player.isCollidable() ? 1 : 0));
            }
            team.addEntry(player.getName());
            player.setCollidable(false);
        } else if (data.has(teamKey)) {
            if (team != null) team.removeEntry(player.getName());
            String previous = data.get(teamKey, PersistentDataType.STRING);
            if (previous != null && !previous.isEmpty() && board.getTeam(previous) != null) {
                board.getTeam(previous).addEntry(player.getName());
            }
            player.setCollidable(data.getOrDefault(collisionKey, PersistentDataType.BYTE, (byte) 1) == 1);
            data.remove(teamKey);
            data.remove(collisionKey);
        }
    }

    private static int number(Map<?, ?> map, String key) { return ((Number) map.get(key)).intValue(); }
    private static double dec(Object value) { return ((Number) value).doubleValue(); }
    private static String key(int x, int y, int z) { return x + "," + y + "," + z; }
    private boolean here(Player player) { return world != null && player.getWorld().equals(world); }
    private Progress progress(Player player) {
        var data = player.getPersistentDataContainer();
        return new Progress(Math.max(0, Math.min(30, data.getOrDefault(roomKey, PersistentDataType.INTEGER, 0))),
                data.getOrDefault(sealsKey, PersistentDataType.INTEGER, 0) & 7);
    }
    private void save(Player player, Progress progress) {
        var data = player.getPersistentDataContainer();
        data.set(roomKey, PersistentDataType.INTEGER, progress.room());
        data.set(sealsKey, PersistentDataType.INTEGER, progress.seals());
        player.saveData();
    }
    private Room room(Player player) { return rooms.get(Math.min(29, progress(player).room())); }
    private Component text(String id, String... pairs) {
        String line = messages.getString(id, id);
        for (int i = 0; i < pairs.length; i += 2) line = line.replace("{"+pairs[i]+"}", pairs[i+1]);
        return MiniMessage.miniMessage().deserialize(line);
    }
    private void message(Player player, String id, String... pairs) { player.sendMessage(text(id, pairs)); }
    private void notice(Player player, String id) {
        long now = System.currentTimeMillis();
        if (now - noticeTimes.getOrDefault(player.getUniqueId(), 0L) < 1800) return;
        noticeTimes.put(player.getUniqueId(), now);
        message(player, id);
    }
    private void retry(Player player) {
        player.setFallDistance(0);
        player.teleport(room(player).checkpoint());
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .5f, .8f);
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "resume" : args[0].toLowerCase();
        if (action.equals("verify") && (sender.isOp() || !(sender instanceof Player))) {
            try { verify(); sender.sendMessage("Ilum verification passed."); }
            catch (Exception ex) { throw new IllegalStateException("Ilum verification failed", ex); }
            return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Use /ilumreview verify from console."); return true; }
        if (!ready) { message(player, "preparing"); return true; }
        switch (action) {
            case "start", "resume" -> {
                if (progress(player).room() == 30) { message(player, "already-finished"); return true; }
                if (!here(player)) {
                    remember(player, player.getLocation());
                    if (!player.teleport(room(player).checkpoint())) { message(player, "travel-denied"); return true; }
                }
                player.setGameMode(GameMode.ADVENTURE);
                player.setAllowFlight(false);
                collisionPolicy(player, true);
                if (!player.getPersistentDataContainer().has(startedKey)) {
                    player.getPersistentDataContainer().set(startedKey, PersistentDataType.LONG, System.currentTimeMillis());
                }
                save(player, progress(player));
                retry(player);
                message(player, "welcome");
            }
            case "reset" -> {
                save(player, new Progress(0, 0));
                player.getPersistentDataContainer().remove(startedKey);
                if (here(player)) retry(player);
                message(player, "reset");
            }
            case "retry" -> { if (here(player)) retry(player); else message(player, "outside"); }
            case "leave" -> {
                if (here(player)) {
                    Location target = returnLocation(player);
                    if (target == null || !player.teleport(target)) message(player, "travel-denied");
                } else message(player, "outside");
            }
            case "status" -> message(player, "status", "checkpoint", Integer.toString(Math.min(30, progress(player).room()+1)),
                    "title", room(player).title(), "seals", Integer.toString(Integer.bitCount(progress(player).seals())));
            case "help" -> message(player, "help");
            default -> message(player, "help");
        }
        return true;
    }

    private void remember(Player player, Location location) {
        if (location.getWorld().equals(world)) return;
        var data = player.getPersistentDataContainer();
        data.set(modeKey, PersistentDataType.STRING, player.getGameMode().name());
        data.set(returnKey, PersistentDataType.STRING, location.getWorld().getUID()+","+location.getX()+","+
                location.getY()+","+location.getZ()+","+location.getYaw()+","+location.getPitch());
    }
    private Location returnLocation(Player player) {
        String serialized = player.getPersistentDataContainer().get(returnKey, PersistentDataType.STRING);
        if (serialized != null) {
            String[] p = serialized.split(",");
            World target = Bukkit.getWorld(UUID.fromString(p[0]));
            if (target != null && !target.equals(world)) return new Location(target, Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]), Double.parseDouble(p[3]), Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        }
        World spawn = Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));
        return spawn == null ? null : spawn.getSpawnLocation();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void entryLocation(PlayerTeleportEvent event) {
        if (event.getTo().getWorld().equals(world) && !event.getFrom().getWorld().equals(world)) {
            remember(event.getPlayer(), event.getFrom());
        }
    }
    @EventHandler public void changedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (here(player)) {
            collisionPolicy(player, true);
            if (ready) message(player, "arrival");
        } else if (event.getFrom().equals(world)) {
            collisionPolicy(player, false);
            String mode = player.getPersistentDataContainer().get(modeKey, PersistentDataType.STRING);
            if (mode != null) player.setGameMode(GameMode.valueOf(mode));
            player.getPersistentDataContainer().remove(modeKey);
        }
    }
    @EventHandler public void joined(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (here(player) && player.getGameMode() == GameMode.ADVENTURE) {
            Bukkit.getScheduler().runTaskLater(this, () -> { if (player.isOnline() && here(player)) {
                retry(player); collisionPolicy(player, true); message(player, "resumed");
            } }, 40);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        collisionPolicy(event.getPlayer(), false);
        noticeTimes.remove(event.getPlayer().getUniqueId());
        clickTimes.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!ready || !here(player) || player.getGameMode() != GameMode.ADVENTURE || event instanceof PlayerTeleportEvent) return;
        if (!event.hasChangedPosition()) return;
        Room room = room(player);
        Location to = event.getTo();
        double t = room.east() ? to.getX()-room.x0() : room.x0()+111-to.getX();
        if (to.getY() < room.floor()-2 || to.getX() < room.x0() || to.getX() >= room.x0()+112
                || to.getZ() < room.z0()+10 || to.getZ() > room.z0()+70 || to.getY() > room.floor()+6) {
            event.setTo(room.checkpoint()); player.setFallDistance(0); notice(player, "retry"); return;
        }
        if (t >= 99 && progress(player).room() < 30) {
            Progress before = progress(player);
            if (!before.ready(room.puzzle())) {
                event.setTo(event.getFrom()); notice(player, "need-seals"); return;
            }
            if (room.id() == 29) {
                event.setTo(event.getFrom()); notice(player, "attune"); return;
            }
            Progress after = before.advance(room.puzzle());
            save(player, after);
            Room next = rooms.get(after.room());
            event.setTo(next.checkpoint());
            player.showTitle(Title.title(text("checkpoint-title", "checkpoint", Integer.toString(next.id()+1)),
                    Component.text(next.title())));
            player.playSound(next.checkpoint(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, .8f, 1.3f);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void interact(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!here(player)) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (!ready || event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getClickedBlock() == null || player.getGameMode() != GameMode.ADVENTURE) return;
        var at = event.getClickedBlock();
        if (at.getType() == Material.LIGHT_BLUE_STAINED_GLASS
                && Math.abs(at.getX()-crystal.getBlockX()) <= 1 && Math.abs(at.getZ()-crystal.getBlockZ()) <= 1
                && at.getY() >= crystal.getBlockY() && at.getY() <= crystal.getBlockY()+7
                && at.getLocation().distanceSquared(player.getLocation()) <= 36) {
            if (progress(player).room() == 30) { notice(player, "already-finished"); return; }
            if (progress(player).room() != 29 || progress(player).seals() != 7) {
                notice(player, "need-seals"); return;
            }
            save(player, new Progress(30, 0));
            player.showTitle(Title.title(text("finish-title"), text("finish-subtitle")));
            player.spawnParticle(Particle.END_ROD, crystal.clone().add(.5,3,.5), 40, 1.5,2,1.5);
            player.playSound(crystal, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            long start = player.getPersistentDataContainer().getOrDefault(startedKey, PersistentDataType.LONG,
                    System.currentTimeMillis());
            message(player, "finished", "minutes", Long.toString(Math.max(1, (System.currentTimeMillis()-start)/60000)));
            return;
        }
        Button button = buttons.get(key(at.getX(), at.getY(), at.getZ()));
        if (button == null) return;
        if (progress(player).room() != button.room()) { notice(player, "other-room"); return; }
        if (at.getLocation().distanceSquared(player.getLocation()) > 36) return;
        long now = System.currentTimeMillis();
        if (now - clickTimes.getOrDefault(player.getUniqueId(), 0L) < 250) return;
        clickTimes.put(player.getUniqueId(), now);
        Progress before = progress(player);
        if ((before.seals() & (1 << button.panel())) != 0) { notice(player, "already-solved"); return; }
        boolean correct = button.answer() == button.correct();
        if (correct) {
            Progress after = before.answer(button.panel(), true);
            save(player, after);
            player.spawnParticle(Particle.HAPPY_VILLAGER, at.getLocation().add(.5,.5,.5), 12, .4,.4,.4);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, .7f, 1.2f);
            message(player, "solved", "seals", Integer.toString(Integer.bitCount(after.seals())));
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .5f, .7f);
            message(player, "wrong");
        }
    }

    // Safezone enforcement applies to every visitor, including operators inspecting in Creative.
    @EventHandler(priority = EventPriority.LOWEST) public void damageEarly(EntityDamageEvent e) { if (e.getEntity().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void damageLate(EntityDamageEvent e) { if (e.getEntity().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void breakBlock(BlockBreakEvent e) { if (e.getBlock().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void placeBlock(BlockPlaceEvent e) { if (e.getBlock().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void bucket(PlayerBucketEmptyEvent e) { if (here(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void bucketFill(PlayerBucketFillEvent e) { if (here(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void drop(PlayerDropItemEvent e) { if (here(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void pickup(EntityPickupItemEvent e) { if (e.getEntity().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void entityInteract(PlayerInteractEntityEvent e) { if (here(e.getPlayer())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void projectile(ProjectileLaunchEvent e) { if (e.getEntity().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void explode(EntityExplodeEvent e) { if (e.getLocation().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void explodeBlock(BlockExplodeEvent e) { if (e.getBlock().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void fire(BlockIgniteEvent e) { if (e.getBlock().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void burn(BlockBurnEvent e) { if (e.getBlock().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void spread(BlockSpreadEvent e) { if (e.getBlock().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void fade(BlockFadeEvent e) { if (e.getBlock().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void spawn(CreatureSpawnEvent e) { if (e.getLocation().getWorld().equals(world)) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void food(FoodLevelChangeEvent e) { if (e.getEntity().getWorld().equals(world)) { e.setCancelled(true); e.setFoodLevel(20); } }
    @EventHandler(priority = EventPriority.LOWEST) public void weather(WeatherChangeEvent e) { if (e.getWorld().equals(world) && e.toWeatherState()) e.setCancelled(true); }

    private void initializeSigns() {
        try {
            // Setup runs once per generated world; signs are real persisted blocks, not marker entities.
            if (!new File(getDataFolder(), "signs-v1.ready").exists()) {
                for (Map<?, ?> data : map.getMapList("signs")) {
                    var block = world.getBlockAt(number(data, "x"), number(data, "y"), number(data, "z"));
                    // Real signs are installed last, after their decorative pedestals and columns.
                    if (!(block.getState() instanceof Sign)) {
                        boolean east = !Boolean.FALSE.equals(data.get("east"));
                        block.setBlockData(Bukkit.createBlockData("minecraft:birch_sign[rotation="
                                + (east ? 12 : 4) + ",waterlogged=false]"), false);
                    }
                    Sign sign = (Sign) block.getState();
                    List<?> lines = (List<?>) data.get("lines");
                    for (int i = 0; i < Math.min(4, lines.size()); i++) {
                        sign.getSide(Side.FRONT).line(i, Component.text(lines.get(i).toString()));
                        sign.getSide(Side.BACK).line(i, Component.text(lines.get(i).toString()));
                    }
                    sign.setWaxed(true);
                    sign.update(true, false);
                }
                Files.writeString(new File(getDataFolder(), "signs-v1.ready").toPath(), "Native signs initialized\n");
            }
            verify();
            ready = true;
            for (Player player : world.getPlayers()) collisionPolicy(player, true);
            getLogger().info("ILUM REVIEW READY: 30 checkpoints, safe multiplayer personal puzzles");
        } catch (Exception ex) {
            getLogger().log(java.util.logging.Level.SEVERE, "Ilum setup failed; adventure access remains closed", ex);
        }
    }

    private void verify() throws IOException {
        int checks = 0;
        if (world.getPVP() || world.getDifficulty() != Difficulty.PEACEFUL) throw new IllegalStateException("Safe zone policy"); checks++;
        for (Room room : rooms) {
            Location cp = room.checkpoint();
            if (!cp.getBlock().isPassable() || !cp.clone().add(0,1,0).getBlock().isPassable()
                    || !cp.clone().subtract(0,1,0).getBlock().getType().isSolid()) {
                throw new IllegalStateException("Unsafe checkpoint " + room.id());
            }
            checks += 3;
            var lamp = cp.clone().add(-2,-1,-2).getBlock();
            if (lamp.getType() != Material.SEA_LANTERN || lamp.getLightFromBlocks() != 15
                    || cp.clone().add(-2,0,-2).getBlock().getLightFromBlocks() < 10) {
                throw new IllegalStateException("Checkpoint lighting " + room.id());
            }
            checks += 3;
        }
        for (String key : buttons.keySet()) {
            String[] p = key.split(",");
            if (world.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])).getType() != Material.STONE_BUTTON) {
                throw new IllegalStateException("Missing puzzle button " + key);
            }
            checks++;
        }
        if (crystal.getBlock().getType() != Material.LIGHT_BLUE_STAINED_GLASS) throw new IllegalStateException("Missing final kyber"); checks++;
        for (Map<?, ?> data : map.getMapList("signs")) {
            var state = world.getBlockAt(number(data,"x"),number(data,"y"),number(data,"z")).getState();
            if (!(state instanceof Sign sign) || !sign.isWaxed()) throw new IllegalStateException("Missing protected sign");
            List<?> lines = (List<?>) data.get("lines");
            String actual = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(sign.getSide(Side.FRONT).line(0));
            if (!actual.equals(lines.get(0).toString())) throw new IllegalStateException("Sign text mismatch");
            checks += 2;
        }
        for (Map<?, ?> data : map.getMapList("rooms")) {
            for (Object raw : (List<?>) data.get("platforms")) {
                List<?> p = (List<?>) raw;
                int x = ((Number)p.get(0)).intValue(), y = ((Number)p.get(1)).intValue(), z = ((Number)p.get(2)).intValue();
                if (!world.getBlockAt(x,y,z).getType().isSolid() || !world.getBlockAt(x,y+1,z).isPassable()
                        || !world.getBlockAt(x,y+2,z).isPassable()) throw new IllegalStateException("Invalid landing " + p);
                checks += 3;
            }
        }
        for (String line : getResourceLines("walk-proof.csv")) {
            String[] p = line.split(",");
            int x = Integer.parseInt(p[0]), y = Integer.parseInt(p[1]), z = Integer.parseInt(p[2]);
            if (!world.getBlockAt(x,y,z).isPassable() || !world.getBlockAt(x,y+1,z).isPassable()
                    || !world.getBlockAt(x,y-1,z).getType().isSolid()) throw new IllegalStateException("Route footing " + line);
            checks += 3;
        }
        ProgressTest.main(new String[0]);
        checks += verifyPlayerRules();
        String result = "ILUM NATIVE CHECK PASSED: " + checks + " assertions; checkpoints/buttons/landings/route; "
                + "independent player rules passed. Client playtest and completion timing pending.\n";
        Files.writeString(new File(getDataFolder(), "verification.txt").toPath(), result);
        getLogger().info(result);
        world.save();
    }

    private List<String> getResourceLines(String name) throws IOException {
        try (var input = getResource(name)) {
            if (input == null) throw new IOException("Missing " + name);
            return new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
    }

    /** Real Bukkit events/blocks/PDC, isolated player proxies; never moves or edits a connected player. */
    private int verifyPlayerRules() throws IOException {
        var context = world.getPersistentDataContainer().getAdapterContext();
        var aliceData = context.newPersistentDataContainer();
        var bobData = context.newPersistentDataContainer();
        Location[] aliceAt = { rooms.get(0).checkpoint().clone() };
        Location[] bobAt = { rooms.get(0).checkpoint().clone() };
        Player alice = testPlayer(aliceData, aliceAt, "IlumCheckAlice");
        Player bob = testPlayer(bobData, bobAt, "IlumCheckBob");
        boolean previousReady = ready;
        ready = true;
        int checks = 0;
        try {
            for (Room room : rooms) {
                aliceAt[0] = room.checkpoint().clone();
                if (progress(alice).room() != room.id()) throw new IllegalStateException("Personal checkpoint order"); checks++;
                Location exit = room.checkpoint().clone();
                exit.setX(room.east() ? room.x0()+100 : room.x0()+11);
                if (room.puzzle()) {
                    var blocked = new PlayerMoveEvent(alice, aliceAt[0], exit);
                    move(blocked);
                    if (progress(alice).room() != room.id()) throw new IllegalStateException("Unsolved personal gate bypass"); checks++;
                    for (var entry : buttons.entrySet()) {
                        Button button = entry.getValue();
                        if (button.room() != room.id() || button.answer() != button.correct()) continue;
                        String[] p = entry.getKey().split(",");
                        var block = world.getBlockAt(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                        aliceAt[0] = block.getLocation().add(room.east() ? -2 : 2, -1, 0);
                        clickTimes.remove(alice.getUniqueId());
                        var click = new PlayerInteractEvent(alice, Action.RIGHT_CLICK_BLOCK, null, block,
                                org.bukkit.block.BlockFace.WEST, EquipmentSlot.HAND);
                        interact(click);
                        if (!click.isCancelled() || click.useInteractedBlock() != Event.Result.DENY
                                || block.getBlockData() instanceof org.bukkit.block.data.Powerable powered && powered.isPowered()) {
                            throw new IllegalStateException("Shared physical button changed");
                        }
                        checks += 3;
                    }
                    if (progress(alice).seals() != 7) throw new IllegalStateException("Personal button answers"); checks++;
                }
                // Exercise native binary persistence, not merely the immutable Java record.
                byte[] saved = aliceData.serializeToBytes();
                var restored = context.newPersistentDataContainer();
                restored.readFromBytes(saved, true);
                if (!restored.get(roomKey, PersistentDataType.INTEGER).equals(room.id())
                        || !restored.get(sealsKey, PersistentDataType.INTEGER).equals(progress(alice).seals())) {
                    throw new IllegalStateException("PDC restart persistence");
                }
                checks += 2;
                aliceAt[0] = room.checkpoint().clone();
                var advance = new PlayerMoveEvent(alice, aliceAt[0], exit);
                move(advance);
                if (room.id() == 29) {
                    if (progress(alice).room() != 29) throw new IllegalStateException("Final crystal skipped"); checks++;
                    aliceAt[0] = crystal.clone().add(2,-1,0);
                    interact(new PlayerInteractEvent(alice, Action.RIGHT_CLICK_BLOCK, null, crystal.getBlock(),
                            org.bukkit.block.BlockFace.EAST, EquipmentSlot.HAND));
                }
                if (progress(alice).room() != room.id()+1 || progress(bob).room() != 0 || progress(bob).seals() != 0) {
                    throw new IllegalStateException("Cross-player puzzle/checkpoint interference");
                }
                checks += 3;
                if (room.id() < 29 && !advance.getTo().equals(rooms.get(room.id()+1).checkpoint())) {
                    throw new IllegalStateException("Next checkpoint transfer");
                }
                checks++;
            }
            var fall = new PlayerMoveEvent(bob, bobAt[0], bobAt[0].clone().subtract(0,10,0));
            move(fall);
            if (!fall.getTo().equals(rooms.get(0).checkpoint())) throw new IllegalStateException("Safe fall retry"); checks++;
            var damage = new EntityDamageEvent(bob, EntityDamageEvent.DamageCause.FALL,
                    org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(), 15);
            damageEarly(damage);
            if (!damage.isCancelled()) throw new IllegalStateException("Damage safezone"); checks++;
            damage.setCancelled(false);
            damageLate(damage);
            if (!damage.isCancelled()) throw new IllegalStateException("Late damage safezone"); checks++;
            var breakEvent = new BlockBreakEvent(rooms.get(0).checkpoint().clone().subtract(0,1,0).getBlock(), bob);
            breakBlock(breakEvent);
            if (!breakEvent.isCancelled()) throw new IllegalStateException("Protected terrain"); checks++;
            var attemptedSkip = new PlayerMoveEvent(bob, bobAt[0], rooms.get(5).checkpoint());
            move(attemptedSkip);
            if (!attemptedSkip.getTo().equals(rooms.get(0).checkpoint()) || progress(bob).room() != 0) {
                throw new IllegalStateException("Cross-room movement bypass");
            }
            checks += 2;
            return checks;
        } finally {
            ready = previousReady;
            for (Player player : List.of(alice,bob)) { clickTimes.remove(player.getUniqueId()); noticeTimes.remove(player.getUniqueId()); }
        }
    }

    private Player testPlayer(org.bukkit.persistence.PersistentDataContainer data, Location[] at, String name) {
        data.set(roomKey, PersistentDataType.INTEGER, 0);
        data.set(sealsKey, PersistentDataType.INTEGER, 0);
        UUID uuid = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return (Player) java.lang.reflect.Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getName" -> name;
                        case "getUniqueId" -> uuid;
                        case "getWorld" -> world;
                        case "getLocation" -> at[0].clone();
                        case "getGameMode" -> GameMode.ADVENTURE;
                        case "getPersistentDataContainer" -> data;
                        case "isOnline", "isValid" -> true;
                        case "hashCode" -> uuid.hashCode();
                        case "equals" -> proxy == args[0];
                        case "toString" -> name;
                        default -> {
                            Class<?> result = method.getReturnType();
                            if (result == boolean.class) yield false;
                            if (result == int.class) yield 0;
                            if (result == long.class) yield 0L;
                            if (result == double.class) yield 0.0;
                            if (result == float.class) yield 0f;
                            yield null;
                        }
                    };
                });
    }
}
