package net.mandomc.gameplay.clans;

import java.util.*;
import net.mandomc.gameplay.territory.TerritoryService;
import org.bukkit.GameMode;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;

/** Ownership checks are independent from static zones. Existing cancellations always win. */
final class ClanProtection implements Listener {
    private final ClanService clans;
    private final TerritoryService territory;
    ClanProtection(ClanService clans,TerritoryService territory){this.clans=clans;this.territory=territory;}
    private boolean staff(Player p){return p.getGameMode()==GameMode.CREATIVE&&p.hasPermission("mandomc.territory.edit");}
    private boolean edit(Player p,Block block) {
        if(staff(p)){clans.land().staffEdit(block);return true;}
        if(!clans.canEdit(p,block.getLocation())){p.sendActionBar(clans.message("protected"));return false;}
        if(p.getGameMode()!=GameMode.SURVIVAL)return true;
        if(!clans.land().prepare(block.getWorld(),block.getX()>>4,block.getZ()>>4)) {
            p.sendActionBar(clans.message("unprepared"));return false;
        }
        return true;
    }
    private void touch(Player p,Block b){if(p.getGameMode()==GameMode.SURVIVAL)clans.land().touch(LandRestoration.chunk(b));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){if(!edit(e.getPlayer(),e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void place(BlockPlaceEvent e){if(!edit(e.getPlayer(),e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void multi(BlockMultiPlaceEvent e){for(var s:e.getReplacedBlockStates())if(!edit(e.getPlayer(),s.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void broke(BlockBreakEvent e){touch(e.getPlayer(),e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void placed(BlockPlaceEvent e){touch(e.getPlayer(),e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void multiPlaced(BlockMultiPlaceEvent e){e.getReplacedBlockStates().forEach(s->touch(e.getPlayer(),s.getBlock()));}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fill(PlayerBucketFillEvent e){if(!edit(e.getPlayer(),e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void empty(PlayerBucketEmptyEvent e){if(!edit(e.getPlayer(),e.getBlockClicked().getRelative(e.getBlockFace())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void filled(PlayerBucketFillEvent e){touch(e.getPlayer(),e.getBlock());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void emptied(PlayerBucketEmptyEvent e){touch(e.getPlayer(),e.getBlockClicked().getRelative(e.getBlockFace()));}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent e) {
        Block b=e.getClickedBlock();if(b==null||e.useInteractedBlock()==Event.Result.DENY||staff(e.getPlayer()))return;
        if(b.getState() instanceof Container||b.getType()==org.bukkit.Material.ENDER_CHEST) {
            if(!clans.canContainer(e.getPlayer(),b.getLocation()))e.setUseInteractedBlock(Event.Result.DENY);
            return;
        }
        // Doors, redstone and physical/tool interactions cannot modify a rival's base.
        var owner=clans.owner(b.getLocation());
        if(!clans.canEdit(e.getPlayer(),b.getLocation())&&(owner==null||owner.abandonedAt==0)) {
            e.setUseInteractedBlock(Event.Result.DENY);return;
        }
        String item=e.getMaterial().name();
        boolean modifies=item.endsWith("_HOE")||item.endsWith("_SHOVEL")||item.endsWith("_AXE")||item.endsWith("_BUCKET")
                ||Set.of("BONE_MEAL","FLINT_AND_STEEL","FIRE_CHARGE","SHEARS","SWEET_BERRIES").contains(item)
                ||e.getAction()==Action.PHYSICAL;
        if(modifies&&!edit(e.getPlayer(),b)){e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);}
        else if(modifies)touch(e.getPlayer(),b);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void inventory(InventoryOpenEvent e) {
        var l=e.getInventory().getLocation();
        if(l!=null&&e.getPlayer() instanceof Player p&&!staff(p)&&!clans.canContainer(p,l))e.setCancelled(true);
    }
    private boolean automated(Block from,Block to) {
        var a=clans.owner(from.getLocation());var b=clans.owner(to.getLocation());
        return clans.available()&&a==b&&!clans.splitContainer(from.getLocation())&&!clans.splitContainer(to.getLocation())
                &&!clans.land().restoring(LandRestoration.chunk(from))
                &&!clans.land().restoring(LandRestoration.chunk(to));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void moveItem(InventoryMoveItemEvent e) {
        var a=e.getSource().getLocation();var b=e.getDestination().getLocation();
        if(a!=null&&b!=null&&(!automated(a.getBlock(),b.getBlock())
                ||((e.getSource().getHolder() instanceof Entity||e.getDestination().getHolder() instanceof Entity)
                    &&(clans.owner(a)!=null||clans.owner(b)!=null))))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hopperPickup(InventoryPickupItemEvent e) {
        var l=e.getInventory().getLocation();if(l!=null&&(!automated(l.getBlock(),e.getItem().getLocation().getBlock())
                ||(e.getInventory().getHolder() instanceof Entity&&clans.owner(l)!=null)))e.setCancelled(true);
    }
    private boolean propagated(Block from,Block to) {
        if(!automated(from,to))return false;
        var chunk=LandRestoration.chunk(from);
        if(clans.land().hasBaseline(chunk)&&!clans.land().hasBaseline(LandRestoration.chunk(to))) {
            clans.land().prepare(to.getWorld(),to.getX()>>4,to.getZ()>>4);return false;
        }
        if(clans.land().hasBaseline(chunk))clans.land().inherit(chunk,LandRestoration.chunk(to));
        return !clans.land().hasBaseline(LandRestoration.chunk(to))||clans.land().ready(LandRestoration.chunk(to));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void flow(BlockFromToEvent e){if(!propagated(e.getBlock(),e.getToBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spread(BlockSpreadEvent e){if(!propagated(e.getSource(),e.getBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonExtendEvent e){
        if(!propagated(e.getBlock(),e.getBlock().getRelative(e.getDirection()))||e.getBlocks().stream()
                .anyMatch(b->!propagated(e.getBlock(),b)||!propagated(b,b.getRelative(e.getDirection()))))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void pistonBack(BlockPistonRetractEvent e){if(e.getBlocks().stream()
            .anyMatch(b->!propagated(e.getBlock(),b)||!propagated(b,b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void dispense(BlockDispenseEvent e){
        if(e.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional d
                &&!propagated(e.getBlock(),e.getBlock().getRelative(d.getFacing())))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void grow(org.bukkit.event.world.StructureGrowEvent e){
        if(e.getPlayer()!=null&&staff(e.getPlayer()))return;
        e.getBlocks().removeIf(s->!propagated(e.getLocation().getBlock(),s.getBlock()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fertilize(BlockFertilizeEvent e){
        if(e.getPlayer()!=null&&staff(e.getPlayer()))return;
        e.getBlocks().removeIf(s->!propagated(e.getBlock(),s.getBlock()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityChange(EntityChangeBlockEvent e){if(clans.owner(e.getBlock().getLocation())!=null
            ||clans.land().restoring(LandRestoration.chunk(e.getBlock())))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void ignite(BlockIgniteEvent e){if(e.getPlayer()==null&&clans.owner(e.getBlock().getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burn(BlockBurnEvent e){if(clans.owner(e.getBlock().getLocation())!=null)e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityInteract(PlayerInteractEntityEvent e){if((e.getRightClicked() instanceof Hanging||e.getRightClicked() instanceof ArmorStand)
            &&!staff(e.getPlayer())&&!clans.canEdit(e.getPlayer(),e.getRightClicked().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void armorStand(PlayerArmorStandManipulateEvent e){if(!staff(e.getPlayer())&&!clans.canEdit(e.getPlayer(),e.getRightClicked().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hangingPlace(HangingPlaceEvent e){if(e.getPlayer()!=null&&!staff(e.getPlayer())&&!clans.canEdit(e.getPlayer(),e.getEntity().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void hangingBreak(HangingBreakEvent e){
        var owner=clans.owner(e.getEntity().getLocation());if(owner==null)return;
        if(e instanceof HangingBreakByEntityEvent by&&by.getRemover() instanceof Player p
                &&(staff(p)||clans.canEdit(p,e.getEntity().getLocation())))return;
        e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void decorationDamage(EntityDamageEvent e){if(!(e.getEntity() instanceof ArmorStand))return;
        var owner=clans.owner(e.getEntity().getLocation());if(owner==null)return;
        var p=ClanService.attacker(e.getDamageSource().getCausingEntity());
        if(p==null||(!staff(p)&&!clans.canEdit(p,e.getEntity().getLocation())))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void sign(SignChangeEvent e){if(!staff(e.getPlayer())&&!clans.canEdit(e.getPlayer(),e.getBlock().getLocation()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityPlace(EntityPlaceEvent e){if(e.getPlayer()!=null&&!staff(e.getPlayer())&&!clans.canEdit(e.getPlayer(),e.getEntity().getLocation()))e.setCancelled(true);}
}
