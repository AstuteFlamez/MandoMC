package net.mandomc.verification;

import java.nio.file.Files;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.enchantments.EnchantmentService;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.view.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Real native menus and packet pickups, isolated to a disposable loopback Paper fixture. */
public final class SaberEnchantmentProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private final List<Double> throwHits = new ArrayList<>();
    private LivingEntity victim;
    private MandoPlugin mando; private NativeProbePlayers actors; private Player player; private World world;
    private org.bukkit.block.Block station; private int checks;
    private final Enchantment endurance = Registry.ENCHANTMENT.get(EnchantmentService.ENDURANCE);
    private final Enchantment recovery = Registry.ENCHANTMENT.get(EnchantmentService.RECOVERY);
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); checks++; }
    private void later(int ticks, Work work) { Bukkit.getScheduler().runTaskLater(this, () -> { try { work.run(); } catch (Throwable error) { finish(error); } }, ticks); }
    @Override public void onEnable() { later(160, this::start); }
    private Object handle() throws Exception { return player.getClass().getMethod("getHandle").invoke(player); }
    private Object menu() throws Exception { var handle = handle(); return handle.getClass().getField("containerMenu").get(handle); }
    private boolean button(int index) throws Exception { return (boolean) menu().getClass().getMethod("clickMenuButton", Class.forName("net.minecraft.world.entity.player.Player"), int.class).invoke(menu(), handle(), index); }
    private void click(int slot, String action) throws Exception {
        var handle = handle(); var menu = menu(); var type = Class.forName("net.minecraft.world.inventory.ContainerInput");
        var packetType = Class.forName("net.minecraft.network.protocol.game.ServerboundContainerClickPacket");
        var mapType = Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectMap");
        var changes = Class.forName("it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap").getConstructor().newInstance();
        var hashed = Class.forName("net.minecraft.network.HashedStack");
        var packet = packetType.getConstructor(int.class,int.class,short.class,byte.class,type,mapType,hashed).newInstance(
                menu.getClass().getField("containerId").getInt(menu), menu.getClass().getMethod("getStateId").invoke(menu),
                (short) slot, (byte) 0, type.getField(action).get(null), changes, hashed.getField("EMPTY").get(null));
        var connection = handle.getClass().getField("connection").get(handle);
        connection.getClass().getMethod("handleContainerClick", packetType).invoke(connection, packet);
    }
    private boolean empty(ItemStack item) { return item == null || item.isEmpty(); }
    private String plain(Component component) { return component == null ? "" : PlainTextComponentSerializer.plainText().serialize(component); }
    private ItemStack book(Enchantment enchantment, int level) {
        var item = new ItemStack(Material.ENCHANTED_BOOK); var meta = (EnchantmentStorageMeta) item.getItemMeta();
        meta.addStoredEnchant(enchantment, level, false); item.setItemMeta(meta); mando.enchantments().decorate(item); return item;
    }
    private int stored(ItemStack item, Enchantment enchantment) { return item != null && item.getItemMeta() instanceof EnchantmentStorageMeta meta ? meta.getStoredEnchantLevel(enchantment) : 0; }
    private void start() throws Exception {
        check(Bukkit.getPort() == 25591, "Disposable fixture port"); mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(mando != null && mando.isEnabled(), "MandoMC enabled"); check(endurance != null && recovery != null, "Native custom registries");
        world = Bukkit.getWorld(NamespacedKey.minecraft("devworld")); check(world != null, "Fixture world");
        world.getWorldBorder().setCenter(0,0); world.getWorldBorder().setSize(1024);
        for(int x=-14;x<=-11;x++)for(int z=-14;z<=-11;z++)world.getChunkAt(x,z).addPluginChunkTicket(this);
        actors = new NativeProbePlayers("Saber"); player = actors.create(new Location(world,-201.5,200,-199.5));
        player.setGameMode(GameMode.SURVIVAL); player.setLevel(100); player.getInventory().clear();
        station = world.getBlockAt(-200,200,-200); station.setType(Material.SMITHING_TABLE,false);
        station.getChunk().getPersistentDataContainer().set(new NamespacedKey("mandomc","forge_-200_200_-200"),PersistentDataType.BYTE,(byte)1);
        check(mando.forge().station(station), "Existing placed forge migrates to workbench");
        recipeMigration(); presentations(); equipmentLore(); rewardsAndMigration(); adminEnchant(); economyAndRedstone(); crateChances(); inventoryBadges();
        electrumFurnaces();
    }

    private FurnaceInventory fillFurnace(int x, ItemStack input) {
        var block = world.getBlockAt(x, 200, -215);
        block.setType(Material.AIR, false);
        block.setType(Material.FURNACE, false);
        var inventory = ((org.bukkit.block.Furnace) block.getState()).getInventory();
        inventory.setSmelting(input);
        inventory.setFuel(new ItemStack(Material.COAL));
        return inventory;
    }

    private void electrumFurnaces() {
        var dust = fillFurnace(-215, mando.items().create("electrum_dust").asQuantity(2));
        var lapis = fillFurnace(-214, new ItemStack(Material.LAPIS_LAZULI));
        var ore = fillFurnace(-213, mando.items().create("electrum_ore"));
        var deepslate = fillFurnace(-212, mando.items().create("deepslate_electrum_ore"));
        later(235, () -> {
            var ingot = mando.items().create("electrum");
            check(ingot.equals(dust.getResult()) && dust.getSmelting().getAmount() == 1,
                    "A normal furnace consumes one Electrum Dust and produces one catalog ingot");
            check(empty(lapis.getResult()) && new ItemStack(Material.LAPIS_LAZULI).equals(lapis.getSmelting()),
                    "Ordinary lapis stays in the furnace input");
            for (var furnace : List.of(ore, deepslate)) {
                check(ingot.equals(furnace.getResult()) && empty(furnace.getSmelting()),
                        "The native furnace still turns intact Silk Touch ore into one ingot");
            }
            continueWorkbench();
        });
    }

    private void continueWorkbench() throws Exception {
        mando.forge().open(player,station); var top = player.getOpenInventory().getTopInventory();
        check(top.getItem(10).getType()==Material.CRAFTING_TABLE && top.getItem(13).getType()==Material.STONECUTTER && top.getItem(16).getType()==Material.ENCHANTED_BOOK,"Three workbench functions");
        checkTitle("Saber Workbench");
        workbenchRecipe();
    }
    private void workbenchRecipe() throws Exception {
        player.closeInventory();player.getInventory().clear();
        var view=MenuType.CRAFTING.builder().checkReachable(false).build(player);view.open();
        var inventory=(CraftingInventory)view.getTopInventory();
        String[] ids={"durasteel","circuit_board","durasteel","agrinium","minecraft:smithing_table","agrinium","durasteel","durasteel","durasteel"};
        var matrix=new ItemStack[9];
        for(int i=0;i<ids.length;i++) {matrix[i]=ids[i].startsWith("minecraft:")?new ItemStack(Material.SMITHING_TABLE):mando.items().create(ids[i]);matrix[i].setAmount(2);}
        inventory.setMatrix(matrix);
        // Exercise native recipe matching and PrepareItemCraftEvent; never inject a result.
        var handle=menu();var craft=handle.getClass().getSuperclass().getDeclaredField("craftSlots");craft.setAccessible(true);
        handle.getClass().getMethod("slotsChanged",Class.forName("net.minecraft.world.Container")).invoke(handle,craft.get(handle));
        later(2,()->{
            check(inventory.getResult()!=null&&"saber_workbench".equals(mando.items().identify(inventory.getResult())),"Workbench matches genuine ingredients in an ordinary crafting table");
            check(inventory.getRecipe() instanceof org.bukkit.Keyed keyed&&keyed.getKey().getNamespace().equals("mandomc"),"Workbench uses a registered custom recipe");
            click(0,"PICKUP");check("saber_workbench".equals(mando.items().identify(player.getItemOnCursor()))&&player.getItemOnCursor().getAmount()==1,"Native crafting pickup produces one workbench");
            for(var item:inventory.getMatrix())check(item!=null&&item.getAmount()==1,"Workbench crafting consumes one of each ingredient slot");
            player.setItemOnCursor(null);player.closeInventory();
            check(Bukkit.dispatchCommand(player,"recipes saber_workbench"),"Direct workbench recipe command");
            var guide=player.getOpenInventory().getTopInventory();check("saber_workbench".equals(mando.items().identify(guide.getItem(25))),"Workbench recipe preview has the obtainable station");
            check(guide.getItem(23).getType()==Material.CRAFTING_TABLE,"Workbench itself is crafted at a crafting table, avoiding circular station requirements");
            player.closeInventory();Bukkit.dispatchCommand(player,"recipes");guide=player.getOpenInventory().getTopInventory();
            int category=-1;for(int i=0;i<guide.getSize();i++){var item=guide.getItem(i);if(item!=null&&plain(item.getItemMeta().displayName()).contains("Lightsabers"))category=i;}
            check(category>=0,"Lightsabers category exists");click(category,"PICKUP");
            later(2,()->{
                var page=player.getOpenInventory().getTopInventory();
                check(Arrays.stream(page.getContents()).filter(Objects::nonNull).anyMatch(item->"saber_workbench".equals(mando.items().identify(item))),"Workbench recipe is discoverable alongside saber recipes");
                player.closeInventory();player.getInventory().clear();navigation(0);
            });
        });
    }
    private void navigation(int index) throws Exception {
        if(index==3){
            player.closeInventory();player.getInventory().setItem(8,new ItemStack(Material.DIAMOND,3));player.setOp(true);Bukkit.dispatchCommand(player,"staff on");
            check(net.mandomc.gameplay.admin.StaffMode.active(player)&&player.getGameMode()==GameMode.CREATIVE,"Staff creative navigation setup");
            var chest=world.getBlockAt(-198,200,-200);chest.setType(Material.CHEST,false);
            var storage=((org.bukkit.block.Chest)chest.getState()).getInventory();
            storage.setItem(0,new ItemStack(Material.COAL,12));
            check(player.openInventory(storage)!=null,"Staff can inspect shared storage");
            click(0,"PICKUP");
            check(storage.getItem(0).getAmount()==12&&empty(player.getItemOnCursor()),"Staff inventory stays separate from shared storage");
            player.closeInventory();
            var table=world.getBlockAt(-198,200,-199);
            table.setType(Material.ENCHANTING_TABLE,false);
            check(player.openEnchanting(table.getLocation(),true)!=null,"Staff can use a real enchanting table");
            player.closeInventory();
            player.teleport(player.getLocation().add(0,2,0));
        }
        if(index==6){
            player.closeInventory();Bukkit.dispatchCommand(player,"staff off");player.setOp(false);
            check(!net.mandomc.gameplay.admin.StaffMode.active(player)&&player.getGameMode()==GameMode.SURVIVAL,"Staff navigation restores original mode");
            check(new ItemStack(Material.DIAMOND,3).equals(player.getInventory().getItem(8)),"Staff navigation preserves saved survival inventory");
            check(player.getLocation().getY()==200,"Staff off restores the saved location");
            table();later(2,this::tableRolls);return;
        }
        int[] slots={10,13,16};String[] labels={"Saber Crafting","Saber Styles","Saber Enchanting"};
        org.bukkit.event.inventory.InventoryType[] types={org.bukkit.event.inventory.InventoryType.WORKBENCH,org.bukkit.event.inventory.InventoryType.STONECUTTER,org.bukkit.event.inventory.InventoryType.ANVIL};
        int choice=index%3;mando.forge().open(player,station);
        click(slots[choice],"PICKUP");
        later(2,()->{
            var view=player.getOpenInventory();
            check(view.getType()==types[choice],"Real menu-button packet opens "+labels[choice]+" in "+player.getGameMode());
            checkTitle(labels[choice]);
            var session=mando.forge().getClass().getDeclaredMethod("session",InventoryView.class);session.setAccessible(true);
            check(session.invoke(mando.forge(),view)!=null,"Opened native menu keeps workstation validation: "+labels[choice]);
            check(empty(player.getItemOnCursor()),"Navigation never extracts a menu icon");
            player.closeInventory();navigation(index+1);
        });
    }
    private void checkTitle(String expected) {
        var title=player.getOpenInventory().title();
        check(plain(title).equals(expected),"Compact window title: "+expected);
        check(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY.equals(title.color()),"Readable dark-gray window title: "+expected);
        check(net.mandomc.gameplay.tutorial.BookPageLayout.width(expected,false)<=124,"Window title fits native workstation: "+expected);
    }
    private void recipeMigration() throws Exception {
        var old=new NamespacedKey("mandomc","craft_saber_forge");var current=new NamespacedKey("mandomc","craft_saber_workbench");
        check(Bukkit.getRecipe(old)!=null&&"saber_workbench".equals(mando.items().identify(Bukkit.getRecipe(old).getResult())),"Saved forge recipe resolves to new workbench");
        player.discoverRecipe(old);var field=mando.getClass().getDeclaredField("craftingRecipes");field.setAccessible(true);
        ((net.mandomc.gameplay.items.CraftingRecipes)field.get(mando)).join(new org.bukkit.event.player.PlayerJoinEvent(player,Component.empty()));
        check(!player.hasDiscoveredRecipe(old)&&player.hasDiscoveredRecipe(current),"Recipe book replaces legacy key without duplicate entries");
    }
    private void presentations() {
        int vanilla = 0;
        for (var enchantment : Registry.ENCHANTMENT) {
            if (enchantment.getKey().getNamespace().equals("minecraft")) vanilla++;
            var item = book(enchantment,1); var meta = item.getItemMeta(); var lore = meta.lore();
            check(lore != null && lore.size() >= 3, "Book badge and explanation: " + enchantment.getKey());
            check(lore.getFirst().children().stream().anyMatch(c -> c.font()!=null&&c.font().value().equals("enchantment")) || lore.getFirst().toString().contains("mandomc:enchantment"),"Enchantment texture tag: "+enchantment.getKey());
            var before=item.clone();mando.enchantments().decorate(item);check(item.equals(before),"Book styling idempotent: "+enchantment.getKey());
        }
        check(vanilla==43,"Complete pinned vanilla list");
        var jetpack=mando.bobaEquipment().chestplates().book(1);check(stored(jetpack,Registry.ENCHANTMENT.get(new NamespacedKey("mandomc","jetpack")))==1,"Jetpack remains a real book");
        var fish=book(Enchantment.LURE,3);mando.enchantments().maintain(fish);check(fish.getType()==Material.BOOK,"Fishing-only books removed");
        var mixed=book(Enchantment.LURE,1);var m=(EnchantmentStorageMeta)mixed.getItemMeta();m.addStoredEnchant(Enchantment.MENDING,1,false);mixed.setItemMeta(m);mando.enchantments().maintain(mixed);
        check(stored(mixed,Enchantment.LURE)==0&&stored(mixed,Enchantment.MENDING)==1,"Fishing removal preserves unrelated enchants");
        for(var e:List.of(endurance,recovery))check(book(e,3).getItemMeta().lore().stream().noneMatch(c->plain(c).matches(".*[0-9%].*")),"Custom descriptions omit numerical tuning");
    }
    private void equipmentLore() {
        var saber=mando.items().create("vader");var original=saber.clone();var base=saber.getItemMeta().lore();
        saber.addUnsafeEnchantment(Enchantment.SHARPNESS,5);saber.addUnsafeEnchantment(endurance,3);saber.addUnsafeEnchantment(recovery,3);
        mando.enchantments().maintain(saber);var lore=saber.getItemMeta().lore();
        check(lore.subList(0,base.size()).equals(base),"Saber badge, description and controls remain in their original order");
        check(plain(lore.get(base.size())).isBlank(),"One clear separator precedes applied enchantments");
        check(lore.size()==base.size()+4,"Saber footer contains exactly one row per enchantment");
        check(plain(lore.getLast()).contains("Recovery")&&plain(lore.get(lore.size()-2)).contains("Endurance"),"Custom enchantments are at the bottom");
        check(saber.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS),"Native above-lore enchantment block is hidden");
        var once=saber.clone();mando.enchantments().maintain(saber);check(saber.equals(once),"Equipment footer updates without duplicates");
        for(var enchant:List.copyOf(saber.getEnchantments().keySet()))saber.removeEnchantment(enchant);
        mando.enchantments().maintain(saber);check(saber.equals(original),"Disenchanting restores original saber lore and visibility flags exactly");
        var vanilla=new ItemStack(Material.DIAMOND_PICKAXE);vanilla.editMeta(meta->{meta.displayName(Component.text("Named pick"));meta.lore(List.of(Component.text("A player note"),Component.text("Use carefully")));});
        var vanillaOriginal=vanilla.clone();vanilla.addUnsafeEnchantment(Enchantment.EFFICIENCY,4);mando.enchantments().maintain(vanilla);
        check(plain(vanilla.getItemMeta().lore().get(0)).equals("A player note")&&plain(vanilla.getItemMeta().lore().getLast()).toLowerCase(Locale.ROOT).contains("efficiency"),"Vanilla equipment notes precede enchantments");
        vanilla.removeEnchantment(Enchantment.EFFICIENCY);mando.enchantments().maintain(vanilla);check(vanilla.equals(vanillaOriginal),"Vanilla notes and renames restore exactly");
        var hidden=new ItemStack(Material.DIAMOND_HELMET);hidden.addUnsafeEnchantment(Enchantment.PROTECTION,4);hidden.editMeta(meta->meta.addItemFlags(ItemFlag.HIDE_ENCHANTS));
        var hiddenOriginal=hidden.clone();mando.enchantments().maintain(hidden);check(hidden.equals(hiddenOriginal),"Originally hidden enchantments remain hidden");
        var chest=mando.items().create("beskar_chestplate");chest.addUnsafeEnchantment(Enchantment.PROTECTION,4);mando.enchantments().maintain(chest);
        var chestOriginal=chest.clone();var upgraded=mando.bobaEquipment().chestplates().upgrade(chest);
        check(upgraded.getItemMeta().lore().subList(0,chestOriginal.getItemMeta().lore().size()-2).equals(chestOriginal.getItemMeta().lore().subList(0,chestOriginal.getItemMeta().lore().size()-2)),"Jetpack preserves existing armor description");
        var footer=upgraded.getItemMeta().lore();check(plain(footer.getLast()).toLowerCase(Locale.ROOT).contains("protection")&&plain(footer.get(footer.size()-2)).contains("Jetpack"),"Jetpack and vanilla enchants share the bottom footer");
        var legacy=upgraded.clone();var meta=legacy.getItemMeta();var old=new ArrayList<>(chestOriginal.getItemMeta().lore());
        old.removeIf(line->"mandomc:equipment-enchantments".equals(line.insertion()));old.add(2,Component.text("Jetpack I").insertion("mandomc:jetpack-tooltip"));
        meta.lore(old);meta.getPersistentDataContainer().remove(new NamespacedKey("mandomc","enchants_prior_hide"));meta.getPersistentDataContainer().set(new NamespacedKey("mandomc","jetpack_prior_hide_enchants"),PersistentDataType.BYTE,(byte)0);legacy.setItemMeta(meta);
        mando.enchantments().maintain(legacy);check(legacy.getItemMeta().lore().stream().noneMatch(line->"mandomc:jetpack-tooltip".equals(line.insertion())),"Old Jetpack lore migrates out of the description");
        upgraded.removeEnchantment(Registry.ENCHANTMENT.get(new NamespacedKey("mandomc","jetpack")));upgraded=mando.bobaEquipment().chestplates().cleanRemovedEnchantment(upgraded);mando.enchantments().maintain(upgraded);
        check(upgraded.equals(chestOriginal),"Removing Jetpack preserves the normal enchantment footer and armor components");
        var noteBook=book(recovery,1);noteBook.editMeta(m->{var rows=new ArrayList<>(m.lore());rows.add(Component.text("Player book note"));m.lore(rows);});mando.enchantments().maintain(noteBook);
        var bookLore=noteBook.getItemMeta().lore();check(plain(bookLore.get(2)).equals("Player book note")&&plain(bookLore.getLast()).contains("faster"),"Book notes precede the final enchantment explanation");
    }
    private void rewardsAndMigration() throws Exception {
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"crates/crates.yml"));
        for(String crate:List.of("vote","koth")) {
            var entries=config.getMapList("crates."+crate+".rewards");
            check(entries.stream().anyMatch(e->"endurance_book".equals(e.get("item"))),crate+" Endurance reward");
            check(entries.stream().anyMatch(e->"recovery_book".equals(e.get("item"))),crate+" Recovery reward");
        }
        check(config.getMapList("crates.koth.rewards").stream().anyMatch(e->"mending_book".equals(e.get("item"))),"KOTH Mending reward");
        check(stored(mando.items().create("mending_book"),Enchantment.MENDING)==1,"Reward is Mending I");
        var saber=mando.items().create("vader");player.getInventory().setItemInMainHand(saber);
        check(Math.abs(saber.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE).iterator().next().getAmount()-7)<1e-8,"Base netherite damage");
        check(Math.abs(saber.getItemMeta().getAttributeModifiers(Attribute.ATTACK_SPEED).iterator().next().getAmount()+2.4)<1e-8,"Base netherite speed");
        var meta=saber.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("mandomc","forge_damage"),PersistentDataType.INTEGER,2);
        meta.getPersistentDataContainer().set(new NamespacedKey("mandomc","forge_guard"),PersistentDataType.INTEGER,3);saber.setItemMeta(meta);
        mando.enchantments().maintain(saber);check(saber.getEnchantmentLevel(Enchantment.SHARPNESS)==2&&saber.getEnchantmentLevel(endurance)==3,"Old material upgrades migrate");
        var once=saber.clone();mando.enchantments().maintain(saber);check(saber.equals(once),"Saber migration idempotent");
        check(!mando.enchantments().tableAllowed(Enchantment.MENDING)&&!mando.enchantments().tableAllowed(Enchantment.LURE),"Mending and fishing absent from tables");
    }
    private void adminEnchant() {
        player.getInventory().setItemInMainHand(mando.items().create("vader"));player.setOp(false);
        Bukkit.dispatchCommand(player,"enchant recovery 5");check(player.getInventory().getItemInMainHand().getEnchantmentLevel(recovery)==0,"Ordinary players cannot use admin enchanting");
        player.setOp(true);check(Bukkit.dispatchCommand(player,"enchant recovery 5"),"Bare custom enchant command");
        var item=player.getInventory().getItemInMainHand();check(item.getEnchantmentLevel(recovery)==5,"Admin Recovery V bypasses survival cap");
        check(Math.abs(mando.enchantments().regeneration(item)-2.25)<1e-8,"Admin level V changes the actual regeneration rate");
        Bukkit.dispatchCommand(player,"enchant endurance 5");check(player.getInventory().getItemInMainHand().getEnchantmentLevel(endurance)==5,"Admin Endurance V");
        Bukkit.dispatchCommand(player,"enchant sharpness 5");check(player.getInventory().getItemInMainHand().getEnchantmentLevel(Enchantment.SHARPNESS)==5,"Admin vanilla short names");
        Bukkit.dispatchCommand(player,"enchant recovery 0");check(player.getInventory().getItemInMainHand().getEnchantmentLevel(recovery)==0,"Level zero removes enchantment");
        check(recovery.getMaxLevel()==3&&endurance.getMaxLevel()==3,"Admin overrides do not change survival caps");
        player.setOp(false);
    }
    private void inventoryBadges() throws Exception {
        var field=mando.getClass().getDeclaredField("inventoryBadges");field.setAccessible(true);
        var view=field.get(mando);
        var packetStack=com.github.retrooper.packetevents.protocol.item.ItemStack.class;
        var present=view.getClass().getDeclaredMethod("present",packetStack);present.setAccessible(true);
        var loreType=com.github.retrooper.packetevents.protocol.component.ComponentTypes.LORE;
        for (var material : List.of(Material.STONE,Material.IRON_SWORD,Material.CACTUS,Material.BREAD,Material.ELYTRA)) {
            var source=io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitItemStack(new ItemStack(material));
            var decorated=(com.github.retrooper.packetevents.protocol.item.ItemStack)present.invoke(view,source);
            var lore=decorated.getComponent(loreType).orElseThrow().getLines();
            check(source.getComponentOr(loreType,com.github.retrooper.packetevents.protocol.component.builtin.item.ItemLore.EMPTY)
                    .getLines().isEmpty()&&lore.size()==1,"Client-only item badge: "+material);
            if(material==Material.STONE)check(plain(lore.getFirst()).equals(plain(mando.items().badges("common","blocks"))),
                    "Vanilla stone displays the Common Block badges");
            check(present.invoke(view,decorated)==decorated,"Client badge decoration is idempotent");
        }
        var custom=io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitItemStack(mando.items().create("electrum_dust"));
        check(present.invoke(view,custom)==custom,"Catalog dust keeps exactly one original badge row");
    }

    private void crateChances() {
        check(Bukkit.dispatchCommand(player,"crates preview koth"),"KOTH crate preview opens");
        var books=Arrays.stream(player.getOpenInventory().getTopInventory().getContents())
                .filter(Objects::nonNull).filter(item->item.getType()==Material.ENCHANTED_BOOK).toList();
        check(books.size()>=3,"KOTH preview includes new enchantment books");
        for (var book : books) {
            check(book.getItemMeta().lore().stream().anyMatch(line->plain(line).contains("%")),"Preview book shows its chance: "+mando.items().identify(book));
            mando.enchantments().maintain(book);
            check(book.getItemMeta().lore().stream().anyMatch(line->plain(line).contains("%")),"Book maintenance keeps the chance");
        }
        player.closeInventory();
    }

    private void economyAndRedstone() throws Exception {
        check(Bukkit.getWorlds().stream().noneMatch(w -> Boolean.TRUE.equals(w.getGameRuleValue(GameRules.LOCATOR_BAR))),
                "No world sends the player locator bar");
        check(Bukkit.getRecipe(NamespacedKey.minecraft("leather"))==null
                && Bukkit.getRecipe(new NamespacedKey("mandomc","craft_bantha_hide_from_rabbit_hide"))==null,
                "Neither version of the rabbit-hide recipe remains");
        check(Bukkit.getRecipe(NamespacedKey.minecraft("redstone_block"))!=null
                && Bukkit.getRecipe(NamespacedKey.minecraft("redstone"))!=null,"Native redstone compacts and unpacks");
        var source=world.getBlockAt(-206,200,-204);
        var lamp=source.getRelative(org.bukkit.block.BlockFace.EAST);
        source.setType(Material.AIR,false);
        lamp.setType(Material.REDSTONE_LAMP);
        source.setType(Material.REDSTONE_BLOCK);
        check(lamp.isBlockIndirectlyPowered(),"A redstone block supplies native signal to the adjacent lamp");
        var dust=mando.items().create("electrum_dust");
        var dustRecipe = Bukkit.getRecipe(new NamespacedKey("mandomc", "smelt_electrum_from_dust"));
        check(dustRecipe instanceof FurnaceRecipe furnace
                        && furnace.getInputChoice().test(dust)
                        && !furnace.getInputChoice().test(new ItemStack(Material.LAPIS_LAZULI))
                        && mando.items().create("electrum").equals(furnace.getResult()),
                "Electrum Dust is the custom input for the one-ingot furnace recipe");
        check(Bukkit.getRecipe(NamespacedKey.minecraft("redstone_from_smelting_redstone_ore"))==null,
                "No vanilla recipe shadows intact ore smelting");
        for (String crust : List.of("", "deepslate_")) {
            var smelt=Bukkit.getRecipe(new NamespacedKey("mandomc","smelt_electrum_from_"+crust+"ore"));
            check(smelt instanceof FurnaceRecipe furnace
                    && furnace.getInputChoice().test(mando.items().create(crust+"electrum_ore"))
                    && !furnace.getInputChoice().test(dust)
                    && "electrum".equals(mando.items().identify(furnace.getResult())),
                    "Intact "+crust+"Electrum Ore retains its own ingot recipe");
        }
        nativeElectrumWire(dust);
        var renamedDust=dust.asQuantity(4);
        renamedDust.editMeta(meta -> {
            meta.displayName(Component.text("Saved dust"));
            var lore=new ArrayList<>(meta.lore());
            lore.set(lore.size()-1, Component.text("Smelt in a normal furnace for one ingot."));
            meta.lore(lore);
        });
        var refreshed=new net.mandomc.gameplay.items.MetalMaterials(mando.items()).canonical(renamedDust);
        check(refreshed.getAmount()==4&&plain(refreshed.getItemMeta().displayName()).equals("Saved dust")
                && plain(refreshed.getItemMeta().lore().getLast()).equals("Use in circuitry or place as Electrum Wire."),
                "Existing Electrum Dust gains the current hint without losing its name or quantity");
        var old=new ItemStack(Material.ECHO_SHARD,13);
        old.editMeta(meta->meta.getPersistentDataContainer().set(net.mandomc.gameplay.items.ItemCatalog.ITEM_ID,
                PersistentDataType.STRING,"electrum_scrap"));
        var migrated=new net.mandomc.gameplay.items.MetalMaterials(mando.items()).canonical(old);
        check(migrated.getAmount()==13&&"electrum_dust".equals(mando.items().identify(migrated)),"Old tagged scrap keeps its quantity");
        var plainRedstone=new ItemStack(Material.REDSTONE,3);
        check(new net.mandomc.gameplay.items.MetalMaterials(mando.items()).canonical(plainRedstone).equals(dust.asQuantity(3)),
                "Legacy redstone dust preserves its quantity as Electrum Dust");
        var circuit = new ItemStack[9];
        Arrays.fill(circuit, dust);
        check(Bukkit.craftItemResult(circuit,world,player).getResult().getType()==Material.REDSTONE_BLOCK,
                "The actual native compaction recipe uses nine Electrum Dust");
        Arrays.fill(circuit,null);
        circuit[4]=new ItemStack(Material.REDSTONE_BLOCK);
        check(Bukkit.craftItemResult(circuit,world,player).getResult().equals(dust.asQuantity(9)),
                "The native block unpacks into nine Electrum Dust");
        circuit[4]=dust;
        circuit[7]=new ItemStack(Material.STICK);
        check(Bukkit.craftItemResult(circuit,world,player).getResult().getType()==Material.REDSTONE_TORCH,
                "The actual native torch recipe accepts Electrum Dust");
        circuit[4]=new ItemStack(Material.LAPIS_LAZULI);
        check(Bukkit.getCraftingRecipe(circuit,world)==null,"Ordinary lapis is not a circuit ingredient");
        var smith=MenuType.SMITHING.builder().checkReachable(false).build(player);
        smith.open();
        var smithing=(SmithingInventory)smith.getTopInventory();
        smithing.setInputTemplate(new ItemStack(Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE));
        smithing.setInputEquipment(mando.items().create("beskar_chestplate"));
        smithing.setInputMineral(dust);
        check(smithing.getResult()!=null && smithing.getResult().getItemMeta() instanceof ArmorMeta trimmed
                && trimmed.hasTrim() && trimmed.getTrim().getMaterial().equals(org.bukkit.inventory.meta.trim.TrimMaterial.REDSTONE),
                "Electrum Dust makes a gold Electrum trim instead of a blue lapis trim");
        player.closeInventory();
        var expectedName=plain(dust.getItemMeta().displayName());
        check(expectedName.equals("Electrum Dust"),"Mined ingredient is named Electrum Dust");
        var pickaxe=new ItemStack(Material.IRON_PICKAXE);
        pickaxe.addEnchantment(Enchantment.FORTUNE,3);
        player.getInventory().setItemInMainHand(pickaxe);
        var ore=world.getBlockAt(-202,200,-200);
        var recordedDrops=new ArrayList<org.bukkit.entity.Item>();
        var capture=new org.bukkit.event.Listener(){};
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.block.BlockDropItemEvent.class, capture,
                org.bukkit.event.EventPriority.MONITOR, (listener, event) -> {
                    if (event instanceof org.bukkit.event.block.BlockDropItemEvent drop && drop.getBlock().equals(ore)) {
                        recordedDrops.addAll(drop.getItems());
                    }
                }, this, true);
        try {
            for (var mineral : List.of(Material.REDSTONE_ORE,Material.DEEPSLATE_REDSTONE_ORE)) {
                ore.setType(mineral,false);
                recordedDrops.clear();
                check(player.breakBlock(ore),"Real Fortune III break of "+mineral);
                check(recordedDrops.size()==1 && recordedDrops.getFirst().getItemStack().equals(dust),
                        "Exactly one Electrum Dust from the native ore drop transaction for "+mineral);
                recordedDrops.forEach(org.bukkit.entity.Item::remove);
            }
        } finally { org.bukkit.event.HandlerList.unregisterAll(capture); }
    }

    private void nativeElectrumWire(ItemStack dust) throws Exception {
        var support=world.getBlockAt(-203,199,-202);
        var placed=support.getRelative(0,1,0);
        support.setType(Material.STONE,false);
        placed.setType(Material.AIR,false);
        var held=dust.asQuantity(3);
        player.getInventory().setItemInMainHand(held);
        useTop(support);
        check(placed.getType()==Material.REDSTONE_WIRE,"Electrum Dust uses native redstone placement");
        check(player.getInventory().getItemInMainHand().equals(dust.asQuantity(2)),"Placement consumes exactly one dust");

        var power=placed.getRelative(1,0,0);
        power.setType(Material.REDSTONE_BLOCK);
        check(((org.bukkit.block.data.type.RedstoneWire)placed.getBlockData()).getPower()==15,"Placed wire carries native power");
        check(player.breakBlock(placed),"Placed wire breaks natively");
        var drops=world.getNearbyEntities(placed.getLocation().add(.5,.5,.5),1,1,1).stream()
                .filter(org.bukkit.entity.Item.class::isInstance).map(org.bukkit.entity.Item.class::cast).toList();
        check(drops.size()==1&&drops.getFirst().getItemStack().equals(dust),
                "Placed wire returns the same Electrum Dust");
        drops.forEach(org.bukkit.entity.Item::remove);
        power.setType(Material.AIR);

        var rejected = new org.bukkit.event.Listener() { };
        Bukkit.getPluginManager().registerEvent(org.bukkit.event.block.BlockPlaceEvent.class, rejected,
                org.bukkit.event.EventPriority.LOW, (listener, e) -> {
                    if (e instanceof org.bukkit.event.block.BlockPlaceEvent place && place.getBlockPlaced().equals(placed)) {
                        place.setCancelled(true);
                    }
                }, this);
        try {
            useTop(support);
            check(placed.getType().isAir()&&player.getInventory().getItemInMainHand().equals(dust.asQuantity(2)),
                    "Native block-place cancellation prevents Electrum placement and consumption");
        } finally { org.bukkit.event.HandlerList.unregisterAll(rejected); }
    }

    private void useTop(org.bukkit.block.Block support) throws Exception {
        var nativePlayer=handle();
        var mode=nativePlayer.getClass().getField("gameMode").get(nativePlayer);
        var level=world.getClass().getMethod("getHandle").invoke(world);
        var vecType=Class.forName("net.minecraft.world.phys.Vec3");
        var posType=Class.forName("net.minecraft.core.BlockPos");
        var direction=Class.forName("net.minecraft.core.Direction");
        var hitType=Class.forName("net.minecraft.world.phys.BlockHitResult");
        var handType=Class.forName("net.minecraft.world.InteractionHand");
        var vec=vecType.getConstructor(double.class,double.class,double.class)
                .newInstance(support.getX()+.5,support.getY()+1.,support.getZ()+.5);
        var pos=posType.getConstructor(int.class,int.class,int.class)
                .newInstance(support.getX(),support.getY(),support.getZ());
        var hit=hitType.getConstructor(vecType,direction,posType,boolean.class)
                .newInstance(vec,direction.getField("UP").get(null),pos,false);
        var nativeItem=nativePlayer.getClass().getMethod("getMainHandItem").invoke(nativePlayer);
        mode.getClass().getMethod("useItemOn",Class.forName("net.minecraft.server.level.ServerPlayer"),
                Class.forName("net.minecraft.world.level.Level"),Class.forName("net.minecraft.world.item.ItemStack"),handType,hitType)
                .invoke(mode,nativePlayer,level,nativeItem,handType.getField("MAIN_HAND").get(null),hit);
    }

    private void table() {
        player.closeInventory(); var center=station.getLocation().add(0,0,5);center.getBlock().setType(Material.ENCHANTING_TABLE,false);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(Math.abs(x)==2||Math.abs(z)==2)center.clone().add(x,0,z).getBlock().setType(Material.BOOKSHELF,false);
        player.openEnchanting(center,true);
    }
    private void tableRolls() throws Exception {
        var view=(EnchantmentView)player.getOpenInventory();var seen=new HashSet<Enchantment>();
        view.getTopInventory().setItem(0,new ItemStack(Material.BOOK));
        view.getTopInventory().setItem(1,new ItemStack(Material.LAPIS_LAZULI,64));
        check(Arrays.stream(view.getOffers()).noneMatch(Objects::nonNull)&&!button(0),"Vanilla lapis never buys an enchantment");
        for(int seed=1;seed<=300;seed++) {
            player.setLevel(100);view.setEnchantmentSeed(seed);view.getTopInventory().setItem(0,new ItemStack(Material.BOOK));var dust=mando.items().create("electrum_dust");dust.setAmount(64);view.getTopInventory().setItem(1,dust);
            var offers=view.getOffers();int choice=offers[2]!=null?2:offers[1]!=null?1:0;check(offers[choice]!=null,"Native book offer");
            for(var offer:offers)if(offer!=null)check(mando.enchantments().tableAllowed(offer.getEnchantment()),"Only approved offers: "+offer.getEnchantment().getKey());
            check(button(choice),"Native table transaction");var result=view.getTopInventory().getItem(0);
            check(result.getItemMeta() instanceof EnchantmentStorageMeta,"Native enchanted book output");
            for(var enchant:((EnchantmentStorageMeta)result.getItemMeta()).getStoredEnchants().keySet()) {check(mando.enchantments().tableAllowed(enchant),"No hidden excluded enchants");seen.add(enchant);}
            check(player.getLevel()==100-choice-1&&view.getTopInventory().getItem(1).getAmount()==63-choice,"Native table charges XP and Electrum Dust");
        }
        check(seen.contains(endurance)&&seen.contains(recovery)&&seen.contains(Enchantment.SWIFT_SNEAK),"Custom and Swift Sneak books genuinely roll");
        view.getTopInventory().setItem(0,mando.items().create("vader"));check(Arrays.stream(view.getOffers()).anyMatch(Objects::nonNull),"Shield-based saber is enchantable");
        check(button(0),"Direct saber enchanting");check(!view.getTopInventory().getItem(0).getEnchantments().isEmpty(),"Native saber enchants applied");
        player.closeInventory();player.getInventory().clear();combine();
    }
    private void inputs(ItemStack left,ItemStack right) {var view=(AnvilView)player.getOpenInventory();view.getTopInventory().setItem(0,left);view.getTopInventory().setItem(1,right);}
    private void combine() {
        mando.forge().function(player,station,16);inputs(book(endurance,1),book(endurance,1));
        later(3,()->{
            var view=(AnvilView)player.getOpenInventory();check(stored(view.getTopInventory().getResult(),endurance)==2,"I plus I produces II");
            int cost=view.getRepairCost(),xp=player.getLevel();click(2,"PICKUP");
            check(stored(player.getItemOnCursor(),endurance)==2,"Real packet takes combined book");
            check(empty(view.getTopInventory().getItem(0))&&empty(view.getTopInventory().getItem(1)),"Combining consumes both books");
            check(player.getLevel()==xp-cost,"Native displayed XP cost consumed exactly");
            player.setItemOnCursor(null);inputs(book(recovery,2),book(recovery,2));
            later(3,()->{check(stored(view.getTopInventory().getResult(),recovery)==3,"II plus II produces III");apply();});
        });
    }
    private void apply() {
        var saber=mando.items().create("vader");saber.addUnsafeEnchantment(Enchantment.SHARPNESS,5);saber.addUnsafeEnchantment(endurance,3);inputs(saber,book(recovery,3));
        later(3,()->{
            var view=(AnvilView)player.getOpenInventory();var result=view.getTopInventory().getResult();
            check(result!=null&&result.getEnchantmentLevel(recovery)==3&&result.getEnchantmentLevel(endurance)==3&&result.getEnchantmentLevel(Enchantment.SHARPNESS)==5,"Native saber book application preserves all enchants");
            player.setLevel(0);click(2,"PICKUP");check(empty(player.getItemOnCursor())&&!empty(view.getTopInventory().getItem(1)),"Insufficient XP cannot take result");
            player.setLevel(100);click(2,"PICKUP");var taken=player.getItemOnCursor();check(taken.getEnchantmentLevel(recovery)==3,"Native pickup applies Recovery");
            check(empty(view.getTopInventory().getItem(0))&&empty(view.getTopInventory().getItem(1)),"Upgrade consumes one saber and book");
            player.setItemOnCursor(null);player.closeInventory();style(taken);
        });
    }
    private void style(ItemStack saber) {
        mando.forge().function(player,station,13);var view=(StonecutterView)player.getOpenInventory();
        saber.editMeta(meta->meta.displayName(Component.text("My Saber")));view.getTopInventory().setInputItem(saber);
        later(3,()->{
            int choice=-1;for(int i=0;i<view.getRecipes().size();i++)if(view.getRecipes().get(i).getKey().getKey().equals("refinish_darth_maul_apprentice_lightsaber"))choice=i;
            check(choice>=0,"Style available for red saber");check(button(choice),"Native style selection");
            later(2,()->{
                var result=view.getTopInventory().getResult();check(result!=null,"Native style result");
                check(result.getEnchantmentLevel(recovery)==3&&result.getEnchantmentLevel(endurance)==3&&result.getEnchantmentLevel(Enchantment.SHARPNESS)==5,"Styles preserve all paid enchantments");
                check(plain(result.getItemMeta().displayName()).equals("My Saber"),"Styles preserve renames");
                click(1,"PICKUP");check("darth_maul_apprentice_lightsaber".equals(mando.items().identify(player.getItemOnCursor())),"Native style pickup returns selected identity");
                check(empty(view.getTopInventory().getInputItem()),"Style consumes one input");player.setItemOnCursor(null);player.closeInventory();styleRefreshStart();
            });
        });
    }
    private Object clientRecipes;
    private Material clientInput=Material.AIR;
    private List<String> clientChoices=List.of();
    private List<String> recipeIds(Object options) throws Exception {
        var out=new ArrayList<String>();
        for(var entry:(List<?>)options.getClass().getMethod("entries").invoke(options)) {
            var recipe=entry.getClass().getMethod("recipe").invoke(entry);
            var holder=(Optional<?>)recipe.getClass().getMethod("recipe").invoke(recipe);
            if(holder.isPresent()) {
                var key=holder.get().getClass().getMethod("id").invoke(holder.get());
                out.add(key.getClass().getMethod("identifier").invoke(key).toString());
            }
        }
        return out;
    }
    private void clientSlot(Object stack) throws Exception {
        var nativeItem=Class.forName("net.minecraft.world.item.ItemStack");
        var bukkit=(ItemStack)Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack").getMethod("asBukkitCopy",nativeItem).invoke(null,stack);
        // Pinned 26.1.2 client StonecutterMenu.slotsChanged caches by material, not components.
        if(clientInput==bukkit.getType())return;
        clientInput=bukkit.getType();
        clientChoices=recipeIds(clientRecipes.getClass().getMethod("selectByInput",nativeItem).invoke(clientRecipes,stack));
    }
    @SuppressWarnings("unchecked") private void replayStylePackets() throws Exception {
        var f=NativeProbePlayers.class.getDeclaredField("channels");f.setAccessible(true);
        var channel=((Map<UUID,io.netty.channel.embedded.EmbeddedChannel>)f.get(actors)).get(player.getUniqueId());channel.runPendingTasks();
        int id=menu().getClass().getField("containerId").getInt(menu());Object packet;
        while((packet=channel.readOutbound())!=null) {
            var type=packet.getClass();
            switch(type.getSimpleName()) {
                case "ClientboundUpdateRecipesPacket" -> clientRecipes=type.getMethod("stonecutterRecipes").invoke(packet);
                case "ClientboundContainerSetSlotPacket" -> {
                    if((int)type.getMethod("getContainerId").invoke(packet)==id&&(int)type.getMethod("getSlot").invoke(packet)==0)
                        clientSlot(type.getMethod("getItem").invoke(packet));
                }
                case "ClientboundContainerSetContentPacket" -> {
                    if((int)type.getMethod("containerId").invoke(packet)==id)clientSlot(((List<?>)type.getMethod("items").invoke(packet)).getFirst());
                }
            }
        }
    }
    private void styleRefreshStart() throws Exception {
        actors.drain(player);
        var server=Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        var manager=server.getClass().getMethod("getRecipeManager").invoke(server);
        clientRecipes=manager.getClass().getMethod("stonecutterRecipes").invoke(manager);
        mando.forge().function(player,station,13);
        later(2,()->{replayStylePackets();check(clientChoices.isEmpty(),"Empty Style screen has no options");styleRefresh(0);});
    }
    private void styleRefresh(int index) throws Exception {
        String[] ids={"windu","vader","quigon","windu"};
        var view=(StonecutterView)player.getOpenInventory();
        var input=mando.items().create(ids[index]);input.addUnsafeEnchantment(recovery,3);
        input.editMeta(meta->meta.displayName(Component.text("Keep my name")));
        view.getTopInventory().setInputItem(input);
        later(3,()->{
            replayStylePackets();var expected=view.getRecipes().stream().map(r->r.getKey().toString()).toList();
            check(!expected.isEmpty(),"Native style options exist for "+ids[index]);
            check(clientChoices.equals(expected),"Client refreshes matching ordered styles for "+ids[index]+": "+clientChoices);
            check(view.getTopInventory().getInputItem().equals(input),"Client refresh preserves authoritative input");
            if(ids[index].equals("windu")) {
                check(new HashSet<>(expected).equals(Set.of("mandomc:refinish_revan_redeemed_lightsaber","mandomc:refinish_vernestra_rwoh_lightsaber")),"Both purple Mace styles are available");
                maceChoice(view,expected,0,()->styleRefreshNext(index,ids.length));return;
            }
            styleRefreshNext(index,ids.length);
        });
    }
    private void maceChoice(StonecutterView view,List<String> expected,int choice,Work next) throws Exception {
        check(button(choice),"Mace style selection "+choice);
        later(2,()->{
            var result=view.getTopInventory().getResult();
            check(result!=null&&expected.get(choice).equals("mandomc:refinish_"+mando.items().identify(result)),"Mace preview agrees with client index "+choice);
            check(result.getEnchantmentLevel(recovery)==3&&plain(result.getItemMeta().displayName()).equals("Keep my name"),"Mace preview preserves enchant and rename");
            if(choice+1<expected.size())maceChoice(view,expected,choice+1,next);else next.run();
        });
    }
    private void styleRefreshNext(int index,int total) throws Exception {
        if(index+1<total){styleRefresh(index+1);return;}
        var view=(StonecutterView)player.getOpenInventory();var expected=view.getTopInventory().getResult().clone();
        click(1,"PICKUP");check(expected.equals(player.getItemOnCursor()),"Mace native pickup returns the chosen purple style with all metadata");
        check(empty(view.getTopInventory().getInputItem()),"Mace style pickup consumes exactly one input");
        player.setItemOnCursor(null);player.closeInventory();recipes();
    }
    private void recipes() {
        for(String id:List.of("vader","darth_maul_apprentice_lightsaber")) {
            check(Bukkit.dispatchCommand(player,"recipes "+id),"Recipe command");var icon=player.getOpenInventory().getTopInventory().getItem(23);
            check(icon.getType()==Material.SMITHING_TABLE&&plain(icon.getItemMeta().displayName()).contains("Saber Workbench"),"Recipe station shows workbench for "+id);player.closeInventory();
        }
        mando.forge().function(player,station,16);inputs(mando.items().create("vader"),book(recovery,1));
        later(3,()->{
            station.setType(Material.AIR,false);click(2,"PICKUP");check(empty(player.getItemOnCursor()),"Removed workbench denies pickup");combat();
        });
    }
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if(event.getDamager().equals(player)&&mando.sabers().dealingThrowDamage(player))throwHits.add(event.getDamage());
    }
    private void combat() {
        player.closeInventory();player.getInventory().clear();player.setSneaking(false);player.setRotation(90,0);
        // Earlier retained fixture probes leave display blocks in this arena; clear the flight corridor.
        for(int x=-222;x<=-194;x++)for(int y=199;y<=207;y++)for(int z=-210;z<=-189;z++)world.getBlockAt(x,y,z).setType(Material.AIR,false);
        var saber=mando.items().create("vader");saber.addUnsafeEnchantment(Enchantment.SHARPNESS,5);
        saber.addUnsafeEnchantment(endurance,3);saber.addUnsafeEnchantment(recovery,3);
        player.getInventory().setItemInMainHand(saber);Bukkit.getPluginManager().registerEvents(this,this);
        later(25,()->{
            // Socket-free actors do not receive normal client equipment/cooldown ticks.
            handle().getClass().getMethod("detectEquipmentUpdates").invoke(handle());
            var strength=Class.forName("net.minecraft.world.entity.LivingEntity").getDeclaredField("attackStrengthTicker");strength.setAccessible(true);strength.setInt(handle(),100);
            check(Math.abs(player.getAttribute(Attribute.ATTACK_DAMAGE).getValue()-8)<1e-8,"Equipped native base damage");
            check(Math.abs(mando.enchantments().consumption(saber)-.8)<1e-8,"Endurance III effect bound to actual enchant");
            check(Math.abs(mando.enchantments().regeneration(saber)-1.75)<1e-8,"Recovery III effect bound to actual enchant");
            var mob=world.spawn(player.getLocation().add(-2,0,0),Husk.class);victim=mob;mob.setAI(false);mob.setGravity(false);
            mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);mob.getAttribute(Attribute.ARMOR).setBaseValue(0);mob.setHealth(200);player.setFallDistance(0);player.attack(mob);
            check(Math.abs(mob.getHealth()-189)<1e-8,"Native fully charged Sharpness V melee deals eleven; health="+mob.getHealth());
            mob.remove();mob=world.spawn(player.getLocation().add(-6,0,0),Husk.class);victim=mob;mob.setAI(false);mob.setGravity(false);
            mob.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);mob.getAttribute(Attribute.ARMOR).setBaseValue(0);mob.setHealth(200);
            check(mando.sabers().launch(player),"Native enchanted saber launches");
            later(1,()->{
                var mapField=mando.sabers().getClass().getDeclaredField("flights");mapField.setAccessible(true);var flights=(Map<?,?>)mapField.get(mando.sabers());var flight=flights.get(player.getUniqueId());
                getLogger().info("FLIGHT DEBUG busy="+mando.sabers().busy(player)+" online="+player.isOnline()+" alive="+!player.isDead()+" hand="+player.getInventory().getItemInMainHand().getType()+" mob="+victim.getLocation()+" aim="+player.getEyeLocation().getDirection());
                if(flight!=null)for(String key:List.of("age","target","location","damage")){var f=flight.getClass().getDeclaredField(key);f.setAccessible(true);getLogger().info("FLIGHT "+key+"="+f.get(flight));}
            });
            later(80,()->{
                check(!throwHits.isEmpty(),"Actual throw contacts damage the target");
                for(double damage:throwHits)check(Math.abs(damage-15)<1e-8,"Each throw contact equals full melee critical damage");
                mando.sabers().finish(player.getUniqueId());
                var returned=player.getInventory().getItemInMainHand();check(returned.getEnchantmentLevel(Enchantment.SHARPNESS)==5&&returned.getEnchantmentLevel(endurance)==3&&returned.getEnchantmentLevel(recovery)==3,"Throw return preserves all enchants");
                finish(null);
            });
        });
    }
    private void finish(Throwable error) {
        try { if(victim!=null)victim.remove();if(player!=null)player.closeInventory();if(actors!=null)actors.close();Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve(error==null?"PASSED.txt":"FAILED.txt"),error==null?checks+" native saber assertions passed\n":error.toString());
            if(error==null)getLogger().info("SABER ENCHANTMENTS PASSED: "+checks);else getLogger().log(java.util.logging.Level.SEVERE,"SABER ENCHANTMENTS FAILED",error);
        } catch(Exception failure){getLogger().log(java.util.logging.Level.SEVERE,"Probe cleanup failed",failure);}Bukkit.shutdown();
    }
}
