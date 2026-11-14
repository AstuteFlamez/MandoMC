package net.mandomc.verification;

import java.nio.file.Files;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.territory.TerritoryService;
import net.mandomc.gameplay.territory.TerritoryPolicy;
import net.mandomc.gameplay.clans.ClanService;
import net.mandomc.gameplay.clans.ClanState;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only island arrival, border, terrain-original and menu checks on the server thread. */
public final class NabooProbe extends JavaPlugin {
    private int checks;
    private void check(boolean value,String reason) {
        if(!value) throw new IllegalStateException(reason);
        checks++;
    }
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskTimer(this,()->{
            var folder=getDataFolder().toPath();
            if(Files.exists(folder.resolve("PASSED.txt"))||Files.exists(folder.resolve("FAILED.txt")))return;
            var complete=Bukkit.getPluginManager().getPlugin("MandoMC").getDataFolder().toPath()
                    .resolve("data/frozen-land/wilderness-naboo-caves-20261113.journal.completed");
            if(!Files.exists(complete))return;
            try {
                run();getDataFolder().mkdirs();
                Files.writeString(folder.resolve("PASSED.txt"),"NABOO ISLAND PASSED: "+checks);
                getLogger().info("NABOO ISLAND PASSED: "+checks);
            } catch(Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE,"NABOO ISLAND FAILED",error);
                try {getDataFolder().mkdirs();Files.writeString(folder.resolve("FAILED.txt"),error.toString());}
                catch(Exception ignored) { }
            }
        },200,20);
    }
    private void run() {
        var plugin=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");
        check(plugin!=null&&plugin.isEnabled(),"Gameplay enabled");
        var world=Bukkit.getWorld(NamespacedKey.minecraft("naboo"));
        check(world!=null&&plugin.worlds().resolve("Naboo")==world,"World and alias");
        var arrival=plugin.warps().destination("naboo");
        check(arrival.getWorld()==world,"Warp world");
        check(arrival.getX()==-13341.430043026525&&arrival.getY()==59.0&&arrival.getZ()==3209.6097442130404,"Exact captured player position");
        check(arrival.getYaw()==36.899933f&&arrival.getPitch()==-3.6000152f,"Exact captured camera");
        ArrivalDestinations.safeSpawn(arrival);checks++;
        var service=Bukkit.getServicesManager().load(TerritoryService.class);
        var clans=Bukkit.getServicesManager().load(ClanService.class);
        check(service!=null&&clans!=null,"Native territory/clans active");
        var policy=service.policy();var atlas=policy.map("minecraft:naboo",-834,200);
        check(atlas.rows().stream().flatMapToInt(String::chars).filter(c->c=='S').count()==100,"Island is exactly 100 chunks");
        check(atlas.rows().stream().flatMapToInt(String::chars).filter(c->c=='W').count()==100,"Build area is exactly 100 warzone chunks");
        for(int z=188;z<=231;z++)for(int x=-864;x<=-831;x++) {
            char zone=atlas.zone(x,z);boolean safe=zone=='S';boolean wild=zone=='.';
            for(int y:new int[]{-64,0,59,319})for(int offset:new int[]{0,15}) {
                var point=new Location(world,x*16+offset,y,z*16+offset);
                check(service.zone(point)==(safe?TerritoryPolicy.Zone.SAFEZONE:wild?TerritoryPolicy.Zone.WILDERNESS:TerritoryPolicy.Zone.WARZONE),"Full-height chunk boundary");
                check(service.combat(point)==!safe,"Combat boundary");
                check(service.spawning(point)==!safe,"Creature-spawn boundary");
                check(policy.edits("minecraft:naboo",point.getBlockX(),y,point.getBlockZ())==wild,"Building boundary");
            }
            if(!wild)check(!clans.land().supported(new ClanState.Chunk("minecraft:naboo",x,z)),"Safe island removed from claim scope");
        }
        check(service.zone(new Location(world,-13720,80,3280))==TerritoryPolicy.Zone.WARZONE,"Build area protected");
        check(service.zone(new Location(world,-13600,80,3280))==TerritoryPolicy.Zone.WILDERNESS,"Beyond build area wilderness");
        check(service.zone(arrival)==TerritoryPolicy.Zone.SAFEZONE,"New arrival protected");
        check(service.zone(new Location(world,-13551,101,3359))==TerritoryPolicy.Zone.WILDERNESS,"Former arrival restored to wilderness");
        check(service.zone(new Location(world,-13480,59,3080))==TerritoryPolicy.Zone.SAFEZONE,"Island north of border protected too");
        check(service.zone(new Location(world,-13400,59,3250))==TerritoryPolicy.Zone.WILDERNESS,"Nearby mainland remains wilderness");
        var old=new ClanState.Chunk("minecraft:naboo",-847,209);
        check(clans.land().supported(old)&&clans.land().ready(old)&&clans.wilderness(old),"Former arrival has durable wilderness original");
        check(clans.land().prepare(world,-847,209),"Former arrival permits normal survival edits");
        check(world.getWorldBorder().getSize()==500,"500-block border retained");
        check(world.getWorldBorder().getCenter().getX()==-13552&&world.getWorldBorder().getCenter().getZ()==3440,"Border center unchanged");
        check(!world.getWorldBorder().isInside(new Location(world,-13552,59,3189)),"North border unchanged");
        check(world.getGameRuleValue(GameRules.RANDOM_TICK_SPEED)==0&&!world.getGameRuleValue(GameRules.COMMAND_BLOCKS_WORK),"Authored scenery/legacy commands preserved");
        Inventory[] menu=new Inventory[1];UUID id=UUID.randomUUID();
        var actor=(Player)Proxy.newProxyInstance(Player.class.getClassLoader(),new Class<?>[]{Player.class},(p,m,a)->switch(m.getName()) {
            case "getUniqueId"->id;case "openInventory"->{menu[0]=(Inventory)a[0];yield null;}
            case "hasPermission","isOnline"->true;case "getGameMode"->GameMode.SURVIVAL;
            case "hashCode"->id.hashCode();case "equals"->p==a[0];default->null;
        });
        plugin.warps().open(actor);
        check(menu[0]!=null&&menu[0].getSize()==54,"Galaxy Map opens");
        var icon=menu[0].getItem(15);
        check(icon!=null&&NamespacedKey.fromString("mandomc:planets/naboo").equals(icon.getItemMeta().getItemModel()),"Naboo model retained");
        check(plugin.getCommand("warp").tabComplete(actor,"warp",new String[]{"nab"}).contains("naboo"),"Completion retained");
        var warPoint=new Location(world,-13720,80,3280);
        check(service.combat(warPoint)&&!service.edits(warPoint)&&!service.interactions(warPoint),"Warzone permits PvP while locking scenery");
        var guard=new net.mandomc.gameplay.territory.TerritoryProtection(service);
        var door=(org.bukkit.block.Block)Proxy.newProxyInstance(org.bukkit.block.Block.class.getClassLoader(),new Class<?>[]{org.bukkit.block.Block.class},(p,m,a)->switch(m.getName()) {
            case "getLocation"->warPoint.clone();case "getType"->Material.OAK_DOOR;case "getState"->null;
            case "getWorld"->world;case "hashCode"->1;case "equals"->p==a[0];default->null;
        });
        var interacting=new org.bukkit.event.player.PlayerInteractEvent(actor,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,null,door,org.bukkit.block.BlockFace.NORTH,org.bukkit.inventory.EquipmentSlot.HAND);
        guard.interact(interacting);check(interacting.isCancelled(),"Build doors cannot be used by normal players");
        var breaking=new org.bukkit.event.block.BlockBreakEvent(door,actor);
        guard.breakBlock(breaking);check(breaking.isCancelled(),"Build blocks cannot be broken by normal players");
        for(String existing:new String[]{"spawn","hoth","tatooine","mandalore","ilum"})
            check(plugin.warps().destination(existing).getWorld()!=null,"Existing warp "+existing);
    }
}
