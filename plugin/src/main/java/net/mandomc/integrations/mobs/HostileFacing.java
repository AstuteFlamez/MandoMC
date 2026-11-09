package net.mandomc.integrations.mobs;

import java.util.Set;
import java.util.UUID;
import net.mandomc.commands.RootCommand;
import net.mandomc.integrations.models.ModelFacing;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

/** Console-only skill bridge; Mythic owns targets, wind-up timing and repeat lifetimes. */
public final class HostileFacing {
    private static final Set<String> TYPES=Set.of("BobaFett","Bossk","Rancor","GamorreanGuard","QuarrenGuard","TwilekGuard","TuskenRaiderSniper","TuskenRaiderMelee","Massiff");
    public HostileFacing(JavaPlugin plugin,RootCommand root) throws ReflectiveOperationException {
        var models=new ModelFacing();var mobs=new MythicMobAccess();
        root.register("mobface","mandomc.admin.status",(sender,args)->{
            if(!(sender instanceof ConsoleCommandSender) || args.length<2 || args.length>3)return;
            if(args.length==3 && !args[2].equals("now"))return;
            try {
                var source=Bukkit.getEntity(UUID.fromString(args[0]));var target=Bukkit.getEntity(UUID.fromString(args[1]));
                if(!(source instanceof LivingEntity actor) || !(target instanceof LivingEntity victim)
                        || !actor.isValid() || !victim.isValid() || actor.isDead() || victim.isDead()
                        || actor.getWorld()!=victim.getWorld()
                        || actor.getLocation().distanceSquared(victim.getLocation())>4096)return;
                String type=mobs.type(actor);if(type==null || !TYPES.contains(type))return;
                models.face(actor,victim.getLocation().add(0,victim.getHeight()*0.6,0),args.length==3?360:30);
            }catch(IllegalArgumentException ignored){ /* Expired/unresolved Mythic UUIDs are rejected. */ }
            catch(IllegalStateException error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Hostile facing failed",error);}
        },(sender,args)->java.util.List.of());
    }
}
