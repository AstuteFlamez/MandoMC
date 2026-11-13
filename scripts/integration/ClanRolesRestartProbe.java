package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.mandomc.gameplay.clans.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Two disposable starts exercise actual startup migration and saved rank/legacy claim preservation. */
public final class ClanRolesRestartProbe extends JavaPlugin {
    private int checks;
    private void check(boolean condition,String why){if(!condition)throw new AssertionError(why);checks++;}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable e){fail(e);}},120);}
    private void run()throws Exception {
        check(getServer().getPort()==25590,"Migration test isolated");
        var config=YamlConfiguration.loadConfiguration(getDataFolder().toPath().resolve("fixture.yml").toFile());
        var clans=Bukkit.getServicesManager().load(ClanService.class);check(clans!=null&&clans.settings().members()==4,"Current clan service configured four");
        var legacy=clans.state().clans.get(UUID.fromString(config.getString("legacy")));
        var modern=clans.state().clans.get(UUID.fromString(config.getString("modern")));
        check(legacy!=null&&modern!=null,"Both fixture clans loaded by native storage");
        check(legacy.members.size()==5,"Existing over-limit members not removed");
        for(UUID member:legacy.members)check(clans.state().role(legacy,member)==(member.equals(legacy.leader)?ClanRole.LEADER:ClanRole.MEMBER),"Legacy membership migrates to Member");
        check(clans.state().role(modern,UUID.fromString(config.getString("officer")))==ClanRole.OFFICER,"Officer compatibility alias survives startup");
        check(clans.state().role(modern,UUID.fromString(config.getString("recruit")))==ClanRole.RECRUIT,"Recruit persists across startup");
        for(var chunk:List.of(new ClanState.Chunk("minecraft:mandalore_review",12,12),new ClanState.Chunk("minecraft:mandalore_review",13,13))) {
            check(clans.state().owner(chunk)==legacy,"Disconnected saved claims retained");check(clans.land().ready(chunk),"Claim original retained");
        }
        clans.save().get(5,java.util.concurrent.TimeUnit.SECONDS);
        String data=Files.readString(Bukkit.getPluginManager().getPlugin("MandoMC").getDataFolder().toPath().resolve("data/clans.yml"));
        check(!data.toLowerCase(Locale.ROOT).contains("co_leader"),"Legacy role identifier normalized to Officer");
        Path first=getDataFolder().toPath().resolve("FIRST.txt");
        if(!Files.exists(first)){Files.writeString(first,"FIRST MIGRATION PASSED: "+checks);getLogger().info("FIRST MIGRATION PASSED: "+checks);}
        else {
            clans.state().remove(legacy);clans.state().remove(modern);clans.save().get(5,java.util.concurrent.TimeUnit.SECONDS);
            Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),"RESTART MIGRATION PASSED: "+checks+" assertions");getLogger().info("RESTART MIGRATION PASSED: "+checks);
        }
        Bukkit.shutdown();
    }
    private void fail(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"ROLE MIGRATION FAILED",error);try{Files.writeString(getDataFolder().toPath().resolve("FAILED.txt"),error.toString());}catch(Exception ignored){}Bukkit.shutdown();}
}
