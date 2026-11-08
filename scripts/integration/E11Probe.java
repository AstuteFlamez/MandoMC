package net.mandomc.verification;

import java.util.List;
import me.deecaad.core.compatibility.CompatibilityAPI;
import me.deecaad.core.compatibility.HitBox;
import me.deecaad.weaponmechanics.WeaponMechanics;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.Projectile;
import me.deecaad.weaponmechanics.weapon.skin.SkinSelector;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Cow;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Temporary local-server probe; never install on a populated server. No player inventory is changed. */
public final class E11Probe extends JavaPlugin {
    private int checks;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                verify();
                getLogger().info("E11 PROBE PASSED: " + checks + " assertions");
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "E11 PROBE FAILED", error);
            }
        }, 60L);
    }

    private void verify() {
        var wm = WeaponMechanics.getInstance();
        var config = wm.getWeaponConfigurations();
        var weapon = WeaponMechanicsAPI.generateWeapon("E11");
        check("E11".equals(WeaponMechanicsAPI.getWeaponTitle(weapon)), "vendor weapon identity");
        model(weapon, "blasters/e11", 2);
        var skins = config.getObject("E11.Skin", SkinSelector.class);
        skins.apply(weapon, "default", SkinSelector.SkinAction.SCOPE, new String[0]);
        model(weapon, "blasters/e11_scope", 1002);
        skins.apply(weapon, "default", SkinSelector.SkinAction.SPRINT, new String[0]);
        model(weapon, "blasters/e11_sprint", 2002);
        skins.applyDefaultSkin(weapon);
        model(weapon, "blasters/e11", 2);
        var ammo = WeaponMechanicsAPI.generateAmmo("Standard_Plasma_Cells", false);
        model(ammo, "ammo/standard_plasma_cells", 25);
        check("Standard_Plasma_Cells".equals(WeaponMechanicsAPI.getCurrentAmmo(weapon).getAmmoTitle()),
                "reload resolves the recovered plasma cells");
        var reload = wm.getWeaponHandler().getReloadHandler();
        check(reload.getAmmoLeft(weapon, "E11") == 30, "new weapon magazine has 30 shots");
        check(reload.consumeAmmo(weapon, "E11", 30), "loaded ammunition can be consumed");
        check(reload.getAmmoLeft(weapon, "E11") == 0, "magazine reaches zero");
        check(!reload.consumeAmmo(weapon, "E11", 1), "empty weapon cannot consume another shot");
        check(config.getInt("E11.Reload.Reload_Duration") == 45, "legacy reload duration preserved");
        check(config.getDouble("E11.Damage.Base_Damage") == 0.5, "legacy base damage preserved");
        var projectile = config.getObject("E11.Projectile", Projectile.class);
        check(projectile != null, "red bolt resolves through the projectile registry");
        var survival = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        var spawn = Bukkit.getWorld(NamespacedKey.minecraft("spawnworld"));
        var guard = CompatibilityAPI.getWorldGuardCompatibility();
        check(guard.isInstalled(), "vendor WorldGuard integration installed");
        for (var point : List.of(spawn.getSpawnLocation(), survival.getSpawnLocation(),
                new Location(survival, -176, 300, -144), new Location(survival, -112, 300, -144),
                new Location(survival, -144, 300, -176), new Location(survival, -144, 300, -112))) {
            check(!guard.testFlag(point, null, "weapon-shoot"), "shoot denied in protected area");
            check(!guard.testFlag(point, null, "weapon-damage"), "incoming damage denied in protected area");
        }
        for (var point : List.of(new Location(survival, -177, 300, -144),
                new Location(survival, -111, 300, -144), new Location(survival, -144, 300, -177),
                new Location(survival, -144, 300, -111))) {
            check(guard.testFlag(point, null, "weapon-shoot"), "shoot permitted beyond boundary");
            check(guard.testFlag(point, null, "weapon-damage"), "damage permitted beyond boundary");
        }
        hit(projectile, weapon, new Location(survival, -174, 300, -144), false);
        hit(projectile, weapon, new Location(survival, -180, 300, -144), true);
    }

    private void hit(Projectile definition, ItemStack weapon, Location targetLocation, boolean shouldDamage) {
        var world = targetLocation.getWorld();
        var shooterChunk = world.getChunkAt(-182 >> 4, -144 >> 4);
        var targetChunk = targetLocation.getChunk();
        shooterChunk.addPluginChunkTicket(this);
        targetChunk.addPluginChunkTicket(this);
        var shooter = world.spawn(new Location(world, -182, 300, -144), ArmorStand.class, entity -> {
            entity.setPersistent(false);
            entity.setGravity(false);
        });
        var target = world.spawn(new Location(world, -180, 300, -144), Cow.class, entity -> {
            entity.setPersistent(false);
            entity.setAI(false);
            entity.setGravity(false);
        });
        try {
            check(!target.isDead(), "temporary target spawned alive");
            check(target.teleport(targetLocation), "temporary target moved to test location");
            var origin = shooter.getLocation().add(0, 0.5, 0);
            var direction = target.getLocation().add(0, 0.5, 0).toVector().subtract(origin.toVector()).normalize();
            var projectile = definition.create(shooter, origin, direction, weapon, "E11", EquipmentSlot.HAND);
            try {
                model((ItemStack) projectile.getProjectileSettings().getDisguiseData(), "projectiles/redlaser", 40);
                var hit = HitBox.getHitbox(target).rayTrace(origin.toVector(), direction);
                check(hit != null, "real target hitbox intersects bolt path");
                double before = target.getHealth();
                WeaponMechanics.getInstance().getWeaponHandler().getHitHandler().handleHit(hit, projectile);
                check(shouldDamage ? target.getHealth() < before : target.getHealth() == before,
                        shouldDamage ? "E11 hit damages target outside protection" : "incoming E11 hit blocked at safe-zone border");
            } finally {
                projectile.remove();
            }
        } finally {
            target.remove();
            shooter.remove();
            shooterChunk.removePluginChunkTicket(this);
            targetChunk.removePluginChunkTicket(this);
        }
    }

    private void model(ItemStack item, String key, int data) {
        check(item != null && NamespacedKey.fromString("mandomc:" + key).equals(item.getItemMeta().getItemModel()),
                "item model " + key);
        check(item.getItemMeta().getCustomModelData() == data, "custom model data " + data);
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        checks++;
        getLogger().info("PASS " + message);
    }
}
