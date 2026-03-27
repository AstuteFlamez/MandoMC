package net.mandomc.core.modules.core;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.module.Module;

public class GUIModule implements Module {

    public static GUIManager GUI_MANAGER;

    @Override
    public void enable() {
        GUI_MANAGER = new GUIManager();
    }

    @Override
    public void disable() {}
}