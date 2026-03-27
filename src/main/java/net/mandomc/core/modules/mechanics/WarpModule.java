package net.mandomc.core.modules.mechanics;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.core.modules.core.GUIModule;
import net.mandomc.mechanics.warps.WarpCommand;

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