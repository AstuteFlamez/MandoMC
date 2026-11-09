package net.mandomc.integrations.models;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.events.*;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.bone.SimpleManualAnimator;
import com.ticxo.modelengine.api.model.bone.type.Mount;
import com.ticxo.modelengine.api.mount.controller.MountControllerType;
import com.ticxo.modelengine.api.mount.controller.impl.AbstractMountController;
import com.ticxo.modelengine.api.nms.entity.wrapper.MoveController;

import net.mandomc.gameplay.vehicles.*;

import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;

/** ModelEngine renders the legacy blueprints and supplies native-sized hitboxes and mount input. */
public final class ModelEngineVehicles implements VehicleModels, Listener {
    private final Map<UUID, Model> models = new HashMap<>();
    private final JavaPlugin plugin;

    public ModelEngineVehicles(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!Bukkit.getPluginManager().isPluginEnabled("ModelEngine"))
            throw new IllegalStateException("Vehicles require ModelEngine");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public Handle attach(LivingEntity entity, VehicleDefinition d, Access access) {
        if (ModelEngineAPI.getBlueprint(d.id()) == null)
            throw new IllegalStateException("Missing vehicle blueprint: " + d.id());
        var modeled = ModelEngineAPI.getOrCreateModeledEntity(entity);
        // Preserve the recovered mount's native one-block climbing behavior.
        modeled.getBase().setMaxStepHeight(1.0);
        modeled.setSaved(false);
        modeled.setBaseEntityVisible(false);
        var active = ModelEngineAPI.createActiveModel(d.id());
        active.setScale(d.scale());
        active.setHitboxScale(d.scale());
        modeled.addModel(active, true);
        var manager =
                active.getMountManager()
                        .orElseThrow(
                                () -> new IllegalStateException("Missing mounts for " + d.id()));
        manager.setCanDrive(true);
        manager.setCanRide(true);
        var model = new Model(entity, d, active, access);
        models.put(entity.getUniqueId(), model);
        return model;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mount(ModelMountEvent e) {
        var model = models.get(e.getVehicle().getModeledEntity().getBase().getUUID());
        if (model != null
                && (!(e.getPassenger() instanceof Player p)
                        || (e.isDriver() && !model.access.mayPilot(p)))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void dismount(ModelDismountEvent e) {
        var model = models.get(e.getVehicle().getModeledEntity().getBase().getUUID());
        if (model != null && plugin.isEnabled() && e.getPassenger() instanceof Player player) {
            // The vendor fires before clearing its pair. Clear any residual seat after completion,
            // but leave a newly created mount controller alone if the player boards again.
            Bukkit.getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (ModelEngineAPI.getMountPairManager()
                                                .getController(player.getUniqueId())
                                        == null) {
                                    model.eject(player);
                                }
                            });
        }
        if (model != null && e.isDriver()) {
            model.stopSound();
            model.entity.setAI(true);
            model.entity.setGravity(true);
            model.entity.setVelocity(new Vector(0, VehicleModels.grounded(model.entity) ? 0 : -.12, 0));
            model.active.getBone("body").ifPresent(b -> b.setManualAnimator(null));
            model.animate(false);
        }
    }

    @EventHandler
    public void interact(BaseEntityInteractEvent e) {
        var model = models.get(e.getBaseEntity().getUUID());
        if (model != null
                && e.getSlot() == EquipmentSlot.HAND
                && e.getAction() != BaseEntityInteractEvent.Action.ATTACK) {
            model.access.interact(e.getPlayer());
        }
    }

    private final class Model implements Handle {
        private final LivingEntity entity;
        private final VehicleDefinition definition;
        private final ActiveModel active;
        private final Access access;
        private final MountControllerType controller;
        private final Set<Player> soundListeners = new HashSet<>();

        Model(
                LivingEntity entity,
                VehicleDefinition definition,
                ActiveModel active,
                Access access) {
            this.entity = entity;
            this.definition = definition;
            this.active = active;
            this.access = access;
            controller =
                    new MountControllerType((rider, mount) -> new Controller(rider, mount, this));
        }

        private void stopSound() {
            for (Player player : soundListeners) player.stopSound(definition.sound());
            soundListeners.clear();
        }

        private void playSound(org.bukkit.Location location) {
            for (Player player : location.getWorld().getPlayers()) {
                if (player.getLocation().distanceSquared(location) <= 256) {
                    player.playSound(location, definition.sound(), .7f, 1);
                    soundListeners.add(player);
                }
            }
        }

        private void animate(boolean mounted) {
            var handler = active.getAnimationHandler();
            handler.stopAnimation(mounted ? "dismount" : "mount");
            handler.playAnimation(mounted ? "mount" : "dismount", .2, .2, 1, true);
        }

        @Override
        public boolean mount(Player player, Seat seat) {
            return mount(player, seat, null);
        }

        @Override
        public List<SeatStatus> seats() {
            var manager = active.getMountManager().orElseThrow();
            var result = new ArrayList<SeatStatus>();
            result.add(new SeatStatus("pilot", Seat.PILOT, driver()));
            for (Seat role : List.of(Seat.PASSENGER, Seat.GUNNER)) {
                for (String id : role == Seat.GUNNER ? definition.gunners() : definition.passengers()) {
                    var bone = manager.getSeat(id).orElse(null);
                    Player occupant = manager.getPassengerSeatMap().entrySet().stream()
                            .filter(entry -> entry.getValue() == bone && entry.getKey() instanceof Player)
                            .map(entry -> (Player) entry.getKey()).findFirst().orElse(null);
                    result.add(new SeatStatus(id, role, occupant));
                }
            }
            return List.copyOf(result);
        }

        @Override
        public boolean mount(Player player, Seat seat, String seatId) {
            // Resolve an empty destination before releasing the current seat. Transfers stay
            // within this model, and failed/cancelled destination mounts restore the old seat.
            var snapshot = seats();
            var target = snapshot.stream()
                    .filter(s -> s.role() == seat && (seatId == null || seatId.equals(s.id())))
                    .filter(s -> s.occupant() == null).findFirst().orElse(null);
            if (target == null) return false;
            var previous = snapshot.stream().filter(s -> player.equals(s.occupant())).findFirst().orElse(null);
            if (previous != null) {
                eject(player);
                if (riders().contains(player)) return false;
            }
            boolean mounted = board(player, seat, target.id());
            if (!mounted && previous != null) board(player, previous.role(), previous.id());
            return mounted;
        }

        private boolean board(Player player, Seat seat, String seatId) {
            var seats = active.getMountManager().orElseThrow();
            boolean driver = seat == Seat.PILOT;
            var allowed = seat == Seat.GUNNER ? definition.gunners() : definition.passengers();
            if (!driver && seatId != null && !allowed.contains(seatId)) return false;
            boolean mounted =
                    driver
                            ? seats.mountDriver(player, controller)
                            : seatId != null
                                    ? seats.mountPassenger(seatId, player, controller)
                                    : seats.mountAvailable(player, allowed, controller);
            if (mounted && driver) {
                entity.setAI(true);
                entity.setGravity(!definition.aerial());
                animate(true);
            }
            return mounted;
        }

        @Override
        public org.bukkit.Location muzzle(String bone) {
            return active.getBone(bone).map(b -> b.getLocation()).map(org.bukkit.Location::clone).orElse(null);
        }

        @Override
        public Vector aim() {
            var facing = entity.getLocation();
            var pilot = driver();
            facing.setPitch(pilot == null ? 0 : (float) Math.clamp(pilot.getEyeLocation().getPitch(),
                    -definition.pitch(), definition.pitch()));
            return facing.getDirection();
        }

        @Override
        public boolean mayShoot(Player player) {
            var seats = active.getMountManager().orElseThrow();
            var occupied = seats.getPassengerSeatMap().get(player);
            return occupied != null
                    && definition.gunners().stream()
                            .anyMatch(id -> seats.getSeat(id).orElse(null) == occupied);
        }

        @Override
        public Player driver() {
            Entity driver = active.getMountManager().orElseThrow().getDriver();
            return driver instanceof Player player ? player : null;
        }

        @Override
        public List<Player> riders() {
            var result = new ArrayList<Player>();
            var seats = active.getMountManager().orElseThrow();
            if (driver() != null) result.add(driver());
            for (Entity rider : seats.getPassengerSeatMap().keySet())
                if (rider instanceof Player p) result.add(p);
            return List.copyOf(result);
        }

        @Override
        public void eject(Player p) {
            active.getMountManager()
                    .ifPresent(
                            m -> {
                                if (p.equals(m.getDriver())) m.dismountDriver();
                                m.dismountPassenger(p);
                            });
        }

        @Override
        public void remove() {
            stopSound();
            models.remove(entity.getUniqueId());
            active.getMountManager()
                    .ifPresent(
                            m -> {
                                m.dismountDriver();
                                m.dismountAll();
                            });
            var modeled = ModelEngineAPI.removeModeledEntity(entity);
            if (modeled != null) modeled.destroy();
        }
    }

    private static final class Controller extends AbstractMountController {
        private final Model model;
        private final VehicleMotion motion = new VehicleMotion();
        private SimpleManualAnimator animator;
        private int soundTicks;

        Controller(Entity rider, Mount mount, Model model) {
            super(rider, mount);
            this.model = model;
        }

        @Override
        public void updateDriverMovement(MoveController controller, ActiveModel active) {
            if (!(getEntity() instanceof Player player) || !model.access.mayPilot(player)) {
                model.stopSound();
                soundTicks = 0;
                controller.setVelocity(0, 0, 0);
                return;
            }
            var input = getInput();
            var eye = player.getEyeLocation();
            var step =
                    motion.tick(
                            model.definition,
                            new VehicleMotion.Input(
                                    input.getFront(),
                                    input.getSide(),
                                    input.isJump(),
                                    input.isSneak(),
                                    eye.getYaw(),
                                    eye.getPitch()),
                            VehicleModels.grounded(model.entity),
                            controller.getVelocity().getY());
            if (step.dismount()) {
                motion.stop();
                controller.setVelocity(0, 0, 0);
                model.stopSound();
                model.eject(player);
                return;
            }
            var from = model.entity.getLocation();
            var to = from.clone().add(step.x(), step.y(), step.z());
            if (controller.isInWater() || !model.access.mayMove(from, to)) {
                motion.stop();
                model.stopSound();
                soundTicks = 0;
                controller.setVelocity(0, 0, 0);
                return;
            }
            controller.setVelocity(step.x(), step.y(), step.z());
            controller.nullifyFallDistance();
            active.getModeledEntity().getBase().getLookController().setBodyYaw(eye.getYaw());
            if (model.definition.aerial()) {
                if (animator == null)
                    active.getBone("body")
                            .ifPresent(
                                    bone -> {
                                        animator = new SimpleManualAnimator(bone);
                                        animator.getPosition().zero();
                                        bone.setManualAnimator(animator);
                                    });
                if (animator != null)
                    animator.getRotation()
                            .identity()
                            .rotateX((float) Math.toRadians(step.pitch()))
                            .rotateZ((float) Math.toRadians(step.roll()));
            }
            boolean moving = step.x() * step.x() + step.z() * step.z() > .0001
                    || (!VehicleModels.grounded(model.entity) && Math.abs(step.y()) > .01);
            if (!moving) {
                model.stopSound();
                soundTicks = 0;
            } else if (--soundTicks <= 0 && !model.definition.sound().isBlank()) {
                model.playSound(from);
                soundTicks = model.definition.soundTicks();
            }
        }

        @Override
        public void updatePassengerMovement(MoveController controller, ActiveModel active) {
            if (getEntity() instanceof Player p && getInput().isSneak()) model.eject(p);
        }
    }

    @Override
    public void close() {
        for (var model : List.copyOf(models.values())) model.remove();
        HandlerList.unregisterAll(this);
    }
}
