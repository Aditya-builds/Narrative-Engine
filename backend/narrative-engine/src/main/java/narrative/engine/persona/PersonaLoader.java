package narrative.engine.persona;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import narrative.engine.world.EntityStore;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@ApplicationScoped
public class PersonaLoader implements EntityStore {

    private final Path storagePath;
    private final ObjectMapper objectMapper;

    @Inject
    public PersonaLoader(
            @ConfigProperty(name = "persona.storage.path") Path storagePath,
            ObjectMapper objectMapper) {
        this.storagePath = resolveStoragePath(storagePath);
        this.objectMapper = objectMapper;
    }

    public String resolveKey(String personaKey) {
        return findKey(personaKey).orElseThrow(() -> new PersonaNotFoundException(personaKey));
    }

    public Optional<String> findKey(String personaKey) {
        validateKey(personaKey);

        if (!Files.isDirectory(storagePath)) {
            return Optional.empty();
        }

        Path exact = storagePath.resolve(personaKey);
        if (Files.isRegularFile(exact.resolve("character.json"))) {
            return Optional.of(folderName(exact, personaKey));
        }

        try (Stream<Path> stream = Files.list(storagePath)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.equalsIgnoreCase(personaKey))
                    .filter(name -> Files.isRegularFile(storagePath.resolve(name).resolve("character.json")))
                    .findFirst();
        } catch (IOException e) {
            throw new RuntimeException("Failed to resolve persona " + personaKey, e);
        }
    }

    public JsonNode loadManifest(String personaKey) {
        Path manifest = personaDirectory(personaKey).resolve("character.json");

        if (!Files.isRegularFile(manifest)) {
            throw new PersonaNotFoundException(personaKey);
        }

        return readJson(manifest, "Failed to read character.json for " + personaKey);
    }

    public JsonNode loadFile(String personaKey, String fileKey, String fileName) {
        Path file = personaDirectory(personaKey).resolve(fileName);

        if (!Files.isRegularFile(file)) {
            throw new MissingPersonaFileException(personaKey, fileKey, fileName);
        }

        return readJson(file, "Failed to read " + fileName + " for " + personaKey);
    }

    public void create(String personaKey, JsonNode manifest, Map<String, JsonNode> files) {
        if (findKey(personaKey).isPresent()) {
            throw new PersonaAlreadyExistsException(personaKey);
        }

        Path dir = personaDirectory(personaKey);
        Path manifestPath = dir.resolve("character.json");
        if (Files.isRegularFile(manifestPath)) {
            throw new PersonaAlreadyExistsException(personaKey);
        }

        try {
            Files.createDirectories(dir);
            writeJson(manifestPath, manifest);
            for (var entry : files.entrySet()) {
                writeJson(dir.resolve(entry.getKey()), entry.getValue());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create persona " + personaKey, e);
        }
    }

    public void save(String personaKey, JsonNode manifest, Map<String, JsonNode> files) {
        Path dir = personaDirectory(personaKey);
        Path manifestPath = dir.resolve("character.json");
        if (!Files.isRegularFile(manifestPath)) {
            throw new PersonaNotFoundException(personaKey);
        }

        try {
            writeJson(manifestPath, manifest);
            for (var entry : files.entrySet()) {
                Path file = dir.resolve(entry.getKey());
                if (!Files.isRegularFile(file)) {
                    throw new MissingPersonaFileException(personaKey, entry.getKey(), entry.getKey());
                }
                writeJson(file, entry.getValue());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to update persona " + personaKey, e);
        }
    }

    Path storagePath() {
        return storagePath;
    }

    private Path personaDirectory(String personaKey) {
        validateKey(personaKey);
        return storagePath.resolve(personaKey);
    }

    private void validateKey(String personaKey) {
        if (personaKey == null
                || personaKey.isBlank()
                || personaKey.contains("..")
                || personaKey.indexOf('/') >= 0
                || personaKey.indexOf('\\') >= 0) {
            throw new InvalidPersonaRequestException("Invalid persona key");
        }
    }

    private String folderName(Path directory, String fallback) {
        Path name = directory.getFileName();
        if (name == null) {
            return fallback;
        }
        try {
            return directory.toRealPath().getFileName().toString();
        } catch (IOException e) {
            return name.toString();
        }
    }

    private Path resolveStoragePath(Path configured) {
        Path fromConfig = configured.toAbsolutePath().normalize();
        if (Files.isDirectory(fromConfig)) {
            return fromConfig;
        }

        Path dir = Path.of("").toAbsolutePath().normalize();
        while (dir != null) {
            Path world = dir.resolve("World");
            Path personas = world.resolve("Persona");
            if (Files.isDirectory(personas) || Files.isDirectory(world)) {
                return personas;
            }
            Path relative = dir.resolve(configured).normalize();
            if (Files.isDirectory(relative)) {
                return relative;
            }
            dir = dir.getParent();
        }

        return fromConfig;
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
