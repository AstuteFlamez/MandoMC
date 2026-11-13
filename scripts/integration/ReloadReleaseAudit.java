package net.mandomc.verification;

import java.nio.file.Files;
import java.util.ArrayList;
import net.mandomc.integrations.weapons.ReloadIndicator;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Startup/read-only release gate. No actors, item writes, cooldown writes or event changes. */
public final class ReloadReleaseAudit extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, this::audit, 120);
    }
    private void audit() {
        var rows = new ArrayList<String>();
        try {
            var main = Bukkit.getPluginManager().getPlugin("MandoMC");
            require(main != null && main.isEnabled(), "MandoMC enabled", rows);
            var failed = main.getClass().getDeclaredField("failed"); failed.setAccessible(true);
            require(!failed.getBoolean(main), "complete bootstrap passed without login gate", rows);
            var indicator = Bukkit.getServicesManager().load(ReloadIndicator.class);
            require(indicator != null, "native reload service registered through current additive hook", rows);
            require(indicator.activeReloads() >= 0, "reload observer available without creating test actors", rows);
            require("26.1.2-v1".equals(System.getProperty("mandomc.redstone.agent")), "mandatory native agent active", rows);
            for (String service : new String[]{"worlds", "warps", "events"})
                require(main.getClass().getMethod(service).invoke(main) != null, "existing " + service + " bootstrap retained", rows);
            String pack = getConfig().getString("pack-sha1");
            if (pack != null) {
                var server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
                String actual = String.valueOf(server.getClass().getMethod("getServerResourcePack").invoke(server));
                require(actual.contains(pack) && actual.contains(getConfig().getString("pack-tag")), "effective resource pack retained", rows);
            }
            if (getConfig().contains("current")) {
                var events = main.getClass().getMethod("events").invoke(main);
                Object current = events.getClass().getMethod("activeId").invoke(events);
                Object next = events.getClass().getMethod("queuedId").invoke(events);
                require(java.util.Objects.equals(current, getConfig().getString("current")), "fresh current event restored", rows);
                require(java.util.Objects.equals(next, getConfig().getString("next")), "fresh next event restored", rows);
            }
            Files.createDirectories(getDataFolder().toPath());
            Files.write(getDataFolder().toPath().resolve("PASSED.txt"), rows);
            getLogger().info("NATIVE RELOAD RELEASE AUDIT PASSED: " + rows.size() + " checks; no actors or gameplay mutations");
        } catch (Throwable error) {
            try { Files.createDirectories(getDataFolder().toPath()); Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"), error.toString()); }
            catch (Exception ignored) { }
            getLogger().log(java.util.logging.Level.SEVERE, "NATIVE RELOAD RELEASE AUDIT FAILED", error);
        } finally {
            if (getConfig().getBoolean("shutdown", false)) Bukkit.shutdown();
            else Bukkit.getPluginManager().disablePlugin(this);
        }
    }
    private void require(boolean valid, String description, ArrayList<String> rows) {
        if (!valid) throw new AssertionError(description);
        rows.add(description);
    }
}
