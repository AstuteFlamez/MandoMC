package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.guide.GitHubKnowledge;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable native player/AI checks. Never install on a shared development or production runtime. */
public final class DroidExpandedProbe extends JavaPlugin {
    private MandoPlugin mando;
    private NativeProbePlayers actors;
    private Player player;
    private int checks, question = Integer.getInteger("mandomc.probe.first-question", 0), waited;
    private final List<String> answers = new ArrayList<>();
    private final String[] questions = {"Who is Darth Vader?", "What is the capital of France?",
            "How much health does a Massiff have on MandoMC?", "Where do Massiffs spawn on MandoMC?",
            "What do they drop?", "How much base damage does the E11 do on MandoMC?",
            "What is the Black Kyber chance in the KOTH crate?"};

    @Override public void onEnable() { later(80, this::start); }
    private interface Work { void run() throws Exception; }
    private void later(long ticks, Work work) {
        Bukkit.getScheduler().runTaskLater(this, () -> { try { work.run(); } catch (Throwable error) { finish(error); } }, ticks);
    }
    private void check(boolean okay, String message) {
        if (!okay) throw new IllegalStateException(message);
        checks++; getLogger().info("PASS " + message);
    }
    private void start() throws Exception {
        mando = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(mando != null && mando.isEnabled(), "MandoMC enabled");
        var field = mando.droid().getClass().getDeclaredField("repository");field.setAccessible(true);
        var repository = (GitHubKnowledge) field.get(mando.droid());
        check(repository.passageCount() > 500, "offline bundled knowledge ready");
        check(repository.find("Massiff health").getFirst().facts().contains("30"), "native health record");
        check(repository.find("E11 damage").stream().anyMatch(n -> n.facts().contains("2.0")), "native weapon record");
        check(repository.find("where do massiffs spawn").stream().anyMatch(n -> n.facts().contains("Tatooine")), "native spawn rules");
        actors = new NativeProbePlayers("Expanded");
        player = actors.create(new Location(mando.worlds().resolve("DevWorld"),-201.5,200,-199.5));
        player.setOp(true);mando.ranks().apply(player);player.setGameMode(GameMode.SURVIVAL);
        check(mando.droid().location() != null, "skin NPC remains present");
        byte[] before = allowance();
        for (String q : List.of("Hi", "bye", "thanks", "what is my balance", "what event is next", "where to get coal", "where can I find durastel")) {
            actors.drain(player);mando.droid().ask(player,q);
            var messages = drain();
            check(messages.stream().anyMatch(s -> s.contains("M4-ND") && s.contains("»")), "instant private answer: " + q);
            check(messages.stream().noneMatch(s -> s.contains("Consulting")), "no AI request: " + q);
            if(q.contains("balance"))check(messages.stream().anyMatch(s -> s.contains("0.00")), "own committed balance");
            if(q.contains("event"))check(messages.stream().anyMatch(s -> s.contains("Current event:") && s.contains("Next event:")), "live public event state");
            if(q.contains("coal"))check(messages.stream().anyMatch(s -> s.contains("Tatooine") && s.contains("pickaxe")), "exact coal source");
            if(q.contains("durastel"))check(messages.stream().anyMatch(s -> s.toLowerCase().contains("durasteel")), "material typo resolved");
        }
        check(Arrays.equals(before,allowance()), "instant answers do not change AI allowance");
        ask();
    }
    private byte[] allowance() throws Exception {
        var path=mando.getDataFolder().toPath().resolve("data/guide-allowance.properties");
        return Files.exists(path)?Files.readAllBytes(path):new byte[0];
    }
    private List<String> drain() throws Exception {
        return actors.drain(player).stream().map(c -> PlainTextComponentSerializer.plainText().serialize(c)).toList();
    }
    private void ask() throws Exception {
        answers.clear();waited=0;actors.drain(player);mando.droid().ask(player,questions[question]);later(10,this::answer);
    }
    private void answer() throws Exception {
        for(String value:drain())if(!value.contains("Consulting"))answers.add(value);
        var reply=answers.stream().filter(s -> s.contains("M4-ND") && s.contains("»")).findFirst();
        if(reply.isEmpty()) {
            if((waited+=10)>1600)throw new IllegalStateException("Timed out: "+questions[question]);
            later(10,this::answer);return;
        }
        check(answers.stream().noneMatch(s -> s.contains("unavailable") || s.contains("written guidance") || s.contains("allowance is resting")), "live AI healthy: "+questions[question]);
        String value=reply.get().toLowerCase(Locale.ROOT);
        getLogger().info("ANSWER ["+questions[question]+"] "+reply.get());
        check(!value.contains("groq") && !value.contains("server/config/") && !value.contains("<gray>"), "plain player-facing answer");
        switch(question) {
            case 0 -> check(value.contains("anakin") && (value.contains("sith") || value.contains("jedi")), "Star Wars lore answered");
            case 1 -> check(value.contains("paris"), "general knowledge answered");
            case 2 -> check(value.contains("30") || value.contains("15 hearts"), "verified Massiff health");
            case 3 -> check(value.contains("tatooine") && !value.contains("minecraft:"), "verified Massiff spawn planet");
            case 4 -> check(value.contains("rib") && (value.contains("75") || value.contains("0.75")), "follow-up uses same mob and verified drop chance");
            case 5 -> check(value.contains("2") && !value.contains("20 damage"), "verified current E11 base damage");
            case 6 -> check(value.matches("(?sU).*\\b1(?:\\.0+)?\\s*(?:%|percent).*"), "computed Black Kyber probability");
            default -> throw new IllegalStateException("Unknown question");
        }
        if(++question==questions.length){finish(null);return;}
        later(440,this::ask);
    }
    private void finish(Throwable error) {
        try {
            if(actors!=null)actors.close();Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve(error==null?"PASSED.txt":"FAILED.txt"),
                    error==null?checks+" native expanded droid assertions passed\n":error.toString());
            if(error==null)getLogger().info("DROID EXPANDED PASSED: "+checks);
            else getLogger().log(java.util.logging.Level.SEVERE,"DROID EXPANDED FAILED",error);
        } catch(Exception failure){getLogger().log(java.util.logging.Level.SEVERE,"Probe cleanup failed",failure);}
        Bukkit.shutdown();
    }
}
