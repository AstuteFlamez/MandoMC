package net.mandomc.verification;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.tutorial.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Exercises actual native inventory menus, models, combat hooks and persistence only on disposable localhost. */
public final class TutorialProbe extends JavaPlugin {
    private MandoPlugin mando;
    private NativeProbePlayers actors;
    private Player player, second;
    private ItemStack[] original;
    private Location origin;
    private final Map<org.bukkit.block.Block, org.bukkit.block.data.BlockData> practiceOriginal = new LinkedHashMap<>();
    private int checks;
    private boolean finished, brakePressed;
    private void check(boolean pass, String reason) { if (!pass) throw new AssertionError(reason); checks++; }
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private void later(long ticks, Work action) { Bukkit.getScheduler().runTaskLater(this, () -> run(action), ticks); }
    private void run(Work action) { if (finished) return; try { action.run(); } catch (Throwable error) { fail(error); } }
    private Object field(Object target, String name) throws Exception { var f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); }
    @SuppressWarnings("unchecked") private Object session(Player p) throws Exception { return ((Map<UUID, ?>) field(mando.tutorial(), "sessions")).get(p.getUniqueId()); }
    private TutorialProgress progress(Player p) throws Exception { return (TutorialProgress) field(session(p), "progress"); }
    private void stage(String name) throws Exception {
        getLogger().info("Checking tutorial step " + name);
        check(progress(player).step().name().equals(name), "Expected stage " + name + ", got " + progress(player).step());
        String line = instruction();
        check(!line.contains("\n") && line.codePoints().map(c -> c < 128 ? 7 : 10).sum() <= 240,
                "Action bar stays compact at " + name + ": " + line);
    }
    private String instruction() throws Exception {
        var session = session(player);
        var method = InteractiveTutorial.class.getDeclaredMethod("instruction", session.getClass()); method.setAccessible(true);
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize((Component) method.invoke(mando.tutorial(), session));
    }
    private void command(Player p, String raw) { var event = new PlayerCommandPreprocessEvent(p, raw); Bukkit.getPluginManager().callEvent(event); check(event.isCancelled(), "Tutorial owns " + raw); }
    private void click(Player p, int raw) { Bukkit.getPluginManager().callEvent(new InventoryClickEvent(p.getOpenInventory(), InventoryType.SlotType.CONTAINER, raw, ClickType.LEFT, InventoryAction.PICKUP_ALL)); }
    private void put(Player p, Location location) throws Exception {
        var method = InteractiveTutorial.class.getDeclaredMethod("teleport", Player.class, Location.class); method.setAccessible(true);
        check((boolean) method.invoke(mando.tutorial(), p, location), "Fixture movement succeeds");
    }
    private void packs(Player p) {
        var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(), "combat/sabers.yml"));
        for (String key : List.of("main-pack-id", "id", "base-pack.id")) mando.tutorial().pack(new PlayerResourcePackStatusEvent(p,
                UUID.fromString(cfg.getString("upstream-pack." + key)), PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED));
    }
    @Override public void onEnable() { later(320, this::start); }
    private void start() throws Exception {
        check(getServer().getPort() == 25590, "Disposable port required");
        mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(mando.tutorial() != null, "Tutorial initialized");
        actors = new NativeProbePlayers("TutorialCheck");
        if (Files.exists(getDataFolder().toPath().resolve("restart-ready"))) { restartCheck(); return; }
        Files.deleteIfExists(getDataFolder().toPath().resolve("FAILED.txt"));
        player = actors.create(mando.worlds().spawn("Batuu")); origin = player.getLocation().clone();
        player.setGameMode(GameMode.SURVIVAL); player.setInvulnerable(false); player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 7));
        player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET)); player.getInventory().setItemInOffHand(new ItemStack(Material.EMERALD, 9));
        original = Arrays.stream(player.getInventory().getContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        mando.starterKit().join(new PlayerJoinEvent(player, Component.empty()));
        later(3, () -> {
            sameInventory(player, original, "First join defers starter kit");
            mando.tutorial().request(player); check(!TutorialInventory.active(player), "Pack not loaded blocks entry");
            packs(player); mando.combat().tag(player); mando.tutorial().request(player);
            check(!TutorialInventory.active(player), "Combat blocks entry"); mando.combat().clear(player);
            mando.tutorial().request(player); later(30, this::begun);
        });
    }
    private void sameInventory(Player p, ItemStack[] wanted, String reason) {
        var actual = p.getInventory().getContents(); check(wanted.length == actual.length, reason + " length");
        for (int i = 0; i < wanted.length; i++) check(Objects.equals(emptyToNull(wanted[i]), emptyToNull(actual[i])), reason + " slot " + i + " expected=" + wanted[i] + " actual=" + actual[i]);
    }
    private static ItemStack emptyToNull(ItemStack item) { return item == null || item.isEmpty() ? null : item; }
    private void titleSent(String reason) throws Exception {
        actors.drain(player);
        var packets = actors.otherPackets(player);
        check(packets != null && packets.stream().anyMatch(name -> name.endsWith(".ClientboundSetTitleTextPacket")), reason + ": " + packets);
    }
    private void begun() throws Exception {
        check(TutorialInventory.active(player), "Original inventory escrowed"); stage("WARP");
        check(TutorialInventory.world(player.getWorld()), "Player in isolated tutorial dimension");
        check(player.getInventory().isEmpty(), "Tutorial starts with empty inventory");
        check(player.getGameMode() == GameMode.ADVENTURE, "Tutorial adventure mode");
        check(player.isInvulnerable(), "Tutorial cannot die");
        second = actors.create(origin); packs(second); mando.tutorial().request(second);
        later(25, () -> {
            check(TutorialInventory.active(second), "Second player enters"); check(second.getWorld() != player.getWorld(), "Concurrent players have separate worlds");
            var crates = Bukkit.getServicesManager().load(net.mandomc.gameplay.crates.CrateService.class);
            crates.enqueueKey(second, "kyber", 1); second.saveData(); crates.claim(second);
            check(second.getInventory().isEmpty(), "External physical crate key stays in mail during tutorial");
            command(player, "/crates open kyber"); stage("WARP");
            command(player, "/mandomc:warp"); check(player.getOpenInventory().getTopInventory().getItem(22).getType() == Material.DIAMOND, "Central Tutorial diamond");
            var trainingMap = player.getOpenInventory().getTopInventory();
            var normalMap = mando.warps().createMenu(null);
            check(trainingMap.getSize() == normalMap.getSize(), "Tutorial uses normal warp menu dimensions");
            for (int slot = 0; slot < normalMap.getSize(); slot++) if (slot != 22)
                check(Objects.equals(trainingMap.getItem(slot), normalMap.getItem(slot)), "Normal warp layout preserved at " + slot);
            click(player, 20); stage("WARP"); check(player.getOpenInventory().getTopInventory() == trainingMap, "Normal destination cannot close or escape tutorial menu");
            click(player, 12); stage("WARP"); player.closeInventory(); stage("WARP");
            command(player, "/warp"); click(player, 22); later(3, this::map);
        });
    }
    private void map() throws Exception {
        stage("MAP"); titleSent("Safezone title emitted on lesson arrival"); command(player, "/c create Early"); stage("MAP");
        check(field(session(player), "hologram") == null, "Map lesson has no hologram");
        command(player, "/c map"); stage("WILDERNESS");
        check(mando.territories().zone(player.getLocation()) == net.mandomc.gameplay.territory.TerritoryPolicy.Zone.SAFEZONE, "Map uses real safezone classification");
        put(player, new Location(player.getWorld(), 33.5, 65, .5)); later(3, () -> {
            stage("CREATE"); titleSent("Wilderness title emitted across boundary"); command(player, "/c create Training"); stage("CLAIM");
            var clans = Bukkit.getServicesManager().load(net.mandomc.gameplay.clans.ClanService.class);
            check(clans.member(player) == null, "Practice clan never enters real clan storage");
            command(player, "/c claim"); stage("CLAIM");
            put(player, new Location(player.getWorld(), 50.5, 65, .5)); command(player, "/c claim"); stage("DISBAND");
            check(mando.territories().identity(player, player.getLocation()).name().equals("Training"), "Practice claim uses normal territory identity");
            check(clans.owner(player.getLocation()) == null, "Practice claim never enters real claims");
            later(2, () -> {
                titleSent("Own-clan title emitted after practice claim");
                command(player, "/c disband"); stage("COMBAT");
                later(2, () -> { titleSent("Wilderness title emitted after disband"); later(10, this::combat); });
            });
        });
    }
    @SuppressWarnings("unchecked") private void combat() throws Exception {
        var guards = (Set<UUID>) field(session(player), "guards"); check(guards.size() == 3, "Exactly three real guards");
        var api = Class.forName("me.deecaad.weaponmechanics.WeaponMechanicsAPI", true, Bukkit.getPluginManager().getPlugin("WeaponMechanics").getClass().getClassLoader());
        check("SE14C".equals(api.getMethod("getWeaponTitle", ItemStack.class).invoke(null, player.getInventory().getItem(0))), "Real SE-14C supplied");
        player.getInventory().setItem(1, null); later(3, () -> {
            check(player.getInventory().getItem(1).getAmount() == 64, "Ammo refills");
            check(!mando.combat().tagged(player), "Tutorial combat has no logout punishment");
            int delay = 0;
            for (UUID id : Set.copyOf(guards)) {
                var guard = (LivingEntity) Bukkit.getEntity(id); guard.setAI(false); guard.setHealth(1);
                for (int attempt = 0; attempt < 3; attempt++) {
                    later(++delay * 8L, () -> {
                        if (guard.isValid() && !guard.isDead()) api.getMethod("shoot", LivingEntity.class, String.class, Location.class)
                                .invoke(null, player, "SE14C", guard.getEyeLocation());
                    });
                }
            }
            later(100, () -> {
                stage("SABER"); check(mando.items().identify(player.getInventory().getItem(0)).equals("vader"), "Vader saber supplied");
                player.setSneaking(true); check(mando.sabers().launch(player), "Real saber throw launches"); player.setSneaking(false);
                later(1, () -> awaitSaberReturn(0));
            });
        });
    }
    private void awaitSaberReturn(int ticks) throws Exception {
        check(ticks < 150, "Saber returns within native timeout");
        if (mando.sabers().busy(player)) { stage("SABER"); later(1, () -> awaitSaberReturn(ticks + 1)); return; }
        stage("SABER");
        check("vader".equals(mando.items().identify(player.getInventory().getItemInMainHand())), "Returned saber is actually held");
        later(10, () -> {
            stage("SABER");
            check("vader".equals(mando.items().identify(player.getInventory().getItemInMainHand())), "Returned saber remains held before one second");
        });
        later(25, this::shops);
    }
    private void shops() throws Exception {
        stage("BUY"); var brown = (LivingEntity) Bukkit.getEntity((UUID) field(session(player), "brown"));
        put(player, brown.getLocation().clone().add(-2, 0, 0));
        long balance = mando.credits().ledger().balance(player.getUniqueId());
        mando.jawas().open(player, brown); check(player.getOpenInventory().getTopInventory().getItem(13).getType() == Material.OAK_SAPLING, "Brown model opens lesson buy menu");
        player.closeInventory(); stage("BUY"); mando.jawas().open(player, brown); click(player, 13);
        later(3, () -> {
            stage("SELL"); check(mando.credits().ledger().balance(player.getUniqueId()) == balance, "Practice purchase leaves real credits unchanged");
            var gray = (LivingEntity) Bukkit.getEntity((UUID) field(session(player), "gray")); put(player, gray.getLocation().clone().add(-2, 0, 0));
            mando.jawas().open(player, gray); click(player, 31); stage("SELL");
            // Storage hotbar slot one is raw 64 in the 36-slot sale window.
            click(player, 64); check(player.getOpenInventory().getTopInventory().getItem(0) != null, "Scrap moves into sale tray");
            player.closeInventory(); check(mando.items().identify(player.getInventory().getItem(1)).equals("agrinium_scrap"), "Closing returns unsold scrap");
            mando.jawas().open(player, gray); click(player, 64); click(player, 31);
            later(3, () -> {
                stage("RECIPES");
                check(player.getLocation().distanceSquared(new Location(player.getWorld(), 8.5, 65, .5)) < .01, "Sale returns to lesson starting point");
                check(player.getLocation().getPitch() == 0 && player.getLocation().getYaw() == -90, "Recipe lesson faces straight ahead");
                check(mando.credits().ledger().balance(player.getUniqueId()) == balance, "Practice sale leaves real credits unchanged");
                command(player, "/recipes"); check(net.mandomc.gameplay.items.RecipeGuide.isMenu(player.getOpenInventory().getTopInventory()), "Actual recipe guide opens");
                stage("RECIPES"); player.closeInventory();
                later(3, () -> {
                    stage("RECIPES"); command(player, "/recipes");
                    var inventory = player.getOpenInventory().getTopInventory(); int weapons = -1;
                    var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
                    for (int slot = 0; slot < inventory.getSize(); slot++) {
                        var item = inventory.getItem(slot);
                        if (item != null && item.getItemMeta().displayName() != null && plain.serialize(item.getItemMeta().displayName()).contains("Weapons")) weapons = slot;
                    }
                    check(weapons >= 0, "Weapons category present"); click(player, weapons);
                    later(3, () -> {
                        check(net.mandomc.gameplay.items.RecipeGuide.isCategory(player.getOpenInventory().getTopInventory(), "weapons"), "Actual weapons page is required");
                        player.closeInventory(); later(5, this::flight);
                    });
                });
            });
        });
    }
    private void flight() throws Exception {
        stage("PLACE");
        check(field(session(player), "vehicle") == null, "No X-wing blocks arrival instructions");
        check("xwing".equals(mando.items().identify(player.getInventory().getItemInMainHand())), "Training X-wing supplied in inventory");
        check(progress(player).flightMillis() == 0, "Placement has no flight progress");
        // Drive the ordinary vehicle block-interaction event; only a valid placement advances.
        var block = player.getWorld().getBlockAt(48, 64, 46);
        var denied = new PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        denied.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        Bukkit.getPluginManager().callEvent(denied); stage("PLACE");
        var placed = new PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placed); stage("BOARD");
        check(player.getInventory().getItemInMainHand().isEmpty(), "Placement consumes training X-wing item");
        UUID id = (UUID) field(session(player), "vehicle");
        var entity = mando.vehicles().entity(id); check(entity != null, "Real modeled X-wing created");
        var movement = mando.vehicles().getClass().getDeclaredMethod("movement", UUID.class, Location.class, Location.class);
        movement.setAccessible(true);
        for (var destination : List.of(new Location(entity.getWorld(), -35, 90, -80), new Location(entity.getWorld(), 130, 150, 80)))
            check((boolean) movement.invoke(mando.vehicles(), id, entity.getLocation(), destination), "Full training world permits flight outside former runway");
        check(new net.mandomc.integrations.regions.VehicleProtection().use(new Location(entity.getWorld(), 8, 100, 0)), "Training cannon use includes former lesson safezone");
        var journal = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(), "data/vehicles.yml"));
        check(!journal.contains("vehicles." + id), "Training ship is not real vehicle ownership");
        check(instruction().contains("board"), "Flight first shows only boarding instruction");
        mando.tutorial().board(player, id); check(mando.vehicles().occupant(id, player.getUniqueId()), "Player boards real ModelEngine pilot seat");
        check(mando.tutorial().lockDismount(player), "Dismount locked during flight");
        later(25, () -> {
            stage("FLIGHT"); check(progress(player).flightMillis() == 0, "Grounded idle time does not count");
            check(instruction().contains("take off"), "Grounded pilot sees takeoff instruction");
            check(!mando.vehicleCannons().fire(player), "Cannons locked before gunnery lesson");
            // Native test actors do not have a real client chunk subscription. Prepare their flight corridor.
            var chunks = new ArrayList<java.util.concurrent.CompletableFuture<?>>();
            for (int x = 0; x <= 10; x++) for (int z = 2; z <= 24; z++) chunks.add(player.getWorld().getChunkAtAsync(x, z, true));
            java.util.concurrent.CompletableFuture.allOf(chunks.toArray(java.util.concurrent.CompletableFuture[]::new))
                    .thenRun(() -> later(1, () -> {
                        for (int x = 0; x <= 10; x++) for (int z = 2; z <= 24; z++)
                            player.getWorld().addPluginChunkTicket(x, z, this);
                        driveFlight(0);
                    }));
        });
    }
    private void driveFlight(int ticks) throws Exception {
        check(ticks < 700, "Flight must finish within native test deadline");
        var step = progress(player).step();
        if (step == TutorialProgress.Step.TARGET) { target(); return; }
        var id = (UUID) field(session(player), "vehicle"); var ship = mando.vehicles().entity(id);
        var controller = com.ticxo.modelengine.api.ModelEngineAPI.getMountPairManager().getController(player.getUniqueId());
        check(controller != null, "Training pilot has native controller");
        double side = step == TutorialProgress.Step.RIGHT ? -1 : step == TutorialProgress.Step.LEFT ? 1 : 0;
        double forward = step == TutorialProgress.Step.FLIGHT && ticks == 0 ? 1 : step == TutorialProgress.Step.STOP && !brakePressed ? -1 : 0;
        if (step == TutorialProgress.Step.STOP) brakePressed = true;
        player.setRotation(ticks < 60 ? -20 : 0, ticks < 35 ? -20 : 0);
        controller.setInput(new com.ticxo.modelengine.api.mount.controller.MountController.MountInput((float) side, (float) forward, false, false));
        var model = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(ship);
        controller.updateDriverMovement(model.getBase().getMoveController(), model.getModels().values().iterator().next());
        if (ticks == 50) {
            // Read the controller output now; Entity#getVelocity is sampled after native drag/interpolation.
            var requested = model.getBase().getMoveController().getVelocity();
            check(Math.abs(requested.length() - 2.1) < .001, "Training requests normal X-wing speed after W release: " + requested);
            check(requested.getX() > .3 && requested.getZ() > .3, "Native flight follows mouse heading on both axes");
        }
        later(1, () -> driveFlight(ticks + 1));
    }
    private void target() throws Exception {
        stage("TARGET"); var session = session(player); UUID vehicle = (UUID) field(session, "vehicle");
        @SuppressWarnings("unchecked") var blocks = Set.copyOf((Set<org.bukkit.block.Block>) field(session, "targetBlocks"));
        check(blocks.size() == 289, "Target is a solid 17-by-17 block structure");
        check(blocks.stream().allMatch(block -> block.getType() == Material.RED_CONCRETE || block.getType() == Material.SEA_LANTERN), "Target uses actual world blocks");
        check(player.getWorld().getEntitiesByClass(Slime.class).isEmpty(), "Target has no hidden slime hitbox");
        check(player.getWorld().getEntitiesByClass(BlockDisplay.class).isEmpty(), "Target has no display-entity substitute");
        mando.tutorial().cannonHit(player, UUID.randomUUID(), blocks.iterator().next()); stage("TARGET");
        var platform = mando.vehicles().firingPlatform(player, List.of("weapon_one", "weapon_two"));
        check(platform != null, "Native X-wing muzzles resolve");
        var ray = player.getWorld().rayTraceBlocks(platform.muzzles().getFirst(), platform.direction(), 60,
                FluidCollisionMode.NEVER, true);
        check(ray != null && blocks.contains(ray.getHitBlock()), "Actual spawned block target intersects native cannon ray without moving it");
        check(mando.vehicleCannons().fire(player), "Native cannons emit and consume supplied ammo");
        later(30, () -> {
            stage("TARGET"); check(blocks.stream().allMatch(block -> block.getType().isAir()), "Cannon destroyed the actual target blocks before explosion delay completes");
        });
        later(90, () -> land(vehicle));
    }
    private void land(UUID vehicle) throws Exception {
        stage("LAND"); var ship = mando.vehicles().entity(vehicle);
        var controller = com.ticxo.modelengine.api.ModelEngineAPI.getMountPairManager().getController(player.getUniqueId());
        controller.setInput(new com.ticxo.modelengine.api.mount.controller.MountController.MountInput(0, 0, false, true));
        var model = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(ship);
        controller.updateDriverMovement(model.getBase().getMoveController(), model.getModels().values().iterator().next());
        later(1, () -> { if (progress(player).step() == TutorialProgress.Step.LAND) land(vehicle); else eventGuide(vehicle); });
    }
    private void eventGuide(UUID vehicle) throws Exception {
            stage("EVENTS"); check(mando.vehicles().entity(vehicle) == null, "Training ship cleaned up after target");
            @SuppressWarnings("unchecked") var originals = (Map<org.bukkit.block.Block, org.bukkit.block.BlockState>) field(session(player), "practiceBlocks");
            originals.forEach((block, state) -> practiceOriginal.put(block, state.getBlockData()));
            check(originals.size() >= 370, "Target and landing pad both record original blocks for cleanup");
            var bar = (net.kyori.adventure.bossbar.BossBar) field(session(player), "bar");
            var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
            check(plain.serialize(bar.name()).contains("Auto-continue") && plain.serialize(bar.name()).contains("/tutorial end"), "Event bossbar explains automatic timing and exit");
            var hologram = (TextDisplay) field(session(player), "hologram");
            check(plain.serialize(hologram.text()).contains("No action needed"), "Event hologram clearly requires no action");
            int firstSeconds = (int) field(session(player), "eventSeconds");
            later(25, () -> {
                check((int) field(session(player), "eventSeconds") < firstSeconds, "Visible countdown decreases");
                check(bar.progress() < 1, "Event timer bar drains");
            });
            later(380, () -> {
                check((int) field(session(player), "eventPage") == 2, "Event pages advance without player input");
                check(plain.serialize(((TextDisplay) field(session(player), "hologram")).text()).contains("Tutorial completes in"), "Final page explains timed completion");
            });
            later(580, this::completed);
    }
    private void completed() throws Exception {
        check(!TutorialInventory.active(player), "Completion exits tutorial");
        check(!practiceOriginal.isEmpty(), "Native fixture captured temporary target and landing blocks");
        practiceOriginal.forEach((block, data) -> check(block.getBlockData().equals(data), "Completion restores practice block " + block.getLocation()));
        check(player.getWorld() == origin.getWorld(), "Original world restored");
        var handle = player.getClass().getMethod("getHandle").invoke(player);
        boolean rawInvulnerable = (boolean) handle.getClass().getMethod("isInvulnerable").invoke(handle);
        check(player.getGameMode() == GameMode.SURVIVAL && !rawInvulnerable,
                "Original mode and vulnerability restored: mode=" + player.getGameMode() + " raw=" + rawInvulnerable
                        + " removed=" + handle.getClass().getMethod("isRemoved").invoke(handle));
        check(player.getInventory().getItem(0).getType() == Material.DIAMOND && player.getInventory().getItem(0).getAmount() == 7, "Original held stack preserved");
        check(player.getInventory().getItemInOffHand().getAmount() == 9, "Original offhand preserved");
        check(TutorialInventory.has(player, TutorialInventory.COMPLETED), "Durable completion marker");
        check(player.getPersistentDataContainer().has(new NamespacedKey("mandomc", "starter_kit_v1")), "Starter kit awarded after completion");
        check(mando.credits().ledger().balance(player.getUniqueId()) == 500000, "Exactly 5000 completion credits");
        var rewarded = Arrays.stream(player.getInventory().getContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        TutorialInventory.flag(player, TutorialInventory.REWARD_PENDING); // Simulate lost callback after ledger commit.
        mando.tutorial().request(player); later(25, () -> {
            stage("WARP");
            player.addAttachment(this, "weaponmechanics.use.*", true);
            player.getInventory().setHeldItemSlot(0);
            var vendor = Bukkit.getPluginManager().getPlugin("WeaponMechanics");
            var api = Class.forName("me.deecaad.weaponmechanics.WeaponMechanicsAPI", true, vendor.getClass().getClassLoader());
            var weapon = (ItemStack) api.getMethod("generateWeapon", String.class).invoke(null, "SE14C");
            var tag = Class.forName("me.deecaad.weaponmechanics.utils.CustomTag", true, vendor.getClass().getClassLoader());
            tag.getMethod("setInteger", ItemStack.class, int.class).invoke(tag.getField("AMMO_LEFT").get(null), weapon, 0);
            player.getInventory().setItemInMainHand(weapon);
            var reloadAmmo = (ItemStack) api.getMethod("generateAmmo", String.class, boolean.class).invoke(null, "Standard_Plasma_Cells", false);
            reloadAmmo.setAmount(64); player.getInventory().setItem(1, reloadAmmo);
            check((boolean) api.getMethod("tryReload", LivingEntity.class).invoke(null, player), "Real reload starts before tutorial exit");
            command(player, "/tutorial end");
            check(!net.mandomc.integrations.weapons.WeaponMechanicsItems.reloading(player), "Exit cancels native reload callbacks");
            sameInventory(player, rewarded, "Replay exit restores all gear");
            later(30, () -> {
                check(mando.credits().ledger().balance(player.getUniqueId()) == 500000, "Lost reward callback cannot duplicate payout");
                check(TutorialInventory.active(second), "Other player remains in their own lesson");
                command(second, "/tutorial end"); check(!TutorialInventory.has(second, TutorialInventory.COMPLETED), "Quit is not completion");
                check(mando.credits().ledger().balance(second.getUniqueId()) == 0, "Quit gives no credits");
                check(second.getInventory().isEmpty(), "Quit restores original inventory before crate mail");
                var crates = Bukkit.getServicesManager().load(net.mandomc.gameplay.crates.CrateService.class);
                crates.claim(second);
                check("ilum_kyber_crate_key".equals(mando.items().identify(second.getInventory().getItem(0))), "Deferred physical key reaches normal inventory");
                recoveryCheck();
            });
        });
    }
    private void recoveryCheck() throws Exception {
        var before = Arrays.stream(player.getInventory().getContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
        TutorialInventory.capture(player); player.getInventory().clear(); player.getInventory().setItem(0, mando.items().create("vader"));
        player.setGameMode(GameMode.ADVENTURE); player.setInvulnerable(true); player.saveData();
        // Join recovery has no in-memory session, as after an unexpected process exit.
        mando.tutorial().join(new PlayerJoinEvent(player, Component.empty()));
        sameInventory(player, before, "Crash-style escrow recovery"); check(!TutorialInventory.active(player), "Recovery consumes escrow only after restoring");
        later(10, this::autoAndClanChecks);
    }
    private void autoAndClanChecks() throws Exception {
        var clans = Bukkit.getServicesManager().load(net.mandomc.gameplay.clans.ClanService.class);
        var clan = clans.state().create("Existing" + UUID.randomUUID().toString().substring(0, 8), second.getUniqueId(), System.currentTimeMillis());
        mando.tutorial().request(second);
        later(25, () -> {
            command(second, "/warp"); click(second, 22);
            later(3, () -> {
                command(second, "/c map"); put(second, new Location(second.getWorld(), 50.5, 65, .5));
                later(3, () -> {
                    command(second, "/c create Practice"); command(second, "/c claim"); command(second, "/c disband");
                    check(clans.member(second) == clan, "Practice disband preserves existing real membership");
                    command(second, "/tutorial end"); check(clans.member(second) == clan, "Exit preserves real clan");
                    var fresh = actors.create(origin); mando.tutorial().join(new PlayerJoinEvent(fresh, Component.empty()));
                    check(!TutorialInventory.active(fresh), "First join waits for pack stack"); packs(fresh);
                    later(35, () -> {
                        check(TutorialInventory.active(fresh), "First join automatically enters after required packs");
                        command(fresh, "/tutorial end"); check(!TutorialInventory.has(fresh, TutorialInventory.COMPLETED), "Immediate quit does not complete");
                        prepareRestart(); success();
                    });
                });
            });
        });
    }
    private void prepareRestart() throws Exception {
        var folder = getDataFolder().toPath(); Files.createDirectories(folder);
        var expected = ItemStack.serializeItemsAsBytes(player.getInventory().getContents());
        Files.write(folder.resolve("expected-inventory.bin"), expected);
        TutorialInventory.capture(player); player.getInventory().clear(); player.getInventory().setItem(0, mando.items().create("vader"));
        TutorialInventory.flag(player, TutorialInventory.REWARD_PENDING); player.saveData();
        Path saved;
        try (var paths = Files.walk(Path.of(".").toAbsolutePath())) {
            saved = paths.filter(path -> path.getFileName().toString().equals(player.getUniqueId() + ".dat")).findFirst().orElseThrow();
        }
        Files.copy(saved, folder.resolve("crash-player.dat"), StandardCopyOption.REPLACE_EXISTING);
        var properties = new Properties(); properties.setProperty("uuid", player.getUniqueId().toString());
        properties.setProperty("player-file", Path.of(".").toAbsolutePath().relativize(saved).toString());
        try (var output = Files.newOutputStream(folder.resolve("restart.properties"))) { properties.store(output, "Disposable recovery fixture"); }
        // Cleanup restores the real actor normally. The external runner reinstates the crash snapshot while stopped.
    }
    private void restartCheck() throws Exception {
        var properties = new Properties();
        try (var input = Files.newInputStream(getDataFolder().toPath().resolve("restart.properties"))) { properties.load(input); }
        player = actors.create(mando.worlds().spawn("Batuu"), UUID.fromString(properties.getProperty("uuid"))); player.loadData();
        check(TutorialInventory.active(player), "Escrow survived an actual JVM restart");
        check(mando.credits().ledger().balance(player.getUniqueId()) == 500000, "Reward balance survived restart");
        mando.tutorial().join(new PlayerJoinEvent(player, Component.empty()));
        sameInventory(player, ItemStack.deserializeItemsFromBytes(Files.readAllBytes(getDataFolder().toPath().resolve("expected-inventory.bin"))), "Native restart restores exact inventory");
        check(!TutorialInventory.active(player), "Restart recovery clears temporary inventory marker");
        later(45, () -> {
            check(mando.credits().ledger().balance(player.getUniqueId()) == 500000, "Restart cannot duplicate a committed reward");
            check(!TutorialInventory.has(player, TutorialInventory.REWARD_PENDING), "Restart reconciles lost reward callback");
            Files.writeString(getDataFolder().toPath().resolve("RESTART-PASSED.txt"), "TUTORIAL RESTART PASSED " + checks + " native assertions");
            success();
        });
    }
    private void success() throws Exception {
        finished = true; actors.close();
        Files.createDirectories(getDataFolder().toPath());
        Files.deleteIfExists(getDataFolder().toPath().resolve("FAILED.txt"));
        Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"), "TUTORIAL PASSED " + checks + " native assertions\n");
        getLogger().info("TUTORIAL PASSED " + checks + " native assertions"); Bukkit.shutdown();
    }
    private void fail(Throwable error) {
        finished = true; getLogger().log(java.util.logging.Level.SEVERE, "TUTORIAL PROBE FAILED", error);
        try { Files.createDirectories(getDataFolder().toPath()); Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"), error.toString()); } catch (Exception ignored) { }
        if (actors != null) actors.close(); Bukkit.shutdown();
    }
}
