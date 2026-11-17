package net.mandomc.gameplay.guide;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Read-only public GitHub boundary. No branch lookups, authentication, player data or local-file retrieval. */
public final class GitHubKnowledge implements AutoCloseable {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final RepositorySnapshot.Manifest manifest;
    private final Path cache;
    private final String fallback;
    private final Consumer<String> log;
    private final Callable<byte[]> fetch;
    private InputStream bundle;
    private volatile RepositorySearch search;
    private volatile GameplayEncyclopedia encyclopedia;
    private volatile boolean closed;

    public GitHubKnowledge(InputStream buildManifest, Path cacheDirectory, boolean enabled,
            String fallback, Consumer<String> log) {
        this(buildManifest, cacheDirectory, enabled, fallback, log, null);
    }

    public GitHubKnowledge(InputStream buildManifest, InputStream bundle, Path cacheDirectory, boolean enabled,
            String fallback, Consumer<String> log) {
        this(buildManifest, cacheDirectory, enabled, fallback, log, null);
        this.bundle = bundle;
    }

    GitHubKnowledge(InputStream buildManifest, Path cacheDirectory, boolean enabled,
            String fallback, Consumer<String> log, Callable<byte[]> fetch) {
        this.fetch = fetch == null ? this::download : fetch;
        this.fallback = fallback;
        this.log = log;
        RepositorySnapshot.Manifest parsed = null;
        try {
            if (enabled) parsed = RepositorySnapshot.manifest(buildManifest);
            else if (buildManifest != null) buildManifest.close();
        } catch (IOException invalid) {
            log.accept("M4-ND public repository knowledge unavailable; retained verified local guidance.");
        }
        manifest = parsed;
        cache = parsed == null ? null : cacheDirectory.resolve(parsed.commit() + ".json");
    }

    /** Call on the existing guide worker, never on Paper's tick loop. Each deployment gets its own immutable cache. */
    public void refresh() {
        if (closed || manifest == null) { closeBundle(); return; }
        if (bundle != null) {
            try {
                var snapshot = RepositorySnapshot.bundled(manifest, bundle);
                bundle = null;
                publish(snapshot, "bundled with this build");
                return;
            } catch (Exception invalid) {
                closeBundle();
                log.accept("M4-ND bundled knowledge invalid; trying this build's verified public cache.");
            }
        }
        if (!manifest.retrievable()) {
            log.accept("M4-ND repository knowledge disabled for a modified source build; retained local guidance.");
            return;
        }
        try {
            RepositorySnapshot snapshot = null;
            boolean cached = false;
            if (Files.isRegularFile(cache, LinkOption.NOFOLLOW_LINKS)) {
                try { snapshot = RepositorySnapshot.cache(manifest, cache); cached = true; }
                catch (IOException invalid) { log.accept("M4-ND ignored an invalid public knowledge cache."); }
            }
            if (snapshot == null) {
                snapshot = RepositorySnapshot.archive(manifest, fetch.call());
                try { snapshot.save(cache); }
                catch (IOException unavailable) { log.accept("M4-ND could not persist its verified public cache; using it for this session."); }
            }
            publish(snapshot, cached ? "local cache" : "verified public download");
        } catch (Exception unavailable) {
            if (unavailable instanceof InterruptedException) Thread.currentThread().interrupt();
            if (!closed) log.accept("M4-ND could not refresh build-pinned public knowledge; retained local guidance.");
        }
    }

    private void publish(RepositorySnapshot snapshot, String origin) {
        var index = new RepositorySearch(snapshot, fallback);
        var records = new GameplayEncyclopedia(snapshot, fallback);
        if (closed) return;
        encyclopedia = records;
        search = index;
        log.accept("M4-ND public knowledge ready: " + snapshot.documents().size() + " approved sources, "
                + index.passageCount() + " passages @ " + manifest.commit().substring(0, 12)
                + " (" + origin + "; " + records.size() + " encyclopedia records).");
    }

    private void closeBundle() {
        if (bundle == null) return;
        try { bundle.close(); }
        catch (IOException unavailable) { log.accept("M4-ND could not close its bundled knowledge stream."); }
        bundle = null;
    }

    private byte[] download() throws IOException, InterruptedException {
        URI endpoint = URI.create("https://api.github.com/repos/" + RepositorySnapshot.REPOSITORY
                + "/zipball/" + manifest.commit());
        var response = request(endpoint);
        if (response.statusCode() == 302) {
            URI target;
            try { target = URI.create(response.headers().firstValue("location").orElseThrow()); }
            catch (RuntimeException invalid) { throw new IOException("Missing public archive redirect", invalid); }
            if (!trustedArchive(target, manifest.commit())) throw new IOException("Untrusted public archive redirect");
            response = request(target);
        }
        else if (response.statusCode() == 403 || response.statusCode() == 429) {
            // A shared unauthenticated REST quota must not require a token; the public archive is still commit-pinned.
            response = request(URI.create("https://codeload.github.com/" + RepositorySnapshot.REPOSITORY
                    + "/zip/" + manifest.commit()));
        }
        if (response.statusCode() != 200) throw new IOException("Public repository unavailable");
        return response.body();
    }

    static boolean trustedArchive(URI target, String commit) {
        String path = target.getPath();
        return "https".equals(target.getScheme()) && "codeload.github.com".equals(target.getHost())
                && target.getPort() == -1 && target.getUserInfo() == null && target.getQuery() == null
                && target.getFragment() == null && commit.matches("[a-f0-9]{40}")
                && (path.equals("/" + RepositorySnapshot.REPOSITORY + "/legacy.zip/" + commit)
                    || path.equals("/" + RepositorySnapshot.REPOSITORY + "/zip/" + commit));
    }

    private HttpResponse<byte[]> request(URI endpoint) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(45))
                .header("User-Agent", "MandoMC-public-knowledge/1.0").GET().build();
        return client.send(request, info -> new BoundedBody(RepositorySnapshot.MAX_ARCHIVE));
    }

    public List<GuideKnowledge.Note> find(String question) {
        var index = search;
        if (index == null) return List.of();
        var notes = new ArrayList<GuideKnowledge.Note>();
        var records = encyclopedia;
        if (records != null) notes.addAll(records.find(question));
        notes.addAll(index.find(question));
        return List.copyOf(notes);
    }
    public String commit() { return manifest == null ? "" : manifest.commit(); }
    public int passageCount() { var index = search; return index == null ? 0 : index.passageCount(); }
    @Override public void close() { closed = true; closeBundle(); client.shutdownNow(); }

    /** Streaming subscriber cancels oversized responses before allocating an unbounded archive body. */
    static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final int maximum;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        BoundedBody(int maximum) { this.maximum = maximum; }
        @Override public CompletionStage<byte[]> getBody() { return result; }
        @Override public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) { subscription.cancel(); return; }
            this.subscription = subscription;
            subscription.request(1);
        }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (result.isDone()) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > maximum - bytes.size()) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("Public archive exceeds download bound"));
                    return;
                }
                byte[] part = new byte[buffer.remaining()];
                buffer.get(part);
                bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        @Override public void onError(Throwable error) { result.completeExceptionally(error); }
        @Override public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
