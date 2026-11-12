package net.mandomc.verification;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable runtime only: actual native breaking and aggregate event anti-duplication. */
public final class OreQuantityProbe extends JavaPlugin implements Listener {
    private int checks;
    private MandoPlugin mando;
    private World world;
    private Player actor;
    private Block block;
    private boolean denyBreak, denyDrops, noDrops;
    private int xp;
    private final Set<String> scraps = Set.of("agrinium_scrap", "electrum_scrap", "durasteel_scrap", "beskar_scrap");
    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); getLogger().info("ORE QUANTITY PASSED: " + checks + " assertions"); }
            catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "ORE QUANTITY FAILED", error); }
            finally { Bukkit.shutdown(); }
        }, 80);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void guard(BlockBreakEvent event) {
        if (event.getPlayer()!=actor) return;
        if (denyBreak) event.setCancelled(true);
        if (noDrops) event.setDropItems(false);
    }
    @EventHandler(priority=EventPriority.MONITOR) public void xp(BlockBreakEvent event) {
        if (event.getPlayer()==actor) xp=event.getExpToDrop();
    }
    @EventHandler(priority=EventPriority.LOWEST) public void dropsGuard(BlockDropItemEvent event) {
        if (event.getPlayer()==actor && denyDrops) event.setCancelled(true);
    }
    private void verify() throws Exception {
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");world=Objects.requireNonNull(Bukkit.getWorld(NamespacedKey.minecraft("mandalore_review")));
        block=world.getBlockAt(200,280,200);block.getChunk().load();actor=nativeTestPlayer(world,block.getLocation().add(0,1,2));actor.setGameMode(GameMode.SURVIVAL);
        var cases=new LinkedHashMap<Material,String>();
        cases.put(Material.IRON_ORE,"agrinium_scrap");cases.put(Material.DEEPSLATE_IRON_ORE,"agrinium_scrap");
        cases.put(Material.REDSTONE_ORE,"electrum_scrap");cases.put(Material.DEEPSLATE_REDSTONE_ORE,"electrum_scrap");
        cases.put(Material.DIAMOND_ORE,"durasteel_scrap");cases.put(Material.DEEPSLATE_DIAMOND_ORE,"durasteel_scrap");
        cases.put(Material.NETHER_GOLD_ORE,"beskar_scrap");cases.put(Material.ANCIENT_DEBRIS,"beskar_scrap");
        var seen=new HashSet<Integer>();int normal=0,fortune=0;
        for (var entry:cases.entrySet()) {
            Material ore=entry.getKey();String id=entry.getValue();
            for(int enchanted=0;enchanted<2;enchanted++) for(int trial=0;trial<32;trial++) {
                var pick=new ItemStack(Material.NETHERITE_PICKAXE);if(enchanted==1)pick.addUnsafeEnchantment(Enchantment.FORTUNE,3);
                mine(ore,pick,true);var items=drops();
                check(items.size()==1,"one native scrap entity "+ore+" Fortune="+enchanted);
                var stack=items.getFirst().getItemStack();check(id.equals(mando.items().identify(stack)),"matching catalog identity "+ore);
                int count=stack.getAmount();check(count>=1&&count<=5,"final range unaffected by Fortune "+ore+" count="+count);seen.add(count);
                if(enchanted==0)normal++;else fortune++;
                if(ore==Material.DIAMOND_ORE||ore==Material.DEEPSLATE_DIAMOND_ORE)check(xp>=3&&xp<=7,"native diamond XP retained");
                if(ore==Material.REDSTONE_ORE||ore==Material.DEEPSLATE_REDSTONE_ORE)check(xp>=1&&xp<=5,"native redstone XP retained");
            }
            var silk=new ItemStack(Material.NETHERITE_PICKAXE);silk.addUnsafeEnchantment(Enchantment.SILK_TOUCH,1);mine(ore,silk,true);
            check(drops().size()==1&&drops().getFirst().getItemStack().getType()==ore,"Silk Touch keeps ore "+ore);check(xp==0,"Silk Touch zero XP "+ore);
            Material wrong=ore==Material.ANCIENT_DEBRIS?Material.IRON_PICKAXE:ore==Material.NETHER_GOLD_ORE?Material.WOODEN_SHOVEL:Material.WOODEN_PICKAXE;
            mine(ore,new ItemStack(wrong),true);check(drops().isEmpty(),"wrong tool creates no scrap "+ore);
            denyBreak=true;mine(ore,new ItemStack(Material.NETHERITE_PICKAXE),false);check(drops().isEmpty(),"protected/cancelled break creates no scrap "+ore);denyBreak=false;
            denyDrops=true;mine(ore,new ItemStack(Material.NETHERITE_PICKAXE),true);check(drops().isEmpty(),"cancelled drop creates no scrap "+ore);denyDrops=false;
            noDrops=true;mine(ore,new ItemStack(Material.NETHERITE_PICKAXE),true);check(drops().isEmpty(),"event-owned/no-drop break creates no global scrap "+ore);noDrops=false;
            actor.setGameMode(GameMode.CREATIVE);mine(ore,new ItemStack(Material.NETHERITE_PICKAXE),true);check(drops().isEmpty(),"Creative creates no scrap "+ore);actor.setGameMode(GameMode.SURVIVAL);
        }
        check(normal==256&&fortune==256&&seen.equals(Set.of(1,2,3,4,5)),"512 native samples cover all five final quantities");
        for(Material ordinary:List.of(Material.COAL_ORE,Material.DEEPSLATE_COAL_ORE,Material.COPPER_ORE,Material.DEEPSLATE_COPPER_ORE,Material.GOLD_ORE,Material.DEEPSLATE_GOLD_ORE)) {
            mine(ordinary,new ItemStack(Material.NETHERITE_PICKAXE),true);check(!drops().isEmpty(),"ordinary ore still drops loot "+ordinary);
            check(drops().stream().noneMatch(i->{String id=mando.items().identify(i.getItemStack());return id!=null&&scraps.contains(id);}),"ordinary ore is not converted to scrap "+ordinary);
        }
        for(boolean silkTouch:List.of(false,true)) {
            var pick=new ItemStack(Material.NETHERITE_PICKAXE);if(silkTouch)pick.addUnsafeEnchantment(Enchantment.SILK_TOUCH,1);
            var meta=(Damageable)pick.getItemMeta();meta.setDamage(Material.NETHERITE_PICKAXE.getMaxDurability()-1);pick.setItemMeta(meta);
            mine(Material.ANCIENT_DEBRIS,pick,true);check(actor.getInventory().getItemInMainHand().isEmpty(),"last-durability tool actually breaks");
            check(drops().size()==1&&(silkTouch?drops().getFirst().getItemStack().getType()==Material.ANCIENT_DEBRIS:"beskar_scrap".equals(mando.items().identify(drops().getFirst().getItemStack()))),"debris tool snapshot preserves Silk/non-Silk after break");
        }
        clear();block.setType(Material.REDSTONE_ORE,false);var state=block.getState();block.setType(Material.AIR,false);
        var first=world.dropItem(block.getLocation(),new ItemStack(Material.REDSTONE,64));var second=world.dropItem(block.getLocation(),new ItemStack(Material.REDSTONE,64));
        var other=world.dropItem(block.getLocation(),new ItemStack(Material.GUNPOWDER,3));var event=new BlockDropItemEvent(block,state,actor,new ArrayList<>(List.of(first,second,other)));
        Bukkit.getPluginManager().callEvent(event);
        check(event.getItems().size()==2&&drops().size()==2,"split native matching entities consolidated without extra spawn");
        check(first.getItemStack().getAmount()>=1&&first.getItemStack().getAmount()<=5&&"electrum_scrap".equals(mando.items().identify(first.getItemStack())),"128 native items become one final 1–5 roll");
        check(other.getItemStack().equals(new ItemStack(Material.GUNPOWDER,3)),"unrelated plugin loot retained exactly");clear();
        getLogger().info("NATIVE SAMPLE COUNTS normal="+normal+" FortuneIII="+fortune+" outcomes="+seen);
    }
    private void mine(Material type,ItemStack tool,boolean success) {
        clear();block.setType(type,false);actor.getInventory().setItemInMainHand(tool);xp=-1;
        boolean broken=actor.breakBlock(block);
        check(broken==success,"native break outcome "+type+" xp="+xp+" world="+world.getKey()+" insideBorder="+world.getWorldBorder().isInside(block.getLocation())+" mode="+actor.getGameMode()+" actual="+broken);
    }
    private List<Item> drops(){return new ArrayList<>(world.getNearbyEntitiesByType(Item.class,block.getLocation(),4));}
    private void clear(){drops().forEach(Entity::remove);world.getNearbyEntitiesByType(ExperienceOrb.class,block.getLocation(),4).forEach(Entity::remove);}
    private void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;}
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "CombatTest");
        Object handle = playerClass.getConstructor(serverClass, levelClass, profileClass, infoClass)
                .newInstance(server, level, profile, infoClass.getMethod("createDefault").invoke(null));
        Class<?> flowClass = Class.forName("net.minecraft.network.protocol.PacketFlow");
        Object flow = flowClass.getField("SERVERBOUND").get(null);
        Class<?> connectionClass = Class.forName("net.minecraft.network.Connection");
        Object connection = connectionClass.getConstructor(flowClass).newInstance(flow);
        Class<?> cookieClass = Class.forName("net.minecraft.server.network.CommonListenerCookie");
        Object cookie = cookieClass.getMethod("createInitial", profileClass, boolean.class).invoke(null, profile, false);
        Class<?> listenerClass = Class.forName("net.minecraft.server.network.ServerGamePacketListenerImpl");
        Object packetListener = listenerClass.getConstructor(serverClass, connectionClass, playerClass, cookieClass)
                .newInstance(server, connection, handle, cookie);
        playerClass.getField("connection").set(handle, packetListener);
        // Isolated players have no network client to send its loaded acknowledgement.
        var loadTimer = listenerClass.getDeclaredField("clientLoadedTimeoutTimer");
        loadTimer.setAccessible(true);loadTimer.setInt(packetListener, 0);
        var waiting = listenerClass.getDeclaredField("waitingForRespawn");
        waiting.setAccessible(true);waiting.setBoolean(packetListener, false);
        playerClass.getMethod("setPos", double.class, double.class, double.class)
                .invoke(handle, location.getX(), location.getY(), location.getZ());
        playerClass.getMethod("setYRot", float.class).invoke(handle, location.getYaw());
        playerClass.getMethod("setXRot", float.class).invoke(handle, location.getPitch());
        return (Player) playerClass.getMethod("getBukkitEntity").invoke(handle);
    }
}
