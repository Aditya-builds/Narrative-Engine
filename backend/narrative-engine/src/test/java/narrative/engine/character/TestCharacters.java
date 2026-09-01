package narrative.engine.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class TestCharacters {

    private TestCharacters() {}

    static Path newRoot(ObjectMapper mapper) throws IOException {
        Path root = Files.createTempDirectory("ne-characters");
        writeCharacter(root, "Aurora", CharacterClass.MAGE, mapper);
        return root;
    }

    public static void writeCharacter(Path root, String name, CharacterClass characterClass, ObjectMapper mapper)
            throws IOException {
        Path dir = root.resolve(name);
        Files.createDirectories(dir);
        ObjectNode manifest = CharacterClassDefaults.manifest(mapper, characterClass, name);
        mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("character.json").toFile(), manifest);
        for (var entry : CharacterClassDefaults.files(mapper, characterClass).entrySet()) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(entry.getKey()).toFile(), entry.getValue());
        }
    }

    static void writeJson(Path file, JsonNode node, ObjectMapper mapper) throws IOException {
        Files.createDirectories(file.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), node);
    }

    static CharacterLoader loader(Path root, ObjectMapper mapper) {
        return new CharacterLoader(root, mapper);
    }

    static CharacterMapper mapper(Path root, ObjectMapper objectMapper) {
        return new CharacterMapper(loader(root, objectMapper), objectMapper);
    }

    static Map<String, JsonNode> defaultFiles(ObjectMapper mapper, CharacterClass characterClass) {
        return CharacterClassDefaults.files(mapper, characterClass);
    }
}
