package net.mandomc.gameplay.mobs;

import java.util.*;
import net.mandomc.integrations.mobs.MythicMobAccess;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Custom-mob XP uses the native death/orb pipeline and actual melee/projectile attribution once. */
public final class MobExperience implements Listener,AutoCloseable {
    private final MythicMobAccess mobs;
    private final net.mandomc.integrations.weapons.PlayerMobKills weapons;
    private final Map<String,Integer> rewards;
    private final Map<EntityDeathEvent,Integer> deaths=new WeakHashMap<>();
    public MobExperience(JavaPlugin plugin,ConfigurationSection config) throws ReflectiveOperationException {
        mobs=new MythicMobAccess();weapons=new net.mandomc.integrations.weapons.PlayerMobKills(plugin);var amounts=Objects.requireNonNull(config.getConfigurationSection("mobs"));
        if(config.getInt("schema-version")!=1)throw new IllegalArgumentException("mobs/experience.yml: expected schema 1");
        var parsed=new HashMap<String,Integer>();
        for(String id:amounts.getKeys(false)) {
            if(!id.matches("[A-Za-z0-9_]+")||!amounts.isInt(id)||amounts.getInt(id)<0||amounts.getInt(id)>1000)
                throw new IllegalArgumentException("mobs/experience.yml: invalid XP for "+id);
            parsed.put(id,amounts.getInt(id));
        }
        rewards=Map.copyOf(parsed);Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void identify(EntityDeathEvent e) {
        var amount=rewards.get(mobs.type(e.getEntity()));if(amount!=null)deaths.put(e,amount);
    }
    public static Player playerKiller(EntityDeathEvent e) {
        Entity source=e.getDamageSource().getCausingEntity();
        if(source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter)source=shooter;
        if(source instanceof Player player)return player;
        return null;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void experience(EntityDeathEvent e) {
        var amount=deaths.remove(e);if(amount==null)return;
        var player=playerKiller(e);
        var gunPlayer=weapons.killer(e.getEntity());
        if(player==null && e.getDamageSource().getCausingEntity()==null)player=gunPlayer;
        boolean eligible=player!=null && (player.getGameMode()==GameMode.SURVIVAL || player.getGameMode()==GameMode.ADVENTURE);
        e.setDroppedExp(eligible?amount:0);
    }
    @Override public void close(){weapons.close();deaths.clear();HandlerList.unregisterAll(this);}
}
