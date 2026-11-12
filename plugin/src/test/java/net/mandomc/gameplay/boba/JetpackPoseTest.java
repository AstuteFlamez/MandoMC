package net.mandomc.gameplay.boba;
import org.bukkit.Location;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.Test;
import static org.junit.Assert.*;
public class JetpackPoseTest {
    @Test public void finalClientGeometryIsBehindTorsoForEveryCardinalHeading() {
        for(float yaw:new float[]{0,90,180,270,-90}) {
            var pose=JetpackPose.at(new Location(null,0,0,0),yaw,0,false,false);
            // Exact additional Y half-turn confirmed in 26.1.2 ItemDisplayRenderer.submitInner.
            var client=new Quaternionf(pose.rotation()).rotateY((float)Math.PI);
            var back=new Vector3f(0,0,1).rotate(client);
            var forward=new Vector3f(-(float)Math.sin(Math.toRadians(yaw)),0,(float)Math.cos(Math.toRadians(yaw)));
            assertTrue("back cannot point in front at yaw "+yaw,back.dot(forward)<-.999);
            assertTrue(new Vector3f(0,1,0).rotate(client).y>.999);
        }
    }
    @Test public void glideGeometryTopFollowsFlightDirectionAndBackPointsAboveBody() {
        for(float pitch:new float[]{-45,0,45}) {
            var pose=JetpackPose.at(new Location(null,0,0,0),0,pitch,false,true);
            var top=new Vector3f(0,1,0).rotate(pose.modelRotation());
            var flight=new Vector3f(0,-(float)Math.sin(Math.toRadians(pitch)),(float)Math.cos(Math.toRadians(pitch)));
            assertTrue(top.dot(flight)>.999);
            assertTrue(new Vector3f(0,0,1).rotate(pose.modelRotation()).y>0);
        }
    }
    @Test public void standingIgnoresHeadPitchAndSneakingLowersTorso() {
        var feet=new Location(null,2,3,4,100,70);
        var a=JetpackPose.at(feet,40,0,false,false);var b=JetpackPose.at(feet,40,70,false,false);
        assertTrue(a.rotation().equals(b.rotation(),.00001f));assertEquals(0,a.center().getYaw(),0);
        assertTrue(JetpackPose.at(feet,40,0,true,false).center().getY()<a.center().getY());
    }
}
