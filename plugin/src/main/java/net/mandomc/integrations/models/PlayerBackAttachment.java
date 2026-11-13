package net.mandomc.integrations.models;

import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import net.mandomc.gameplay.boba.JetpackPose;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

/** Paper 26.1.2 native passenger pivot; isolates the two supported NMS attachment queries. */
public final class PlayerBackAttachment {
    private final Method handle, riding, vehiclePoint, flyingTicks, swimAmount, attackAnim;
    private final java.lang.reflect.Field x,y,z;
    public PlayerBackAttachment() {
        try {
            var entity=Class.forName("net.minecraft.world.entity.Entity");
            handle=Class.forName("org.bukkit.craftbukkit.entity.CraftEntity").getMethod("getHandle");
            riding=entity.getMethod("getPassengerRidingPosition",entity);
            vehiclePoint=entity.getMethod("getVehicleAttachmentPoint",entity);
            var living=Class.forName("net.minecraft.world.entity.LivingEntity");
            flyingTicks=living.getMethod("getFallFlyingTicks");
            swimAmount=living.getMethod("getSwimAmount",float.class);
            attackAnim=living.getMethod("getAttackAnim",float.class);
            var vector=Class.forName("net.minecraft.world.phys.Vec3");
            x=vector.getField("x");y=vector.getField("y");z=vector.getField("z");
        } catch(ReflectiveOperationException error) {
            throw new IllegalStateException("Player back attachment requires the pinned Paper passenger API",error);
        }
    }
    /** Uses native animation progress instead of a separate timer or an immediate glide snap. */
    public JetpackPose.Pose pose(Player player) {
        try {
            var nativePlayer=handle.invoke(player);
            float attack=((Number)attackAnim.invoke(nativePlayer,1f)).floatValue();
            float twist=(float)Math.sin(Math.sqrt(attack)*Math.PI*2)*.2f;
            var arm=nativePlayer.getClass().getField("swingingArm").get(nativePlayer);
            boolean offhand=String.valueOf(arm).equals("OFF_HAND");
            boolean left=player.getMainHand()==org.bukkit.inventory.MainHand.LEFT;
            if(left!=offhand)twist=-twist;
            var velocity=player.getVelocity();var look=player.getEyeLocation().getDirection();
            float bank=0;
            if(velocity.getX()*velocity.getX()+velocity.getZ()*velocity.getZ()>1e-5
                    &&look.getX()*look.getX()+look.getZ()*look.getZ()>1e-5) {
                double angle=Math.atan2(velocity.getX()*look.getZ()-velocity.getZ()*look.getX(),
                        velocity.getX()*look.getX()+velocity.getZ()*look.getZ());
                // AvatarRenderer uses sign(cross)*acos(abs(dot)), not a full heading rotation.
                bank=(float)(Math.signum(angle)*Math.acos(Math.abs(Math.cos(angle))));
            }
            var scale=player.getAttribute(Attribute.SCALE);
            boolean crouch=player.getPose()==org.bukkit.entity.Pose.SNEAKING;
            boolean swimming=player.getPose()==org.bukkit.entity.Pose.SWIMMING;
            return JetpackPose.at(player.getLocation(),new JetpackPose.Frame(player.getBodyYaw(),player.getPitch(),
                    crouch,player.isGliding(),((Number)flyingTicks.invoke(nativePlayer)).floatValue(),
                    ((Number)swimAmount.invoke(nativePlayer,1f)).floatValue(),swimming,player.isInWater(),bank,
                    twist,scale==null?1:(float)scale.getValue(),(player.getInventory().getChestplate()!=null&&!player.getInventory().getChestplate().getType().isAir())));
        } catch(ReflectiveOperationException error) {
            throw new IllegalStateException("Could not resolve native player torso animation",error);
        }
    }

    /** World-space display translation relative to the exact client/server native mount position. */
    public Vector3f translation(Player player,ItemDisplay display,Location torso) {
        try {
            var parent=handle.invoke(player);var passenger=handle.invoke(display);
            var pivot=riding.invoke(parent,passenger);var own=vehiclePoint.invoke(passenger,parent);
            return new Vector3f((float)(torso.getX()-x.getDouble(pivot)+x.getDouble(own)),
                    (float)(torso.getY()-y.getDouble(pivot)+y.getDouble(own)),
                    (float)(torso.getZ()-z.getDouble(pivot)+z.getDouble(own)));
        } catch(ReflectiveOperationException error) {
            throw new IllegalStateException("Could not resolve player back attachment pivot",error);
        }
    }
}
