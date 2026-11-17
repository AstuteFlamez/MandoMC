package net.mandomc.gameplay.guide;

import java.util.*;
import java.util.function.Supplier;
import net.mandomc.gameplay.items.RecipeSearch;
import org.bukkit.configuration.ConfigurationSection;

/** These answers read current authorities on the server thread and never reach the AI or shared cache. */
final class GuideLiveAnswers {
    private GuideLiveAnswers() { }

    static Optional<String> answer(String question, ConfigurationSection config, Supplier<String> balance,
            Supplier<String> events) {
        String q = RecipeSearch.normalize(question);
        if (q.matches("(?:what is |what s |whats |show |check |tell me )?my (?:current )?(?:balance|credits|money)")
                || q.matches("how (?:many credits|much money) do i have"))
            return Optional.of(config.getString("live.balance", "Your current balance is {balance} credits.")
                    .replace("{balance}", balance.get()));
        if (q.matches(".*\\b(?:event|events|koth|jabba|beskar event)\\b.*")
                && q.matches(".*\\b(?:now|current|currently|next|when|schedule|scheduled|running|active|start|starts)\\b.*"))
            return Optional.of(events.get());
        return Optional.empty();
    }
}
