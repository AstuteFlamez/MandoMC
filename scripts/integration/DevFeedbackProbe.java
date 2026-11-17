package net.mandomc.verification;

import java.nio.file.Files;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.*;
import net.mandomc.gameplay.ilum.ParkourService;
import net.mandomc.gameplay.messaging.ChatStyle;
import net.mandomc.gameplay.trophies.*;
import net.mandomc.integrations.mobs.MythicMobAccess;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.view.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Socket-free native gameplay checks. Install only in a disposable fixture on port 25591. */
public final class DevFeedbackProbe extends JavaPlugin implements Listener {
    private MandoPlugin mando;
    private NativeProbePlayers actors;
    private Player player;
    private World world;
    private RancorTrophies trophies;
    private int checks;
    private final List<Entity> mobs = new ArrayList<>();
    private final Map<UUID, List<ItemStack>> drops = new HashMap<>();
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }
    private void later(int ticks, Work work) { Bukkit.getScheduler().runTaskLater(this, () -> {
        try { work.run(); } catch (Throwable error) { finish(error); }
    }, ticks); }
    private void until(BooleanSupplier ready, Work work) { until(ready, work, 0); }
    private void until(BooleanSupplier ready, Work work, int ticks) {
        later(2, () -> { if (ready.getAsBoolean()) work.run(); else if (ticks > 200) throw new AssertionError("Async operation timed out"); else until(ready, work, ticks + 2); });
    }
    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    @Override public void onEnable() { later(180, this::start); }
    private void start() throws Exception {
        check(getServer().getPort() == 25591, "Disposable port required");
        mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(mando != null && mando.isEnabled(), "MandoMC enabled");
        world = Bukkit.getWorld(NamespacedKey.minecraft("devworld"));
        // The retained fixture has a small DevWorld border; this test arena is disposable.
        world.getWorldBorder().setCenter(0, 0); world.getWorldBorder().setSize(1024);
        for (int x = -14; x <= -11; x++) for (int z = -14; z <= -11; z++) world.getChunkAt(x,z).addPluginChunkTicket(this);
        actors = new NativeProbePlayers("Feedback");
        player = actors.create(new Location(world, -201.5, 200, -199.5));
        player.setGameMode(GameMode.SURVIVAL);
        mando.ranks().apply(player);
        Bukkit.getPluginManager().registerEvents(this, this);
        recipeBookAndGuide();
        advancements();
        enchanting();
        later(3, this::checkEnchanting);
    }

    @SuppressWarnings("unchecked") private void recipeBookAndGuide() throws Exception {
        var recipes = (CraftingRecipes) field(mando, "craftingRecipes");
        recipes.join(new PlayerJoinEvent(player, Component.empty()));
        var keys = (List<NamespacedKey>) field(recipes, "keys");
        check(keys.size() > 150, "Native and delayed vendor recipes registered");
        for (var key : keys) check(player.hasDiscoveredRecipe(key), "Recipe unlocked: " + key);
        var vendor = new NamespacedKey("mandomc", "craft_e11");
        check(keys.contains(vendor) && player.hasDiscoveredRecipe(vendor), "Delayed WeaponMechanics recipe unlocked");
        var notice = new PlayerRecipeDiscoverEvent(player, vendor, true);
        Bukkit.getPluginManager().callEvent(notice);
        check(!notice.shouldShowNotification(), "Bulk unlock avoids recipe toast flood");
        var hide = mando.items().create("bantha_hide");
        check(hide.getType() == Material.LEATHER, "Bantha hide retains native leather");
        var matrix = new ItemStack[9]; matrix[0] = hide;
        matrix[1] = new ItemStack(Material.PAPER); matrix[2] = new ItemStack(Material.PAPER); matrix[3] = new ItemStack(Material.PAPER);
        var book = Bukkit.getCraftingRecipe(matrix, world);
        check(book != null && book.getResult().getType() == Material.BOOK, "Bantha hide crafts ordinary books");
        check(Bukkit.dispatchCommand(player, "recipes"), "Recipe guide opens");
        var menu = player.getOpenInventory().getTopInventory();
        var actions = (Map<Integer, Consumer<Player>>) field(menu.getHolder(), "actions");
        int saberSlot = -1, weaponSlot = -1;
        for (int slot : actions.keySet()) {
            String name = ChatStyle.plain(menu.getItem(slot).getItemMeta().displayName());
            if (name.equals("Category: Lightsabers")) saberSlot = slot;
            if (name.equals("Category: Weapons")) weaponSlot = slot;
        }
        check(saberSlot >= 0 && weaponSlot >= 0 && saberSlot != weaponSlot, "Separate Lightsabers and Weapons categories");
        actions.get(saberSlot).accept(player);
        menu = player.getOpenInventory().getTopInventory();
        check(RecipeGuide.isCategory(menu, "lightsabers"), "Tutorial category is reachable");
        check("obiwan".equals(mando.items().identify(menu.getItem(0))), "Blue base saber starts color grouping");
        player.closeInventory();
        actions.get(weaponSlot).accept(player);
        check(RecipeGuide.isCategory(player.getOpenInventory().getTopInventory(), "weapons")
                && player.getOpenInventory().getTopInventory().getItem(0).getType() == mando.items().create("e11").getType(), "Other weapons retain E-11");
        player.closeInventory();
    }

    private void advancements() {
        var advancement = Bukkit.getAdvancement(NamespacedKey.minecraft("story/mine_stone"));
        var event = new PlayerAdvancementDoneEvent(player, advancement, Component.text("Advancement announcement"));
        Bukkit.getPluginManager().callEvent(event);
        check(event.message() == null, "Advancement chat announcement suppressed");
        check(!player.getAdvancementProgress(advancement).isDone(), "Message suppression does not award progress");
    }

    private void enchanting() {
        var table = world.getBlockAt(-200, 200, -200);
        table.setType(Material.ENCHANTING_TABLE, false);
        var use = new PlayerInteractEvent(player, org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,
                null, table, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(use);
        check(use.useInteractedBlock() != Event.Result.DENY, "Enchanting table interaction allowed");
        player.setLevel(50);
        var view = (EnchantmentView) player.openEnchanting(table.getLocation(), true);
        check(view != null, "Native enchanting menu opens");
        view.getTopInventory().setItem(0, mando.items().create("robes_chestplate"));
        view.getTopInventory().setItem(1, new ItemStack(Material.LAPIS_LAZULI, 64));
    }

    private void checkEnchanting() throws Exception {
        var view = (EnchantmentView) player.getOpenInventory();
        check(Arrays.stream(view.getOffers()).anyMatch(Objects::nonNull), "Native enchantment offers for robes");
        // Use the actual menu button: Paper owns RNG, lapis/XP consumption and the resulting enchants.
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        Object menu = handle.getClass().getField("containerMenu").get(handle);
        boolean result = (boolean) menu.getClass().getMethod("clickMenuButton", Class.forName("net.minecraft.world.entity.player.Player"), int.class).invoke(menu, handle, 0);
        check(result, "Native enchanting button succeeds");
        check(!view.getTopInventory().getItem(0).getEnchantments().isEmpty(), "Enchantments applied");
        check("robes_chestplate".equals(mando.items().identify(view.getTopInventory().getItem(0))), "Enchanting preserves custom identity");
        check(player.getLevel() == 49 && view.getTopInventory().getItem(1).getAmount() == 63, "Native enchantment XP and lapis consumed");
        player.closeInventory();
        var anvil = world.getBlockAt(-200, 200, -198); anvil.setType(Material.ANVIL, false);
        var av = (AnvilView) player.openAnvil(anvil.getLocation(), true);
        var book = new ItemStack(Material.ENCHANTED_BOOK);
        var meta = (EnchantmentStorageMeta) book.getItemMeta(); meta.addStoredEnchant(Enchantment.PROTECTION, 1, false); book.setItemMeta(meta);
        av.getTopInventory().setItem(0, mando.items().create("robes_chestplate")); av.getTopInventory().setItem(1, book);
        later(3, () -> {
            check(av.getTopInventory().getResult() != null && av.getTopInventory().getResult().getEnchantmentLevel(Enchantment.PROTECTION) == 1, "Native anvil book enchanting allowed");
            av.getTopInventory().setItem(0, mando.items().create("robes_chestplate"));
            var worn = av.getTopInventory().getItem(0); var wear = (org.bukkit.inventory.meta.Damageable) worn.getItemMeta(); wear.setDamage(50); worn.setItemMeta(wear);
            av.getTopInventory().setItem(0, worn); av.getTopInventory().setItem(1, mando.items().create("bantha_hide"));
            later(3, () -> {
                var repaired = av.getTopInventory().getResult();
                check(repaired != null && ((org.bukkit.inventory.meta.Damageable) repaired.getItemMeta()).getDamage() < 50, "Bantha hide repairs leather robes in native anvil");
                player.closeInventory(); testDrops();
            });
        });
    }

    @EventHandler(priority=EventPriority.MONITOR) public void death(EntityDeathEvent event) {
        if (mobs.contains(event.getEntity())) drops.put(event.getEntity().getUniqueId(), event.getDrops().stream().map(ItemStack::clone).toList());
    }
    private void testDrops() throws Exception {
        var access = new MythicMobAccess();
        var location = new Location(world, -190, 201, -200);
        for (int i = 0; i < 16; i++) {
            var entity = access.spawn(i == 15 ? "Bantha" : "Dewback", location, 1);
            check(entity instanceof Horse, "Herd native horse base");
            mobs.add(entity); ((Horse) entity).setAdult();
            if (i == 0) ((Horse) entity).getInventory().setSaddle(new ItemStack(Material.SADDLE));
            entity.setHealth(0);
        }
        later(3, () -> {
            check(drops.size() == 16, "All herd deaths observed");
            for (int i = 0; i < 15; i++) check(drops.get(mobs.get(i).getUniqueId()).stream().noneMatch(item -> item.getType() == Material.LEATHER || "bantha_hide".equals(mando.items().identify(item))), "Dewback drops no hide " + i);
            check(drops.get(mobs.getFirst().getUniqueId()).stream().anyMatch(item -> item.getType() == Material.SADDLE), "Dewback saddle return preserved");
            check(drops.get(mobs.getLast().getUniqueId()).stream().anyMatch(item -> "bantha_hide".equals(mando.items().identify(item))), "Bantha still drops hide");
            ammoDrops();
            decorations();
        });
    }

    @SuppressWarnings("unchecked") private void ammoDrops() throws Exception {
        var access = new MythicMobAccess();
        var vendor = Bukkit.getPluginManager().getPlugin("MythicMobs");
        Object api = vendor.getClass().getMethod("getAPIHelper").invoke(vendor);
        Class<?> activeType = Class.forName("io.lumine.mythic.core.mobs.ActiveMob", true, vendor.getClass().getClassLoader());
        Class<?> eventType = Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent", true, vendor.getClass().getClassLoader());
        for (String id : List.of("QuarrenGuard", "TwilekGuard", "BobaFett", "JawaBrown")) {
            var entity = access.spawn(id, new Location(world, -190, 201, -200), 1);
            Object active = api.getClass().getMethod("getMythicMobInstance", Entity.class).invoke(api, entity);
            var ammo = mando.items().create(id.equals("JawaBrown") ? "ionized_energy_cells" : "standard_plasma_cells");
            int rounds = 0;
            for (int trial = 0; trial < 1000; trial++) {
                var event = (Event) eventType.getConstructor(activeType, LivingEntity.class, List.class).newInstance(active, null, new ArrayList<>());
                Bukkit.getPluginManager().callEvent(event);
                var loot = (List<ItemStack>) eventType.getMethod("getDrops").invoke(event);
                int count = loot.stream().filter(item -> item.isSimilar(ammo)).mapToInt(ItemStack::getAmount).sum();
                check(count >= 0 && count <= 20, id + " ammo bound"); rounds += count;
            }
            check(rounds > 9500 && rounds < 10500, id + " mean near ten rounds: " + rounds / 1000.0);
            getLogger().info(id + " ammo average: " + rounds / 1000.0);
            access.remove(entity);
        }
    }

    private TrophyJournal.Entry decoration(Block block) { return trophies.journal().at(new TrophyJournal.Cell(world.getKey().toString(), block.getX(), block.getY(), block.getZ())); }
    private void decorations() throws Exception {
        getLogger().info("Checking mixed helmet/skull custody messages");
        player.setOp(true); mando.ranks().apply(player);
        check(Bukkit.dispatchCommand(player, "staff on"), "Native staff build mode");
        check(player.getGameMode() == GameMode.CREATIVE && player.hasPermission("mandomc.territory.edit"), "DevWorld build authority");
        trophies = Bukkit.getServicesManager().load(RancorTrophies.class);
        var helmet = world.getBlockAt(-200, 200, -202); helmet.setType(Material.AIR, false); helmet.getRelative(0, -1, 0).setType(Material.STONE, false);
        player.getInventory().clear(); player.getInventory().setItemInMainHand(mando.items().create("robes_helmet"));
        check(player.getWorld().equals(world), "Decoration actor remains in test world");
        check(player.getEyeLocation().distanceSquared(helmet.getLocation().add(.5,.5,.5)) <= 36, "Decoration within reach");
        getLogger().info("Decoration actor: " + player.getLocation() + " health=" + player.getHealth() + " mode=" + player.getGameMode() + " authority=" + player.hasPermission("mandomc.territory.edit"));
        check(player.isOnline(), "Decoration actor online");
        check(!player.isDead(), "Decoration actor alive");
        check(trophies.decoration(player.getInventory().getItemInMainHand()), "Held helmet recognized: " + player.getInventory().getItemInMainHand());
        check(!(boolean) field(trophies, "closed"), "Custody service open");
        var clear = trophies.getClass().getDeclaredMethod("clear", Block.class); clear.setAccessible(true);
        check((boolean) clear.invoke(trophies, helmet), "Helmet placement cell clear and within border");
        check(world.isChunkLoaded(helmet.getX() >> 4, helmet.getZ() >> 4), "Placement chunk held for async custody");
        trophies.place(player, helmet, EquipmentSlot.HAND);
        getLogger().info("Placement responses: " + actors.drain(player).stream().map(ChatStyle::plain).toList());
        until(() -> decoration(helmet) != null && decoration(helmet).phase() == TrophyJournal.Phase.PLACED, () -> {
            getLogger().info("Helmet placed; checking skull");
            actors.drain(player);
            var skull = world.getBlockAt(-200, 200, -201); skull.setType(Material.AIR, false); skull.getRelative(0, -1, 0).setType(Material.STONE, false);
            player.getInventory().setItemInMainHand(mando.items().create("rancor_skull"));
            trophies.place(player, skull, EquipmentSlot.HAND);
            // Change hands before the asynchronous callback; the operation must retain the skull context.
            player.getInventory().setItemInOffHand(mando.items().create("robes_helmet"));
            until(() -> decoration(skull) != null && decoration(skull).phase() == TrophyJournal.Phase.PLACED, () -> {
                getLogger().info("Skull placed; checking recovery");
                var messages = actors.drain(player).stream().map(ChatStyle::plain).toList();
                check(messages.stream().anyMatch(s -> s.contains("Rancor Skull placed")), "Skull names itself with an older owned helmet");
                check(messages.stream().noneMatch(s -> s.contains("Helmet placed")), "No misleading helmet placement message");
                trophies.breakTrophy(player, decoration(skull));
                until(() -> decoration(skull) == null, () -> { trophies.breakTrophy(player, decoration(helmet)); until(() -> decoration(helmet) == null, this::dropper); });
            });
        });
    }

    private void dropper() throws Exception {
        Bukkit.dispatchCommand(player, "staff off");
        player.setGameMode(GameMode.SURVIVAL);
        var parkour = Bukkit.getServicesManager().load(ParkourService.class);
        parkour.begin(player);
        player.getPersistentDataContainer().set(new NamespacedKey("mandomc", "ilum_checkpoint"), PersistentDataType.INTEGER, 3);
        parkour.retry(player);
        Location saved = player.getLocation();
        Object handle = player.getClass().getMethod("getHandle").invoke(player);
        handle.getClass().getMethod("setPos", double.class, double.class, double.class).invoke(handle, saved.getX()+2, saved.getY()+2, saved.getZ());
        Location before = player.getLocation();
        var suffocation = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.SUFFOCATION, DamageSource.builder(DamageType.IN_WALL).build(), 2);
        Bukkit.getPluginManager().callEvent(suffocation);
        check(suffocation.isCancelled(), "Dropper suffocation damage cancelled");
        player.setFireTicks(30);
        var move = new PlayerMoveEvent(player, before, before.clone().add(.1, 0, 0)); Bukkit.getPluginManager().callEvent(move);
        check(move.getTo().distanceSquared(saved) > 1, "Fire movement does not retry dropper"); player.setFireTicks(0);
        later(3, () -> {
            check(player.getLocation().distanceSquared(before) < .01, "Suffocation never teleports runner");
            var fall = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL, DamageSource.builder(DamageType.FALL).build(), 2);
            Bukkit.getPluginManager().callEvent(fall); check(fall.isCancelled(), "Dropper fall damage cancelled");
            later(3, () -> {
                check(player.getLocation().distanceSquared(saved) < .01, "Fall retries saved checkpoint");
                player.getPersistentDataContainer().set(new NamespacedKey("mandomc", "ilum_checkpoint"), PersistentDataType.INTEGER, 2);
                var normal = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.SUFFOCATION, DamageSource.builder(DamageType.IN_WALL).build(), 2);
                Bukkit.getPluginManager().callEvent(normal); check(!normal.isCancelled(), "Other checkpoints retain ordinary damage");
                parkour.leave(player, true, false); vote();
            });
        });
    }

    private void vote() throws Exception {
        var votePlayer = actors.create(mando.worlds().spawn("Batuu"));
        mando.ranks().apply(votePlayer);
        var played = votePlayer.getClass().getDeclaredField("hasPlayedBefore");
        played.setAccessible(true); played.setBoolean(votePlayer, true);
        var joined = new PlayerJoinEvent(votePlayer, Component.empty());
        for (var listener : PlayerJoinEvent.getHandlerList().getRegisteredListeners())
            if (listener.getPlugin().getName().equals("VotingPlugin")) listener.callEvent(joined);
        actors.drain(votePlayer);
        var vendor = Bukkit.getPluginManager().getPlugin("Votifier");
        Class<?> voteType = Class.forName("com.vexsoftware.votifier.model.Vote", true, vendor.getClass().getClassLoader());
        var vote = voteType.getConstructor(String.class, String.class, String.class, String.class)
                .newInstance("Minecraft-MP.com", votePlayer.getName(), "127.0.0.1", Long.toString(System.currentTimeMillis() / 1000));
        Class<?> eventType = Class.forName("com.vexsoftware.votifier.model.VotifierEvent", true, vendor.getClass().getClassLoader());
        Bukkit.getPluginManager().callEvent((Event) eventType.getConstructor(voteType).newInstance(vote));
        var key = new NamespacedKey("mandomc", "crate_keys_vote");
        until(() -> mando.credits().ledger().balance(votePlayer.getUniqueId()) == 50000
                && votePlayer.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0) == 1, () -> {
            later(20, () -> {
                var messages = actors.drain(votePlayer).stream().map(ChatStyle::plain).toList();
                check(messages.stream().anyMatch(m -> m.startsWith(ChatStyle.PREFIX) && m.contains("voted on")), "Voting broadcast uses server formatting");
                check(messages.stream().anyMatch(m -> m.startsWith(ChatStyle.PREFIX) && m.contains("500 credits") && m.contains("Voting Crate key")), "Voting reward uses server formatting");
                check(mando.credits().ledger().balance(votePlayer.getUniqueId()) == 50000
                        && votePlayer.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0) == 1, "Voting rewards remain exactly once");
                finish(null);
            });
        });
    }

    private void finish(Throwable error) {
        try {
            if (actors != null) actors.close();
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve(error == null ? "PASSED.txt" : "FAILED.txt"), error == null ? checks + " native dev feedback assertions passed\n" : error.toString());
            if (error == null) getLogger().info("DEV FEEDBACK PASSED: " + checks);
            else getLogger().log(java.util.logging.Level.SEVERE, "DEV FEEDBACK FAILED", error);
        } catch (Exception failure) { getLogger().log(java.util.logging.Level.SEVERE, "Probe cleanup failed", failure); }
        Bukkit.shutdown();
    }
}
