package narrative.engine.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

public class IsolatedCharacterStorageResource implements QuarkusTestResourceLifecycleManager {

    private Path root;

    @Override
    public Map<String, String> start() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            root = TestCharacters.newRoot(mapper);
            return Map.of("character.storage.path", root.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create isolated character storage", e);
        }
    }

    @Override
    public void stop() {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            });
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }
}
