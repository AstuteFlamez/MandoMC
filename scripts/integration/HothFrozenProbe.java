package net.mandomc.verification;
import java.nio.file.*;import java.util.*;import org.bukkit.*;import org.bukkit.plugin.java.JavaPlugin;import net.mandomc.gameplay.clans.*;
public final class HothFrozenProbe extends JavaPlugin{
 int waits;
 public void onEnable(){Bukkit.getScheduler().runTaskTimer(this,()->{try{run();}catch(Throwable t){getLogger().log(java.util.logging.Level.SEVERE,"FROZEN HOTH FAILED",t);write("FAILED.txt",t.toString());Bukkit.shutdown();}},120,20);}
 void write(String n,String s){try{Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(n),s);}catch(Exception e){throw new RuntimeException(e);}}
 void run()throws Exception{
 Path root=Bukkit.getPluginManager().getPlugin("MandoMC").getDataFolder().toPath();Path journal=root.resolve("data/frozen-land/wilderness-hoth-20261111.journal");if(Files.exists(journal.resolveSibling(journal.getFileName()+".failed")))throw new AssertionError("Frozen engine reported failure");
 if(!Files.exists(journal.resolveSibling(journal.getFileName()+".completed"))){if(++waits>1200)throw new AssertionError("Frozen capture timed out");return;}
 var lines=Files.readAllLines(journal);if(lines.size()!=2115)throw new AssertionError("Expected2115 scoped originals");var land=Bukkit.getServicesManager().load(ClanService.class).land();int hoth=0,kept=0;
 for(String line:lines){var a=line.split(" ");var chunk=new ClanState.Chunk(a[0],Integer.parseInt(a[1]),Integer.parseInt(a[2]));if(!land.supported(chunk)||!land.hasBaseline(chunk)||!land.ready(chunk))throw new AssertionError("Incomplete original "+line);if(a[0].equals("minecraft:hoth")){hoth++;if(!a[4].equals("captured"))throw new AssertionError("Hoth must be a fresh final-terrain capture");}else{kept++;if(!a[4].equals("preserved"))throw new AssertionError("Old originals resampled");}}
 if(hoth!=681||kept!=1434)throw new AssertionError("Wrong scope counts");write("PASSED.txt","2115 scoped originals ready;1434 preserved and681 newly captured Hoth chunks");Bukkit.shutdown();
 }
}
