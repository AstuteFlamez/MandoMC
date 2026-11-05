package net.mandomc.gameplay.lottery.storage;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.mandomc.core.storage.JsonRepository;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JSON-backed repository for the singleton {@link LotteryState}.
 *
 * Stores a single lottery state record (current pot + ticket map) keyed by
 * {@link LotteryState#CURRENT_ID}. The backing file is
 * {@code gambling/lottery_state.json}.
 *
 * Usage:
 * <pre>
 *   LotteryState state = repository.getState();
 *   // ... modify logic ...
 *   repository.saveState(updatedState);
 *   repository.flush();
 * </pre>
 */
public class LotteryRepository extends JsonRepository<LotteryState, String> {

    /**
     * Creates the lottery repository.
     *
     * @param plugin the plugin owning the data folder
     */
    public LotteryRepository(Plugin plugin) {
        super(plugin, "gambling/lottery_state.json");
    }

    // -----------------------------------------------------------------------
    // Domain convenience methods
    // -----------------------------------------------------------------------

    /**
     * Returns the current lottery state, or an empty state if none persisted.
     *
     * @return the current {@link LotteryState}
     */
    public LotteryState getState() {
        return findById(LotteryState.CURRENT_ID).orElse(LotteryState.empty());
    }

    /**
     * Stores the provided state in the cache. Call {@link #flush()} to persist.
     *
     * @param state the new lottery state
     */
    public void saveState(LotteryState state) {
        save(state);
    }

    // -----------------------------------------------------------------------
    // JsonRepository template methods
    // -----------------------------------------------------------------------

    @Override
    protected void populate(String json, Map<String, LotteryState> target) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        if (!root.has(LotteryState.CURRENT_ID)) return;

        JsonObject obj = root.getAsJsonObject(LotteryState.CURRENT_ID);
        double pot = obj.has("pot") ? obj.get("pot").getAsDouble() : 0.0;

        Map<UUID, Integer> tickets = new HashMap<>();
        if (obj.has("tickets")) {
            JsonObject ticketObj = obj.getAsJsonObject("tickets");
            for (String key : ticketObj.keySet()) {
                try {
                    tickets.put(UUID.fromString(key), ticketObj.get(key).getAsInt());
                } catch (IllegalArgumentException ignored) {
                    // skip malformed UUIDs
                }
            }
        }

        target.put(LotteryState.CURRENT_ID, new LotteryState(LotteryState.CURRENT_ID, pot, tickets));
    }

    @Override
    protected String serialize(Map<String, LotteryState> data) {
        JsonObject root = new JsonObject();

        LotteryState state = data.getOrDefault(LotteryState.CURRENT_ID, LotteryState.empty());
        JsonObject obj = new JsonObject();
        obj.addProperty("pot", state.getPot());

        JsonObject tickets = new JsonObject();
        for (Map.Entry<UUID, Integer> entry : state.getTickets().entrySet()) {
            tickets.addProperty(entry.getKey().toString(), entry.getValue());
        }
        obj.add("tickets", tickets);

        root.add(LotteryState.CURRENT_ID, obj);
        return new GsonBuilder().setPrettyPrinting().create().toJson(root);
    }

    @Override
    protected String idOf(LotteryState entity) {
        return entity.getId();
    }
}
