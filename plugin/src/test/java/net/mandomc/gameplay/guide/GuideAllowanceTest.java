package net.mandomc.gameplay.guide;
import java.nio.file.*;
import java.time.*;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;
public class GuideAllowanceTest {
    @Test public void redirectsRefundPersonalAnswersButKeepActualSharedTokenCost() throws Exception {
        var file=Files.createTempDirectory("guide-scope").resolve("usage.properties");
        var player=UUID.randomUUID();var now=Instant.parse("2026-11-17T18:00:00Z");
        var allowance=new GuideAllowance(file,1,8000,4000,20);
        assertEquals(GuideAllowance.Denial.NONE,allowance.reserve(player,now));
        allowance.finish(player,now,500,true,false);
        var values=new java.util.Properties();try(var in=Files.newInputStream(file)){values.load(in);}
        assertEquals("500",values.getProperty("tokens"));
        assertEquals("0",values.getProperty("count."+player));
        allowance=new GuideAllowance(file,1,8000,4000,20);
        assertEquals(GuideAllowance.Denial.NONE,allowance.reserve(player,now.plusSeconds(1)));
        allowance.finish(player,now.plusSeconds(1),1000,true,true);
        assertEquals(GuideAllowance.Denial.DAILY,allowance.reserve(player,now.plusSeconds(30)));
    }
    @Test public void restartRetainsAllowanceAndFailuresRefundThePlayer() throws Exception {
        var file=Files.createTempDirectory("guide").resolve("usage.properties");var player=UUID.randomUUID();var now=Instant.parse("2026-11-16T18:00:00Z");
        var allowance=new GuideAllowance(file,2,12000,4000,20);
        assertEquals(GuideAllowance.Denial.NONE,allowance.reserve(player,now));allowance.finish(player,now,1000,true);
        allowance=new GuideAllowance(file,2,12000,4000,20);
        assertEquals(GuideAllowance.Denial.COOLDOWN,allowance.reserve(player,now.plusSeconds(5)));
        assertEquals(GuideAllowance.Denial.NONE,allowance.reserve(player,now.plusSeconds(25)));allowance.finish(player,now.plusSeconds(25),0,false);
        assertEquals(GuideAllowance.Denial.NONE,allowance.reserve(player,now.plusSeconds(26)));allowance.finish(player,now.plusSeconds(26),1000,true);
        assertEquals(GuideAllowance.Denial.DAILY,allowance.reserve(player,now.plusSeconds(60)));
        assertEquals(GuideAllowance.Denial.NONE,allowance.reserve(player,now.plus(Duration.ofDays(1))));
    }
    @Test public void sharedBudgetCannotBeSpentByManyAccountsOrResetByRestart() throws Exception {
        var file=Files.createTempDirectory("guide").resolve("usage.properties");var now=Instant.parse("2026-11-16T18:00:00Z");
        var allowance=new GuideAllowance(file,10,4000,4000,20);assertEquals(GuideAllowance.Denial.NONE,allowance.reserve(UUID.randomUUID(),now));
        allowance=new GuideAllowance(file,10,4000,4000,20);assertEquals(GuideAllowance.Denial.SHARED,allowance.reserve(UUID.randomUUID(),now));
    }
    @Test public void oldResponseCannotSpendTheNewDay() throws Exception {
        var file=Files.createTempDirectory("guide").resolve("usage.properties");var player=UUID.randomUUID();var now=Instant.parse("2026-11-16T05:59:59Z");
        var allowance=new GuideAllowance(file,1,12000,4000,20);allowance.reserve(player,now);allowance.reserve(player,now.plusSeconds(2));
        allowance.finish(player,now,100,true);assertEquals(GuideAllowance.Denial.DAILY,allowance.reserve(player,now.plusSeconds(30)));
    }
}
