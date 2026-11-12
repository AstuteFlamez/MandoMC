package net.mandomc.integrations.mobs;

import com.ticxo.modelengine.api.events.BaseEntityInteractEvent;
import com.ticxo.modelengine.api.events.ModelMountEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.mobs.HerdAge;
import net.mandomc.gameplay.mobs.HerdSettings;
import net.mandomc.integrations.models.HerdModels;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** One interaction/birth authority. Native horses own owner, age/love/cooldown and the real saddle. */
public final class HerdAnimals implements Listener {
    private final JavaPlugin plugin;
    private final MythicMobAccess mobs;
    private final HerdBabies babies;
    private final HerdModels models;
    private final HerdSettings settings;
    private final ConfigurationSection language;
    private final Predicate<Location> allowed;
    private final Map<UUID, Integer> clicked = new HashMap<>();
    private final HerdEquipment equipment;
    private final HerdLeads leads;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final net.mandomc.gameplay.messaging.ChatStyle chat = new net.mandomc.gameplay.messaging.ChatStyle();

    public HerdAnimals(JavaPlugin plugin, MythicMobAccess mobs, HerdBabies babies, HerdModels models,
            HerdSettings settings, ConfigurationSection language, Predicate<Location> allowed) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.babies = babies;
        this.models = models;
        this.settings = settings;
        this.language = language;
        this.allowed = allowed;
        equipment = new HerdEquipment(plugin, this, models, language);
        leads = new HerdLeads(plugin, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public String type(Entity entity) { return mobs.type(entity); }
    public boolean leadLocation(Location location) { return allowed.test(location); }
    public boolean leadAccess(Player player, LivingEntity entity) {
        return player.isValid() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && entity.isValid() && !entity.isDead() && player.getWorld().equals(entity.getWorld())
                && player.getLocation().distanceSquared(entity.getLocation()) <= 36
                && allowed.test(player.getLocation()) && allowed.test(entity.getLocation())
                && (!(entity instanceof Tameable tameable) || !tameable.isTamed()
                    || player.getUniqueId().equals(tameable.getOwnerUniqueId()));
    }
    public boolean herd(Entity entity) { return HerdAge.herd(mobs.type(entity)); }
    public boolean owner(Player player, Horse horse) {
        return horse.isTamed() && horse.getOwnerUniqueId() != null && horse.getOwnerUniqueId().equals(player.getUniqueId());
    }
    public boolean access(Player player, Horse horse) {
        return player.isValid() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && horse.isValid() && !horse.isDead() && horse.isAdult() && owner(player, horse)
                && player.getWorld().equals(horse.getWorld())
                && player.getLocation().distanceSquared(horse.getLocation()) <= 36
                && allowed.test(player.getLocation()) && allowed.test(horse.getLocation());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void interact(PlayerInteractEntityEvent event) {
        if (!herd(event.getRightClicked())) return;
        event.setCancelled(true); // Replaces native wheat/carrot feeding, hand saddling and bareback auto-mounts.
        // Suppress Mythic's separate vanilla-food tame path before it runs. The native tame/breed
        // events and the authoritative territory check still decide whether our action succeeds.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getRightClicked() instanceof LivingEntity entity) interact(event.getPlayer(), entity);
        });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void interactAt(PlayerInteractAtEntityEvent event) {
        // Bukkit gives precise-hit events their own handler list. Cancel the native action
        // here too, before our queued model interaction attaches/feeds exactly once.
        interact(event);
    }

    @EventHandler public void modelInteract(BaseEntityInteractEvent event) {
        if (event.getAction() == BaseEntityInteractEvent.Action.ATTACK) return;
        UUID id = event.getBaseEntity().getUUID();
        // ModelEngine can emit both interact and interact-at, then Paper's mirrored interaction.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getEntity(id) instanceof LivingEntity entity && herd(entity)) interact(event.getPlayer(), entity);
        });
    }

    public void interact(Player player, LivingEntity entity) {
        int tick = Bukkit.getCurrentTick();
        Integer last = clicked.get(player.getUniqueId());
        if (last != null && tick - last <= 1) return;
        clicked.put(player.getUniqueId(), tick);
        if (!player.isValid() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR
                || !entity.isValid() || entity.isDead() || player.getWorld() != entity.getWorld()
                || player.getLocation().distanceSquared(entity.getLocation()) > 36) return;
        if (!allowed.test(entity.getLocation()) || !allowed.test(player.getLocation())) { send(player, "protected", entity); return; }
        String type = mobs.type(entity);
        if (!HerdAge.herd(type)) return;
        var food = settings.food(type);
        EquipmentSlot hand = player.getInventory().getItemInMainHand().getType() == food ? EquipmentSlot.HAND
                : player.getInventory().getItemInOffHand().getType() == food ? EquipmentSlot.OFF_HAND : null;
        EquipmentSlot leadHand = player.getInventory().getItemInMainHand().getType() == Material.LEAD ? EquipmentSlot.HAND
                : player.getInventory().getItemInMainHand().getType() != food
                    && player.getInventory().getItemInOffHand().getType() == Material.LEAD ? EquipmentSlot.OFF_HAND : null;
        if (!(entity instanceof Horse) && hand == null && leadHand == null) { send(player, "feed", entity); return; }
        Horse horse = entity instanceof Horse h ? h : migrate(entity, type);
        if (leadHand != null) { leads.interact(player, horse, leadHand); return; }
        if (hand != null) { feed(player, horse, hand); return; }
        if (horse.isLeashed() && !player.isSneaking()) { leads.interact(player, horse, null); return; }
        if (!horse.isAdult()) { send(player, "young", horse); return; }
        if (!owner(player, horse)) { send(player, horse.isTamed() ? "owner" : "feed", horse); return; }
        if (models.occupied(horse)) return;
        if (player.isSneaking() || !HerdModels.saddled(horse)) equipment.open(player, horse);
        else models.mount(horse, player);
    }

    private Horse migrate(LivingEntity original, String type) {
        var replacement = mobs.spawn(type, original.getLocation(), mobs.level(original));
        if (!(replacement instanceof Horse horse)) {
            if (replacement != null) mobs.remove(replacement);
            throw new IllegalStateException("Reload the HORSE-backed herd definitions before taming existing animals");
        }
        try {
            if (original instanceof Ageable age) { horse.setAge(age.getAge()); horse.setAgeLock(age.getAgeLock()); }
            horse.setAI(original.hasAI());
            if (original instanceof Mob mob) horse.setAware(mob.isAware());
            horse.setVelocity(original.getVelocity());
            for (String key : new String[] {"sandy_encounter_v1", "sandy_patrol_v1"}) {
                var name = new NamespacedKey("mandomc", key);
                String value = original.getPersistentDataContainer().get(name, PersistentDataType.STRING);
                if (value != null) horse.getPersistentDataContainer().set(name, PersistentDataType.STRING, value);
            }
            horse.setHealth(Math.min(original.getHealth(), horse.getAttribute(Attribute.MAX_HEALTH).getValue()));
            horse.setPersistent(original.isPersistent());
            if (HerdAge.baby(original)) mobs.persist(horse);
            mobs.remove(original); // Removal never invokes the death/reward pipeline.
            return horse;
        } catch (RuntimeException error) { mobs.remove(horse); throw error; }
    }

    public boolean feed(Player player, Horse horse, EquipmentSlot hand) {
        if (!herd(horse) || !player.isValid() || player.isDead() || horse.isDead()
                || player.getWorld() != horse.getWorld() || player.getLocation().distanceSquared(horse.getLocation()) > 36
                || !allowed.test(horse.getLocation()) || !allowed.test(player.getLocation()) || !horse.isValid()
                || models.occupied(horse) || player.getGameMode() == GameMode.SPECTATOR) return false;
        var item = player.getInventory().getItem(hand);
        if (item.getType() != settings.food(mobs.type(horse))) return false;
        if (horse.isTamed() && !owner(player, horse)) { send(player, "owner", horse); return false; }
        if (!horse.isAdult()) {
            horse.setAge(Math.min(0, horse.getAge() + Math.max(20, (int) (-horse.getAge() * settings.babyGrowth()))));
        } else if (!horse.isTamed()) {
            if (ThreadLocalRandom.current().nextDouble() < settings.tameChance()) {
                var event = new EntityTameEvent(horse, player);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) return false;
                horse.setOwner(player);
                horse.setPersistent(true);
                horse.setRemoveWhenFarAway(false);
                mobs.persist(horse);
                horse.getWorld().spawnParticle(Particle.HEART, horse.getLocation().add(0, horse.getHeight(), 0), 6, .5, .3, .5);
                send(player, "tamed", horse);
            } else horse.getWorld().spawnParticle(Particle.SMOKE, horse.getLocation().add(0, horse.getHeight(), 0), 5, .4, .3, .4, 0);
        } else if (horse.getHealth() < horse.getAttribute(Attribute.MAX_HEALTH).getValue()) {
            var event = new EntityRegainHealthEvent(horse, settings.heal(), EntityRegainHealthEvent.RegainReason.EATING);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getAmount() <= 0) return false;
            horse.setHealth(Math.min(horse.getAttribute(Attribute.MAX_HEALTH).getValue(), horse.getHealth() + event.getAmount()));
        } else if (horse.getAge() == 0 && !horse.isLoveMode()) {
            horse.setBreedCause(player.getUniqueId());
            horse.setLoveModeTicks(settings.loveTicks());
        } else return false;
        if (player.getGameMode() != GameMode.CREATIVE) item.subtract(1);
        horse.getWorld().playSound(horse.getLocation(), Sound.ENTITY_GENERIC_EAT, .6f, 1);
        return true;
    }

    public boolean pair(Horse first, Horse second) {
        return first != second && first.isValid() && second.isValid() && !first.isDead() && !second.isDead()
                && first.getWorld().equals(second.getWorld()) && first.isTamed() && second.isTamed()
                && first.getOwnerUniqueId() != null && first.getOwnerUniqueId().equals(second.getOwnerUniqueId())
                && first.getAge() == 0 && second.getAge() == 0 && first.isLoveMode() && second.isLoveMode()
                && mobs.type(first).equals(mobs.type(second)) && HerdAge.herd(mobs.type(first))
                && !models.occupied(first) && !models.occupied(second) && !first.isLeashed() && !second.isLeashed()
                && allowed.test(first.getLocation()) && allowed.test(second.getLocation());
    }

    public boolean breed(Horse first, Horse second) {
        if (!pair(first, second) || first.getLocation().distanceSquared(second.getLocation()) > 9) return false;
        var location = first.getLocation().add(second.getLocation()).multiply(.5);
        if (!allowed.test(location)) return false;
        var child = (Horse) babies.spawn(mobs.type(first), location, Math.min(mobs.level(first), mobs.level(second)));
        try {
            var breeder = Bukkit.getPlayer(first.getOwnerUniqueId());
            var event = new EntityBreedEvent(child, first, second, breeder,
                    new org.bukkit.inventory.ItemStack(settings.food(mobs.type(first))), ThreadLocalRandom.current().nextInt(1, 8));
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) { mobs.remove(child); return false; }
            child.setOwner(first.getOwner());
            child.setPersistent(true);
            child.setRemoveWhenFarAway(false);
            first.setLoveModeTicks(0); second.setLoveModeTicks(0);
            first.setAge(settings.breedCooldown()); second.setAge(settings.breedCooldown());
            first.getWorld().spawnParticle(Particle.HEART, location.clone().add(0, 1, 0), 7, .5, .5, .5);
            if (event.getExperience() > 0) first.getWorld().spawn(location, ExperienceOrb.class,
                    orb -> orb.setExperience(event.getExperience()));
            return true;
        } catch (RuntimeException error) { mobs.remove(child); throw error; }
    }

    private Horse inventory(Inventory inventory) {
        return inventory.getHolder() instanceof Horse horse && herd(horse) ? horse : null;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void open(InventoryOpenEvent event) {
        var horse = inventory(event.getInventory());
        if (horse != null) event.setCancelled(true); // Equipment is managed only through the custom GUI.
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        var horse = inventory(event.getView().getTopInventory());
        if (horse == null) return;
        if (!(event.getWhoClicked() instanceof Player p) || !access(p, horse) || models.occupied(horse)
                || event.getRawSlot() == 1 || event.getClick() == ClickType.DOUBLE_CLICK
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    && event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.SADDLE) {
            event.setCancelled(true);
        }
        // The native menu validates saddle type/quantity and all hotbar/shift transfer semantics.
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        var horse = inventory(event.getView().getTopInventory());
        if (horse != null && (!(event.getWhoClicked() instanceof Player p) || !access(p, horse)
                || models.occupied(horse) || event.getRawSlots().stream().anyMatch(slot -> slot < event.getView().getTopInventory().getSize()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hopper(InventoryMoveItemEvent event) {
        if (inventory(event.getSource()) != null || inventory(event.getDestination()) != null) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mount(ModelMountEvent event) {
        var entity = Bukkit.getEntity(event.getVehicle().getModeledEntity().getBase().getUUID());
        if (entity instanceof Horse horse && herd(horse)
                && (!(event.getPassenger() instanceof Player p) || !event.isDriver() || !access(p, horse)
                    || !HerdModels.saddled(horse) || horse.isLeashed() || p.isSneaking()
                    || p.getInventory().getItemInMainHand().getType() == Material.LEAD
                    || p.getInventory().getItemInOffHand().getType() == Material.LEAD
                    || p.getInventory().getItemInMainHand().getType() == settings.food(mobs.type(horse))
                    || p.getInventory().getItemInOffHand().getType() == settings.food(mobs.type(horse)))) event.setCancelled(true);
    }
    @EventHandler public void quit(PlayerQuitEvent event) { clicked.remove(event.getPlayer().getUniqueId()); }
    private void send(Player player, String key, Entity entity) {
        var category = switch (key) {
            case "owner", "protected" -> net.mandomc.gameplay.messaging.ChatCategory.ERROR;
            case "tamed" -> net.mandomc.gameplay.messaging.ChatCategory.SUCCESS;
            default -> net.mandomc.gameplay.messaging.ChatCategory.INFO;
        };
        player.sendMessage(chat.format(mini.deserialize(language.getString(key, key), Placeholder.component("food",
                net.kyori.adventure.text.Component.translatable(settings.food(mobs.type(entity)).translationKey()))), category));
    }
}
