package net.mandomc.core.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionalPluginSupportTest {

    @AfterEach
    void tearDown() {
        OptionalPluginSupport.resetCheckers();
    }

    @Test
    void hasFancyHologramsRequiresPluginAndApiClass() {
        OptionalPluginSupport.useTestCheckers(
                name -> name.equals("FancyHolograms"),
                name -> name.equals("de.oliver.fancyholograms.api.FancyHologramsPlugin")
        );
        assertTrue(OptionalPluginSupport.hasFancyHolograms());

        OptionalPluginSupport.useTestCheckers(name -> true, name -> false);
        assertFalse(OptionalPluginSupport.hasFancyHolograms());
    }

    @Test
    void hasModelEngineRequiresPluginAndApiClass() {
        OptionalPluginSupport.useTestCheckers(
                name -> name.equals("ModelEngine"),
                name -> name.equals("com.ticxo.modelengine.api.ModelEngineAPI")
        );
        assertTrue(OptionalPluginSupport.hasModelEngine());

        OptionalPluginSupport.useTestCheckers(name -> false, name -> true);
        assertFalse(OptionalPluginSupport.hasModelEngine());
    }

    @Test
    void hasWeaponMechanicsRequiresPluginAndApiClass() {
        OptionalPluginSupport.useTestCheckers(
                name -> name.equals("WeaponMechanics"),
                name -> name.equals("me.deecaad.weaponmechanics.WeaponMechanicsAPI")
        );
        assertTrue(OptionalPluginSupport.hasWeaponMechanics());

        OptionalPluginSupport.useTestCheckers(name -> false, name -> true);
        assertFalse(OptionalPluginSupport.hasWeaponMechanics());
    }
}
