package narrative.engine.persona;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import narrative.engine.character.CharacterClass;
import narrative.engine.character.TestCharacters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonaMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path root;

    private PersonaMapper mapper;

    @BeforeEach
    void setUp() throws Exception {
        TestCharacters.writeCharacter(root, "Aurora", CharacterClass.MAGE, objectMapper);
        mapper = new PersonaMapper(new PersonaLoader(root, objectMapper), objectMapper);
    }

    @Test
    void listNamesIncludesAurora() {
        assertEquals("Aurora", mapper.listNames().get(0));
    }

    @Test
    void mapCombinesManifestAndDomainFiles() {
        ObjectNode combined = mapper.map("aurora");
        assertEquals("Aurora", combined.path("name").asText());
        assertEquals(75, combined.path("files").path("stats.json").path("attributes").path("intelligence").asInt());
    }

    @Test
    void createThenUpdate() {
        mapper.create("Nova", "mage");
        ObjectNode body = objectMapper.createObjectNode().put("rank", "B");
        mapper.update("Nova", body);
        assertEquals("B", mapper.map("Nova").path("rank").asText());
    }

    @Test
    void mapMissingPersona() {
        assertThrows(PersonaNotFoundException.class, () -> mapper.map("Nope"));
    }
}
