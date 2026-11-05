package net.mandomc.gameplay.lottery;

import net.mandomc.gameplay.lottery.task.LotteryScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class LotteryConfigValidationTest {

    @Test
    void invalidDrawDayDoesNotThrowAndDoesNotMatchDrawWindow() throws Exception {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("day", "NOT_A_DAY");
        cfg.set("hour", 12);
        cfg.set("minute", 0);

        Method method = LotteryScheduler.class.getDeclaredMethod("isDrawTime", LocalDateTime.class, org.bukkit.configuration.ConfigurationSection.class);
        method.setAccessible(true);

        LocalDateTime now = LocalDateTime.of(2026, 5, 3, 12, 0);
        boolean result = assertDoesNotThrow(() -> (boolean) method.invoke(null, now, cfg));
        assertFalse(result);
    }
}
