package net.mandomc.core.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.function.Function;

/**
 * Central helpers for optional plugin integrations declared in plugin.yml softdepend.
 *
 * Keeps availability checks in one place so feature modules can fail closed
 * without hard-crashing when an optional plugin is absent.
 */
public final class OptionalPluginSupport {

    private static final String FANCY_HOLOGRAMS = "FancyHolograms";
    private static final String MODEL_ENGINE = "ModelEngine";
    private static final String WEAPON_MECHANICS = "WeaponMechanics";

    private static Function<String, Boolean> pluginEnabledChecker = OptionalPluginSupport::defaultPluginEnabled;
    private static Function<String, Boolean> classPresentChecker = OptionalPluginSupport::defaultClassPresent;

    private OptionalPluginSupport() {
    }

    public static boolean hasFancyHolograms() {
        return pluginEnabled(FANCY_HOLOGRAMS)
                && classPresent("de.oliver.fancyholograms.api.FancyHologramsPlugin");
    }

    public static boolean hasModelEngine() {
        return pluginEnabled(MODEL_ENGINE)
                && classPresent("com.ticxo.modelengine.api.ModelEngineAPI");
    }

    public static boolean hasWeaponMechanics() {
        return pluginEnabled(WEAPON_MECHANICS)
                && classPresent("me.deecaad.weaponmechanics.WeaponMechanicsAPI");
    }

    private static boolean pluginEnabled(String pluginName) {
        return safeApply(pluginEnabledChecker, pluginName);
    }

    private static boolean classPresent(String className) {
        return safeApply(classPresentChecker, className);
    }

    private static boolean safeApply(Function<String, Boolean> checker, String key) {
        try {
            return Boolean.TRUE.equals(checker.apply(key));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean defaultPluginEnabled(String pluginName) {
        Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        return plugin != null && plugin.isEnabled();
    }

    private static boolean defaultClassPresent(String className) {
        try {
            Class.forName(className, false, OptionalPluginSupport.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    static void useTestCheckers(Function<String, Boolean> pluginChecker, Function<String, Boolean> classChecker) {
        pluginEnabledChecker = pluginChecker;
        classPresentChecker = classChecker;
    }

    static void resetCheckers() {
        pluginEnabledChecker = OptionalPluginSupport::defaultPluginEnabled;
        classPresentChecker = OptionalPluginSupport::defaultClassPresent;
    }
}
