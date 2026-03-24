package com.astuteflamez.mandomc.modules.mechanics;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.core.module.Module;
import com.astuteflamez.mandomc.mechanics.warps.WarpCommand;
import com.astuteflamez.mandomc.modules.core.GUIModule;

public class WarpModule implements Module {

    private final MandoMC plugin;

    public WarpModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        // Command is GUI-driven, so we hook into GUIManager
        if (plugin.getCommand("warps") != null) {
            plugin.getCommand("warps")
                    .setExecutor(new WarpCommand(GUIModule.GUI_MANAGER));
        }
    }

    @Override
    public void disable() {}
}