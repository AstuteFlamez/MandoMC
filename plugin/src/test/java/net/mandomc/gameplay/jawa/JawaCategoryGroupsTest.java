package net.mandomc.gameplay.jawa;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class JawaCategoryGroupsTest {
    private final List<String> catalog=List.of("plants","farming","ocean","utility");
    @Test public void newGroupAssignmentsAreDistinctAndIndependentGroupsCanReuseCategories(){
        var members=new HashMap<UUID,String>();for(int i=0;i<4;i++)members.put(UUID.randomUUID(),"");
        var first=JawaCategoryGroups.plan(UUID.randomUUID().toString(),Map.of(),members,catalog);
        var other=JawaCategoryGroups.plan(UUID.randomUUID().toString(),Map.of(),members,catalog);
        assertEquals(4,new HashSet<>(first.values()).size());assertEquals(new HashSet<>(catalog),new HashSet<>(other.values()));
    }
    @Test public void legacyDuplicatesAreRepairedWithoutStealingAnotherMembersUniqueCategory(){
        var a=new UUID(0,1);var b=new UUID(0,2);var c=new UUID(0,3);
        var planned=JawaCategoryGroups.plan(UUID.randomUUID().toString(),Map.of(),Map.of(a,"plants",b,"plants",c,"ocean"),catalog);
        assertEquals("plants",planned.get(a));assertEquals("ocean",planned.get(c));assertNotEquals(planned.get(a),planned.get(b));assertEquals(3,new HashSet<>(planned.values()).size());
    }
    @Test public void unloadedMembersKeepReservationsAndRestartKeepsEveryChoice()throws Exception {
        var path=Files.createTempDirectory("jawa-groups").resolve("groups.yml");var groups=new JawaCategoryGroups(path);var id=UUID.randomUUID().toString();var a=UUID.randomUUID();var b=UUID.randomUUID();
        groups.assign(id,Map.of(a,"plants"),catalog);groups.assign(id,Map.of(b,"plants"),catalog);
        var restored=new JawaCategoryGroups(path);assertEquals("plants",restored.category(id,a));assertNotEquals("plants",restored.category(id,b));
        var choice=restored.category(id,b);restored.assign(id,Map.of(a,"ocean",b,"utility"),List.of("utility","ocean","farming","plants"));
        assertEquals("plants",restored.category(id,a));assertEquals(choice,restored.category(id,b));
    }
    @Test public void tooManyMembersAreRejectedWithoutChangingTheSavedGroup()throws Exception {
        var path=Files.createTempDirectory("jawa-groups").resolve("groups.yml");var groups=new JawaCategoryGroups(path);var id=UUID.randomUUID().toString();var a=UUID.randomUUID();var b=UUID.randomUUID();var c=UUID.randomUUID();
        groups.assign(id,Map.of(a,"plants",b,"ocean"),List.of("plants","ocean"));
        assertThrows(IllegalArgumentException.class,()->groups.assign(id,Map.of(c,""),List.of("plants","ocean")));
        assertNull(groups.category(id,c));assertEquals("plants",new JawaCategoryGroups(path).category(id,a));
    }
    @Test public void failedSaveCannotExposeAnUnreservedCategory()throws Exception {
        var path=Files.createTempDirectory("jawa-groups").resolve("groups.yml");var groups=new JawaCategoryGroups(path);var id=UUID.randomUUID().toString();var a=UUID.randomUUID();groups.assign(id,Map.of(a,"plants"),catalog);
        Files.delete(path);Files.createDirectory(path);Files.writeString(path.resolve("occupied"),"occupied");var b=UUID.randomUUID();
        assertThrows(Exception.class,()->groups.assign(id,Map.of(b,""),catalog));assertNull(groups.category(id,b));assertEquals("plants",groups.category(id,a));
    }
}
