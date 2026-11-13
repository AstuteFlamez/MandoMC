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
    @Test public void mountingPlateTracksActualClientChestMatrixAcrossPoseSweep() {
        int combinations=0;
        for(boolean armor:new boolean[]{false,true})
        for(float scale:new float[]{.5f,1,1.5f})
        for(float yaw:new float[]{-179,-90,0,45,179})
        for(float pitch:new float[]{-90,-45,0,45,90})
        for(int mode=0;mode<5;mode++)
        for(float progress:new float[]{0,2,5,8,10})
        for(float bank:new float[]{-.6f,0,.6f})
        for(float twist:new float[]{-.2f,0,.2f}) {
            boolean crouch=mode==1,glide=mode==2,swim=mode>=3;
            var frame=new JetpackPose.Frame(yaw,pitch,crouch,glide,progress,
                    swim?progress/10:0,swim,mode==3,bank,twist,scale,armor);
            var pose=JetpackPose.at(new Location(null,0,0,0),frame);
            // Independently reproduce the client's PoseStack, including its down-positive model axes.
            float lean=glide?Math.min(1,progress*progress/100)*(-90-pitch)
                    :swim?progress/10*(mode==3?-90-pitch:-90):0;
            var reference=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw))
                    .rotateX((float)Math.toRadians(lean));
            if(glide)reference.rotateY(bank);
            if(swim&&progress>0)reference.translate(0,-1,.3f);
            reference.scale(-.9375f*scale,-.9375f*scale,.9375f*scale).translate(0,-1.501f,0);
            if(crouch)reference.translate(0,3.2f/16,0);
            reference.rotate(new Quaternionf().rotationZYX(0,twist,crouch?.5f:0));
            var surface=reference.transformPosition(new Vector3f(0,6f/16,(armor?3f:2.25f)/16));
            if(crouch)surface.y-=.125f*scale;
            var normal=reference.transformDirection(new Vector3f(0,0,1)).normalize();
            assertTrue("plate faces same plane as chest",new Vector3f(0,0,1).rotate(pose.modelRotation()).dot(normal)>.99999f);
            var plate=new Vector3f(0,0,.125f*scale).rotate(pose.modelRotation())
                    .add((float)pose.center().getX(),(float)pose.center().getY(),(float)pose.center().getZ());
            var clearance=plate.sub(surface);
            assertEquals("plate clearance",.005f*scale,clearance.dot(normal),.000002f);
            assertTrue("plate cannot slip along torso",clearance.sub(new Vector3f(normal).mul(.005f*scale)).length()<.000002f);
            combinations++;
        }
        assertEquals(33750,combinations);
    }
    @Test public void glideTakeoffRotatesWholeTorsoAroundNativeRenderOrigin() {
        var start=JetpackPose.at(new Location(null,0,0,0),
                new JetpackPose.Frame(0,0,false,true,0,0,false,false,0,0,1,true));
        var cruise=JetpackPose.at(new Location(null,0,0,0),0,0,false,true);
        assertTrue(start.center().getY()>1);
        assertTrue(cruise.center().getZ()>1);
        assertTrue(cruise.center().getY()<.1);
    }
}
