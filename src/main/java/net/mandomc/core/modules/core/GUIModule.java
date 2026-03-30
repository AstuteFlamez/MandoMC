package net.mandomc.core.modules.core;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.module.Module;

/**
 * Initializes the central GUI management system.
 *
 * Exposes a static reference to the GUIManager for use by other modules
 * during command and listener setup.
 */
public class GUIModule implements Module {

    /**
     * The shared GUI manager instance, available after this module is enabled.
     */
    public static GUIManager GUI_MANAGER;

    /**
     * Instantiates the GUI manager.
     */
    @Override
    public void enable() {
        GUI_MANAGER = new GUIManager();
    }

    @Override
    public void disable() {}
}