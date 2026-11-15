package net.mandomc.verification;

import java.nio.file.Files;
import java.lang.reflect.Proxy;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.credits.CreditLedger;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable runtime only: real command aliases, permission denial and durable balance changes. */
public final class EcoAdminProbe extends JavaPlugin {
    private final UUID target = UUID.randomUUID();
    private record Step(String command, long expected) { }
    private final List<Step> steps = new ArrayList<>();
    private int index, checks;
    private void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks++; }
    private CreditLedger ledger() { return ((MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC")).credits().ledger(); }
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (var pair : List.of(new Step("eco set",10000),new Step("eco give",12550),
                    new Step("economy add",13000),new Step("eco remove",10000),new Step("eco take",9900),
                    new Step("eco set",0),new Step("eco reset",250000),new Step("credits GIVE",250100),
                    new Step("eco give",250100),new Step("eco remove",250100),new Step("eco reset",250100),
                    new Step("eco help",250100),new Step("mandomc:eco set",1000))) steps.add(pair);
            next();
        }, 100);
    }
    private void next() {
        try {
            if (index == steps.size()) { finish(); return; }
            String amount = List.of("100","25.50","4.50","30","1","0","","1","-1","999999","50","","10").get(index);
            var step = steps.get(index++);
            String command = step.command() + (step.command().endsWith("help") ? "" : " " + target + (amount.isEmpty() ? "" : " " + amount));
            check(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command), "Registered command " + command);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                try { check(ledger().balance(target) == step.expected(), "Balance after " + command); next(); }
                catch (Throwable failure) { fail(failure); }
            }, 20);
        } catch (Throwable failure) { fail(failure); }
    }
    private void finish() throws Exception {
        var denied = (CommandSender) Proxy.newProxyInstance(getClassLoader(),new Class<?>[]{CommandSender.class},(p,m,a)->switch(m.getName()) {
            case "hasPermission", "isOp", "isPermissionSet" -> false;
            case "getName" -> "EcoDenied";
            case "getServer" -> Bukkit.getServer();
            case "toString" -> "EcoDenied";
            case "hashCode" -> 1;
            case "equals" -> p == a[0];
            default -> null;
        });
        var command = Objects.requireNonNull(Bukkit.getPluginCommand("eco"));
        command.execute(denied, "eco", new String[]{"give", target.toString(), "100"});
        check(ledger().balance(target)==1000,"Unprivileged sender cannot change balances");
        check(command.tabComplete(denied,"eco",new String[]{""}).isEmpty(),"Admin completions hidden without permission");
        check(command.tabComplete(Bukkit.getConsoleSender(),"eco",new String[]{"r"}).containsAll(List.of("remove","reset")),"Remove/reset completion");
        var file = Bukkit.getPluginManager().getPlugin("MandoMC").getDataFolder().toPath().resolve("data/credits-data.yml");
        check(new CreditLedger(file).balance(target)==1000,"Adjustments persist in sole native ledger");
        getDataFolder().mkdirs();Files.writeString(getDataFolder().toPath().resolve("PASSED.txt"),"ECO ADMIN PASSED " + checks);
        getLogger().info("ECO ADMIN PASSED " + checks);Bukkit.shutdown();
    }
    private void fail(Throwable failure) { getLogger().log(java.util.logging.Level.SEVERE,"Eco admin check failed",failure);Bukkit.shutdown(); }
}
