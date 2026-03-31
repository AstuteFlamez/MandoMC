package net.mandomc.core.modules.gameplay;

import net.mandomc.MandoMC;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;


/**
 * Manages the lifecycle of the warp system.
 *
 * Registers the GUI-driven /warps command on enable. The {@link GUIManager}
 * is resolved from the {@link ServiceRegistry} rather than a static field.
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
    public void enable(ServiceRegistry registry) {
        // Command registration handled centrally by CommandModule
    }

    /**
     * Disables the warp system. No cleanup required.
     */
    @Override
    public void disable() {}
}
