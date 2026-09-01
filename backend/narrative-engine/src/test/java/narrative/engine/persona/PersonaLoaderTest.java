package narrative.engine.persona;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import narrative.engine.character.CharacterClass;
import narrative.engine.character.CharacterMapper;
import narrative.engine.character.TestCharacters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonaLoaderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path root;

    private PersonaLoader loader;

    @BeforeEach
    void setUp() throws Exception {
        TestCharacters.writeCharacter(root, "Aurora", CharacterClass.MAGE, mapper);
        loader = new PersonaLoader(root, mapper);
    }

    @Test
    void listNamesIncludesAurora() {
        assertEquals(List.of("Aurora"), loader.listKeys());
    }

    @Test
    void resolveKeyIsCaseInsensitive() {
        assertEquals("Aurora", loader.resolveKey("aurora"));
        assertEquals("Aurora", loader.resolveKey("AURORA"));
    }

    @Test
    void resolveKeyMissingPersona() {
        assertThrows(PersonaNotFoundException.class, () -> loader.resolveKey("Missing"));
    }

    @Test
    void rejectsInvalidKeys() {
        assertThrows(InvalidPersonaRequestException.class, () -> loader.resolveKey(null));
        assertThrows(InvalidPersonaRequestException.class, () -> loader.resolveKey("  "));
        assertThrows(InvalidPersonaRequestException.class, () -> loader.resolveKey("../secret"));
        assertThrows(InvalidPersonaRequestException.class, () -> loader.resolveKey("a/b"));
        assertThrows(InvalidPersonaRequestException.class, () -> loader.resolveKey("a\\b"));
    }

    @Test
    void loadManifestAndFile() {
        assertEquals("Aurora", loader.loadManifest("Aurora").path("name").asText());
        assertTrue(loader.loadFile("Aurora", "stats", "stats.json").path("attributes").has("intelligence"));
    }

    @Test
    void loadManifestMissing() {
        assertThrows(PersonaNotFoundException.class, () -> loader.loadManifest("Ghost"));
    }

    @Test
    void loadFileMissing() throws Exception {
        Files.delete(root.resolve("Aurora").resolve("stats.json"));
        assertThrows(MissingPersonaFileException.class, () -> loader.loadFile("Aurora", "stats", "stats.json"));
    }

    @Test
    void readInvalidJsonFails() throws Exception {
        Files.writeString(root.resolve("Aurora").resolve("character.json"), "{not-json");
        RuntimeException error = assertThrows(RuntimeException.class, () -> loader.loadManifest("Aurora"));
        assertTrue(error.getMessage().contains("Failed to read character.json"));
    }

    @Test
    void createWritesClassFiles() {
        new CharacterMapper(loader, mapper).create("Nova", "melee");
        assertEquals("melee", loader.loadManifest("Nova").path("class").asText());
        assertTrue(Files.isRegularFile(root.resolve("Nova").resolve("equipment.json")));
    }

    @Test
    void createRejectsDuplicateIgnoreCase() {
        ObjectNode manifest = mapper.createObjectNode().put("name", "aurora");
        assertThrows(PersonaAlreadyExistsException.class, () -> loader.create("aurora", manifest, Map.of()));
    }

    @Test
    void saveUpdatesManifestAndFiles() {
        ObjectNode manifest = (ObjectNode) loader.loadManifest("Aurora").deepCopy();
        manifest.put("rank", "A");
        ObjectNode stats = (ObjectNode) loader.loadFile("Aurora", "stats", "stats.json").deepCopy();
        ((ObjectNode) stats.get("attributes")).put("intelligence", 99);
        loader.save("Aurora", manifest, Map.of("stats.json", stats));
        assertEquals("A", loader.loadManifest("Aurora").path("rank").asText());
        assertEquals(99, loader.loadFile("Aurora", "stats", "stats.json").path("attributes").path("intelligence").asInt());
    }

    @Test
    void saveMissingPersona() {
        ObjectNode manifest = mapper.createObjectNode().put("name", "Ghost");
        assertThrows(PersonaNotFoundException.class, () -> loader.save("Ghost", manifest, Map.of()));
    }

    @Test
    void saveMissingFile() throws Exception {
        Files.delete(root.resolve("Aurora").resolve("stats.json"));
        ObjectNode manifest = (ObjectNode) loader.loadManifest("Aurora");
        assertThrows(MissingPersonaFileException.class,
                () -> loader.save("Aurora", manifest, Map.of("stats.json", mapper.createObjectNode())));
    }

    @Test
    void createWriteFailureWrapsIOException() throws Exception {
        Files.writeString(root.resolve("Blocked"), "not-a-directory");
        RuntimeException error = assertThrows(RuntimeException.class,
                () -> loader.create("Blocked", mapper.createObjectNode(), Map.of()));
        assertTrue(error.getMessage().contains("Failed to create persona"));
    }

    @Test
    void saveWriteFailureWrapsIOException() throws Exception {
        Path stats = root.resolve("Aurora").resolve("stats.json");
        assertTrue(stats.toFile().setReadOnly());
        try {
            ObjectNode manifest = (ObjectNode) loader.loadManifest("Aurora");
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> loader.save("Aurora", manifest, Map.of("stats.json", mapper.createObjectNode())));
            assertTrue(error.getMessage().contains("Failed to update persona"));
        } finally {
            assertTrue(stats.toFile().setWritable(true));
        }
    }

    @Test
    void findKeySkipsSameNameDirectoryWithoutManifest() throws Exception {
        Files.createDirectory(root.resolve("ghost"));
        assertTrue(loader.findKey("Ghost").isEmpty());
    }

    @Test
    void findKeyListsDirectoriesWhenExactCasingDiffersOnCaseSensitiveLookup() throws Exception {
        TestCharacters.writeCharacter(root, "Laxus", CharacterClass.MAGE, mapper);
        assertEquals("Laxus", loader.findKey("laxus").orElseThrow());
    }

    @Test
    void findKeyListFailureWhenStorageDeleted() throws Exception {
        Path other = Files.createTempDirectory("ne-persona-other");
        TestCharacters.writeCharacter(other, "Aurora", CharacterClass.MAGE, mapper);
        PersonaLoader isolated = new PersonaLoader(other, mapper);
        Files.walk(other).sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        });
        assertTrue(isolated.findKey("Someone").isEmpty());
    }

    @Test
    void findKeyWhenStorageIsMissingReturnsEmpty() throws Exception {
        Path missing = Files.createTempDirectory("ne-persona-missing");
        PersonaLoader isolated = new PersonaLoader(missing, mapper);
        Files.delete(missing);
        assertTrue(isolated.findKey("Someone").isEmpty());
        assertTrue(isolated.listKeys().isEmpty());
        isolated.create("Nova", mapper.createObjectNode().put("name", "Nova"), Map.of());
        assertEquals("Nova", isolated.resolveKey("Nova"));
    }

    @Test
    void resolveStoragePathFallsBackToProjectPersonas() {
        PersonaLoader walked = new PersonaLoader(Path.of("does-not-exist"), mapper);
        assertTrue(Files.isDirectory(walked.storagePath()));
        assertTrue(walked.storagePath().endsWith(Path.of("World", "Persona")));
    }

    @Test
    void resolveStoragePathUsesRelativeConfiguredWhenItIsARoot() {
        PersonaLoader production = new PersonaLoader(Path.of("../../World/Persona"), mapper);
        assertTrue(production.storagePath().endsWith(Path.of("World", "Persona")));
    }

    @Test
    void findPortraitUsesFallbackMainJpg() throws Exception {
        assertTrue(loader.findPortrait("Aurora").isEmpty());
        Path fallback = root.resolve("Aurora").resolve("references").resolve("main.jpg");
        Files.createDirectories(fallback.getParent());
        Files.write(fallback, new byte[] {7, 8, 9});
        assertEquals(fallback.toAbsolutePath().normalize(), loader.findPortrait("aurora").orElseThrow());
    }
}
