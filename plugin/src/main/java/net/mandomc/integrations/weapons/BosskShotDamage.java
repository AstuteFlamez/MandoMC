package net.mandomc.integrations.weapons;
import java.util.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.gameplay.mobs.BosskShotSettings;

/** Snapshot shot type at emission; tune only Bossk's projectiles through WM's existing damage event. */
public final class BosskShotDamage implements Listener {
    public static final String STYLE="mandomc_bossk_shot_style";
    @SuppressWarnings("unchecked")
    public BosskShotDamage(JavaPlugin plugin,Function<Entity,String> mobType,Predicate<Location> allowed,
            BosskShotSettings settings) throws ReflectiveOperationException {
        var wm=Bukkit.getPluginManager().getPlugin("WeaponMechanics");var loader=wm.getClass().getClassLoader();
        var shot=(Class<? extends Event>)Class.forName("me.deecaad.weaponmechanics.weapon.weaponevents.WeaponShootEvent",true,loader);
        var bullet=shot.getMethod("getProjectile");var projectile=bullet.getReturnType();
        var tag=projectile.getMethod("setIntTag",String.class,int.class);var getTag=projectile.getMethod("getIntTag",String.class);
        var shooter=shot.getMethod("getShooter");var title=shot.getMethod("getWeaponTitle");
        var me=Bukkit.getPluginManager().getPlugin("ModelEngine").getClass().getClassLoader();
        var modeled=Class.forName("com.ticxo.modelengine.api.ModelEngineAPI",true,me).getMethod("getModeledEntity",Entity.class);
        var models=Class.forName("com.ticxo.modelengine.api.model.ModeledEntity",true,me).getMethod("getModels");
        var animation=Class.forName("com.ticxo.modelengine.api.model.ActiveModel",true,me).getMethod("getAnimationHandler");
        var playing=animation.getReturnType().getMethod("isPlayingAnimation",String.class);
        Bukkit.getPluginManager().registerEvent(shot,this,EventPriority.HIGH,(l,e)->{
            try {
                var source=(LivingEntity)shooter.invoke(e);
                if(source==null||!"RelbyV10".equals(title.invoke(e))||!"Bossk".equals(mobType.apply(source)))return;
                var entity=modeled.invoke(null,source);var model=entity==null?null:((Map<?,?>)models.invoke(entity)).get("bossk");
                boolean single=model!=null&&(boolean)playing.invoke(animation.invoke(model),"attack4");
                tag.invoke(bullet.invoke(e),STYLE,single?1:2);
            }catch(ReflectiveOperationException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Bossk shot classification failed",ex);}
        },plugin);
        var damage=(Class<? extends Event>)Class.forName("me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent",true,loader);
        var damageShooter=damage.getMethod("getShooter");var damageTitle=damage.getMethod("getWeaponTitle");
        var victim=damage.getMethod("getVictim");var source=damage.getMethod("getSource");var setDamage=damage.getMethod("setFinalDamage",double.class);
        var projectileSource=Class.forName("me.deecaad.weaponmechanics.weapon.damage.ProjectileDamageSource",true,loader);var sourceBullet=projectileSource.getMethod("getProjectile");
        Bukkit.getPluginManager().registerEvent(damage,this,EventPriority.HIGH,(l,e)->{
            try {
                var attacker=(LivingEntity)damageShooter.invoke(e);var target=(LivingEntity)victim.invoke(e);
                if(attacker==null||!"Bossk".equals(mobType.apply(attacker))||!"RelbyV10".equals(damageTitle.invoke(e)))return;
                if(!allowed.test(attacker.getLocation())||!allowed.test(target.getLocation())){((Cancellable)e).setCancelled(true);return;}
                var provenance=source.invoke(e);if(!projectileSource.isInstance(provenance))return;
                int style=(int)getTag.invoke(sourceBullet.invoke(provenance),STYLE);
                if(style==1||style==2)setDamage.invoke(e,settings.perShot(style==1));
            }catch(ReflectiveOperationException|RuntimeException ex){((Cancellable)e).setCancelled(true);plugin.getLogger().log(java.util.logging.Level.SEVERE,"Bossk shot damage failed; hit denied",ex);}
        },plugin,true);
    }
}
