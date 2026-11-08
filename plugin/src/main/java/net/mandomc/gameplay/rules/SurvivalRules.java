package net.mandomc.gameplay.rules;

import java.util.function.Predicate;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Explicit survival restrictions. No operator bypass; custom saber identity is exempt from shield bans. */
public final class SurvivalRules implements Listener {
    private final boolean elytras;
    private final boolean enchanting;
    private final boolean potions;
    private final boolean vanillaWeapons;
    private final boolean dimensions;
    private final Predicate<ItemStack> saber;
    private final ItemCatalog items;
    private final ConfigurationSection language;

    public SurvivalRules(JavaPlugin plugin, ConfigurationSection config, ConfigurationSection language,
                         Predicate<ItemStack> saber, ItemCatalog items) {
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("survival-rules.yml: expected schema-version 1");
        this.saber = saber;
        this.items = items;
        this.language = language;
        elytras = config.getBoolean("disable-elytras", true);
        enchanting = config.getBoolean("disable-enchanting", true);
        potions = config.getBoolean("disable-potions", true);
        vanillaWeapons = config.getBoolean("disable-vanilla-weapons", true);
        dimensions = config.getBoolean("disable-nether-and-end", true);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean banned(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        Material type = item.getType();
        if (elytras && type == Material.ELYTRA) return true;
        if (potions && (type == Material.POTION || type == Material.SPLASH_POTION
                || type == Material.LINGERING_POTION || type == Material.TIPPED_ARROW)) return true;
        if (!vanillaWeapons || "gaffi_stick".equals(items.identify(item))) return false;
        return type.name().endsWith("_SWORD") || type == Material.BOW || type == Material.CROSSBOW
                || type == Material.SHIELD && !saber.test(item);
    }

    private void deny(Player player, String reason) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(language.getString(reason)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        // RIGHT_CLICK_AIR often starts cancelled; use the two independent use results instead.
        boolean right = event.getAction().isRightClick();
        if (banned(event.getItem())) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            if (right) deny(event.getPlayer(), "item-disabled");
        }
        var block = event.getClickedBlock();
        if (right && block != null && enchanting && block.getType() == Material.ENCHANTING_TABLE) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            deny(event.getPlayer(), "enchanting-disabled");
        }
        if (right && block != null && dimensions && block.getType() == Material.END_PORTAL_FRAME
                && event.getItem() != null && event.getItem().getType() == Material.ENDER_EYE) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            deny(event.getPlayer(), "dimension-disabled");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void melee(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && banned(player.getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            deny(player, "item-disabled");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void shoot(org.bukkit.event.entity.EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && banned(event.getBow())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void glide(EntityToggleGlideEvent event) {
        if (elytras && event.isGliding()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        if (banned(event.getItem())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "item-disabled");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void dispense(BlockDispenseEvent event) {
        if (banned(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void launch(ProjectileLaunchEvent event) {
        if (potions && event.getEntity() instanceof ThrownPotion) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void splash(PotionSplashEvent event) { if (potions) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lingering(LingeringPotionSplashEvent event) { if (potions) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void effects(EntityPotionEffectEvent event) {
        if (!potions || event.getNewEffect() == null) return;
        switch (event.getCause()) {
            case POTION_DRINK, POTION_SPLASH, AREA_EFFECT_CLOUD -> event.setCancelled(true);
            default -> { } // Food, beacons and plugin/mob abilities keep their own effects.
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void brew(BrewEvent event) { if (potions) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void enchant(EnchantItemEvent event) { if (enchanting) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void anvil(PrepareAnvilEvent event) {
        if (!enchanting || event.getResult() == null) return;
        var left = event.getInventory().getItem(0);
        var right = event.getInventory().getItem(1);
        boolean book = right != null && right.getItemMeta() instanceof EnchantmentStorageMeta meta
                && !meta.getStoredEnchants().isEmpty();
        boolean added = event.getResult().getEnchantments().entrySet().stream()
                .anyMatch(entry -> left == null || entry.getValue() > left.getEnchantmentLevel(entry.getKey()));
        if (book || added) event.setResult(null);
        // Repair materials, combining wear and renaming remain native anvil behavior.
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void craft(PrepareItemCraftEvent event) {
        var result = event.getInventory().getResult();
        if (banned(result)) {
            event.getInventory().setResult(null);
            return;
        }
        if (!event.isRepair() || result == null) return;
        ItemStack custom = null;
        String id = null;
        for (ItemStack input : event.getInventory().getMatrix()) {
            if (input == null || input.isEmpty()) continue;
            String next = items.identify(input);
            if (next != null) {
                if (id != null && !id.equals(next)) { event.getInventory().setResult(null); return; }
                id = next;
                if (custom == null) custom = input;
            }
        }
        if (custom == null) return;
        // Crafting repairs normally discard custom metadata. Only combine matching owned items.
        for (ItemStack input : event.getInventory().getMatrix()) {
            if (input != null && !input.isEmpty() && !id.equals(items.identify(input))) {
                event.getInventory().setResult(null);
                return;
            }
        }
        var repaired = custom.clone();
        repaired.setAmount(1);
        if (repaired.getItemMeta() instanceof Damageable meta && result.getItemMeta() instanceof Damageable wear) {
            meta.setDamage(wear.getDamage());
            repaired.setItemMeta(meta);
        }
        // Native crafting repairs remove enchantments; preserve identity, not free enchantment retention.
        for (var enchantment : repaired.getEnchantments().keySet()) repaired.removeEnchantment(enchantment);
        event.getInventory().setResult(repaired);
    }

    public boolean disabledWorld(World world) {
        return dimensions && world != null && (world.getEnvironment() == World.Environment.NETHER
                || world.getEnvironment() == World.Environment.THE_END);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void portal(PortalCreateEvent event) { if (dimensions) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityPortal(EntityPortalEvent event) { if (dimensions) event.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void teleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && (disabledWorld(event.getTo().getWorld())
                || dimensions && (event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.END_GATEWAY))) {
            event.setCancelled(true);
            deny(event.getPlayer(), "dimension-disabled");
        }
    }
}
