package com.astuteflamez.mandomc.modules.core;

import com.astuteflamez.mandomc.core.guis.GUIManager;
import com.astuteflamez.mandomc.core.module.Module;

public class GUIModule implements Module {

    public static GUIManager GUI_MANAGER;

    @Override
    public void enable() {
        GUI_MANAGER = new GUIManager();
    }

    @Override
    public void disable() {}
}