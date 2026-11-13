package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.credits.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable runtime only: exercise registered first-login authority and restart durability. */
public final class StartingCreditsProbe extends JavaPlugin {
    private CreditService service;
    private NativeProbePlayers actors;
    private int checks;
    private final Path state = Path.of("starting-credits-probe.properties");
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> run(() -> {
            service = ((MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC")).credits();
            check(service != null, "native credits authority ready");
            if (Files.exists(getDataFolder().toPath().resolve("live-only"))) { liveReadOnly(); return; }
            actors = new NativeProbePlayers("CreditProbe");
            if (Files.exists(state)) restart(); else first();
        }), 80);
    }
    private void liveReadOnly() throws Exception {
        var initial = CreditService.class.getDeclaredField("initialBalance");initial.setAccessible(true);
        check(initial.getLong(service)==250000,"configured starting balance is 2500");
        check(Arrays.stream(PlayerJoinEvent.getHandlerList().getRegisteredListeners()).filter(l->l.getListener()==service).count()==1,"one native credits join hook");
        check(Arrays.stream(org.bukkit.event.entity.PlayerDeathEvent.getHandlerList().getRegisteredListeners()).noneMatch(l->l.getListener()==service),"no death grant hook");
        check(Arrays.stream(org.bukkit.event.player.PlayerRespawnEvent.getHandlerList().getRegisteredListeners()).noneMatch(l->l.getListener()==service),"no respawn grant hook");
        CreditLedger.class.getMethod("supply",UUID.class,UUID.class,String.class,long.class,boolean.class);
        CreditLedger.class.getMethod("sellSupplies",UUID.class,Set.class,long.class);
        check(true,"deployed Jawa economy API retained");
        CreditLedger.class.getMethod("initializeAccount",UUID.class,boolean.class,long.class);
        check(true,"atomic initialization API present");
        Files.createDirectories(getDataFolder().toPath());
        Files.writeString(getDataFolder().toPath().resolve("LIVE-PASSED.txt"),"STARTING CREDITS LIVE READ-ONLY PASSED "+checks+" assertions");
        getLogger().info("STARTING CREDITS LIVE READ-ONLY PASSED "+checks+" assertions");
    }
    private void join(Player p) throws Exception {
        var event = new PlayerJoinEvent(p, (net.kyori.adventure.text.Component) null);
        int count = 0;
        for (var listener : PlayerJoinEvent.getHandlerList().getRegisteredListeners()) {
            if (listener.getListener() == service) { listener.callEvent(event); count++; }
        }
        check(count == 1, "exactly one credits join listener");
    }
    private void after(Runnable next) {
        service.auctionTask(() -> true, (ok, error) -> run(() -> {
            if (error != null) throw new IllegalStateException(error);
            next.run();
        }));
    }
    private void first() throws Exception {
        Player fresh = actors.create(Bukkit.getWorlds().getFirst().getSpawnLocation());
        check(!fresh.hasPlayedBefore(), "native first player has no prior play");
        Player returning = actors.create(fresh.getLocation());
        var field = returning.getClass().getDeclaredField("hasPlayedBefore");field.setAccessible(true);field.setBoolean(returning,true);
        check(returning.hasPlayedBefore(), "native returning player recognized");
        Player existing = actors.create(fresh.getLocation());
        service.auctionTask(() -> {service.ledger().adjust(existing.getUniqueId(),"set",73125);return true;}, (ok,error) -> run(() -> {
            if(error != null) throw new IllegalStateException(error);
            join(fresh);join(fresh);join(returning);join(existing);
            after(() -> run(() -> {
                check(service.ledger().balance(fresh.getUniqueId())==250000,"first account receives exactly 2500");
                check(service.ledger().balance(returning.getUniqueId())==0,"returning absent account stays zero");
                check(service.ledger().balance(existing.getUniqueId())==73125,"existing funded account preserved");
                check(Arrays.stream(org.bukkit.event.entity.PlayerDeathEvent.getHandlerList().getRegisteredListeners()).noneMatch(l->l.getListener()==service),"no death grant listener");
                check(Arrays.stream(org.bukkit.event.player.PlayerRespawnEvent.getHandlerList().getRegisteredListeners()).noneMatch(l->l.getListener()==service),"no respawn grant listener");
                Properties data=new Properties();data.setProperty("fresh",fresh.getUniqueId().toString());data.setProperty("returning",returning.getUniqueId().toString());data.setProperty("existing",existing.getUniqueId().toString());
                try(var out=Files.newOutputStream(state)){data.store(out,"isolated probe identities");}
                finish("FIRST");
            }));
        }));
    }
    private void restart() throws Exception {
        var p=new Properties();try(var in=Files.newInputStream(state)){p.load(in);}
        UUID fresh=UUID.fromString(p.getProperty("fresh")), returning=UUID.fromString(p.getProperty("returning")), existing=UUID.fromString(p.getProperty("existing"));
        check(service.ledger().balance(fresh)==250000,"first grant survives process restart");
        check(service.ledger().balance(returning)==0,"returning zero survives restart");
        check(service.ledger().balance(existing)==73125,"existing balance survives restart");
        service.auctionTask(() -> {
            check(!service.ledger().initializeAccount(fresh,true,250000),"first-play replay cannot grant after restart");
            service.ledger().adjust(fresh,"set",0);
            check(!service.ledger().initializeAccount(fresh,true,250000),"spent balance cannot be replenished");
            return true;
        }, (ok,error)->run(()->{if(error!=null)throw new IllegalStateException(error);check(service.ledger().balance(fresh)==0,"spent balance stays zero");finish("RESTART");}));
    }
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private void finish(String phase) throws Exception {actors.close();getLogger().info("STARTING CREDITS "+phase+" PASSED "+checks+" assertions");Bukkit.shutdown();}
    @FunctionalInterface private interface Work {void run() throws Exception;}
    private void run(Work work){try{work.run();}catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"STARTING CREDITS FAILED",error);
        if (!Files.exists(getDataFolder().toPath().resolve("live-only"))) Bukkit.shutdown();}}
}
