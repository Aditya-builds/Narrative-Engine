package narrative.engine.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@ApplicationScoped
public class CharacterLoader {

    private final Path storagePath;
    private final ObjectMapper objectMapper;

    @Inject
    public CharacterLoader(
            @ConfigProperty(name = "character.storage.path") Path storagePath,
            ObjectMapper objectMapper) {
        this.storagePath = resolveStoragePath(storagePath);
        this.objectMapper = objectMapper;
    }

    public JsonNode loadManifest(String characterKey) {
        Path manifest = characterDirectory(characterKey).resolve("character.json");

        if (!Files.isRegularFile(manifest)) {
            throw new CharacterNotFoundException(characterKey);
        }

        return readJson(manifest, "Failed to read character.json for " + characterKey);
    }

    public JsonNode loadFile(String characterKey, String fileKey, String fileName) {
        Path file = characterDirectory(characterKey).resolve(fileName);

        if (!Files.isRegularFile(file)) {
            throw new MissingCharacterFileException(characterKey, fileKey, fileName);
        }

        return readJson(file, "Failed to read " + fileName + " for " + characterKey);
    }

    public void create(String characterKey, JsonNode manifest, Map<String, JsonNode> files) {
        Path dir = characterDirectory(characterKey);
        Path manifestPath = dir.resolve("character.json");
        if (Files.isRegularFile(manifestPath)) {
            throw new CharacterAlreadyExistsException(characterKey);
        }

        try {
            Files.createDirectories(dir);
            writeJson(manifestPath, manifest);
            for (var entry : files.entrySet()) {
                writeJson(dir.resolve(entry.getKey()), entry.getValue());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create character " + characterKey, e);
        }
    }

    public void save(String characterKey, JsonNode manifest, Map<String, JsonNode> files) {
        Path dir = characterDirectory(characterKey);
        Path manifestPath = dir.resolve("character.json");
        if (!Files.isRegularFile(manifestPath)) {
            throw new CharacterNotFoundException(characterKey);
        }

        try {
            writeJson(manifestPath, manifest);
            for (var entry : files.entrySet()) {
                Path file = dir.resolve(entry.getKey());
                if (!Files.isRegularFile(file)) {
                    throw new MissingCharacterFileException(characterKey, entry.getKey(), entry.getKey());
                }
                writeJson(file, entry.getValue());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to update character " + characterKey, e);
        }
    }

    private Path characterDirectory(String characterKey) {
        if (characterKey == null
                || characterKey.isBlank()
                || characterKey.contains("..")
                || characterKey.indexOf('/') >= 0
                || characterKey.indexOf('\\') >= 0) {
            throw new InvalidCharacterRequestException("Invalid character key");
        }
        return storagePath.resolve(characterKey);
    }

    private Path resolveStoragePath(Path configured) {
        Path fromConfig = configured.toAbsolutePath().normalize();
        if (isCharacterRoot(fromConfig)) {
            return fromConfig;
        }

        Path dir = Path.of("").toAbsolutePath().normalize();
        while (dir != null) {
            Path characters = dir.resolve("Characters");
            if (isCharacterRoot(characters)) {
                return characters;
            }
            Path relative = dir.resolve(configured).normalize();
            if (isCharacterRoot(relative)) {
                return relative;
            }
            dir = dir.getParent();
        }

        throw new IllegalStateException(
                "Could not find Characters folder (looked from "
                        + Path.of("").toAbsolutePath().normalize()
                        + " using "
                        + configured
                        + ")");
    }

    private boolean isCharacterRoot(Path path) {
        return Files.isDirectory(path)
                && Files.isRegularFile(path.resolve("Aurora").resolve("character.json"));
    }

    private JsonNode readJson(Path file, String errorMessage) {
        try {
            return objectMapper.readTree(file.toFile());
        } catch (IOException e) {
            throw new RuntimeException(errorMessage, e);
        }
    }

    private void writeJson(Path file, JsonNode node) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), node);
    }
}
