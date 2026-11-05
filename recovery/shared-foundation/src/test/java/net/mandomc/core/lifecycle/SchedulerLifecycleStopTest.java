package net.mandomc.core.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;

import net.mandomc.gameplay.lottery.task.LotteryBroadcastTask;
import net.mandomc.gameplay.lottery.task.LotteryScheduler;
import net.mandomc.world.ilum.ParkourTimerDisplay;
import net.mandomc.world.ilum.manager.ParkourLeaderboardManager;

class SchedulerLifecycleStopTest {

    @Test
    void lotterySchedulerStopCancelsActiveTask() throws Exception {
        FakeTask task = new FakeTask(false);
        setStaticField(LotteryScheduler.class, "drawTask", task);
        setStaticField(LotteryScheduler.class, "lastRun", LocalDateTime.now());

        LotteryScheduler.stop();

        assertEquals(1, task.cancelCount);
        assertNull(getStaticField(LotteryScheduler.class, "lastRun"));
    }

    @Test
    void lotteryBroadcastStopCancelsActiveTask() throws Exception {
        FakeTask task = new FakeTask(false);
        setStaticField(LotteryBroadcastTask.class, "broadcastTask", task);

        LotteryBroadcastTask.stop();

        assertEquals(1, task.cancelCount);
    }

    @Test
    void parkourTimerStopCancelsActiveTask() throws Exception {
        ParkourTimerDisplay timerDisplay = new ParkourTimerDisplay(null, null, null);
        FakeTask task = new FakeTask(false);
        setField(timerDisplay, "timerTask", task);

        timerDisplay.stop();

        assertEquals(1, task.cancelCount);
    }

    @Test
    void leaderboardStopAutoUpdateSkipsCancelledTask() throws Exception {
        ParkourLeaderboardManager manager = new ParkourLeaderboardManager(null, null, null);
        FakeTask task = new FakeTask(true);
        setField(manager, "autoUpdateTask", task);

        manager.stopAutoUpdate();

        assertEquals(0, task.cancelCount);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private void setStaticField(Class<?> target, String fieldName, Object value) throws Exception {
        Field field = target.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }

    private Object getStaticField(Class<?> target, String fieldName) throws Exception {
        Field field = target.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    private static final class FakeTask implements BukkitTask {
        private boolean cancelled;
        private int cancelCount;

        private FakeTask(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public int getTaskId() {
            return 0;
        }

        @Override
        public Plugin getOwner() {
            return null;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public void cancel() {
            cancelCount++;
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
