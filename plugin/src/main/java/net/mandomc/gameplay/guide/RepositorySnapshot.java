package net.mandomc.gameplay.guide;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipInputStream;
import net.mandomc.config.AtomicData;

/** Only build-approved public source bytes from one exact commit can enter the knowledge cache. */
public final class RepositorySnapshot {
    public static final String REPOSITORY = "AstuteFlamez/MandoMC";
    public static final int MAX_ARCHIVE = 32 * 1024 * 1024;
    private static final int MAX_EXPANDED = 64 * 1024 * 1024;
    private static final Set<String> RESOURCE_AREAS = Set.of(
            "arrival", "clans", "combat", "crates", "economy", "equipment", "events", "items", "leaderboards",
            "mobs", "parkour", "rules", "territory", "travel", "trophies", "tutorial", "vehicles", "worlds");
    private static final Set<String> CODE_AREAS = Set.of(
            "arrival", "auction", "boba", "credits", "dungeon", "explosions", "leaderboards", "messaging",
            "territory", "trophies", "clans", "combat", "crates", "events", "herd", "ilum", "items", "jawa", "mining", "mobs",
            "progression", "rules", "saber", "travel", "tutorial", "vehicles", "worlds");
    public record Source(String path, String sha256, int bytes) { }
    public record Manifest(String commit, boolean clean, String hash, Map<String, Source> sources) {
        public Manifest { sources = Map.copyOf(sources); }
        public boolean retrievable() { return clean; }
    }
    private final Manifest manifest;
    private final Map<String, String> documents;

    private RepositorySnapshot(Manifest manifest, Map<String, String> documents) {
        this.manifest = manifest;
        this.documents = Collections.unmodifiableMap(new TreeMap<>(documents));
    }
    public Manifest manifest() { return manifest; }
    public Map<String, String> documents() { return documents; }

    public static Manifest manifest(InputStream input) throws IOException {
        if (input == null) throw new IOException("No build-pinned public knowledge manifest");
        byte[] raw;
        try (input) { raw = input.readNBytes(262145); }
        if (raw.length > 262144) throw new IOException("Knowledge manifest exceeds size bound");
        try {
            var root = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)).getAsJsonObject();
            String commit = root.get("commit").getAsString();
            if (root.get("schemaVersion").getAsInt() != 1 || !REPOSITORY.equals(root.get("repository").getAsString())
                    || !commit.matches("[a-f0-9]{40}")) throw new IOException("Invalid knowledge source identity");
            var sources = new LinkedHashMap<String, Source>();
            long total = 0;
            for (var entry : root.getAsJsonArray("sources")) {
                var row = entry.getAsJsonObject();
                String path = row.get("path").getAsString(), hash = row.get("sha256").getAsString();
                int bytes = row.get("bytes").getAsInt();
                if (!approved(path) || !hash.matches("[a-f0-9]{64}") || bytes < 0 || bytes > 131072
                        || sources.putIfAbsent(path, new Source(path, hash, bytes)) != null)
                    throw new IOException("Invalid approved knowledge source");
                total += bytes;
            }
            if (sources.isEmpty() || sources.size() > 512 || total > 8388608)
                throw new IOException("Knowledge manifest outside size bounds");
            return new Manifest(commit, root.get("clean").getAsBoolean(), digest(raw), sources);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid public knowledge manifest", invalid);
        }
    }

    public static boolean approved(String path) {
        if (!path.matches("[a-zA-Z0-9_./-]+") || path.startsWith("/") || path.contains("..")) return false;
        if (Set.of("docs/commands.md", "docs/player-experience.md",
                "plugin/src/main/resources/admin/utilities.yml",
                "server/config/common/mandomc/admin/utilities.yml",
                "plugin/src/main/resources/language/handbook.yml",
                "server/config/common/mandomc/language/handbook.yml").contains(path)) return true;
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (path.startsWith("docs/features/") && path.endsWith(".md"))
            return List.of("maintenance", "security", "deployment", "dev-").stream().noneMatch(name::contains);
        String[] parts = path.split("/");
        if ((path.startsWith("plugin/src/main/resources/") || path.startsWith("server/config/common/mandomc/"))
                && path.endsWith(".yml"))
            return parts.length > 5 && RESOURCE_AREAS.contains(parts[4]);
        return path.startsWith("plugin/src/main/java/net/mandomc/gameplay/") && path.endsWith(".java")
                && parts.length > 8 && CODE_AREAS.contains(parts[7]);
    }

    public static RepositorySnapshot archive(Manifest manifest, byte[] archive) throws IOException {
        if (!manifest.retrievable() || archive.length > MAX_ARCHIVE)
            throw new IOException("Unpinned or oversized repository archive");
        var documents = new HashMap<String, String>();
        long expanded = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                String name = entry.getName();
                if (name.startsWith("/") || name.contains("\\") || Arrays.asList(name.split("/")).contains(".."))
                    throw new IOException("Unsafe repository archive path");
                int slash = name.indexOf('/');
                String path = slash < 0 ? "" : name.substring(slash + 1);
                Source source = manifest.sources().get(path);
                var body = source == null ? null : new ByteArrayOutputStream(source.bytes());
                byte[] buffer = new byte[8192];
                int count, size = 0;
                while ((count = zip.read(buffer)) != -1) {
                    expanded += count;
                    size += count;
                    if (expanded > MAX_EXPANDED || source != null && size > source.bytes())
                        throw new IOException("Repository archive exceeds expanded size bounds");
                    if (body != null) body.write(buffer, 0, count);
                }
                if (source != null) {
                    byte[] bytes = body.toByteArray();
                    verify(source, bytes);
                    if (documents.putIfAbsent(path, new String(bytes, StandardCharsets.UTF_8)) != null)
                        throw new IOException("Duplicate repository source");
                }
            }
        }
        if (!documents.keySet().equals(manifest.sources().keySet()))
            throw new IOException("Repository archive does not match this build's approved source set");
        return new RepositorySnapshot(manifest, documents);
    }

    public static RepositorySnapshot cache(Manifest manifest, Path path) throws IOException {
        if (!manifest.retrievable() || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || Files.size(path) > MAX_ARCHIVE) throw new IOException("Invalid cache size or version");
        try {
            var root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            if (!manifest.commit().equals(root.get("commit").getAsString())
                    || !manifest.hash().equals(root.get("manifestHash").getAsString()))
                throw new IOException("Knowledge cache belongs to a different build");
            var documents = new HashMap<String, String>();
            var rows = root.getAsJsonObject("documents");
            if (!rows.keySet().equals(manifest.sources().keySet())) throw new IOException("Cache source set differs");
            for (String name : rows.keySet()) {
                String content = rows.get(name).getAsString();
                verify(manifest.sources().get(name), content.getBytes(StandardCharsets.UTF_8));
                documents.put(name, content);
            }
            return new RepositorySnapshot(manifest, documents);
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid knowledge cache", invalid);
        }
    }

    public void save(Path path) throws IOException {
        var root = new JsonObject();
        root.addProperty("commit", manifest.commit());
        root.addProperty("manifestHash", manifest.hash());
        root.add("documents", new Gson().toJsonTree(documents));
        String data = root.toString();
        if (data.getBytes(StandardCharsets.UTF_8).length > MAX_ARCHIVE) throw new IOException("Cache exceeds size bound");
        AtomicData.write(path, data);
    }

    private static void verify(Source source, byte[] bytes) throws IOException {
        if (bytes.length != source.bytes() || !digest(bytes).equals(source.sha256()))
            throw new IOException("Public source does not match deployed build: " + source.path());
    }
    static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
