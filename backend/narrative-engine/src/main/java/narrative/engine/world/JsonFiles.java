package narrative.engine.world;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class JsonFiles {

    private JsonFiles() {}

    public static void writeAtomic(ObjectMapper mapper, Path file, JsonNode node) throws IOException {
        Path dir = file.getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        if (Files.exists(file) && !Files.isWritable(file)) {
            throw new IOException("File is not writable: " + file);
        }
        Path lockPath = file.resolveSibling(file.getFileName().toString() + ".lock");
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                FileLock lock = channel.lock()) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), node);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
            Files.deleteIfExists(lockPath);
        }
    }
}
