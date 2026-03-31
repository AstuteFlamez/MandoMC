package net.mandomc.gameplay.lottery;

import net.mandomc.gameplay.lottery.task.LotteryScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LotterySchedulerDrawWindowTest {

    @Test
    void alreadyRanOnlyBlocksSameCalendarMinute() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 4, 7, 10, 30);
        setStaticField("lastRun", now.minusWeeks(1));

        assertFalse(invokeAlreadyRan(now), "prior week draw should not block current draw");

        setStaticField("lastRun", now);
        assertTrue(invokeAlreadyRan(now), "same minute draw should be blocked");
    }

    private boolean invokeAlreadyRan(LocalDateTime now) throws Exception {
        Method method = LotteryScheduler.class.getDeclaredMethod("alreadyRan", LocalDateTime.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, now);
    }

    private void setStaticField(String fieldName, Object value) throws Exception {
        Field field = LotteryScheduler.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}
