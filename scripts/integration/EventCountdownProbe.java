package net.mandomc.verification;
import java.util.*;import java.lang.reflect.Proxy;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.bootstrap.MandoPlugin;import net.mandomc.gameplay.events.*;
import net.kyori.adventure.title.Title;import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
/** Real event audience predicates with captured Paper title API calls; no real-player UI or world edits. */
public final class EventCountdownProbe extends JavaPlugin {
 private int checks;
 private record Viewer(Player player,List<Title> messages){}
 private record Active(ManagedEvent base) implements ManagedEvent {
  public String id(){return base.id();}public String displayName(){return base.displayName();}public void start(){}public void tick(){}public void stop(boolean a){}public boolean running(){return true;}public boolean relevant(Player p){return base.relevant(p);}
 }
 @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"EVENT COUNTDOWN PROBE FAILED",e);}},100);}
 private Viewer viewer(World world,double x,double y,double z){var id=UUID.randomUUID();var messages=new ArrayList<Title>();var loc=new Location(world,x,y,z);var player=(Player)Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()){case "getUniqueId"->id;case "isOnline"->true;case "isDead"->false;case "getWorld"->world;case "getLocation"->loc.clone();case "showTitle"->{messages.add((Title)a[0]);yield null;}default->throw new UnsupportedOperationException(m.getName());});return new Viewer(player,messages);}
 private void run(){var plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");var manager=plugin.events();var hoth=Bukkit.getWorld(NamespacedKey.minecraft("hoth"));var tatooine=Bukkit.getWorld(NamespacedKey.minecraft("tatooine"));var earth=Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
  var palace=viewer(tatooine,1670,90,2190);var distant=viewer(tatooine,500,90,500);var hill=viewer(hoth,-148,27,141);var unrelated=viewer(earth,0,100,0);var players=List.of(palace.player,distant.player,hill.player,unrelated.player);
  var countdown=new EventEndCountdown(EventEndCountdown.DEFAULT_TITLE,EventEndCountdown.DEFAULT_SUBTITLE);var jabba=new Active(manager.event("jabba"));
  for(int n=10;n>=1;n--){check(countdown.send(jabba,n,players)==1,"Jabba sends only to palace occupants at "+n);check(countdown.send(jabba,n,players)==0,"no duplicate title within second "+n);}
  check(palace.messages.size()==10,"palace receives full ten-to-one sequence");check(distant.messages.isEmpty()&&hill.messages.isEmpty()&&unrelated.messages.isEmpty(),"outside players receive no Jabba countdown");check(plain(palace.messages.getLast().title()).equals("Event ending in 1"),"title ends with current second");check(plain(palace.messages.getLast().subtitle()).equals("Jabba's Palace"),"subtitle names the event");
  countdown.reset();var koth=new Active(manager.event("koth"));for(int n=10;n>=1;n--)check(countdown.send(koth,n,players)==1,"KOTH countdown targets Hoth at "+n);
  check(hill.messages.size()==10,"Hoth receives full countdown");check(palace.messages.size()==10&&distant.messages.isEmpty()&&unrelated.messages.isEmpty(),"KOTH does not title other planets");
  check(EventEndCountdown.number(Long.MAX_VALUE,200)==10,"natural timeout also enters countdown");check(countdown.send(koth,0,players)==0,"no stale zero/end title");
  getLogger().info("EVENT COUNTDOWN PROBE PASSED: "+checks+" assertions");
 }
 private String plain(net.kyori.adventure.text.Component c){return PlainTextComponentSerializer.plainText().serialize(c);}
 private void check(boolean ok,String s){if(!ok)throw new AssertionError(s);checks++;getLogger().info("PASS: "+s);}
}
