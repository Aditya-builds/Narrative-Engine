package narrative.engine.health;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldStorageHealthCheckTest {

    @TempDir
    Path dir;

    @Test
    void readableDirectoryRequiresExistingFolder() throws Exception {
        assertFalse(WorldStorageHealthCheck.readableDirectory(null));
        assertFalse(WorldStorageHealthCheck.readableDirectory(dir.resolve("missing")));
        Path folder = dir.resolve("world");
        Files.createDirectory(folder);
        assertTrue(WorldStorageHealthCheck.readableDirectory(folder));
        WorldStorageHealthCheck check = new WorldStorageHealthCheck(folder, folder);
        assertTrue(check.call().getStatus().equals(org.eclipse.microprofile.health.HealthCheckResponse.Status.UP));
        WorldStorageHealthCheck down = new WorldStorageHealthCheck(dir.resolve("missing"), folder);
        assertTrue(down.call().getStatus().equals(org.eclipse.microprofile.health.HealthCheckResponse.Status.DOWN));
    }
}
