package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.admin.StaffMode;
import net.mandomc.gameplay.saber.SaberUpgrades;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable Paper regression checks for player-reported droid and staff-forge behavior. */
public final class DroidFeedbackProbe extends JavaPlugin {
    private MandoPlugin mando;private NativeProbePlayers actors;private Player player;private Block block;private int checks;
    private int question;private int waited;private final List<String> answers=new ArrayList<>();
    private final String[] questions={"where to get coal","how to get saber","what should I do","Who are you?"};
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->run(this::start),160);}
    private interface Work{void run()throws Exception;}
    private void run(Work work){try{work.run();}catch(Throwable error){finish(error);}}
    private void later(int ticks,Work work){Bukkit.getScheduler().runTaskLater(this,()->run(work),ticks);}
    private void check(boolean value,String why){if(!value)throw new IllegalStateException(why);checks++;}
    private void start()throws Exception{
        mando=(MandoPlugin)Bukkit.getPluginManager().getPlugin("MandoMC");check(mando!=null && mando.isEnabled(),"MandoMC enabled");
        var at=mando.droid().location();check(at!=null,"droid spawned");
        var bodies=at.getWorld().getNearbyEntities(at,1,1,1).stream().filter(e->e instanceof Mannequin).map(e->(Mannequin)e).toList();
        check(bodies.size()==1,"one real skin mannequin at Cassian anchor");var body=bodies.getFirst();
        var profile=body.getProfile();check(profile!=null,"resolvable player skin profile present");
        var resolved=profile.resolve().join();
        var textures=resolved.getProperties().stream().filter(p->p.getName().equals("textures")).toList();
        check(textures.size()==1 && textures.getFirst().getSignature()!=null,"signed full-body skin");
        String skin=new String(Base64.getDecoder().decode(textures.getFirst().getValue()),java.nio.charset.StandardCharsets.UTF_8);
        check(skin.contains("textures.minecraft.net/texture/"),"Mojang texture configured");
        check(body.isImmovable() && body.isInvulnerable() && !body.hasGravity(),"native NPC protected and stationary");
        check(Arrays.stream(body.getEquipment().getArmorContents()).allMatch(s->s==null || s.isEmpty()),"skin has no armor covering it");
        check(at.getWorld().getNearbyEntities(at,1,1,1).stream().noneMatch(e->e instanceof ArmorStand),"armor stand removed");
        actors=new NativeProbePlayers("DroidFix");player=actors.create(new Location(mando.worlds().resolve("DevWorld"),-201.5,200,-199.5));
        player.setGameMode(GameMode.SURVIVAL);player.getInventory().clear();player.getInventory().setItem(5,new ItemStack(Material.DIAMOND,9));
        player.setOp(true);mando.ranks().apply(player);Bukkit.getPluginCommand("staff").execute(player,"staff",new String[]{"on"});
        check(StaffMode.active(player) && player.getGameMode()==GameMode.CREATIVE,"real staff mode entered");
        block=player.getLocation().clone().add(1,0,0).getBlock();block.setType(Material.SMITHING_TABLE);
        block.getChunk().getPersistentDataContainer().set(new NamespacedKey("mandomc","forge_"+block.getX()+"_"+block.getY()+"_"+block.getZ()),PersistentDataType.BYTE,(byte)1);
        prepare();interact();click(10);later(2,this::creativePaid);
    }
    private void prepare(){player.closeInventory();player.getInventory().clear();player.getInventory().setHeldItemSlot(0);player.getInventory().setItem(0,mando.items().create("vader"));var mat=mando.items().create("beskar");mat.setAmount(2);player.getInventory().setItem(1,mat);}
    private void interact(){
        var event=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,player.getInventory().getItemInMainHand(),block,BlockFace.UP,EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        check(player.getOpenInventory().getTopInventory().getSize()==27,"native right-click opens forge while staff "+player.getGameMode());
    }
    private void click(int slot){var e=new InventoryClickEvent(player.getOpenInventory(),InventoryType.SlotType.CONTAINER,slot,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(e);check(e.isCancelled(),"forge preview protected");}
    private void paid(){check(SaberUpgrades.level(player.getInventory().getItem(0),"damage")==1,"staff upgrade committed");check(player.getInventory().getItem(1)==null || player.getInventory().getItem(1).isEmpty(),"temporary staff materials consumed");}
    private void creativePaid(){paid();player.setGameMode(GameMode.SURVIVAL);prepare();interact();click(10);later(2,this::survivalPaid);}
    private void survivalPaid()throws Exception{
        paid();Bukkit.getPluginCommand("staff").execute(player,"staff",new String[]{"off"});
        check(!StaffMode.active(player),"staff mode exited");check(player.getInventory().getItem(5).equals(new ItemStack(Material.DIAMOND,9)),"normal inventory restored unchanged");
        check(player.getInventory().getItem(0)==null || player.getInventory().getItem(0).isEmpty(),"temporary upgraded saber discarded");
        player.teleport(mando.droid().location().clone().add(1,0,0));actors.drain(player);mando.droid().prompt(player);later(3,this::dialog);
    }
    private void dialog()throws Exception{
        actors.drain(player);check(actors.otherPackets(player).stream().anyMatch(s->s.contains("ShowDialog")),"native droid question dialog");
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(new java.io.File(mando.getDataFolder(),"guide/protocol-droid.yml"));
        check(!config.getConfigurationSection("text").getValues(false).toString().toLowerCase().contains("groq"),"no provider branding in menu text");ask();
    }
    private void ask()throws Exception{answers.clear();waited=0;actors.drain(player);mando.droid().ask(player,questions[question]);later(10,this::answer);}
    private void answer()throws Exception{
        for(var c:actors.drain(player)){String s=PlainTextComponentSerializer.plainText().serialize(c);if(!s.contains("Consulting"))answers.add(s);}
        var reply=answers.stream().filter(s->s.contains("M4-ND") && s.contains("»")).findFirst();
        if(reply.isEmpty()){if((waited+=10)>1000)throw new IllegalStateException("Timed out: "+questions[question]);later(10,this::answer);return;}
        check(answers.stream().noneMatch(s->s.contains("unavailable") || s.contains("written guidance")),"answer path healthy "+questions[question]);
        String text=reply.get().toLowerCase();getLogger().info("Verified answer to ["+questions[question]+"]: "+reply.get());
        check(!text.contains("groq"),"no provider name in response");
        if(question==0){check(text.contains("tatooine") && text.contains("coal ore") && text.contains("pickaxe") && !text.contains("refine"),"coal answer gives verified mining source");}
        if(question==1){check(text.contains("crafting table") && text.contains("ilum") && text.contains("kyber"),"first saber explains actual acquisition");check(!text.contains("already possess") && !text.contains("should already"),"no assumed first saber");}
        if(question==2){check(text.contains("/tutorial") && (text.contains("naboo") || text.contains("500")),"beginner gets achievable first steps");}
        if(question==3){check(text.contains("protocol") && text.contains("mando"),"live AI retains droid identity");finish(null);return;}
        question++;later(440,this::ask);
    }
    private void finish(Throwable error){try{if(actors!=null)actors.close();Files.createDirectories(getDataFolder().toPath());Files.writeString(getDataFolder().toPath().resolve(error==null?"PASSED.txt":"FAILED.txt"),error==null?checks+" native droid feedback assertions passed\n":error.toString());if(error==null)getLogger().info("DROID FEEDBACK PASSED: "+checks);else getLogger().log(java.util.logging.Level.SEVERE,"DROID FEEDBACK FAILED",error);}catch(Exception e){getLogger().log(java.util.logging.Level.SEVERE,"Probe cleanup failed",e);}Bukkit.shutdown();}
}
