package net.mandomc.gameplay.guide;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.ui.TextPrompt;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;

/** One native golden protocol droid and a bounded, private explanations service. */
public final class ProtocolDroid implements Listener,AutoCloseable {
    private final MandoPlugin plugin;private final YamlConfiguration config;private final GuideKnowledge knowledge;
    private final GroqBrain brain;private final GuideAllowance allowance;private final GitHubKnowledge repository;
    private final GuideConversation conversation;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"MandoMC-guide");t.setDaemon(true);return t;});
    private final Map<UUID,Integer> promptTicks=new HashMap<>();
    private final Set<UUID> pending=new HashSet<>();private final Map<String,GroqBrain.Answer> cache=new ConcurrentHashMap<>();
    private Mannequin droid;private TextDisplay title;private long nextRequest;private volatile boolean closed;private volatile boolean storageHealthy=true;
    private final org.bukkit.scheduler.BukkitTask ticker;
    public ProtocolDroid(MandoPlugin plugin,YamlConfiguration config) throws Exception{
        this.plugin=plugin;this.config=config;
        if(config.getInt("schema-version")!=1 || config.getInt("daily-answers")<1 || config.getInt("daily-answers")>100
                || config.getInt("cooldown-seconds")<1 || config.getInt("request-spacing-seconds")<1 || config.getInt("request-spacing-seconds")>60
                || config.getString("identity","").isBlank() || !config.getString("model","").matches("[a-zA-Z0-9._/-]+"))throw new IllegalArgumentException("guide/protocol-droid.yml: invalid bounds or identity");
        if(config.getString("skin.texture","").isBlank() || config.getString("skin.signature","").isBlank())
            throw new IllegalArgumentException("guide/protocol-droid.yml: missing signed protocol-droid skin");
        knowledge=new GuideKnowledge(Objects.requireNonNull(config.getConfigurationSection("knowledge")),YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"items/recipes.yml")));
        conversation=new GuideConversation(config);
        var secret=plugin.getDataFolder().toPath().resolve("secrets/groq-api-key");
        String key=Files.exists(secret)?Files.readString(secret).strip():System.getenv().getOrDefault("MANDOMC_GROQ_API_KEY","").strip();
        brain=new GroqBrain(key,config.getString("model"),config.getString("identity"));
        allowance=new GuideAllowance(plugin.getDataFolder().toPath().resolve("data/guide-allowance.properties"),config.getInt("daily-answers"),config.getInt("daily-token-budget"),config.getInt("reserved-tokens"),config.getInt("cooldown-seconds"),config.getInt("daily-request-budget",800));
        repository=new GitHubKnowledge(plugin.getResource("guide-repository.json"),plugin.getResource("guide-public.json.gz"),plugin.getDataFolder().toPath().resolve("data/guide/public-knowledge"),
                config.getBoolean("repository.enabled",true),config.getString("repository.fallback",config.getString("unknown")),plugin.getLogger()::info);
        worker.execute(()->{repository.refresh();cache.clear();});
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Objects.requireNonNull(plugin.getCommand("ask")).setExecutor((sender,command,label,args)->{if(sender instanceof Player player){if(args.length==0)prompt(player);else ask(player,String.join(" ",args));}return true;});
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,5);
        plugin.getLogger().info("M4-ND ready: "+(brain.configured()?"Groq configured":"FAQ only; add secrets/groq-api-key")+"; private explanations only.");
    }
    private Component text(String key,String...values){var tags=new ArrayList<net.kyori.adventure.text.minimessage.tag.resolver.TagResolver>();for(int i=0;i<values.length;i+=2)tags.add(Placeholder.unparsed(values[i],values[i+1]));return MiniMessage.miniMessage().deserialize(config.getString("text."+key,key),tags.toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new));}
    public void prompt(Player player){if(!safe(player))return;int tick=Bukkit.getCurrentTick();if(tick-promptTicks.getOrDefault(player.getUniqueId(),tick-10)<10)return;promptTicks.put(player.getUniqueId(),tick);TextPrompt.open(plugin,player,text("title"),text("question"),text("submit"),"",240,this::ask);}
    private boolean safe(Player player){if(!player.hasPermission("mandomc.arrival") || player.isDead() || plugin.combat().tagged(player) || player.isInsideVehicle() || net.mandomc.gameplay.tutorial.TutorialInventory.active(player)){player.sendMessage(text("unsafe"));return false;}return true;}
    public void ask(Player player,String question){
        if(!safe(player) || question.isBlank())return;if(question.length()>240){player.sendMessage(text("length"));return;}
        var greeting=conversation.smallTalk(question);if(greeting.isPresent()){reply(player,greeting.get());return;}
        var live=GuideLiveAnswers.answer(question,config,
                ()->net.mandomc.gameplay.credits.CreditLedger.format(plugin.credits().ledger().balance(player.getUniqueId())),
                this::liveEvents);
        if(live.isPresent()){reply(player,live.get());return;}
        var local=knowledge.find(question);
        var publicNotes=repository.find(question);
        var resolved=conversation.resolve(player.getUniqueId(),question,!local.isEmpty() || !publicNotes.isEmpty(),System.currentTimeMillis());
        String resolvedQuestion=resolved.text();
        var notes=new ArrayList<GuideKnowledge.Note>();
        if(resolved.topic()==GuideConversation.Topic.SERVER){
            if(!resolvedQuestion.equals(question)){local=knowledge.find(resolvedQuestion);publicNotes=repository.find(resolvedQuestion);}
            var verified=resolvedQuestion.equals(question)?knowledge.verifiedAnswer(question,local):Optional.<String>empty();if(verified.isPresent()){reply(player,verified.get());return;}
            notes.addAll(publicNotes);notes.addAll(local);
            if(notes.isEmpty()){reply(player,config.getString("unknown"));return;}
        }
        if(notes.isEmpty())notes.add(new GuideKnowledge.Note("conversation","", "",config.getString("conversation.unavailable",config.getString("unknown"))));
        // Live answers bypass this cache; follow-ups and personal wording remain per-player requests.
        boolean share=resolved.topic()==GuideConversation.Topic.SERVER && resolvedQuestion.equals(question)
                && !net.mandomc.gameplay.items.RecipeSearch.normalize(question).matches(".*\\b(?:i|me|my|we|our)\\b.*");
        String cacheKey=net.mandomc.gameplay.items.RecipeSearch.normalize(resolvedQuestion);var cached=share?cache.get(cacheKey):null;
        if(cached!=null){reply(player,cached.text());return;}
        if(!brain.configured() || !storageHealthy){reply(player,notes.getFirst().fallback());return;}
        if(pending.contains(player.getUniqueId()) || pending.size()>=4){fallback(player,notes,"busy");return;}
        pending.add(player.getUniqueId());UUID id=player.getUniqueId();player.sendMessage(text("thinking"));
        long delay=Math.max(0,nextRequest-System.currentTimeMillis());nextRequest=Math.max(System.currentTimeMillis(),nextRequest)+config.getInt("request-spacing-seconds")*1000L;
        worker.schedule(()->{
            String answer=notes.getFirst().fallback(),notice="";Instant reserved=Instant.now();boolean held=false;
            try{
                var denial=allowance.reserve(id,reserved);
                if(denial!=GuideAllowance.Denial.NONE)notice=denial==GuideAllowance.Denial.DAILY?"daily":denial==GuideAllowance.Denial.COOLDOWN?"cooldown":"shared";
                else{held=true;var generated=brain.answer(resolvedQuestion,notes,resolved.topic());answer=generated.text();allowance.finish(id,reserved,generated.tokens(),true);held=false;if(share){if(cache.size()>=256)cache.clear();cache.put(cacheKey,generated);}}
            }catch(Exception error){
                if(held)try{allowance.finish(id,reserved,0,false);}catch(Exception save){plugin.getLogger().warning("M4-ND allowance save failed; AI requests paused.");storageHealthy=false;}
                notice="unavailable";plugin.getLogger().warning("M4-ND request unavailable; served a maintained FAQ answer.");
            }
            String result=answer,status=notice;
            if(!closed && plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{pending.remove(id);var viewer=Bukkit.getPlayer(id);if(viewer!=null && viewer.isOnline()){if(!status.isBlank())viewer.sendMessage(text(status));reply(viewer,result);}});
        },delay,TimeUnit.MILLISECONDS);
    }
    private String liveEvents(){
        var state=plugin.events().publicStatus();
        String result=config.getString("live.events","Current event: {current}. Next event: {next}.")
                .replace("{current}",RepositorySearch.playerText(state.current()))
                .replace("{next}",RepositorySearch.playerText(state.next()));
        return result+" "+(state.scheduled()?config.getString("live.scheduled","Next scheduled start in {time}.")
                .replace("{time}",net.mandomc.gameplay.events.EventManager.formatDuration(state.startsIn()))
                :config.getString("live.unscheduled","Automatic event scheduling is disabled."));
    }
    @EventHandler public void quit(PlayerQuitEvent event){conversation.forget(event.getPlayer().getUniqueId());promptTicks.remove(event.getPlayer().getUniqueId());}
    private void fallback(Player player,List<GuideKnowledge.Note> notes,String reason){player.sendMessage(text(reason));reply(player,notes.getFirst().fallback());}
    private void reply(Player player,String answer){player.sendMessage(text("reply","answer",answer));}
    public Location location(){return droid==null?null:droid.getLocation();}
    private void tick(){
        if(closed)return;
        if(droid==null || !droid.isValid()){
            var guides=YamlConfiguration.loadConfiguration(new java.io.File(plugin.getDataFolder(),"decor/travel-guides.yml"));
            var anchor=Objects.requireNonNull(guides.getConfigurationSection("npcs.cassian_andor"));
            var world=Bukkit.getWorld(Objects.requireNonNull(NamespacedKey.fromString(anchor.getString("world"))));
            if(world==null)return;
            Location at=new Location(world,anchor.getDouble("x"),anchor.getDouble("y"),anchor.getDouble("z"),(float)anchor.getDouble("yaw"),(float)anchor.getDouble("pitch"));
            Location site=at;
            droid=(Mannequin)plugin.territories().spawnNpc(EntityType.MANNEQUIN,site,()->site.getWorld().spawn(site,Mannequin.class,e->{
                var profile=Bukkit.createProfile(UUID.nameUUIDFromBytes("mandomc:protocol-droid:m4-nd".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures",config.getString("skin.texture"),config.getString("skin.signature")));
                e.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(profile));
                e.setDescription(Component.empty());e.setImmovable(true);e.setGravity(false);e.setInvulnerable(true);
                e.setSilent(true);e.setPersistent(false);e.setRemoveWhenFarAway(false);e.setCollidable(false);e.setCanPickupItems(false);e.setCustomNameVisible(false);
            }));
            if(droid==null)return;
            if(title!=null)title.remove();title=at.getWorld().spawn(at.clone().add(0,2.25,0),TextDisplay.class,t->{t.setPersistent(false);t.setBillboard(Display.Billboard.CENTER);t.setViewRange(.5f);t.setShadowed(true);t.text(text("name"));});
        }
        var nearest=droid.getWorld().getPlayers().stream().filter(p->!p.isDead() && p.getGameMode()!=GameMode.SPECTATOR && p.getLocation().distanceSquared(droid.getLocation())<25).min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(droid.getLocation())));
        nearest.ifPresent(p->{var delta=p.getEyeLocation().toVector().subtract(droid.getEyeLocation().toVector());var look=droid.getLocation().setDirection(delta);droid.setRotation(look.getYaw(),look.getPitch());droid.setBodyYaw(look.getYaw());});
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEntityEvent event){if(droid!=null && event.getRightClicked().getUniqueId().equals(droid.getUniqueId())){event.setCancelled(true);if(event.getHand()==EquipmentSlot.HAND)Bukkit.getScheduler().runTask(plugin,()->prompt(event.getPlayer()));}}
    @EventHandler(priority=EventPriority.HIGHEST) public void interactAt(PlayerInteractAtEntityEvent event){interact(event);}
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent event){if(droid!=null && event.getEntity().getUniqueId().equals(droid.getUniqueId()))event.setCancelled(true);}
    @Override public void close(){closed=true;ticker.cancel();worker.shutdownNow();repository.close();brain.close();HandlerList.unregisterAll(this);if(droid!=null)droid.remove();if(title!=null)title.remove();pending.clear();conversation.clear();}
}
