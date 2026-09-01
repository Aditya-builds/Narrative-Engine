package narrative.engine.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;

public class IsolatedCharacterStorageResource implements QuarkusTestResourceLifecycleManager {

    private Path charactersRoot;
    private Path personasRoot;

    @Override
    public Map<String, String> start() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            charactersRoot = TestCharacters.newRoot(mapper);
            personasRoot = TestCharacters.newRoot(mapper);
            return Map.of(
                    "character.storage.path", charactersRoot.toAbsolutePath().toString(),
                    "persona.storage.path", personasRoot.toAbsolutePath().toString());
        } catch (IOException e) {
            throw new RuntimeException("Failed to create isolated character storage", e);
        }
    }

    @Override
    public void stop() {
        deleteTree(charactersRoot);
        deleteTree(personasRoot);
    }

    private static void deleteTree(Path root) {
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
