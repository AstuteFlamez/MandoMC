package net.mandomc.gameplay.territory;

import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;

/** Native action enforcement. Never uncancels another system's denial; staff edits require Creative. */
public final class TerritoryProtection implements Listener {
    private final TerritoryService territories;
    public TerritoryProtection(TerritoryService territories){this.territories=territories;}
    private boolean blocked(Block b){return !territories.edits(b.getLocation());}
    private boolean staff(Player p){return p.getGameMode()==org.bukkit.GameMode.CREATIVE && p.hasPermission("mandomc.territory.edit");}
    private void deny(Player p){p.sendActionBar(territories.message("protected"));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){if(blocked(e.getBlock())&&!staff(e.getPlayer())&&!net.mandomc.gameplay.events.EventMining.allowed(e.getBlock(),e.getPlayer())){e.setCancelled(true);deny(e.getPlayer());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent e){if(blocked(e.getBlock())&&!staff(e.getPlayer())){e.setCancelled(true);deny(e.getPlayer());}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void multiPlace(BlockMultiPlaceEvent e){if(!staff(e.getPlayer())&&e.getReplacedBlockStates().stream().anyMatch(s->blocked(s.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketFill(PlayerBucketFillEvent e){if(blocked(e.getBlock())&&!staff(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void bucketEmpty(PlayerBucketEmptyEvent e){if((blocked(e.getBlock())||blocked(e.getBlockClicked().getRelative(e.getBlockFace())))&&!staff(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(PlayerInteractEvent e){
        Block b=e.getClickedBlock();if(b==null||!blocked(b)||staff(e.getPlayer()))return;
        boolean physical=e.getAction()==Action.PHYSICAL;
        boolean container=b.getState() instanceof Container;
        String item=e.getMaterial().name();
        boolean tool=item.endsWith("_HOE")||item.endsWith("_SHOVEL")||item.endsWith("_AXE")||item.equals("BONE_MEAL")
                ||item.equals("FLINT_AND_STEEL")||item.equals("FIRE_CHARGE")||item.endsWith("_BUCKET")
                ||item.equals("SHEARS")||item.equals("SWEET_BERRIES");
        if((physical&&(!territories.mechanisms(b.getLocation())||b.getType()==org.bukkit.Material.FARMLAND))||container||tool)e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityInteract(PlayerInteractEntityEvent e){if(!territories.edits(e.getRightClicked().getLocation())&&!staff(e.getPlayer())
            &&(e.getRightClicked() instanceof Hanging||e.getRightClicked() instanceof ArmorStand))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void armorStand(PlayerArmorStandManipulateEvent e){if(!territories.edits(e.getRightClicked().getLocation())&&!staff(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hangingPlace(HangingPlaceEvent e){if(!territories.edits(e.getEntity().getLocation())&&(e.getPlayer()==null||!staff(e.getPlayer())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hangingBreak(HangingBreakEvent e){if(!territories.edits(e.getEntity().getLocation())){
        if(e instanceof HangingBreakByEntityEvent by&&by.getRemover() instanceof Player p&&staff(p))return;e.setCancelled(true);}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityPlace(EntityPlaceEvent e){if(!territories.edits(e.getEntity().getLocation())&&(e.getPlayer()==null||!staff(e.getPlayer())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void inventoryMove(org.bukkit.event.inventory.InventoryMoveItemEvent e){
        var from=e.getSource().getLocation();var to=e.getDestination().getLocation();
        if(from!=null&&to!=null&&from.getWorld().equals(to.getWorld())&&territories.mechanisms(from))return;
        if((from!=null&&!territories.edits(from))||(to!=null&&!territories.edits(to)))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent e){
        if(!territories.combat(e.getEntity().getLocation())){e.setCancelled(true);return;}
        Entity attacker=e.getDamageSource().getCausingEntity();
        if(e.getEntity() instanceof ArmorStand && !territories.edits(e.getEntity().getLocation())
                && !(attacker instanceof Player player && staff(player))) {e.setCancelled(true);return;}
        if(attacker!=null&&!territories.combat(attacker.getLocation()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void vehicleDamage(VehicleDamageEvent e){if(!territories.combat(e.getVehicle().getLocation())
            ||(e.getAttacker()!=null&&!territories.combat(e.getAttacker().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void vehicleDestroy(VehicleDestroyEvent e){if(!territories.combat(e.getVehicle().getLocation())
            ||(e.getAttacker()!=null&&!territories.combat(e.getAttacker().getLocation())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spawn(CreatureSpawnEvent e){if(!territories.spawning(e.getLocation())&&!territories.approvedNpcSpawn(e))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityChange(EntityChangeBlockEvent e){if(blocked(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void form(EntityBlockFormEvent e){if(blocked(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(EntityExplodeEvent e){e.blockList().removeIf(this::blocked);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void blockExplode(BlockExplodeEvent e){e.blockList().removeIf(this::blocked);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dispense(BlockDispenseEvent e){
        if(territories.mechanisms(e.getBlock().getLocation()))return;
        if(blocked(e.getBlock()) || (e.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional direction
                && blocked(e.getBlock().getRelative(direction.getFacing()))))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void tree(org.bukkit.event.world.StructureGrowEvent e){
        if(e.getPlayer()!=null&&staff(e.getPlayer()))return;e.getBlocks().removeIf(s->blocked(s.getBlock()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fertilize(BlockFertilizeEvent e){if(e.getPlayer()!=null&&staff(e.getPlayer()))return;e.getBlocks().removeIf(s->blocked(s.getBlock()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void sign(SignChangeEvent e){if(blocked(e.getBlock())&&!staff(e.getPlayer()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(BlockBurnEvent e){if(blocked(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void ignite(BlockIgniteEvent e){if(blocked(e.getBlock())&&(e.getPlayer()==null||!staff(e.getPlayer())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spread(BlockSpreadEvent e){if(blocked(e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void flow(BlockFromToEvent e){if(territories.mechanisms(e.getBlock().getLocation()))return;if(blocked(e.getBlock())||blocked(e.getToBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pistonExtend(BlockPistonExtendEvent e){if(territories.mechanisms(e.getBlock().getLocation()))return;if(blocked(e.getBlock())||blocked(e.getBlock().getRelative(e.getDirection()))||e.getBlocks().stream().anyMatch(b->blocked(b)||blocked(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pistonRetract(BlockPistonRetractEvent e){if(territories.mechanisms(e.getBlock().getLocation()))return;if(blocked(e.getBlock())||blocked(e.getBlock().getRelative(e.getDirection()))||e.getBlocks().stream().anyMatch(b->blocked(b)||blocked(b.getRelative(e.getDirection()))))e.setCancelled(true);}
}
