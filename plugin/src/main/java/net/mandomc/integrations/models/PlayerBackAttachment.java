package net.mandomc.integrations.models;

import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

/** Paper 26.1.2 native passenger pivot; isolates the two supported NMS attachment queries. */
public final class PlayerBackAttachment {
    private final Method handle, riding, vehiclePoint;
    private final java.lang.reflect.Field x,y,z;
    public PlayerBackAttachment() {
        try {
            var entity=Class.forName("net.minecraft.world.entity.Entity");
            handle=Class.forName("org.bukkit.craftbukkit.entity.CraftEntity").getMethod("getHandle");
            riding=entity.getMethod("getPassengerRidingPosition",entity);
            vehiclePoint=entity.getMethod("getVehicleAttachmentPoint",entity);
            var vector=Class.forName("net.minecraft.world.phys.Vec3");
            x=vector.getField("x");y=vector.getField("y");z=vector.getField("z");
        } catch(ReflectiveOperationException error) {
            throw new IllegalStateException("Player back attachment requires the pinned Paper passenger API",error);
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
