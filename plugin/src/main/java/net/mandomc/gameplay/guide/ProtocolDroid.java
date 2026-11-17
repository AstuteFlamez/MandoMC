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
    private final GroqBrain brain;private final GuideAllowance allowance;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"MandoMC-guide");t.setDaemon(true);return t;});
    private final Set<UUID> pending=new HashSet<>();private final Map<String,GroqBrain.Answer> cache=new ConcurrentHashMap<>();
    private ArmorStand droid;private TextDisplay title;private long nextRequest;private volatile boolean closed;
    private final org.bukkit.scheduler.BukkitTask ticker;
    public ProtocolDroid(MandoPlugin plugin,YamlConfiguration config) throws Exception{
        this.plugin=plugin;this.config=config;knowledge=new GuideKnowledge(Objects.requireNonNull(config.getConfigurationSection("knowledge")));
        var secret=plugin.getDataFolder().toPath().resolve("secrets/groq-api-key");
        String key=Files.exists(secret)?Files.readString(secret).strip():System.getenv().getOrDefault("MANDOMC_GROQ_API_KEY","").strip();
        brain=new GroqBrain(key,config.getString("model"),config.getString("identity"));
        allowance=new GuideAllowance(plugin.getDataFolder().toPath().resolve("data/guide-allowance.properties"),config.getInt("daily-answers"),config.getInt("daily-token-budget"),config.getInt("reserved-tokens"),config.getInt("cooldown-seconds"));
        Bukkit.getPluginManager().registerEvents(this,plugin);
        Objects.requireNonNull(plugin.getCommand("ask")).setExecutor((sender,command,label,args)->{if(sender instanceof Player player){if(args.length==0)prompt(player);else ask(player,String.join(" ",args));}return true;});
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,5);
        plugin.getLogger().info("M4-ND ready: "+(brain.configured()?"Groq configured":"FAQ only; add secrets/groq-api-key")+"; private explanations only.");
    }
    private Component text(String key,String...values){var tags=new ArrayList<net.kyori.adventure.text.minimessage.tag.resolver.TagResolver>();for(int i=0;i<values.length;i+=2)tags.add(Placeholder.unparsed(values[i],values[i+1]));return MiniMessage.miniMessage().deserialize(config.getString("text."+key,key),tags.toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new));}
    public void prompt(Player player){if(!safe(player))return;TextPrompt.open(plugin,player,text("title"),text("question"),text("submit"),"",240,this::ask);}
    private boolean safe(Player player){if(!player.hasPermission("mandomc.arrival") || player.isDead() || plugin.combat().tagged(player) || player.isInsideVehicle() || net.mandomc.gameplay.tutorial.TutorialInventory.active(player)){player.sendMessage(text("unsafe"));return false;}return true;}
    public void ask(Player player,String question){
        if(!safe(player) || question.isBlank())return;if(question.length()>240){player.sendMessage(text("length"));return;}
        var notes=knowledge.find(question);if(notes.isEmpty()){reply(player,config.getString("unknown"));return;}
        String cacheKey=net.mandomc.gameplay.items.RecipeSearch.normalize(question);var cached=cache.get(cacheKey);
        if(cached!=null){reply(player,cached.text());return;}
        if(!brain.configured()){reply(player,notes.getFirst().fallback());return;}
        if(pending.contains(player.getUniqueId()) || pending.size()>=4){fallback(player,notes,"busy");return;}
        pending.add(player.getUniqueId());UUID id=player.getUniqueId();player.sendMessage(text("thinking"));
        long delay=Math.max(0,nextRequest-System.currentTimeMillis());nextRequest=Math.max(System.currentTimeMillis(),nextRequest)+config.getInt("request-spacing-seconds")*1000L;
        worker.schedule(()->{
            String answer=notes.getFirst().fallback(),notice="";Instant reserved=Instant.now();boolean held=false;
            try{
                var denial=allowance.reserve(id,reserved);
                if(denial!=GuideAllowance.Denial.NONE)notice=denial==GuideAllowance.Denial.DAILY?"daily":denial==GuideAllowance.Denial.COOLDOWN?"cooldown":"shared";
                else{held=true;var generated=brain.answer(question,notes);answer=generated.text();allowance.finish(id,reserved,generated.tokens(),true);held=false;if(cache.size()>=256)cache.clear();cache.put(cacheKey,generated);}
            }catch(Exception error){
                if(held)try{allowance.finish(id,reserved,0,false);}catch(Exception save){plugin.getLogger().warning("M4-ND allowance save failed; AI requests paused.");closed=true;}
                notice="unavailable";plugin.getLogger().warning("M4-ND request unavailable; served a maintained FAQ answer.");
            }
            String result=answer,status=notice;
            if(!closed && plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->{pending.remove(id);var viewer=Bukkit.getPlayer(id);if(viewer!=null && viewer.isOnline()){if(!status.isBlank())viewer.sendMessage(text(status));reply(viewer,result);}});
        },delay,TimeUnit.MILLISECONDS);
    }
    private void fallback(Player player,List<GuideKnowledge.Note> notes,String reason){player.sendMessage(text(reason));reply(player,notes.getFirst().fallback());}
    private void reply(Player player,String answer){player.sendMessage(text("reply","answer",answer));}
    public Location location(){return droid==null?null:droid.getLocation();}
    private void tick(){
        if(closed)return;
        if(droid==null || !droid.isValid()){
            var spawn=plugin.worlds().spawn("Batuu");Location at=null;
            for(int[] offset:new int[][]{{3,0},{-3,0},{0,3},{0,-3},{4,2},{-4,2}}){var trial=spawn.clone().add(offset[0],0,offset[1]);if(trial.getBlock().isPassable() && trial.clone().add(0,1,0).getBlock().isPassable() && !trial.clone().add(0,-1,0).getBlock().isPassable()){at=trial;break;}}
            if(at==null)return;
            Location site=at;
            droid=(ArmorStand)plugin.territories().spawnNpc(EntityType.ARMOR_STAND,site,()->site.getWorld().spawn(site,ArmorStand.class));
            if(droid==null)return;
            droid.teleport(at);droid.setPersistent(false);droid.setGravity(false);droid.setInvulnerable(true);droid.setArms(true);droid.setBasePlate(false);droid.setCollidable(false);
            var equipment=droid.getEquipment();equipment.setHelmet(new ItemStack(Material.GOLDEN_HELMET));equipment.setChestplate(new ItemStack(Material.GOLDEN_CHESTPLATE));equipment.setLeggings(new ItemStack(Material.GOLDEN_LEGGINGS));equipment.setBoots(new ItemStack(Material.GOLDEN_BOOTS));
            if(title!=null)title.remove();title=at.getWorld().spawn(at.clone().add(0,2.25,0),TextDisplay.class,t->{t.setPersistent(false);t.setBillboard(Display.Billboard.CENTER);t.setViewRange(.5f);t.setShadowed(true);t.text(text("name"));});
        }
        var nearest=droid.getWorld().getPlayers().stream().filter(p->!p.isDead() && p.getGameMode()!=GameMode.SPECTATOR && p.getLocation().distanceSquared(droid.getLocation())<25).min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(droid.getLocation())));
        nearest.ifPresent(p->{var delta=p.getEyeLocation().toVector().subtract(droid.getEyeLocation().toVector());var look=droid.getLocation().setDirection(delta);droid.setRotation(look.getYaw(),0);droid.setHeadPose(new org.bukkit.util.EulerAngle(Math.toRadians(look.getPitch()),0,0));});
    }
    private ArmorStand atWorldStand(World world,Location spawn){return world.spawn(spawn,ArmorStand.class);}
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEntityEvent event){if(droid!=null && event.getRightClicked().getUniqueId().equals(droid.getUniqueId())){event.setCancelled(true);if(event.getHand()==EquipmentSlot.HAND)Bukkit.getScheduler().runTask(plugin,()->prompt(event.getPlayer()));}}
    @EventHandler(priority=EventPriority.HIGHEST) public void damage(EntityDamageEvent event){if(droid!=null && event.getEntity().getUniqueId().equals(droid.getUniqueId()))event.setCancelled(true);}
    @EventHandler public void manipulate(PlayerArmorStandManipulateEvent event){if(droid!=null && event.getRightClicked().getUniqueId().equals(droid.getUniqueId()))event.setCancelled(true);}
    @Override public void close(){closed=true;ticker.cancel();worker.shutdownNow();brain.close();HandlerList.unregisterAll(this);if(droid!=null)droid.remove();if(title!=null)title.remove();pending.clear();}
}
