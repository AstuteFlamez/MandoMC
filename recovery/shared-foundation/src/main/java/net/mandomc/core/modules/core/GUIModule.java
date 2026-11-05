package net.mandomc.core.modules.core;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.ServiceRegistry;

/**
 * Initializes the central GUI management system and registers it in the
 * {@link ServiceRegistry} for downstream modules to consume.
 */
public class GUIModule implements Module {

    /**
     * Instantiates the GUI manager and registers it in the service registry.
     */
    @Override
    public void enable(ServiceRegistry registry) {
        registry.register(GUIManager.class, new GUIManager());
    }

    @Override
    public void disable() {}
}
