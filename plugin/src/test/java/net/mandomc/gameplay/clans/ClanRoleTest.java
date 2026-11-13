package net.mandomc.gameplay.clans;

import java.nio.file.*;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClanRoleTest {
    private final ClanState state=new ClanState();
    private ClanState.Clan clan(){return state.create("Alpha",UUID.randomUUID(),1);}
    @Test public void newJoinsAreRecruitsAndOnlyLeadersAssignHigherRoles(){
        var c=clan();var member=UUID.randomUUID();state.join(c,member,4,1);
        assertEquals(ClanRole.LEADER,state.role(c,c.leader));assertEquals(ClanRole.RECRUIT,state.role(c,member));
        assertThrows(IllegalArgumentException.class,()->state.changeRole(c,member,member,true));
        state.changeRole(c,c.leader,member,true);assertEquals(ClanRole.MEMBER,state.role(c,member));
        state.changeRole(c,c.leader,member,true);assertEquals(ClanRole.OFFICER,state.role(c,member));
        assertThrows(IllegalArgumentException.class,()->state.changeRole(c,c.leader,member,true));
        state.changeRole(c,c.leader,member,false);assertEquals(ClanRole.MEMBER,state.role(c,member));
        state.changeRole(c,c.leader,member,false);assertEquals(ClanRole.RECRUIT,state.role(c,member));
        assertThrows(IllegalArgumentException.class,()->state.changeRole(c,c.leader,member,false));
    }
    @Test public void officerCanKickOnlyLowerRanksAndCannotTransferOrChangeRoles(){
        var c=clan();var officer=UUID.randomUUID();var peer=UUID.randomUUID();var recruit=UUID.randomUUID();
        for(var id:List.of(officer,peer,recruit))state.join(c,id,4,1);
        c.roles.put(officer,ClanRole.OFFICER);c.roles.put(peer,ClanRole.OFFICER);
        assertThrows(IllegalArgumentException.class,()->state.kick(c,officer,c.leader));
        assertThrows(IllegalArgumentException.class,()->state.kick(c,officer,peer));
        assertThrows(IllegalArgumentException.class,()->state.kick(c,recruit,officer));
        assertThrows(IllegalArgumentException.class,()->state.transfer(c,officer,recruit));
        assertThrows(IllegalArgumentException.class,()->state.changeRole(c,officer,recruit,true));
        state.kick(c,officer,recruit);assertFalse(c.members.contains(recruit));assertFalse(c.roles.containsKey(recruit));
        state.kick(c,c.leader,peer);assertFalse(c.members.contains(peer));
    }
    @Test public void leadershipTransferHasOneLeaderAndFormerLeaderBecomesMember(){
        var c=clan();var original=c.leader;var next=UUID.randomUUID();state.join(c,next,4,1);
        state.transfer(c,original,next);assertEquals(next,c.leader);assertEquals(ClanRole.LEADER,state.role(c,next));
        assertEquals(ClanRole.MEMBER,state.role(c,original));assertFalse(c.roles.containsKey(next));
        assertThrows(IllegalArgumentException.class,()->state.transfer(c,original,next));
        assertThrows(IllegalArgumentException.class,()->state.changeRole(c,next,next,false));
    }
    @Test public void legacyMembersAndOfficerAliasMigrateWithoutEvictionAndRolesPersist()throws Exception {
        var c=clan();var legacy=UUID.randomUUID();var officer=UUID.randomUUID();c.members.add(legacy);c.members.add(officer);
        var yaml=YamlConfiguration.loadConfiguration(new java.io.StringReader(ClanStorage.encode(state)));
        var rows=yaml.getMapList("clans");@SuppressWarnings("unchecked") var row=(Map<String,Object>)rows.getFirst();
        row.remove("roles");yaml.set("clans",rows);Path file=Files.createTempFile("clan-roles",".yml");
        try {
            Files.writeString(file,yaml.saveToString());var loaded=ClanStorage.load(file);var clan=loaded.clans.get(c.id);
            assertEquals(c.members,clan.members);assertEquals(ClanRole.MEMBER,loaded.role(clan,legacy));assertEquals(ClanRole.LEADER,loaded.role(clan,c.leader));
            row.put("roles",Map.of(officer.toString(),"co_leader"));yaml.set("clans",rows);Files.writeString(file,yaml.saveToString());
            loaded=ClanStorage.load(file);clan=loaded.clans.get(c.id);assertEquals(ClanRole.OFFICER,loaded.role(clan,officer));
            Files.writeString(file,ClanStorage.encode(loaded));var again=ClanStorage.load(file);assertEquals(ClanRole.OFFICER,again.role(again.clans.get(c.id),officer));
            assertFalse(Files.readString(file).contains("co_leader"));
        }finally{Files.deleteIfExists(file);}
    }
    @Test public void recruitDoesNotLoseOrdinaryMembershipAndRoleAuthorityRejectsOutsiders(){
        var c=clan();var recruit=UUID.randomUUID();state.join(c,recruit,4,1);
        assertEquals(c,state.member(recruit));assertNull(state.role(c,UUID.randomUUID()));
        assertFalse(ClanRole.RECRUIT.manages());assertFalse(ClanRole.MEMBER.manages());assertTrue(ClanRole.OFFICER.manages());assertTrue(ClanRole.LEADER.manages());
    }
}
