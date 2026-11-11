package net.mandomc.gameplay.clans;

import java.util.*;
import net.mandomc.gameplay.travel.TeleportService;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ClanCommands implements CommandExecutor, TabCompleter {
    private record Invitation(UUID clan, long expires) {}
    private final ClanService clans;
    private final TeleportService travel;
    private final Map<UUID,Invitation> invites=new HashMap<>();
    private final CommandExecutor map;
    public ClanCommands(JavaPlugin plugin,ClanService clans,TeleportService travel) {
        this.clans=clans;this.travel=travel;
        var command=Objects.requireNonNull(plugin.getCommand("c"));map=command.getExecutor();
        command.setExecutor(this);command.setTabCompleter(this);
    }
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args) {
        if(args.length==1&&args[0].equalsIgnoreCase("map"))return map.onCommand(sender,cmd,label,args);
        if(!(sender instanceof Player p)){sender.sendMessage(clans.message("players-only"));return true;}
        try {
            if(!clans.available())throw new IllegalArgumentException("failed");
            if(args.length==0)throw new IllegalArgumentException("usage");
            String action=args[0].toLowerCase(Locale.ROOT);
            var state=clans.state(); var clan=clans.member(p);long now=System.currentTimeMillis();
            if(action.equals("create")) {
                length(args,2);var created=state.create(args[1],p.getUniqueId(),now);
                clans.saved(p,"created","clan",created.name);return true;
            }
            if(action.equals("join")) {
                length(args,2);var target=state.named(args[1]);if(target==null)throw new IllegalArgumentException("unknown-clan");
                var invite=invites.get(p.getUniqueId());
                if(invite==null||!invite.clan.equals(target.id)||invite.expires<now)throw new IllegalArgumentException("no-invite");
                state.join(target,p.getUniqueId(),clans.settings().members(),now);invites.remove(p.getUniqueId());
                clans.saved(p,"joined","clan",target.name);return true;
            }
            if(action.equals("info")) {
                if(args.length>2)throw new IllegalArgumentException("usage");
                var target=args.length==2?state.named(args[1]):clan;
                if(target==null)throw new IllegalArgumentException("unknown-clan");
                p.sendMessage(clans.message("info","clan",target.name,"members",target.members.size()+"/"+clans.settings().members(),
                        "claims",state.claims.values().stream().filter(target.id::equals).count()+"/"+clans.settings().chunks(),
                        "status",target.abandonedAt==0?"Active":"Public loot"));return true;
            }
            if(clan==null)throw new IllegalArgumentException("not-member");
            if(action.equals("warpoints")) {
                length(args,2);var target=state.named(args[1]);if(target==null)throw new IllegalArgumentException("unknown-clan");
                p.sendMessage(clans.message("warpoints","clan",clan.name,"rival",target.name,"score",""+state.score(clan.id,target.id),
                        "raid",state.canRaid(clan.id,target.id,now,clans.settings().raidThreshold())?"Eligible":"Locked"));return true;
            }
            if(clan.abandonedAt!=0)throw new IllegalArgumentException("abandoned");
            if(action.equals("home")) {
                length(args,1);if(clans.home(clan)==null)throw new IllegalArgumentException("no-home");
                travel.request(p,()->clans.member(p)==clan?clans.home(clan):null);return true;
            }
            if(action.equals("leave")) {
                length(args,1);if(clan.leader.equals(p.getUniqueId()))throw new IllegalArgumentException("leader-leave");
                clan.members.remove(p.getUniqueId());clans.saved(p,"left");return true;
            }
            if(!clan.leader.equals(p.getUniqueId()))throw new IllegalArgumentException("not-leader");
            switch(action) {
                case "invite" -> {
                    length(args,2);var target=Bukkit.getPlayerExact(args[1]);
                    if(target==null||!p.canSee(target))throw new IllegalArgumentException("unknown-player");
                    if(clans.member(target)!=null)throw new IllegalArgumentException("already-member");
                    if(clan.members.size()>=clans.settings().members())throw new IllegalArgumentException("member-limit");
                    invites.put(target.getUniqueId(),new Invitation(clan.id,now+300000));
                    target.sendMessage(clans.message("invitation","clan",clan.name));
                    p.sendMessage(clans.message("invited","player",target.getName(),"clan",clan.name));
                }
                case "kick", "leader" -> {
                    length(args,2);
                    UUID target=clan.members.stream().filter(id->args[1].equalsIgnoreCase(Bukkit.getOfflinePlayer(id).getName()))
                            .findFirst().orElseThrow(()->new IllegalArgumentException("not-clanmate"));
                    if(target.equals(p.getUniqueId()))throw new IllegalArgumentException("not-clanmate");
                    if(action.equals("kick"))clan.members.remove(target);else clan.leader=target;
                    clans.saved(p,action.equals("kick")?"kicked":"leader","player",args[1]);
                }
                case "claim" -> {
                    length(args,1);var chunk=LandRestoration.chunk(p.getLocation());
                    // Validate without allocating or refreshing a terrain snapshot.
                    state.claim(clan,chunk,clans.settings().chunks(),clans::wilderness);state.claims.remove(chunk);
                    if(!clans.land().supported(chunk)||!clans.land().ready(chunk))throw new IllegalArgumentException("unprepared");
                    state.claim(clan,chunk,clans.settings().chunks(),clans::wilderness);
                    clans.saved(p,"claimed","clan",clan.name);
                }
                case "unclaim" -> {
                    length(args,1);var chunk=LandRestoration.chunk(p.getLocation());
                    if(state.owner(chunk)!=clan)throw new IllegalArgumentException("not-owner");
                    clans.land().release(chunk,false);state.claims.remove(chunk);
                    if(clan.home!=null&&chunk.equals(new ClanState.Chunk(clan.home.world(),((int)Math.floor(clan.home.x()))>>4,
                            ((int)Math.floor(clan.home.z()))>>4)))clan.home=null;
                    clans.saved(p,"unclaimed");
                }
                case "sethome" -> {
                    length(args,1);if(clans.owner(p.getLocation())!=clan)throw new IllegalArgumentException("not-owner");
                    var l=p.getLocation();var old=clan.home;
                    clan.home=new ClanState.Home(p.getWorld().getKey().toString(),l.getX(),l.getY(),l.getZ(),l.getYaw(),l.getPitch());
                    if(clans.home(clan)==null){clan.home=old;throw new IllegalArgumentException("no-home");}
                    clans.saved(p,"home-set");
                }
                case "disband" -> {
                    length(args,1);
                    for(var e:List.copyOf(state.claims.entrySet()))if(e.getValue().equals(clan.id))clans.land().release(e.getKey(),false);
                    state.remove(clan);clans.saved(p,"disbanded","clan",clan.name);
                }
                default -> throw new IllegalArgumentException("usage");
            }
        }catch(IllegalArgumentException error){p.sendMessage(clans.message(error.getMessage()));}
        return true;
    }
    private static void length(String[] args,int count){if(args.length!=count)throw new IllegalArgumentException("usage");}
    @Override public List<String> onTabComplete(CommandSender sender,Command cmd,String label,String[] args) {
        List<String> choices=args.length==1?List.of("map","create","invite","join","leave","kick","leader","disband",
                "claim","unclaim","sethome","home","info","warpoints"):args.length==2?
                Set.of("join","info","warpoints").contains(args[0].toLowerCase(Locale.ROOT))?
                        clans.state().clans.values().stream().map(c->c.name).toList():
                        Bukkit.getOnlinePlayers().stream().filter(p->!(sender instanceof Player viewer)||viewer.canSee(p)).map(Player::getName).toList():List.of();
        return choices.stream().filter(s->s.toLowerCase(Locale.ROOT).startsWith(args[args.length-1].toLowerCase(Locale.ROOT))).toList();
    }
}
