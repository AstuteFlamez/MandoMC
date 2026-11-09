package net.mandomc.gameplay.mobs;
/** Shared geometry for visible forward rupture and its player hit strip. */
public final class RancorLine {
 private RancorLine(){}
 public static boolean hits(double along,double sideways,double elevation,double previous,double next){return along>=Math.max(0,previous-0.5)&&along<=next+0.5&&Math.abs(sideways)<=1.8&&Math.abs(elevation)<=3;}
}
