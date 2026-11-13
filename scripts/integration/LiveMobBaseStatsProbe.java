package net.mandomc.verification;

import java.nio.file.Files;
import java.util.ArrayList;
import net.mandomc.integrations.mobs.MythicMobAccess;
import net.mandomc.integrations.mobs.MobBaseStats;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only audit of loaded entities after the coordinated restart; creates no game entities. */
public final class LiveMobBaseStatsProbe extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            var rows = new ArrayList<String>();
            int checked = 0, failed = 0;
            try {
                var mobs = new MythicMobAccess();
                for (var world : Bukkit.getWorlds()) for (var entity : world.getLivingEntities()) {
                    String type = mobs.type(entity);
                    if (!MobBaseStats.TYPES.contains(type)) continue;
                    checked++;
                    int level = mobs.level(entity);
                    String name = entity.getCustomName();
                    double health = entity.getHealth();
                    double max = entity.getAttribute(Attribute.MAX_HEALTH).getValue();
                    boolean ok = level == 1 && (name == null || (!name.contains("Lv.") && !name.contains("Lvl.")))
                            && health > 0 && health <= max;
                    if (!ok) failed++;
                    rows.add(entity.getUniqueId() + " " + world.getName() + " " + type + " level=" + level
                            + " health=" + health + "/" + max + " name=" + name + " " + (ok ? "PASS" : "FAIL"));
                }
                rows.addFirst("checked=" + checked + " failed=" + failed);
                Files.createDirectories(getDataFolder().toPath());
                Files.write(getDataFolder().toPath().resolve("loaded-mobs.txt"), rows);
                getLogger().info(rows.getFirst());
            } catch (Exception error) {
                getLogger().log(java.util.logging.Level.SEVERE, "Loaded mob audit failed", error);
            } finally {
                Bukkit.getPluginManager().disablePlugin(this);
            }
        }, 100);
    }
}
