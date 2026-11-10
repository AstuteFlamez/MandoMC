package net.mandomc.gameplay.vehicles;

import java.io.InputStreamReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class VehicleWeaponSettingsTest {
    private YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/vehicles/weapons.yml")));
    }
    @Test public void recoveredCannonValuesAndAmmoCostsMatchEachVehicle() {
        var config = config();
        var xwing = VehicleWeaponSettings.read(config.getConfigurationSection("vehicles.xwing"));
        assertEquals(4, xwing.ammoCost()); assertEquals(2000, xwing.cooldownMillis());
        assertEquals(10, xwing.damage(), 0); assertEquals(4, xwing.speed(), 0);
        assertEquals("proton_torpedo", xwing.ammo());
        assertTrue(xwing.terrainExplosion());
        assertEquals("tibanna_gas_cells", VehicleWeaponSettings.read(config.getConfigurationSection("vehicles.tiefighter")).ammo());
        assertEquals("standard_plasma_cells", VehicleWeaponSettings.read(config.getConfigurationSection("vehicles.uwing")).ammo());
        assertEquals("standard_plasma_cells", VehicleWeaponSettings.read(config.getConfigurationSection("vehicles.speederbike")).ammo());
        config.set("vehicles.xwing.terrain-explosion", null);
        assertTrue(VehicleWeaponSettings.read(config.getConfigurationSection("vehicles.xwing")).terrainExplosion());
        for (var id : java.util.List.of("speederbike", "tiefighter", "uwing")) {
            var settings = VehicleWeaponSettings.read(config.getConfigurationSection("vehicles." + id));
            assertEquals(2, settings.ammoCost()); assertEquals(0, settings.cooldownMillis());
            assertEquals(100, settings.range(), 0); assertEquals(8, settings.radius(), 0);
        }
    }
    @Test public void invalidParametersFailBeforeSpendingAmmo() {
        final var invalid = config(); invalid.set("vehicles.xwing.speed", Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> VehicleWeaponSettings.read(invalid.getConfigurationSection("vehicles.xwing")));
        var config = config(); config.set("vehicles.xwing.bones", java.util.List.of("weapon_one", "weapon_one"));
        final var duplicate = config;
        assertThrows(IllegalArgumentException.class, () -> VehicleWeaponSettings.read(duplicate.getConfigurationSection("vehicles.xwing")));
    }

    @Test public void greenBoltFollowsTibannaIdentityAndNeverVehicleName() {
        var config = config();
        for (String id : java.util.List.of("speederbike", "tiefighter", "uwing", "xwing")) {
            var weapon = VehicleWeaponSettings.read(config.getConfigurationSection("vehicles." + id));
            assertEquals("mandomc:projectiles/" + (id.equals("tiefighter") ? "greenlaser" : "redlaser"),
                    weapon.projectileModel().toString());
        }
        config.set("vehicles.uwing.ammo", "tibanna_gas_cells");
        assertEquals("mandomc:projectiles/greenlaser", VehicleWeaponSettings.read(
                config.getConfigurationSection("vehicles.uwing")).projectileModel().toString());
    }
}
