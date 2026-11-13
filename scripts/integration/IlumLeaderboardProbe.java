package net.mandomc.verification;

import java.nio.file.Files;
import java.util.*;
import net.mandomc.gameplay.ilum.ParkourService;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Narrow native checks; never respawn the owner's hints, NPCs or course. */
public final class IlumLeaderboardProbe extends JavaPlugin {
    private int checks;
    private void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    private List<Object> presentation(TextDisplay e) {
        return Arrays.asList(e.text(), e.getLocation(), e.getAlignment(), e.getLineWidth(), e.getTextOpacity(),
                e.getTransformation().toString(), e.getBillboard(), e.isShadowed(), e.isSeeThrough(),
                e.isPersistent(), e.getViewRange(), e.getBrightness(), e.getShadowRadius(), e.getShadowStrength());
    }
    private boolean transparent(TextDisplay e) {
        return !e.isDefaultBackground() && e.getBackgroundColor() != null && e.getBackgroundColor().getAlpha() == 0;
    }
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); } catch (Throwable failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "Ilum leaderboard check failed", failure);
                try { getDataFolder().mkdirs(); Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"), failure.toString()); }
                catch (Exception error) { throw new RuntimeException(error); }
            } finally {
                if (new java.io.File(getDataFolder(), "isolated").exists()) Bukkit.shutdown();
                else Bukkit.getPluginManager().disablePlugin(this);
            }
        }, 100);
    }
    private void verify() throws Exception {
        var owner = Objects.requireNonNull(Bukkit.getServicesManager().load(ParkourService.class));
        var field = ParkourService.class.getDeclaredField("leaderboard"); field.setAccessible(true);
        var board = (TextDisplay) field.get(owner);
        check(board != null && board.isValid(), "Owned leaderboard exists");
        check(transparent(board), "Startup/new owned leaderboard has explicit transparent background");
        check(board.isShadowed(), "Leaderboard text shadow retained");
        var before = presentation(board);
        var ids = owner.world().getEntities().stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, List<Object>> others = new HashMap<>();
        for (var world : Bukkit.getWorlds()) for (var display : world.getEntitiesByClass(TextDisplay.class)) {
            if (display != board) {
                var state = new ArrayList<>(presentation(display));
                state.add(display.isDefaultBackground()); state.add(display.getBackgroundColor());
                others.put(display.getUniqueId(), state);
            }
        }
        var update = ParkourService.class.getDeclaredMethod("updateLeaderboard"); update.setAccessible(true);
        // Model the pre-fix already spawned entity, then run the actual owner's existing refresh path.
        board.setDefaultBackground(true); board.setBackgroundColor(Color.fromARGB(100, 0, 0, 0));
        update.invoke(owner);
        check(transparent(board), "Existing leaderboard corrected on owner refresh");
        check(before.equals(presentation(board)), "Text/order/alignment/size/location/shadow and other presentation unchanged");
        update.invoke(owner);
        check(transparent(board) && before.equals(presentation(board)), "Repeated refresh stays transparent and preserves contents");
        check(ids.equals(owner.world().getEntities().stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet())), "Existing entities/NPCs retained");
        for (var entry : others.entrySet()) {
            var display = (TextDisplay) Objects.requireNonNull(Bukkit.getEntity(entry.getKey()));
            var state = new ArrayList<>(presentation(display));
            state.add(display.isDefaultBackground()); state.add(display.getBackgroundColor());
            check(entry.getValue().equals(state), "Unrelated display untouched " + entry.getKey());
        }
        var spawn = ParkourService.class.getDeclaredMethod("lambda$spawnDisplays$1", TextDisplay.class); spawn.setAccessible(true);
        var fresh = owner.world().spawn(board.getLocation().clone().add(0, 10, 0), TextDisplay.class);
        try {
            spawn.invoke(null, fresh);
            check(transparent(fresh), "New display callback sets explicit zero-alpha/default-background false");
            check(fresh.isShadowed() && fresh.getBillboard() == org.bukkit.entity.Display.Billboard.CENTER
                    && fresh.getViewRange() == .7f && !fresh.isPersistent(), "New display retains owner defaults");
        } finally { fresh.remove(); }
        check("1:01.0".equals(ParkourService.format(61_001)), "Deployed timer formatter correction retained");
        getDataFolder().mkdirs();
        var result = "PASSED: " + checks + " Ilum leaderboard assertions\n";
        Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"), result); getLogger().info(result);
    }
}
