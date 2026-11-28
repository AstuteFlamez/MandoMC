package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.mandomc.integrations.discord.DiscordMessages;

/** Disposable copied-spawn check; never installed in shared development or production. */
public final class DiscordDroidProbe extends JavaPlugin {
    private NativeProbePlayers actors;
    private int checks;
    private void check(boolean value, String why) { if (!value) throw new IllegalStateException(why); checks++; }
    private boolean click(Component value, ClickEvent expected) {
        return expected.equals(value.clickEvent()) || value.children().stream().anyMatch(child -> click(child,expected));
    }
    @Override public void onEnable() { Bukkit.getScheduler().runTaskLater(this, this::run, 160); }
    private void run() {
        try {
            var mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
            check(mando != null && mando.isEnabled(), "MandoMC enabled");
            var first = mando.droid().location(); var linking = mando.discordDroid();
            check(first != null && linking != null && linking.location() != null, "Both real droids spawned");
            var second = linking.location();
            var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(), "decor/discord-droid.yml"));
            if (cfg.isConfigurationSection("anchor")) {
                check(second.getWorld().getKey().toString().equals(cfg.getString("anchor.world")), "Captured droid world");
                var expected = new Location(second.getWorld(), cfg.getDouble("anchor.x"), cfg.getDouble("anchor.y"), cfg.getDouble("anchor.z"));
                check(second.distanceSquared(expected) < 1e-10, "C0-M5 stands at its own captured anchor");
            } else {
                check(first.getWorld().equals(second.getWorld()), "Legacy offset uses M4-ND's world");
                check(second.distanceSquared(first) >= 25 && second.distanceSquared(first) <= 100, "Legacy offset spacing");
            }
            check(second.getBlock().isPassable() && second.clone().add(0,1,0).getBlock().isPassable(), "Clear head/body space");
            check(second.clone().subtract(0,.1,0).getBlock().getType().isSolid(), "Supported ground");
            var titles = first.getWorld().getNearbyEntities(first,1,3,1).stream().filter(e -> e instanceof TextDisplay).map(e -> (TextDisplay)e).toList();
            check(titles.size() == 1, "M4-ND has exactly one title");
            for (var display : List.of(titles.getFirst(), linking.title())) {
                check(!display.isDefaultBackground() && display.getBackgroundColor().getAlpha() == 0 && display.isShadowed(), "Transparent title with readable shadow");
            }
            var body = second.getWorld().getNearbyEntities(second,.5,1,.5).stream().filter(e -> e instanceof Mannequin).map(e -> (Mannequin)e).findFirst().orElseThrow();
            check(body.isInvulnerable() && body.isImmovable() && !body.hasGravity(), "C0-M5 protected");
            var profile = body.getProfile().resolve().join();
            var texture = profile.getProperties().stream().filter(p -> p.getName().equals("textures")).findFirst().orElseThrow();
            check(texture.getSignature() != null && new String(Base64.getDecoder().decode(texture.getValue()),java.nio.charset.StandardCharsets.UTF_8).contains("77056f221139afad18d8db903493e3f9eb6780361c7cc23572710bd3e6979a82"), "Actual pinned signed silver skin");
            actors = new NativeProbePlayers("C0Probe"); var player = actors.create(second.clone().add(0,0,2));
            mando.ranks().apply(player); actors.drain(player);
            Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(player,body,EquipmentSlot.HAND));
            String clicked = actors.drain(player).stream().map(PlainTextComponentSerializer.plainText()::serialize).reduce("",(a,b)->a+b);
            check(clicked.contains("Requesting your Discord link code"), "Real right-click invokes existing private proof flow");
            var messages = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"language/discord.yml"));
            player.sendMessage(DiscordMessages.render(messages,"code","ABCD2345"));
            var packets = actors.drain(player);
            check(packets.stream().anyMatch(value -> click(value,ClickEvent.openUrl(DiscordMessages.DEFAULT_INVITE))), "Client packet retains preferred invite open-url");
            check(packets.stream().anyMatch(value -> click(value,ClickEvent.copyToClipboard("ABCD2345"))), "Client packet copies short code");
            check(packets.stream().anyMatch(value -> click(value,ClickEvent.copyToClipboard("/link code:ABCD2345"))), "Client packet copies complete command");
            check(packets.stream().noneMatch(value -> PlainTextComponentSerializer.plainText().serialize(value).contains("production")), "No deployment labels in player instructions");
            for (String question : List.of("How do I join the discord?", "How do I link my account?", "How do I get the linking reward?", "How do I open a support ticket?")) {
                mando.droid().ask(player, question);
                String answer = actors.drain(player).stream().map(PlainTextComponentSerializer.plainText()::serialize).reduce("",(a,b)->a+b);
                check(answer.contains("C0-M5") && answer.contains("5,000") && answer.contains("discord.gg/bruuZCG5Pk") && !answer.contains("production"), "Exact private Discord guidance: " + question);
            }
            Files.writeString(Path.of(System.getProperty("probe.result")), "PASS: " + checks + " native droid, spacing, transparency, skin, click and M4-ND answer checks\n");
            getLogger().info("DISCORD_DROID_PROBE_PASSED: " + checks);
        } catch (Throwable error) { getLogger().log(java.util.logging.Level.SEVERE,"DISCORD_DROID_PROBE_FAILED",error); }
        finally { try { if (actors != null) actors.close(); } catch (Exception ignored) { } Bukkit.shutdown(); }
    }
}
