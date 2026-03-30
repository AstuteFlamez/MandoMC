package net.mandomc.core.modules.mechanics;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;
import net.mandomc.core.modules.core.GUIModule;
import net.mandomc.mechanics.warps.WarpCommand;

/**
 * Manages the lifecycle of the warp system.
 *
 * Registers the GUI-driven /warps command on enable.
 */
public class WarpModule implements Module {

    private final MandoMC plugin;

    /**
     * Creates the warp module.
     *
     * @param plugin the plugin instance
     */
    public WarpModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Enables the warp system by registering the /warps command executor.
     */
    @Override
    public void enable() {
        if (plugin.getCommand("warps") != null) {
            plugin.getCommand("warps").setExecutor(new WarpCommand(GUIModule.GUI_MANAGER));
        }
    }

    /**
     * Disables the warp system. No cleanup required.
     */
    @Override
    public void disable() {}
}
