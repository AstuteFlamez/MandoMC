package net.mandomc.gameplay.decor;

/** World-space yaw with a comfortable independent head cone and shortest-arc body follow. */
public final class DecorLook {
    public record Pose(float body,float head,float pitch) { }
    private DecorLook() { }
    public static float wrap(float angle){return (float)Math.IEEEremainder(angle,360);}
    private static float approach(float current,float target,float limit){return wrap(current+Math.max(-limit,Math.min(limit,wrap(target-current))));}
    public static Pose step(float body,float head,float pitch,float targetYaw,float targetPitch,boolean tracking){
        float delta=wrap(targetYaw-body);
        float bodyTarget=tracking?body+Math.copySign(Math.max(0,Math.abs(delta)-45),delta):targetYaw;
        float nextBody=approach(body,bodyTarget,12);
        float headTarget=tracking?nextBody+Math.max(-60,Math.min(60,wrap(targetYaw-nextBody))):targetYaw;
        float nextHead=approach(head,headTarget,30);
        // If the body moves beneath the head, keep the neck within its physical cone.
        nextHead=wrap(nextBody+Math.max(-60,Math.min(60,wrap(nextHead-nextBody))));
        float desiredPitch=Math.max(-35,Math.min(35,targetPitch));
        float nextPitch=pitch+Math.max(-15,Math.min(15,desiredPitch-pitch));
        return new Pose(nextBody,nextHead,nextPitch);
    }
}
