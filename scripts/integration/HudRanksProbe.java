package net.mandomc.verification;

import java.util.*;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.credits.CreditLedger;
import net.mandomc.gameplay.ranks.RankService;

/** Disposable Paper checks using native unregistered players; no real account, inventory or terrain changes. */
public final class HudRanksProbe extends JavaPlugin {
    private MandoPlugin plugin;
    private Player player;
    private Scoreboard previous, board;
    private Team policy;
    private UUID recipient = UUID.randomUUID();
    private int checks;
    @Override public void onEnable() { later(100, this::begin); }
    private void begin() throws Exception {
        plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(plugin.hud() != null && plugin.ranks() != null && plugin.credits() != null && plugin.warps() != null, "all native systems initialized");
        check(plugin.vehicles() != null && plugin.events() != null, "combined vehicles and events remain initialized");
        check(Bukkit.getPluginManager().getPlugin("LuckPerms") == null && Bukkit.getPluginManager().getPlugin("TAB") == null, "no rank or HUD vendor dependency");
        player = nativeTestPlayer(Bukkit.getWorlds().getFirst(), new Location(Bukkit.getWorlds().getFirst(), 0, 280, 0));
        plugin.ranks().apply(player);
        check(!player.isOp(), "fixture is non-operator");
        check(player.hasPermission("mandomc.credits.pay"), "Default can pay");
        check(!player.hasPermission("mandomc.credits.admin") && !player.hasPermission("mandomc.ranks.manage"), "Default cannot administer money/ranks");
        check(!player.hasPermission("mandomc.admin.fly"), "Default cannot fly");
        previous = Bukkit.getScoreboardManager().getNewScoreboard(); policy = previous.registerNewTeam("policy");
        policy.addEntry(player.getName()); policy.setAllowFriendlyFire(false); policy.setCanSeeFriendlyInvisibles(true);
        policy.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER); policy.prefix(Component.text("protected"));
        player.setScoreboard(previous); plugin.hud().attach(player); board = player.getScoreboard();
        check(board != previous && objective() != null, "persistent HUD assigned");
        check(!board.getTeam("policy").allowFriendlyFire() && board.getTeam("policy").canSeeFriendlyInvisibles(), "team combat/visibility flags preserved");
        check(board.getTeam("policy").getOption(Team.Option.COLLISION_RULE) == Team.OptionStatus.NEVER, "team collision policy preserved");
        check(board.getTeam("policy").prefix().equals(policy.prefix()), "foreign team prefix retained");
        check(objective().getScore("mmc_line_13").isScoreSet() && board.getEntries().stream().filter(e -> e.startsWith("mmc_line_")).count() == 14, "fourteen stable sidebar entries");
        check(line(8).contains("ᴋɪʟʟѕ"), "kills shown outside combat");
        check(line(10).contains("$0.00"), "zero credit balance shown");
        check(text(plugin.warps().badges("hoth")).equals("ĕġ"), "Hoth restores Rare + Planet badges");
        for (String id : List.of("spawn", "earth", "tatooine")) check(text(plugin.warps().badges(id)).equals("đġ"), id + " restores Common + Planet badges");
        plugin.warps().open(player);
        for (int slot : List.of(1, 28, 39, 43)) {
            var meta = player.getOpenInventory().getTopInventory().getItem(slot).getItemMeta();
            check(meta.getItemModel() != null && meta.getItemModel().getNamespace().equals("mandomc"), "planet item model in slot " + slot);
            check(meta.lore().size() == 5 && text(meta.lore().get(0)).endsWith("ġ"), "badge first, blank, description, blank, hint in slot " + slot);
        }
        player.closeInventory();
        plugin.ranks().change(player.getUniqueId(), Set.of("builder"), saved -> {
            try {
                check(saved, "rank grant saved"); plugin.ranks().apply(player);
                check(player.hasPermission("mandomc.admin.fly") && player.hasPermission("mandomc.admin.gamemode"), "Builder receives native tools");
                check(player.hasPermission("mandomc.travel.homes.unlimited"), "inherited exact override applied");
                check(com.sk89q.wepif.PermissionsResolverManager.getInstance().hasPermission(player, "worldedit.selection.pos"), "WorldEdit resolver honors native wildcard");
                check(!player.hasPermission("mandomc.ranks.manage"), "Builder cannot promote itself");
                check(RankService.read(plugin.getDataFolder().toPath().resolve("data/rank-users.yml"), plugin.ranks().catalog()).get(player.getUniqueId()).equals(Set.of("builder")), "rank assignment survives disk reload");
                plugin.ranks().change(player.getUniqueId(), Set.of("mandostudios"), second -> {
                    try {
                        check(second, "MandoStudios grant saved"); plugin.ranks().apply(player);
                        check(player.hasPermission("mandomc.ranks.manage") && player.hasPermission("mandomc.credits.admin"), "owner wildcard covers native administrative commands");
                        plugin.ranks().change(player.getUniqueId(), Set.of("default"), third -> {
                            try { check(third, "demotion saved"); plugin.ranks().apply(player);
                                check(!player.hasPermission("mandomc.admin.fly") && !com.sk89q.wepif.PermissionsResolverManager.getInstance().hasPermission(player, "worldedit.selection.pos") && !player.hasPermission("mandomc.credits.admin"), "demotion revokes old permissions");
                                payments();
                            } catch (Throwable error) { fail(error); }
                        });
                    } catch (Throwable error) { fail(error); }
                });
            } catch (Throwable error) { fail(error); }
        });
    }
    private void payments() {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "credits set " + player.getUniqueId() + " 200.25");
        later(25, () -> {
            check(plugin.credits().ledger().balance(player.getUniqueId()) == 20025, "admin command saved exact cents");
            check(line(10).contains("$200.25"), "sidebar reflects committed credits");
            Bukkit.getPluginCommand("pay").execute(player, "pay", new String[] {recipient.toString(), "50.10"});
            later(25, () -> {
                check(plugin.credits().ledger().balance(player.getUniqueId()) == 15015, "pay debits sender once");
                check(plugin.credits().ledger().balance(recipient) == 5010, "pay credits offline UUID once");
                var reload = new CreditLedger(plugin.getDataFolder().toPath().resolve("data/credits-data.yml"));
                check(reload.balance(player.getUniqueId()) + reload.balance(recipient) == 20025, "payment persisted both sides with conserved total");
                Bukkit.getPluginCommand("pay").execute(player, "pay", new String[] {recipient.toString(), "9999"});
                later(25, () -> { check(plugin.credits().ledger().balance(player.getUniqueId()) == 15015, "overdraft command changes no balance"); combat(); });
            });
        });
    }
    private void combat() {
        plugin.combat().tag(player);
        check(player.getScoreboard() == board, "combat never swaps scoreboard");
        later(3, () -> {
            check(line(8).matches("⚔ ɪɴ ᴄᴏᴍʙᴀᴛ: [0-9]+\\.[0-9]s"), "combat row renders one decimal");
            check(line(10).contains("150.15"), "credits remain visible during combat");
            Bukkit.getPluginCommand("scoreboard").execute(player, "sb", new String[0]);
            check(objective().getDisplaySlot() == null && plugin.combat().tagged(player), "sb hides sidebar without clearing tag");
            Bukkit.getPluginCommand("scoreboard").execute(player, "sb", new String[0]);
            check(objective().getDisplaySlot() == DisplaySlot.SIDEBAR, "sb restores sidebar");
            policy.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.ALWAYS);
            later(15, () -> check(board.getTeam("policy").getOption(Team.Option.COLLISION_RULE) == Team.OptionStatus.ALWAYS, "HUD follows source team policy changes"));
            later(325, () -> {
                check(!plugin.combat().tagged(player) && line(8).contains("ᴋɪʟʟѕ"), "expiry restores kills row");
                check(player.getScoreboard() == board, "expiry keeps full scoreboard");
                plugin.hud().detach(player); check(player.getScoreboard() == previous, "HUD shutdown/detach restores source scoreboard");
                plugin.ranks().quit(new PlayerQuitEvent(player, Component.empty()));
                getLogger().info("HUD RANKS CREDITS PROBE PASSED: " + checks + " assertions");
                Bukkit.shutdown();
            });
        });
    }
    private Objective objective() { return board.getObjective("mandomc_hud"); }
    private String line(int index) { return text(objective().getScore("mmc_line_" + index).customName()); }
    private String text(Component value) { return PlainTextComponentSerializer.plainText().serialize(value); }
    private void check(boolean ok, String description) { if (!ok) throw new AssertionError(description); checks++; getLogger().info("PASS " + description); }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private void later(long ticks, Action action) { Bukkit.getScheduler().runTaskLater(this, () -> { try { action.run(); } catch (Throwable error) { fail(error); } }, ticks); }
    private void fail(Throwable error) { getLogger().log(java.util.logging.Level.SEVERE, "HUD RANKS CREDITS PROBE FAILED", error); Bukkit.shutdown(); }
    private Player nativeTestPlayer(World world, Location location) throws Exception {
        // An unregistered real CraftPlayer supplies attribution, with no login, player-list entry or saved gear.
        Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
        Class<?> levelClass = Class.forName("net.minecraft.server.level.ServerLevel");
        Class<?> profileClass = Class.forName("com.mojang.authlib.GameProfile");
        Class<?> infoClass = Class.forName("net.minecraft.server.level.ClientInformation");
        Class<?> playerClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
        Object level = world.getClass().getMethod("getHandle").invoke(world);
        Object profile = profileClass.getConstructor(UUID.class, String.class).newInstance(UUID.randomUUID(), "HudTest");
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
