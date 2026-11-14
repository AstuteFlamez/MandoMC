package net.mandomc.verification;

import java.nio.file.Files;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Disposable private authoring fixture. Never install on a player server. */
public final class NabooCaveReference extends JavaPlugin {
    @Override
    public void onEnable() {
        getServer().getScheduler().runTask(this, () -> {
            World world = new WorldCreator("naboo_cave_reference")
                    .seed(2026111303L).generateStructures(false).createWorld();
            if (world == null) throw new IllegalStateException("Reference world not created");
            world.setAutoSave(true);
            new BukkitRunnable() {
                private int index;
                @Override
                public void run() {
                    try {
                        // Bound work and space; the offline exporter reads only this square.
                        for (int budget = 0; budget < 2 && index < 1024; budget++, index++) {
                            world.getChunkAt(index % 32, index / 32).load();
                        }
                        if (index % 64 == 0) getLogger().info("REFERENCE CHUNKS " + index + "/1024");
                        if (index == 1024) {
                            world.save();
                            if (!getServer().unloadWorld(world, true)) {
                                throw new IllegalStateException("Reference world still in use");
                            }
                            getDataFolder().mkdirs();
                            Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),
                                    "1024 vanilla chunks generated");
                            cancel();
                        }
                    } catch (Exception error) {
                        getLogger().log(java.util.logging.Level.SEVERE, "Reference generation failed", error);
                        cancel();
                    }
                }
            }.runTaskTimer(this, 1, 1);
        });
    }
}
