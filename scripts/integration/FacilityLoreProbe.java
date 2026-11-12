package net.mandomc.verification;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable runtime only; no real player's items are edited by this probe. */
public final class FacilityLoreProbe extends JavaPlugin {
    private int checks;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); getLogger().info("FACILITY LORE PASSED: " + checks + " assertions"); }
            catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "FACILITY LORE FAILED", error); }
            finally { Bukkit.shutdown(); }
        }, 80);
    }
    private void verify() throws Exception {
        var mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        var items = mando.items();
        var guideField = MandoPlugin.class.getDeclaredField("recipeGuide"); guideField.setAccessible(true);
        Object guide = guideField.get(mando);
        var previewMethod = guide.getClass().getDeclaredMethod("obtainPreview", ItemStack.class); previewMethod.setAccessible(true);
        for (String id : List.of("basic_power_cell", "durable_mid_grade_power_core", "batteries", "switch")) {
            var fresh = items.create(id); var lore = fresh.getItemMeta().lore();
            check(lore != null && lore.size() >= 3 && plain(lore.get(1)).isBlank(), "standard badge spacing retained " + id);
            check(!text(lore).toLowerCase(Locale.ROOT).contains("facility") && !text(lore).toLowerCase(Locale.ROOT).contains("tatooine"), "new catalog item has no obsolete attribution " + id);
            check(!plain(lore.getLast()).isBlank(), "no trailing empty lore " + id);
            var preview = (ItemStack) previewMethod.invoke(guide, fresh);
            check(!text(preview.getItemMeta().lore()).toLowerCase(Locale.ROOT).contains("facility"), "actual recipe preview has no obsolete attribution " + id);
            var old = old(items, id); old.setAmount(7);
            var meta = old.getItemMeta(); meta.displayName(Component.text("Player-named battery"));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 2, true);
            meta.getPersistentDataContainer().set(new NamespacedKey("thirdparty", "keep"), PersistentDataType.STRING, "custom payload"); old.setItemMeta(meta);
            var expected = old.clone(); var em = expected.getItemMeta();
            var expectedLore = new ArrayList<>(lore); expectedLore.add(Component.empty()); expectedLore.add(Component.text("Player's note")); expectedLore.add(Component.text("Tatooine"));
            em.lore(expectedLore); expected.setItemMeta(em);
            check(ItemPresentationRefresh.refresh(items, old), "old owned item refreshed " + id);
            check(old.equals(expected), "all names/enchantments/PDC/count/model/custom lore preserved " + id);
            check(!ItemPresentationRefresh.refresh(items, old), "safe refresh is idempotent " + id);
            var foreign = old(items, id); var fm = foreign.getItemMeta(); fm.getPersistentDataContainer().remove(ItemCatalog.ITEM_ID); foreign.setItemMeta(fm); var copy = foreign.clone();
            check(!ItemPresentationRefresh.refresh(items, foreign) && foreign.equals(copy), "third-party lookalike untouched " + id);
            var wrongType = old(items, id); wrongType.setType(Material.PAPER); var wrongCopy = wrongType.clone();
            check(!ItemPresentationRefresh.refresh(items, wrongType) && wrongType.equals(wrongCopy), "mismatched identity/material untouched " + id);
        }
        var world = Bukkit.getWorlds().getFirst();
        var actor = nativeTestPlayer(world, new Location(world, 1000, 280, 1000));
        actor.getInventory().setItem(0, old(items, "batteries"));
        Bukkit.getPluginManager().callEvent(new InventoryOpenEvent(actor.getOpenInventory()));
        check(!text(actor.getInventory().getItem(0).getItemMeta().lore()).contains("facility"), "inventory-open event refreshes held old item");
        actor.getInventory().setItem(0, old(items, "switch"));
        var joinRefresh = Arrays.stream(org.bukkit.event.player.PlayerJoinEvent.getHandlerList().getRegisteredListeners())
                .map(org.bukkit.plugin.RegisteredListener::getListener).filter(ItemPresentationRefresh.class::isInstance)
                .map(ItemPresentationRefresh.class::cast).findFirst().orElseThrow();
        joinRefresh.join(new org.bukkit.event.player.PlayerJoinEvent(actor, (Component) null));
        check(!text(actor.getInventory().getItem(0).getItemMeta().lore()).contains("facility"), "registered join handler refreshes held old item");
        var entity = world.dropItem(new Location(world, 1000, 280, 1000), old(items, "basic_power_cell"));
        try {
            var cancelled = new EntityPickupItemEvent(actor, entity, 0); cancelled.setCancelled(true);
            Bukkit.getPluginManager().callEvent(cancelled);
            check(text(entity.getItemStack().getItemMeta().lore()).contains("facility"), "cancelled pickup stays untouched");
            Bukkit.getPluginManager().callEvent(new EntityPickupItemEvent(actor, entity, 0));
            check(!text(entity.getItemStack().getItemMeta().lore()).contains("facility"), "allowed pickup refreshes old dropped item");
        } finally { entity.remove(); }
    }
    private ItemStack old(ItemCatalog items, String id) {
        var stack = items.create(id); var meta = stack.getItemMeta(); var lore = new ArrayList<>(meta.lore());
        lore.add(Component.empty()); lore.add(MiniMessage.miniMessage().deserialize("<gold><italic>Extracted from a secured facility on"));
        lore.add(MiniMessage.miniMessage().deserialize("<gold><bold>Tatooine")); lore.add(Component.empty());
        lore.add(Component.text("Player's note")); lore.add(Component.text("Tatooine")); meta.lore(lore); stack.setItemMeta(meta); return stack;
    }
    private String plain(Component line) { return PlainTextComponentSerializer.plainText().serialize(line); }
    private String text(List<Component> lore) { return String.join(" ", lore.stream().map(this::plain).toList()); }
    private void check(boolean value, String message) { if (!value) throw new IllegalStateException(message); checks++; getLogger().info("PASS " + message); }
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
