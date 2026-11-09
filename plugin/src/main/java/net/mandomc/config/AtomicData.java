package net.mandomc.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Small lifecycle journals are durable before their corresponding world/inventory mutation. */
public final class AtomicData {
    private AtomicData() {}

    public static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (var channel =
                FileChannel.open(
                        temporary,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)) {
            var data = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
            while (data.hasRemaining()) channel.write(data);
            channel.force(true);
        }
        Files.move(
                temporary,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
