package narrative.engine.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

class CharacterLoaderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path root;

    private CharacterLoader loader;

    @BeforeEach
    void setUp() throws Exception {
        TestCharacters.writeCharacter(root, "Aurora", CharacterClass.MAGE, mapper);
        loader = TestCharacters.loader(root, mapper);
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
    void resolveKeyMissingCharacter() {
        assertThrows(CharacterNotFoundException.class, () -> loader.resolveKey("Missing"));
    }

    @Test
    void rejectsInvalidKeys() {
        assertThrows(InvalidCharacterRequestException.class, () -> loader.resolveKey(null));
        assertThrows(InvalidCharacterRequestException.class, () -> loader.resolveKey("  "));
        assertThrows(InvalidCharacterRequestException.class, () -> loader.resolveKey("../secret"));
        assertThrows(InvalidCharacterRequestException.class, () -> loader.resolveKey("a/b"));
        assertThrows(InvalidCharacterRequestException.class, () -> loader.resolveKey("a\\b"));
        assertThrows(InvalidCharacterRequestException.class, () -> loader.resolveKey("<script>"));
        assertThrows(InvalidCharacterRequestException.class, () -> loader.resolveKey("a>b"));
    }

    @Test
    void loadManifestAndFile() {
        assertEquals("Aurora", loader.loadManifest("Aurora").path("name").asText());
        assertTrue(loader.loadFile("Aurora", "stats", "stats.json").path("attributes").has("intelligence"));
    }

    @Test
    void loadManifestMissing() {
        assertThrows(CharacterNotFoundException.class, () -> loader.loadManifest("Ghost"));
    }

    @Test
    void loadFileMissing() throws Exception {
        Files.delete(root.resolve("Aurora").resolve("stats.json"));
        assertThrows(MissingCharacterFileException.class, () -> loader.loadFile("Aurora", "stats", "stats.json"));
    }

    @Test
    void readInvalidJsonFails() throws Exception {
        Files.writeString(root.resolve("Aurora").resolve("character.json"), "{not-json");
        RuntimeException error = assertThrows(RuntimeException.class, () -> loader.loadManifest("Aurora"));
        assertTrue(error.getMessage().contains("Failed to read character.json"));
    }

    @Test
    void createWritesClassFiles() {
        loader.create("Nova", CharacterClassDefaults.manifest(mapper, CharacterClass.MELEE, "Nova"),
                CharacterClassDefaults.files(mapper, CharacterClass.MELEE));
        assertEquals("melee", loader.loadManifest("Nova").path("class").asText());
        assertTrue(Files.isRegularFile(root.resolve("Nova").resolve("equipment.json")));
    }

    @Test
    void createRejectsDuplicateIgnoreCase() {
        ObjectNode manifest = CharacterClassDefaults.manifest(mapper, CharacterClass.MAGE, "aurora");
        assertThrows(CharacterAlreadyExistsException.class,
                () -> loader.create("aurora", manifest, CharacterClassDefaults.files(mapper, CharacterClass.MAGE)));
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
    void saveMissingCharacter() {
        ObjectNode manifest = mapper.createObjectNode().put("name", "Ghost");
        assertThrows(CharacterNotFoundException.class, () -> loader.save("Ghost", manifest, Map.of()));
    }

    @Test
    void saveMissingFile() throws Exception {
        Files.delete(root.resolve("Aurora").resolve("stats.json"));
        ObjectNode manifest = (ObjectNode) loader.loadManifest("Aurora");
        assertThrows(MissingCharacterFileException.class,
                () -> loader.save("Aurora", manifest, Map.of("stats.json", mapper.createObjectNode())));
    }

    @Test
    void createWriteFailureWrapsIOException() throws Exception {
        Files.writeString(root.resolve("Blocked"), "not-a-directory");
        RuntimeException error = assertThrows(RuntimeException.class, () -> loader.create(
                "Blocked",
                CharacterClassDefaults.manifest(mapper, CharacterClass.MAGE, "Blocked"),
                CharacterClassDefaults.files(mapper, CharacterClass.MAGE)));
        assertTrue(error.getMessage().contains("Failed to create character"));
    }

    @Test
    void saveWriteFailureWrapsIOException() throws Exception {
        Path stats = root.resolve("Aurora").resolve("stats.json");
        assertTrue(stats.toFile().setReadOnly());
        try {
            ObjectNode manifest = (ObjectNode) loader.loadManifest("Aurora");
            RuntimeException error = assertThrows(RuntimeException.class,
                    () -> loader.save("Aurora", manifest, Map.of("stats.json", mapper.createObjectNode())));
            assertTrue(error.getMessage().contains("Failed to update character"));
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
        Path other = Files.createTempDirectory("ne-other");
        TestCharacters.writeCharacter(other, "Aurora", CharacterClass.MAGE, mapper);
        CharacterLoader isolated = TestCharacters.loader(other, mapper);
        Files.walk(other).sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        });
        RuntimeException error = assertThrows(RuntimeException.class, () -> isolated.findKey("Someone"));
        assertTrue(error.getMessage().contains("Failed to resolve character"));
    }

    @Test
    void resolveStoragePathFallsBackToProjectCharacters() {
        CharacterLoader walked = new CharacterLoader(Path.of("does-not-exist"), mapper);
        assertEquals("Erza", walked.resolveKey("Erza"));
    }

    @Test
    void emptyDirectoryIsNotACharacterRoot() throws Exception {
        Path empty = Files.createTempDirectory("ne-empty-characters");
        CharacterLoader walked = new CharacterLoader(empty, mapper);
        assertEquals("Erza", walked.resolveKey("Erza"));
    }

    @Test
    void resolveStoragePathUsesRelativeConfiguredWhenItIsARoot() {
        CharacterLoader production = new CharacterLoader(Path.of("../../World/Characters"), mapper);
        assertEquals("Erza", production.resolveKey("Erza"));
    }

    @Test
    void findPortraitUsesCanonicalReferenceThenFallback() throws Exception {
        assertTrue(loader.findPortrait("Aurora").isEmpty());

        Path face = root.resolve("Aurora").resolve("face.png");
        Files.write(face, new byte[] {1, 2, 3});
        Files.writeString(root.resolve("Aurora").resolve("visual-identity.json"),
                "{\"canonicalReference\":\"face.png\"}");
        assertEquals(face.toAbsolutePath().normalize(), loader.findPortrait("aurora").orElseThrow());

        Files.writeString(root.resolve("Aurora").resolve("visual-identity.json"),
                "{\"canonicalReference\":\"\"}");
        Path fallback = root.resolve("Aurora").resolve("references").resolve("main.jpg");
        Files.createDirectories(fallback.getParent());
        Files.write(fallback, new byte[] {4, 5, 6});
        assertEquals(fallback.toAbsolutePath().normalize(), loader.findPortrait("Aurora").orElseThrow());
    }
}
