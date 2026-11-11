package net.mandomc.gameplay.events;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/** The active event alone owns registered ore nodes; no general warzone edit bypass. */
public interface EventMining {
    boolean ownsNode(Block block);
    boolean mayMine(Block block, Player player);
    static boolean owns(Block block) {
        var owner=Bukkit.getServicesManager().load(EventMining.class);
        return owner!=null&&owner.ownsNode(block);
    }
    static boolean allowed(Block block,Player player) {
        var owner=Bukkit.getServicesManager().load(EventMining.class);
        return owner!=null&&owner.mayMine(block,player);
    }
}
