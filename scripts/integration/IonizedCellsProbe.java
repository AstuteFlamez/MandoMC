package net.mandomc.verification;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.*;
import net.mandomc.gameplay.saber.*;
import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable runtime only: verifies native crafting and genuine vendor ammunition identities. */
public final class IonizedCellsProbe extends JavaPlugin {
    private int checks;
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); getLogger().info("IONIZED CELLS PASSED: " + checks + " assertions"); }
            catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "IONIZED CELLS FAILED", error); }
            finally { Bukkit.shutdown(); }
        }, 80);
    }
    private void verify() throws Exception {
        var mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(mando != null && mando.isEnabled(), "current combined plugin enabled");
        var field = MandoPlugin.class.getDeclaredField("craftingRecipes"); field.setAccessible(true);
        var recipes = (CraftingRecipes) field.get(mando);
        var world = Bukkit.getWorlds().getFirst();
        var actor = nativeTestPlayer(world, new Location(world, 1000, 280, 1000));
        actor.setGameMode(GameMode.SURVIVAL);
        actor.setOp(true);
        for (String id : List.of("ionized_energy_cells")) {
            var definition = recipes.guideDefinitions().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
            check(Bukkit.getRecipe(new NamespacedKey("mandomc", "craft_" + id)) != null, "recipe registered and guide-visible " + id);
            ItemStack[] matrix = new ItemStack[9];
            for (int z = 0; z < definition.shape().size(); z++) {
                String row = definition.shape().get(z);
                for (int x = 0; x < row.length(); x++) if (row.charAt(x) != ' ')
                    matrix[z * 3 + x] = recipes.ingredient(definition.ingredients().get(row.charAt(x)));
            }
            var output = Bukkit.craftItemResult(matrix, world, actor).getResult();
            check(!output.isEmpty() && output.getAmount() == definition.amount(), "native output quantity " + id);
            check(output.isSimilar(mando.items().create(id)), "exact catalog/vendor result " + id);
            if (id.equals("e11")) check("E11".equals(WeaponMechanicsAPI.getWeaponTitle(output)), "crafted E11 remains vendor weapon");
            if (id.equals("standard_plasma_cells")) check(output.isSimilar(WeaponMechanicsAPI.generateAmmo("Standard_Plasma_Cells", false)), "crafted cells are actual reload ammo");
            ItemStack[] mirrored = new ItemStack[9];
            for (int z = 0; z < 3; z++) for (int x = 0; x < 3; x++) mirrored[z * 3 + x] = matrix[z * 3 + 2 - x];
            check(Bukkit.craftItemResult(mirrored, world, actor).getResult().isSimilar(output), "native mirrored recipe " + id);
            for (int slot = 0; slot < 9; slot++) if (matrix[slot] != null) {
                ItemStack[] missing = matrix.clone(); missing[slot] = null;
                check(Bukkit.craftItemResult(missing, world, actor).getResult().isEmpty(), "missing ingredient rejected " + id + " slot " + slot);
                String ingredient = definition.ingredients().get(definition.shape().get(slot / 3).charAt(slot % 3));
                if (!ingredient.startsWith("minecraft:")) {
                    ItemStack[] fake = matrix.clone(); fake[slot] = new ItemStack(matrix[slot].getType());
                    check(Bukkit.craftItemResult(fake, world, actor).getResult().isEmpty(), "plain lookalike rejected " + id + " slot " + slot);
                }
            }
        }
        var weapon = WeaponMechanicsAPI.generateWeapon("Jawa_Ionization_Blaster");
        check("Ionized_Energy_Cells".equals(WeaponMechanicsAPI.getCurrentAmmo(weapon).getAmmoTitle()), "original vendor ammo identity retained");
        var wm = me.deecaad.weaponmechanics.WeaponMechanics.getInstance();
        var reload = wm.getWeaponHandler().getReloadHandler();
        check(reload.getAmmoLeft(weapon, "Jawa_Ionization_Blaster") == 18, "18-round magazine unchanged");
        check(reload.consumeAmmo(weapon, "Jawa_Ionization_Blaster", 18), "native magazine consumed");
        actor.getInventory().clear(); actor.getInventory().setItemInMainHand(weapon);
        var wrapper = wm.getPlayerWrapper(actor);
        messages.clear();
        check(!reload.startReloadWithoutTrigger(wrapper, "Jawa_Ionization_Blaster", weapon, EquipmentSlot.HAND, false, false), "empty inventory cannot reload");
        check(messages.stream().anyMatch(m -> m.contains("Out of ionized cells!")), "native empty-ammo path delivers exact ionized cells chat: " + messages);
        check(messages.stream().noneMatch(m -> m.contains("Out of energy cells!")), "old generic energy-cells wording absent");
        messages.clear();
        wm.getWeaponHandler().getShootHandler().shootWithoutTrigger(wrapper, "Jawa_Ionization_Blaster", weapon,
                EquipmentSlot.HAND, me.deecaad.weaponmechanics.weapon.trigger.TriggerType.RIGHT_CLICK, false);
        check(messages.stream().anyMatch(m -> m.contains("Out of ionized cells!")), "actual dry-fire sends ionized cells message: " + messages);
        var ammoConfig = wm.getWeaponConfigurations().getObject("Jawa_Ionization_Blaster.Reload.Ammo", me.deecaad.weaponmechanics.weapon.reload.ammo.AmmoConfig.class);
        actor.getInventory().addItem(mando.items().create("ionized_energy_cells").asQuantity(32));
        check(ammoConfig.hasAmmo("Jawa_Ionization_Blaster", weapon, wrapper), "crafted-identity cells accepted by actual ammo handler");
        check(ammoConfig.removeAmmo(weapon, wrapper, 18, 18) == 18, "native ammo handler consumes 18 crafted cells");
        check(actor.getInventory().all(Material.ECHO_SHARD).values().stream().filter(i -> i.isSimilar(mando.items().create("ionized_energy_cells"))).mapToInt(ItemStack::getAmount).sum() == 14, "consumption leaves exactly 14 of the 32 cells");
    }
    private final List<String> messages = new ArrayList<>();
    public final class CaptureConnection extends net.minecraft.network.Connection {
        public CaptureConnection() { super(net.minecraft.network.protocol.PacketFlow.SERVERBOUND); }
        @Override public boolean isConnected() { return true; }
        private void capture(net.minecraft.network.protocol.Packet<?> packet) {
            if (packet instanceof net.minecraft.network.protocol.game.ClientboundSystemChatPacket chat)
                messages.add(chat.content().getString());
        }
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { capture(packet); }
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet, io.netty.channel.ChannelFutureListener listener) { capture(packet); }
        @Override public void send(net.minecraft.network.protocol.Packet<?> packet, io.netty.channel.ChannelFutureListener listener, boolean flush) { capture(packet); }
    }
    private void check(boolean value, String label) { if (!value) throw new IllegalStateException(label); checks++; getLogger().info("PASS " + label); }
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
        Object connection = new CaptureConnection();
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
