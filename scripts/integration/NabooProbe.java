package net.mandomc.verification;

import java.nio.file.Files;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.territory.TerritoryService;
import net.mandomc.gameplay.territory.TerritoryPolicy;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only main-thread assertions; safe to run on the dev server without changing player state. */
public final class NabooProbe extends JavaPlugin {
    private int checks;
    private void check(boolean value, String reason) {
        if (!value) throw new IllegalStateException(reason);
        checks++;
    }
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (Files.exists(getDataFolder().toPath().resolve("PASSED.txt")) || Files.exists(getDataFolder().toPath().resolve("FAILED.txt"))) return;
            var complete = Bukkit.getPluginManager().getPlugin("MandoMC").getDataFolder().toPath()
                    .resolve("data/frozen-land/wilderness-naboo-20261113.journal.completed");
            if (!Files.exists(complete)) return;
            try {
                var world = Bukkit.getWorld(NamespacedKey.minecraft("naboo"));
                world.getWorldBorder().setSize(500);
                run();
                getDataFolder().mkdirs();
                Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"), "NABOO PASSED: " + checks);
                getLogger().info("NABOO PASSED: " + checks);
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "NABOO FAILED", error);
                try { getDataFolder().mkdirs(); Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"), error.toString()); }
                catch (Exception ignored) { }
            }
        }, 200, 20);
    }
    private void run() {
        var plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(plugin != null && plugin.isEnabled(), "Gameplay enabled");
        var world = Bukkit.getWorld(NamespacedKey.minecraft("naboo"));
        check(world != null, "Naboo loaded");
        check(plugin.worlds().resolve("Naboo") == world, "Public alias");
        var arrival = plugin.warps().destination("naboo");
        check(arrival.getWorld() == world, "Warp world");
        check(arrival.getX() == -13550.5 && arrival.getY() == 101 && arrival.getZ() == 3359.5, "Exact arrival");
        check(arrival.getYaw() == 180 && arrival.getPitch() == 0, "Legacy facing");
        ArrivalDestinations.safeSpawn(arrival); checks++;
        check(world.getBlockAt(-13551,100,3359).getType() == Material.GRASS_BLOCK, "Original lakeshore support");
        check(world.getPVP(), "Wilderness PvP enabled");
        var service = Bukkit.getServicesManager().load(TerritoryService.class);
        check(service != null, "Native territory active");
        var policy = service.policy();
        for (int x = -848; x <= -846; x++) for (int z = 208; z <= 210; z++) {
            boolean safe = x == -847 && z == 209;
            check(policy.wildernessChunk("minecraft:naboo",x,z) == !safe, "Claim eligibility");
            for (int y : new int[] {-64,0,263,319}) for (int offset : new int[] {0,15}) {
                var point = new Location(world,x*16+offset,y,z*16+offset);
                check(service.zone(point) == (safe ? TerritoryPolicy.Zone.SAFEZONE : TerritoryPolicy.Zone.WILDERNESS), "Chunk boundary/full height");
                check(service.combat(point) == !safe, "Combat boundary");
                check(service.spawning(point) == !safe, "Spawn boundary");
                check(policy.edits("minecraft:naboo",point.getBlockX(),y,point.getBlockZ()) == !safe, "Build boundary");
            }
        }
        Inventory[] menu = new Inventory[1];
        UUID id = UUID.randomUUID();
        var actor = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p,m,a) -> {
            return switch (m.getName()) {
                case "getUniqueId" -> id;
                case "openInventory" -> { menu[0] = (Inventory) a[0]; yield null; }
                case "hasPermission", "isOnline" -> true;
                case "hashCode" -> id.hashCode();
                case "equals" -> p == a[0];
                default -> null;
            };
        });
        plugin.warps().open(actor);
        check(menu[0] != null && menu[0].getSize() == 54, "Galaxy Map opens");
        var icon = menu[0].getItem(15);
        check(icon != null && NamespacedKey.fromString("mandomc:planets/naboo").equals(icon.getItemMeta().getItemModel()), "Modern Naboo icon at slot 15");
        check(plugin.getCommand("warp").tabComplete(actor,"warp",new String[]{"ald"}).contains("naboo"), "Warp completion");
        for (String existing : new String[]{"spawn","hoth","tatooine","mandalore","ilum"})
            check(plugin.warps().destination(existing).getWorld() != null, "Existing warp " + existing);
        var clans = Bukkit.getServicesManager().load(net.mandomc.gameplay.clans.ClanService.class);
        check(clans != null, "Clans active");
        for (int x = -848; x <= -846; x++) for (int z = 208; z <= 210; z++) {
            var chunk = new net.mandomc.gameplay.clans.ClanState.Chunk("minecraft:naboo", x, z);
            check(clans.land().supported(chunk) == !(x == -847 && z == 209), "Claim scope near arrival");
            check(clans.land().ready(chunk) == !(x == -847 && z == 209), "Durable originals near arrival");
        }
        check(world.getWorldBorder().getSize() == 500, "500-block border");
        check(world.getWorldBorder().getCenter().getX() == -13552 && world.getWorldBorder().getCenter().getZ() == 3440, "Border center");
        check(!world.getWorldBorder().isInside(new Location(world,-13803,101,3440)), "West boundary");
        check(!world.getWorldBorder().isInside(new Location(world,-13301,101,3440)), "East boundary");
        check(!world.getWorldBorder().isInside(new Location(world,-13552,101,3189)), "North boundary");
        check(!world.getWorldBorder().isInside(new Location(world,-13552,101,3691)), "South boundary");
    }
}
