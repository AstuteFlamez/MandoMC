package net.mandomc.verification;

import java.util.*;
import java.nio.file.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.admin.*;
import net.mandomc.gameplay.ilum.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Run only in a disposable full-stack runtime; never install on a shared server. */
public final class DevPlaytestProbe extends JavaPlugin {
    private MandoPlugin mando;
    private NativeProbePlayers actors;
    private Player player;
    private ItemStack[] original;
    private int checks;
    private final List<Entity> entities = new ArrayList<>();
    private void check(boolean value, String why) { if (!value) throw new AssertionError(why); checks++; }
    @FunctionalInterface interface Work { void run() throws Exception; }
    private void later(int ticks, Work work) { Bukkit.getScheduler().runTaskLater(this, () -> {
        try { work.run(); } catch (Throwable error) { failure(error); }
    }, ticks); }
    private void command(String line) { check(Bukkit.dispatchCommand(player, line), "Command dispatch: " + line); }
    @Override public void onEnable() { later(180, this::start); }
    private void start() throws Exception {
        check(getServer().getPort() == 25590, "Disposable port only");
        mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        actors = new NativeProbePlayers("DevFix");
        if (Files.exists(getDataFolder().toPath().resolve("restart-ready"))) { restart(); return; }
        player = actors.create(mando.worlds().spawn("Batuu"));
        player.setGameMode(GameMode.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 13));
        player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        player.getInventory().setItemInOffHand(new ItemStack(Material.EMERALD, 7));
        original = player.getInventory().getContents();
        mando.ranks().apply(player);
        check(!player.hasPermission("mandomc.travel.back"), "Default cannot /back");
        player.setOp(true); mando.ranks().apply(player);
        check(player.hasPermission("mandomc.staff"), "Operator can enable staff mode");
        check(!player.hasPermission("mandomc.admin.gamemode"), "Operator without staff mode has default permissions");
        check(!player.hasPermission("minecraft.command.give"), "Vanilla give blocked while staff mode is off");
        Bukkit.dispatchCommand(player, "minecraft:give " + player.getName() + " dirt 1");
        Bukkit.dispatchCommand(player, "minecraft:gamemode creative");
        sameInventory(); check(player.getGameMode() == GameMode.SURVIVAL, "Namespaced vanilla commands remain blocked");
        command("staff on"); check(StaffMode.active(player), "Staff mode enabled");
        check(player.getInventory().isEmpty(), "Staff inventory empty including armor and offhand");
        check(player.getGameMode() == GameMode.CREATIVE, "Staff starts in Creative");
        check(player.hasPermission("mandomc.admin.gamemode"), "Staff commands enabled");
        var parkour = Bukkit.getServicesManager().load(ParkourService.class);
        check(player.teleport(parkour.start()), "Staff teleports into Ilum");
        check(!ParkourInventory.stored(player), "Staff Ilum does not capture another inventory");
        command("parkour checkpoint 3");
        check(player.getLocation().distanceSquared(parkour.checkpoints().get(3)) < .01, "Staff checkpoint shortcut works");
        check(player.teleport(parkour.checkpoints().get(4)), "Staff free teleport in Ilum");
        player.getInventory().setItem(0, new ItemStack(Material.NETHERITE_BLOCK, 64));
        command("staff off");
        check(!StaffMode.active(player), "Staff mode disabled");
        sameInventory(); check(player.getGameMode() == GameMode.SURVIVAL, "Survival restored");
        check(!player.hasPermission("mandomc.admin.gamemode") && !player.hasPermission("mandomc.travel.back"), "Privileges removed immediately");
        command("staff on"); command("gms");
        later(3, () -> {
            check(StaffMode.active(player) && player.getGameMode() == GameMode.SURVIVAL, "/gms preserves staff mode in Survival");
            check(player.getInventory().isEmpty(), "Survival gamemode retains separate empty staff inventory");
            check(player.hasPermission("mandomc.travel.back"), "Survival staff retains staff permissions");
            command("staff off"); sameInventory();
            command("staff on");
            // Exercise the same durable marker used after a JVM restart, with no reliance on an in-memory mode set.
            check(StaffInventory.active(player), "Durable staff recovery marker exists");
            Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
            check(!StaffMode.active(player), "Join restores interrupted staff session"); sameInventory();
            mando.ranks().revokeOperator(player); mando.ranks().apply(player);
            protection(parkour);
        });
    }
    private void sameInventory() {
        var current = player.getInventory().getContents();
        for (int i = 0; i < current.length; i++) check(Objects.equals(emptyToNull(current[i]), emptyToNull(original[i])), "Survival inventory restored at slot " + i + " expected=" + original[i] + " actual=" + current[i]);
    }
    private static ItemStack emptyToNull(ItemStack item) { return item == null || item.isEmpty() ? null : item; }
    private void protection(ParkourService parkour) throws Exception {
        var food = new FoodLevelChangeEvent(player, 15); Bukkit.getPluginManager().callEvent(food);
        check(food.isCancelled(), "Batuu hunger loss blocked");
        var block = player.getWorld().getBlockAt(player.getLocation().getBlockX()+2, 200, player.getLocation().getBlockZ());
        block.setType(Material.OAK_TRAPDOOR, false);
        var click = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, null, block, org.bukkit.block.BlockFace.UP, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(click); check(click.useInteractedBlock() == org.bukkit.event.Event.Result.DENY, "Batuu trapdoor cannot be flipped"); block.setType(Material.AIR, false);
        var wild = mando.worlds().spawn("Tatooine");
        boolean found = false;
        outer: for (int x=-128; x<=128; x+=16) for (int z=-128; z<=128; z+=16) {
            var at=wild.clone().add(x, 0, z);
            if (mando.territories().edits(at)) { wild=at;found=true;break outer; }
        }
        check(found,"Find wilderness fixture");
        var zombie=wild.getWorld().spawn(wild, Zombie.class);entities.add(zombie);
        var acquire=new EntityTargetLivingEntityEvent(zombie,player,EntityTargetEvent.TargetReason.CLOSEST_PLAYER);
        Bukkit.getPluginManager().callEvent(acquire);check(acquire.isCancelled(),"Wilderness mob cannot acquire safezone player");
        player.teleport(wild); zombie.setTarget(player);check(zombie.getTarget()==player,"Mob may target player in wilderness");
        player.teleport(mando.worlds().spawn("Batuu"));
        later(8, () -> {
            check(zombie.getTarget()==null,"Existing aggro cleared on safezone entry");
            check(player.teleport(parkour.start()), "Enter normal Ilum");
            check(ParkourInventory.stored(player), "Normal player receives course inventory");
            var lever=parkour.world().getBlockAt(parkour.start().getBlockX()+4,200,parkour.start().getBlockZ());lever.setType(Material.LEVER,false);
            var use=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,null,lever,org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(use);check(use.useInteractedBlock()!=org.bukkit.event.Event.Result.DENY,"Ilum lever interaction allowed");lever.setType(Material.AIR,false);
            player.getPersistentDataContainer().set(new NamespacedKey("mandomc","ilum_checkpoint"),PersistentDataType.INTEGER,3);
            var before=player.getLocation();
            var damage=new EntityDamageEvent(player,EntityDamageEvent.DamageCause.FALL,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(),2);
            Bukkit.getPluginManager().callEvent(damage);check(!damage.isCancelled(),"Checkpoint 3 environmental damage allowed");
            var move=new PlayerMoveEvent(player,before,before.clone().add(.1,0,0));player.setFireTicks(40);Bukkit.getPluginManager().callEvent(move);
            check(move.getTo().equals(before.clone().add(.1,0,0)),"Checkpoint 3 fire does not reset movement");player.setFireTicks(0);
            var respawn=new PlayerRespawnEvent(player,before,false);Bukkit.getPluginManager().callEvent(respawn);
            check(respawn.getRespawnLocation().distanceSquared(parkour.checkpoints().get(3))<.01,"Death respawns at checkpoint 3");
            player.getPersistentDataContainer().set(new NamespacedKey("mandomc","ilum_checkpoint"),PersistentDataType.INTEGER,2);
            var earlier=new EntityDamageEvent(player,EntityDamageEvent.DamageCause.FALL,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.FALL).build(),2);
            Bukkit.getPluginManager().callEvent(earlier);check(earlier.isCancelled(),"Earlier checkpoints retain damage reset");
            parkour.leave(player,true,false); sameInventory(); circuits(parkour);
        });
    }
    private void circuits(ParkourService parkour) {
        var world=parkour.world(); int x=parkour.start().getBlockX()+8, z=parkour.start().getBlockZ();
        int y=world.getMaxHeight()-20;
        var source=world.getBlockAt(x,y,z); var lamp=world.getBlockAt(x+1,y,z);
        source.setType(Material.AIR,true); lamp.setType(Material.AIR,true);
        lamp.setType(Material.REDSTONE_LAMP,true);source.setType(Material.REDSTONE_BLOCK,true);
        var other=player.getWorld().getBlockAt(x,200,z);var dark=player.getWorld().getBlockAt(x+1,200,z);
        other.setType(Material.AIR,true);dark.setType(Material.AIR,true);
        dark.setType(Material.REDSTONE_LAMP,true);other.setType(Material.REDSTONE_BLOCK,true);
        later(10,()->{
            Object level=world.getClass().getMethod("getHandle").invoke(world);
            Object dimension=level.getClass().getMethod("dimension").invoke(level);
            Object helper=Class.forName("net.mandomc.redstone.IlumCircuits",true,null).getMethod("ilum",Object.class).invoke(null,level);
            check(lamp.isBlockIndirectlyPowered(),"Ilum native signal reads work: dimension="+dimension+" bukkit="+world.getKey()+" helper="+helper+" heights="+world.getMinHeight()+","+world.getMaxHeight()+" blocks="+source.getType()+","+lamp.getType());
            check(((org.bukkit.block.data.Lightable)lamp.getBlockData()).isLit(),"Ilum native redstone activates lamp");
            check(!dark.isBlockIndirectlyPowered(),"Native redstone remains disabled on Batuu");
            check(!((org.bukkit.block.data.Lightable)dark.getBlockData()).isLit(),"Batuu lamp remains off");
            for(var block:List.of(source,lamp,other,dark))block.setType(Material.AIR,false);
            success();
        });
    }
    private void success() throws Exception {
        prepareRestart();
        entities.forEach(Entity::remove);actors.close();
        Files.createDirectories(getDataFolder().toPath());Files.deleteIfExists(getDataFolder().toPath().resolve("FAILED.txt"));Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"), checks+" native dev-playtest assertions passed\n");
        getLogger().info("DEV PLAYTEST PASSED: "+checks);
    }
    private void prepareRestart() throws Exception {
        var folder=getDataFolder().toPath();Files.createDirectories(folder);
        Files.write(folder.resolve("expected-inventory.bin"),ItemStack.serializeItemsAsBytes(player.getInventory().getContents()));
        player.setOp(true);mando.ranks().apply(player);command("staff on");command("gms");
        check(StaffMode.active(player), "Survival staff marker remains durable before restart");player.saveData();
        Path saved;
        try(var paths=Files.walk(Path.of(".").toAbsolutePath())) {
            saved=paths.filter(path->path.getFileName().toString().equals(player.getUniqueId()+".dat")).findFirst().orElseThrow();
        }
        Files.copy(saved,folder.resolve("crash-player.dat"),StandardCopyOption.REPLACE_EXISTING);
        var properties=new Properties();properties.setProperty("uuid",player.getUniqueId().toString());
        properties.setProperty("player-file",Path.of(".").toAbsolutePath().relativize(saved).toString());
        try(var output=Files.newOutputStream(folder.resolve("restart.properties"))){properties.store(output,"Disposable staff recovery fixture");}
    }
    private void restart() throws Exception {
        var folder=getDataFolder().toPath();var properties=new Properties();
        try(var input=Files.newInputStream(folder.resolve("restart.properties"))){properties.load(input);}
        player=actors.create(mando.worlds().spawn("Batuu"),UUID.fromString(properties.getProperty("uuid")));player.loadData();
        original=ItemStack.deserializeItemsFromBytes(Files.readAllBytes(folder.resolve("expected-inventory.bin")));
        check(StaffInventory.active(player),"Staff escrow survived actual JVM restart");
        Bukkit.getPluginManager().callEvent(new PlayerJoinEvent(player,net.kyori.adventure.text.Component.empty()));
        check(!StaffMode.active(player),"Restart recovers survival mode");sameInventory();
        check(player.getGameMode()==GameMode.SURVIVAL && !player.isOp(),"Restart leaves no native operator privilege");
        check(player.hasPermission("mandomc.staff"),"Operator can still enable staff after restart");
        check(!player.hasPermission("minecraft.command.give"),"Restart restores default permissions");
        mando.ranks().revokeOperator(player);actors.close();
        Files.deleteIfExists(folder.resolve("FAILED.txt"));
        Files.writeString(folder.resolve("RESTART-PASSED.txt"),checks+" staff restart assertions passed\n");
        getLogger().info("STAFF RESTART PASSED: "+checks);
    }
    private void failure(Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE,"DEV PLAYTEST FAILED",error);
        try {Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());}catch(Exception ignored){}
        entities.forEach(Entity::remove);if(actors!=null)actors.close();
    }
}
