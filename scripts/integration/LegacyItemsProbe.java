package net.mandomc.verification;

import java.lang.reflect.Proxy;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import io.papermc.paper.datacomponent.DataComponentTypes;

/** Isolated Paper verification; never reads or edits real player inventories. */
public final class LegacyItemsProbe extends JavaPlugin {
    private int checks;
    private MandoPlugin mando;
    private TestPlayer viewer;
    private RecipeGuide guide;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> step(this::verify), 80);
    }
    private void verify() throws Exception {
        mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(mando.isEnabled(), "MandoMC enabled with complete recovered catalog");
        var items = mando.items();
        for (String id : items.ids()) {
            ItemStack item = items.create(id);
            check(item != null && !item.isEmpty(), "catalog factory " + id);
        }
        for (String family : List.of("plastoid", "durasteel", "beskar")) {
            for (String slot : List.of("helmet", "chestplate", "leggings", "boots")) {
                String id = family + "_" + slot;
                ItemStack item = items.create(id), vanilla = new ItemStack(item.getType());
                var component = item.getData(DataComponentTypes.EQUIPPABLE);
                var original = vanilla.getData(DataComponentTypes.EQUIPPABLE);
                check(items.identify(item).equals(id), "stable armor identity " + id);
                check(component.slot() == original.slot(), "native equipment slot " + id);
                check(component.swappable() && component.damageOnHurt(), "native swapping and wear " + id);
                check(component.equipSound().equals(original.equipSound()), "native equip sound " + id);
                check(slot.equals("helmet") ? component.assetId() == null : component.assetId().equals(NamespacedKey.fromString("mandomc:" + family)), "3D head or worn equipment asset " + id);
                var saved = ItemStack.deserializeBytes(item.serializeAsBytes());
                check(items.identify(saved).equals(id) && Objects.equals(saved.getData(DataComponentTypes.EQUIPPABLE).assetId(),component.assetId()), "serialized helmet/equipment " + id);
                check(item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS).equals(vanilla.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS)), "native armor attributes " + id);
            }
        }
        var field = MandoPlugin.class.getDeclaredField("craftingRecipes"); field.setAccessible(true);
        var recipes = (CraftingRecipes) field.get(mando);
        check(recipes.definitions().size() == 62, "51 crafting and 11 refining recipes registered");
        for (var recipe : recipes.definitions()) {
            var key = new NamespacedKey("mandomc", (recipe.smelting() ? "smelt_" : "craft_") + recipe.id());
            var registered = Bukkit.getRecipe(key);
            check(registered != null && items.identify(registered.getResult()).equals(recipe.result()), "registered output " + recipe.id());
            if (recipe.smelting()) {
                var blast = (BlastingRecipe) registered;
                var input = recipes.ingredient(recipe.ingredients().get('I'));
                check(blast.getInputChoice().test(input) && !blast.getInputChoice().test(new ItemStack(input.getType())), "blast requires exact custom input " + recipe.id());
                check(blast.getCookingTime() == recipe.cookTime() && blast.getExperience() == recipe.experience(), "legacy refining timing/xp " + recipe.id());
            } else {
                ItemStack[] matrix = new ItemStack[9];
                for (int y = 0; y < recipe.shape().size(); y++) for (int x = 0; x < recipe.shape().get(y).length(); x++) {
                    char symbol = recipe.shape().get(y).charAt(x);
                    if (symbol != ' ') matrix[y * 3 + x] = recipes.ingredient(recipe.ingredients().get(symbol));
                }
                var actual = Bukkit.getCraftingRecipe(matrix, Bukkit.getWorlds().getFirst());
                check(actual != null && items.identify(actual.getResult()).equals(recipe.result()), "real native crafting match " + recipe.id());
                for (int i = 0; i < matrix.length; i++) if (matrix[i] != null && !recipe.ingredients().values().stream().anyMatch(v -> v.startsWith("minecraft:"))) matrix[i] = new ItemStack(matrix[i].getType());
                var fake = Bukkit.getCraftingRecipe(matrix, Bukkit.getWorlds().getFirst());
                if (recipe.ingredients().values().stream().noneMatch(v -> v.startsWith("minecraft:"))) check(fake == null || !recipe.result().equals(items.identify(fake.getResult())), "plain materials cannot counterfeit " + recipe.id());
            }
        }
        // Public rule boundary validates every recovered saber, including an ordinary shield denial.
        var serviceField = Arrays.stream(MandoPlugin.class.getDeclaredFields()).filter(f -> f.getType().getSimpleName().equals("SaberService")).findFirst().orElseThrow();serviceField.setAccessible(true);
        var service = (net.mandomc.gameplay.saber.SaberService) serviceField.get(mando);
        for (String id : List.of("vader", "obiwan", "cal_survivor_crossguard", "windu", "quigon", "ventress_yellow")) check(service.isSaber(items.create(id)), "shared saber behavior " + id);
        check(!service.isSaber(new ItemStack(Material.SHIELD)), "ordinary shield is not a saber");
        var guideField = MandoPlugin.class.getDeclaredField("recipeGuide");guideField.setAccessible(true);guide=(RecipeGuide)guideField.get(mando);
        viewer = new TestPlayer("LegacyGuideProbe");
        Bukkit.getPluginCommand("recipes").execute(viewer.player, "recipes", new String[0]);
        check(viewer.view.getTopInventory().getItem(10).getType()==Material.KNOWLEDGE_BOOK,"recipes opens category cards");
        click(10);
        Bukkit.getScheduler().runTaskLater(this, () -> step(this::verifyPage), 2);
    }
    private void verifyPage() {
        check(viewer.view.getTopInventory().getItem(50).getType()==Material.ARROW,"all recipes paginate");
        click(50);
        Bukkit.getScheduler().runTaskLater(this, () -> step(this::verifySecondPage), 2);
    }
    private void verifySecondPage() {
        check(viewer.view.getTopInventory().getItem(48).getType()==Material.ARROW,"second page supports return");
        Bukkit.getPluginCommand("recipes").execute(viewer.player,"recipes",new String[]{"beskar_sheet"});
        check(viewer.view.getTopInventory().getItem(23).getType()==Material.BLAST_FURNACE,"refining shows correct workstation");
        check(viewer.view.getTopInventory().getItem(32).getType()==Material.CLOCK,"refining shows duration");
        check(viewer.view.getTopInventory().getItem(11)==null,"unused refining grid is genuinely empty");
        click(10);
        Bukkit.getScheduler().runTaskLater(this, () -> step(this::verifyIngredient), 2);
    }
    private void verifyIngredient() {
        check(mando.items().identify(viewer.view.getTopInventory().getItem(25)).equals("beskar"),"ingredient click follows ingot recipe");
        check(mando.items().identify(viewer.view.getTopInventory().getItem(10)).equals("beskar_scrap"),"scrap chain displays required inputs");
        for (ClickType type : List.of(ClickType.SHIFT_LEFT,ClickType.NUMBER_KEY,ClickType.DOUBLE_CLICK,ClickType.DROP)) {
            var event = new InventoryClickEvent(viewer.view,InventoryType.SlotType.CONTAINER,25,type,InventoryAction.PICKUP_ALL);
            guide.click(event);check(event.isCancelled(),"preview transfer cancelled " + type);
        }
        var drag = new InventoryDragEvent(viewer.view,new ItemStack(Material.STONE),new ItemStack(Material.STONE),false,Map.of(10,new ItemStack(Material.STONE)));guide.drag(drag);check(drag.isCancelled(),"preview drag cancelled");
        getLogger().info("LEGACY ITEMS PROBE PASSED " + checks + " assertions");Bukkit.shutdown();
    }
    private void click(int slot) {var event = new InventoryClickEvent(viewer.view,InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);guide.click(event);check(event.isCancelled(),"menu click cannot take item");}
    private interface Checked { void run() throws Exception; }
    private void step(Checked action) { try {action.run();} catch(Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"LEGACY ITEMS PROBE FAILED",error);Bukkit.shutdown();} }
    private void check(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);checks++;getLogger().info("PASS " + message);}

    private static final class TestPlayer {
        private final UUID id = UUID.randomUUID();
        private final Inventory storage = Bukkit.createInventory(null, 36);
        private boolean allowed = true;
        private boolean online = true;
        private InventoryView view;
        private final Player player;
        private TestPlayer(String name) {
            var inventory = (PlayerInventory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {PlayerInventory.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getStorageContents")) return storage.getContents();
                        try { return Inventory.class.getMethod(method.getName(), method.getParameterTypes()).invoke(storage, args); }
                        catch (NoSuchMethodException ignored) { return fallback(method.getReturnType()); }
                    });
            player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "isOnline" -> online;
                    case "hasPermission" -> allowed;
                    case "getInventory" -> inventory;
                    case "getOpenInventory" -> view;
                    case "openInventory" -> { view = makeView((Inventory) args[0]); yield view; }
                    case "closeInventory" -> { view = makeView(Bukkit.createInventory(null, 9)); yield null; }
                    case "toString" -> name;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> fallback(method.getReturnType());
                };
            });
            view = makeView(Bukkit.createInventory(null, 9));
        }
        private InventoryView makeView(Inventory top) {
            return (InventoryView) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {InventoryView.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getTopInventory" -> top;
                        case "getBottomInventory" -> storage;
                        case "getPlayer" -> player;
                        case "getType" -> InventoryType.CHEST;
                        case "convertSlot" -> (int) args[0] < 54 ? args[0] : (int) args[0] - 54;
                        case "getInventory" -> (int) args[0] < 54 ? top : storage;
                        case "getItem" -> (int) args[0] < 54 ? top.getItem((int) args[0]) : storage.getItem((int) args[0] - 54);
                        default -> fallback(method.getReturnType());
                    });
        }
        private static Object fallback(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == double.class) return 0d;
            if (type == float.class) return 0f;
            if (type == long.class) return 0L;
            return null;
        }
    }
}
