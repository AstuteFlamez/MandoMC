package net.mandomc.verification;

import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.bootstrap.MandoPlugin;

/** Disposable runtime only: waits for an encrypted vote through the real TCP listener. */
public final class VotingProbe extends JavaPlugin {
    private NativeProbePlayers actors;
    private Player player;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                actors = new NativeProbePlayers("VoteProbe");
                player = actors.create(Bukkit.getWorlds().getFirst().getSpawnLocation());
                var played = player.getClass().getDeclaredField("hasPlayedBefore");
                played.setAccessible(true); played.setBoolean(player, true);
                var event = new PlayerJoinEvent(player, (net.kyori.adventure.text.Component)null);
                for(var listener:PlayerJoinEvent.getHandlerList().getRegisteredListeners())
                    if(listener.getPlugin().getName().equals("VotingPlugin"))listener.callEvent(event);
                Files.writeString(Path.of("voting-probe-player.txt"), player.getName());
                getLogger().info("VOTING PROBE READY");
                Bukkit.getScheduler().runTaskTimer(this, () -> {
                    try {
                        var credits=((MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC")).credits();
                        long balance=credits.ledger().balance(player.getUniqueId());
                        int keys=player.getPersistentDataContainer().getOrDefault(new NamespacedKey("mandomc","crate_keys_vote"),PersistentDataType.INTEGER,0);
                        if(balance==50000 && keys==1) {
                            Files.writeString(Path.of("VOTING-PASSED.txt"),"Encrypted TCP vote awarded exactly 500 credits and one native vote key.");
                            getLogger().info("VOTING END-TO-END PASSED");
                            Bukkit.getScheduler().cancelTasks(this);
                            actors.close();actors=null;
                        } else if(balance>50000 || keys>1)throw new AssertionError("Duplicate vote rewards");
                    }catch(Exception error){throw new IllegalStateException(error);}
                },20,20);
            }catch(Exception error){throw new IllegalStateException(error);}
        },100);
    }
    @Override public void onDisable(){if(actors!=null)actors.close();}
}
