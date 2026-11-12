package net.mandomc.verification;

import java.util.*;
import java.util.function.Consumer;
import java.lang.reflect.Proxy;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class RecipeAcquisitionProbe extends JavaPlugin {
    private int checks;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); getLogger().info("RECIPE ACQUISITION PASSED: " + checks + " assertions"); }
            catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "RECIPE ACQUISITION FAILED", error); }
            finally { Bukkit.shutdown(); }
        }, 100);
    }
    private void check(boolean value, String reason) {
        if (!value) throw new IllegalStateException(reason);
        checks++;
    }
    private Object field(Object owner, String key) throws Exception {
        var field = owner.getClass().getDeclaredField(key);
        field.setAccessible(true);
        return field.get(owner);
    }
    private String plain(Component c) { return PlainTextComponentSerializer.plainText().serialize(c); }
    private void verify() throws Exception {
        var mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        var recipes = (CraftingRecipes) field(mando, "craftingRecipes");
        var guide = (RecipeGuide) field(mando, "recipeGuide");
        var config = YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(), "items/recipes.yml"));
        var preview = RecipeGuide.class.getDeclaredMethod("obtainPreview", ItemStack.class, String.class);
        preview.setAccessible(true);
        var inputs = new TreeSet<String>();
        for (var recipe : recipes.guideDefinitions()) inputs.addAll(recipe.ingredients().values());
        check(inputs.size() == 106, "exact audited visible ingredient set");
        for (String id : inputs) {
            String note = config.getString("sources." + id);
            check(note != null && !note.isBlank(), "source present " + id);
            var original = recipes.ingredient(id);
            var before = original.clone();
            var shown = (ItemStack) preview.invoke(guide, original, id);
            check(original.equals(before), "template unchanged " + id);
            int oldLore = before.getItemMeta().lore() == null ? 0 : before.getItemMeta().lore().size();
            var lore = shown.getItemMeta().lore();
            var lines = note.split("\\R");
            check(lore.size() == oldLore + lines.length + 3, "separate brief source lines " + id);
            for (int i = 0; i < lines.length; i++) {
                var line = lore.get(oldLore + 1 + i);
                check(plain(line).equals(plain(MiniMessage.miniMessage().deserialize(lines[i]))), "source text " + id);
                check(line.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE, "nonitalic source " + id);
            }
        }
        var world = Bukkit.getWorlds().getFirst();
        var actor = nativeTestPlayer(world, world.getSpawnLocation());
        var detail = RecipeGuide.class.getDeclaredMethod("detail", Player.class, RecipeDefinition.class, Consumer.class);
        detail.setAccessible(true);
        var lookup = RecipeGuide.class.getDeclaredMethod("recipesFor", String.class);
        lookup.setAccessible(true);
        int navigations = 0, sourceClicks = 0;
        var seen = new HashSet<String>();
        for (var recipe : recipes.guideDefinitions()) {
            detail.invoke(guide, actor, recipe, (Consumer<Player>) p -> {});
            var inventory = actor.getOpenInventory().getTopInventory();
            check(inventory.getItem(25).equals(recipes.result(recipe)), "result remains pristine " + recipe.id());
            var actions = (Map<Integer, Consumer<Player>>) field(inventory.getHolder(), "actions");
            for (int y = 0; y < recipe.shape().size(); y++) for (int x = 0; x < recipe.shape().get(y).length(); x++) {
                String id = recipe.ingredients().get(recipe.shape().get(y).charAt(x));
                if (id == null || !seen.add(id)) continue;
                int slot = 10 + (y + recipe.guideRowOffset()) * 9 + x;
                check(inventory.getItem(slot).getItemMeta().lore().stream().anyMatch(c -> plain(c).equals(
                        plain(MiniMessage.miniMessage().deserialize(config.getString("sources." + id).split("\\R")[0])))),
                        "actual detail ingredient shows source " + id);
                var matches = (List<RecipeDefinition>) lookup.invoke(guide, id);
                if (!matches.isEmpty()) {
                    actions.get(slot).accept(actor);
                    var next = actor.getOpenInventory().getTopInventory();
                    check(next.getItem(25).equals(recipes.result(matches.getFirst())), "recipe click-through preserved " + id);
                    var nextActions = (Map<Integer, Consumer<Player>>) field(next.getHolder(), "actions");
                    nextActions.get(45).accept(actor);
                    check(actor.getOpenInventory().getTopInventory().getItem(25).equals(recipes.result(recipe)), "back navigation preserved " + id);
                    navigations++;
                } else {
                    var sent = new ArrayList<Component>();
                    var receiver = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                            (p, method, args) -> { if (method.getName().equals("sendMessage") && args[0] instanceof Component c) sent.add(c); return null; });
                    actions.get(slot).accept(receiver);
                    check(sent.size() == 1 && plain(sent.getFirst()).equals(plain(MiniMessage.miniMessage().deserialize(
                            config.getString("sources." + id)))), "source-only click gives audited context " + id);
                    sourceClicks++;
                }
            }
        }
        check(seen.equals(inputs), "all visible ingredients exercised in actual detail views");
        check(navigations > 0 && sourceClicks > 0, "both crafting and source-only navigation paths covered");
        check(recipes.guideDefinitions().stream().filter(r -> r.id().startsWith("bantha_")).count() == 20, "hide utility entries retained");
        actor.closeInventory();
    }
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
