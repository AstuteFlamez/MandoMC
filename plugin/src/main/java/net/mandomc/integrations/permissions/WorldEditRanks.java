package net.mandomc.integrations.permissions;

import java.util.*;
import com.sk89q.wepif.PermissionsProvider;
import com.sk89q.wepif.PermissionsResolverManager;
import net.mandomc.gameplay.ranks.RankCatalog;
import net.mandomc.gameplay.ranks.RankService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** WEPIF's supported provider hook covers unregistered WorldEdit/WorldGuard nodes and native groups. */
public final class WorldEditRanks implements PermissionsProvider, AutoCloseable {
    private final RankService ranks;
    public WorldEditRanks(JavaPlugin plugin, RankService ranks) {
        this.ranks = ranks;
        Bukkit.getServicesManager().register(PermissionsProvider.class, this, plugin, ServicePriority.Highest);
        if (PermissionsResolverManager.isInitialized()) PermissionsResolverManager.getInstance().findResolver();
    }
    @Override public boolean hasPermission(OfflinePlayer player, String permission) {
        if (player == null) return false;
        Boolean value = RankCatalog.resolve(ranks.catalog().permissions(ranks.memberships(player.getUniqueId())),
                permission.toLowerCase(Locale.ROOT));
        if (value != null) return value;
        Player online = player instanceof Player p ? p : player.getPlayer();
        if (online != null) return online.hasPermission(permission);
        var registered = Bukkit.getPluginManager().getPermission(permission);
        return registered != null ? registered.getDefault().getValue(player.isOp()) : player.isOp();
    }
    @Override public boolean hasPermission(String world, OfflinePlayer player, String permission) { return hasPermission(player, permission); }
    @Override public boolean hasPermission(String name, String permission) { return hasPermission(known(name), permission); }
    @Override public boolean hasPermission(String world, String name, String permission) { return hasPermission(known(name), permission); }
    @Override public String[] getGroups(OfflinePlayer player) {
        if (player == null) return new String[0];
        var groups = new LinkedHashSet<String>(); add("default", groups);
        for (String id : ranks.memberships(player.getUniqueId())) add(id, groups);
        return groups.toArray(String[]::new);
    }
    private void add(String id, Set<String> groups) {
        if (!groups.add(id)) return;
        for (String parent : ranks.catalog().get(id).parents()) add(parent, groups);
    }
    @Override public String[] getGroups(String name) { return getGroups(known(name)); }
    @Override public boolean inGroup(OfflinePlayer player, String group) { return Arrays.asList(getGroups(player)).contains(group); }
    @Override public boolean inGroup(String name, String group) { return inGroup(known(name), group); }
    private OfflinePlayer known(String name) {
        var online = Bukkit.getPlayerExact(name);
        return online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
    }
    @Override public void close() {
        Bukkit.getServicesManager().unregister(PermissionsProvider.class, this);
        if (PermissionsResolverManager.isInitialized()) PermissionsResolverManager.getInstance().findResolver();
    }
}
