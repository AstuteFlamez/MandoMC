package com.astuteflamez.mandomc.system.planets.ilum.listeners;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import com.astuteflamez.mandomc.system.planets.ilum.ParkourSession;
import com.astuteflamez.mandomc.system.planets.ilum.managers.CheckpointManager;
import com.astuteflamez.mandomc.system.planets.ilum.managers.ParkourManager;

public class ParkourCheckpointListener implements Listener {

    private final ParkourManager parkourManager;
    private final CheckpointManager checkpointManager;

    public ParkourCheckpointListener(
            ParkourManager parkourManager,
            CheckpointManager checkpointManager
    ) {
        this.parkourManager = parkourManager;
        this.checkpointManager = checkpointManager;
    }

    @EventHandler
    public void onCheckpoint(PlayerInteractEvent event) {

        if (event.getAction() != Action.PHYSICAL) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (block.getType() != Material.HEAVY_WEIGHTED_PRESSURE_PLATE) return;

        Player player = event.getPlayer();

        if (!parkourManager.hasSession(player)) return;

        Location plateLocation = block.getLocation();

        Integer number = checkpointManager.getCheckpointNumber(plateLocation);

        if (number == null) return;

        ParkourSession session = parkourManager.getSession(player);

        if (session.getLastCheckpoint() >= number) return;

        session.setLastCheckpoint(number);

        Location checkpoint = plateLocation.clone().add(0.5, 1, 0.5);

        parkourManager.setCheckpoint(player, checkpoint);

        player.sendTitle(
                "§e§lCheckpoint §l§f#" + number,
                "§7Progress saved",
                5, 40, 10
        );

        player.playSound(
                player.getLocation(),
                Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                5f,
                1f
        );
    }
}