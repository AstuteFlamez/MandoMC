package net.mandomc.gameplay.events;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/** One current number per recipient; lag never replays stale countdown titles. */
public final class EventEndCountdown {
    public static final String DEFAULT_TITLE = "<red><bold>Event ending in <seconds>";
    public static final String DEFAULT_SUBTITLE = "<gold><event>";
    private final String title;
    private final String subtitle;
    private final Map<UUID, Integer> sent = new HashMap<>();

    public EventEndCountdown(String title, String subtitle) {
        if (title == null || title.isBlank() || subtitle == null) {
            throw new IllegalArgumentException("events.yml: invalid ending-countdown title/subtitle");
        }
        this.title = title;
        this.subtitle = subtitle;
    }

    public static long secondsUntilStop(ZonedDateTime now, int endMinute) {
        var end = now.truncatedTo(ChronoUnit.HOURS).plusMinutes(endMinute);
        if (end.isBefore(now)) end = end.plusHours(1);
        var remaining = Duration.between(now, end);
        return remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0);
    }

    public static int number(long scheduledSeconds, long timeoutTicks) {
        long timedSeconds = timeoutTicks == Long.MAX_VALUE ? Long.MAX_VALUE
                : Math.max(0, timeoutTicks / 20 + (timeoutTicks % 20 > 0 ? 1 : 0));
        long remaining = Math.min(scheduledSeconds, timedSeconds);
        return remaining >= 1 && remaining <= 10 ? (int) remaining : 0;
    }

    public int send(ManagedEvent event, long scheduledSeconds, Collection<? extends Player> players) {
        int seconds = number(scheduledSeconds, event.endingInTicks());
        if (!event.running() || seconds == 0) {
            reset();
            return 0;
        }
        var current = new HashSet<UUID>();
        int count = 0;
        var tokens = new net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[] {
            Placeholder.unparsed("seconds", Integer.toString(seconds)),
            Placeholder.unparsed("event", event.displayName())
        };
        var mini = MiniMessage.miniMessage();
        var message = Title.title(mini.deserialize(title, tokens), mini.deserialize(subtitle, tokens),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(150)));
        for (var player : players) {
            if (!player.isOnline() || player.isDead() || !event.relevant(player)) continue;
            UUID id = player.getUniqueId();
            current.add(id);
            if (sent.getOrDefault(id, 0) == seconds) continue;
            player.showTitle(message);
            sent.put(id, seconds);
            count++;
        }
        sent.keySet().retainAll(current);
        return count;
    }

    public void reset() { sent.clear(); }
}
