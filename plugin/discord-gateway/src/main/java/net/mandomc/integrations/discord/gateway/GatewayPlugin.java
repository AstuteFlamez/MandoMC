package net.mandomc.integrations.discord.gateway;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.plugin.java.JavaPlugin;

/** Separate optional plugin: Gateway/library failures cannot disable MandoMC gameplay. */
public final class GatewayPlugin extends JavaPlugin {
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("mandomc-discord-gateway").daemon().factory());
    private final AtomicInteger queued = new AtomicInteger();
    private volatile JDA jda;
    private volatile boolean closed;
    private HttpClient http;
    private URI origin;
    private String secret, guild, category;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!getConfig().getBoolean("enabled", false)) { getLogger().info("Discord Gateway disabled on this server."); return; }
        try {
            if (getConfig().getInt("schema-version") != 1) throw new IllegalArgumentException("schema");
            origin = URI.create(getConfig().getString("endpoint", ""));
            if (!"https".equals(origin.getScheme()) || origin.getHost() == null || origin.getUserInfo() != null
                    || origin.getQuery() != null || origin.getFragment() != null || !origin.getPath().isEmpty())
                throw new IllegalArgumentException("endpoint");
            guild = getConfig().getString("guild-id", ""); category = getConfig().getString("ticket-category-id", "");
            if (!guild.matches("[0-9]{17,20}") || !category.matches("[0-9]{17,20}")) throw new IllegalArgumentException("guild/category");
            String token = Files.readString(getDataFolder().toPath().resolve("private/discord-token")).trim();
            secret = Files.readString(getDataFolder().toPath().resolve("private/gateway-secret")).trim();
            if (token.isBlank() || secret.length() < 32) throw new IllegalArgumentException("credentials");
            http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
            io.execute(() -> connect(token));
            io.scheduleWithFixedDelay(this::presence, 5, 30, TimeUnit.SECONDS);
        } catch (Exception error) { getLogger().warning("Discord Gateway unavailable; check its private credentials and configuration. Gameplay is unaffected."); }
    }

    private void connect(String token) {
        try {
            JDA connected = JDABuilder.createLight(token, GatewayIntent.GUILD_MEMBERS,
                    GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setStatus(OnlineStatus.ONLINE).setActivity(Activity.watching("MandoMC"))
                    .setAutoReconnect(true).setEnableShutdownHook(false).addEventListeners(new Events()).build();
            if (closed) connected.shutdownNow(); else jda = connected;
        } catch (Exception error) { getLogger().warning("Discord Gateway connection failed; token withheld. Check the configured account and intents."); }
    }

    private final class Events extends ListenerAdapter {
        @Override public void onReady(ReadyEvent event) {
            getLogger().info("MandoMC Discord Gateway ready: online, member auto-role and ticket DM relay.");
        }
        @Override public void onGuildMemberJoin(GuildMemberJoinEvent event) {
            if (!guild.equals(event.getGuild().getId()) || event.getUser().isBot()) return;
            var body = new JsonObject(); body.addProperty("event", "join"); body.addProperty("user", event.getUser().getId());
            submit(body);
        }
        @Override public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;
            if (event.isFromGuild()) {
                if (!guild.equals(event.getGuild().getId()) || !event.isFromType(ChannelType.TEXT)
                        || event.getChannel().asTextChannel().getParentCategoryIdLong() != Long.parseLong(category)) return;
            } else if (!event.isFromType(ChannelType.PRIVATE)) return;
            var message = event.getMessage(); var body = new JsonObject();
            body.addProperty("event", "message"); body.addProperty("id", message.getId());
            body.addProperty("user", event.getAuthor().getId()); body.addProperty("name", event.getAuthor().getEffectiveName());
            body.addProperty("channel", event.getChannel().getId());
            if (event.isFromGuild()) body.addProperty("guild", event.getGuild().getId());
            body.addProperty("content", message.getContentRaw());
            var attachments = new JsonArray(); message.getAttachments().forEach(value -> attachments.add(value.getUrl()));
            body.add("attachments", attachments); submit(body);
        }
    }

    private void submit(JsonObject body) {
        if (closed) return;
        if (queued.incrementAndGet() > 128) { queued.decrementAndGet(); getLogger().warning("Discord relay queue full; source messages remain in Discord."); return; }
        io.execute(() -> deliver(body, 0));
    }
    private void deliver(JsonObject body, int attempt) {
        if (closed) { queued.decrementAndGet(); return; }
        try { request("/gateway/event", body); queued.decrementAndGet(); }
        catch (Exception error) {
            if (attempt < 2 && !closed) io.schedule(() -> deliver(body, attempt + 1), 2L << attempt, TimeUnit.SECONDS);
            else { queued.decrementAndGet(); getLogger().warning("Discord ticket relay unavailable; source message retained. No message content logged."); }
        }
    }
    private void presence() {
        if (closed || jda == null || http == null || jda.getStatus() != JDA.Status.CONNECTED) return;
        try {
            var data = request("/gateway/status", null);
            String text = data.get("stale").getAsBoolean() ? "Server status unavailable" : data.get("online").getAsInt() + " players online";
            jda.getPresence().setActivity(Activity.watching(text));
        } catch (Exception ignored) { /* The existing presence survives a temporary bridge outage. */ }
    }
    private JsonObject request(String path, JsonObject body) throws Exception {
        var request = HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(12))
                .header("Authorization", "Bearer " + secret).header("Content-Type", "application/json");
        if (body == null) request.GET(); else request.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var input = response.body()) {
            if (response.statusCode() != 200) throw new IllegalStateException("Gateway bridge HTTP " + response.statusCode());
            byte[] bytes = input.readNBytes(65537);
            if (bytes.length > 65536) throw new IllegalStateException("Gateway response too large");
            return JsonParser.parseString(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Override public void onDisable() {
        closed = true; io.shutdownNow();
        if (jda != null) jda.shutdownNow();
        if (http != null) http.shutdownNow();
    }
}
