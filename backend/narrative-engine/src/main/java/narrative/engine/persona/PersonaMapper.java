package narrative.engine.persona;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import narrative.engine.character.CharacterMapper;

@ApplicationScoped
public class PersonaMapper {

    private final CharacterMapper characterMapper;

    @Inject
    public PersonaMapper(PersonaLoader personaLoader, ObjectMapper objectMapper) {
        this.characterMapper = new CharacterMapper(personaLoader, objectMapper);
    }

    public ObjectNode map(String personaKey) {
        return characterMapper.map(personaKey);
    }

    public void create(String name, String className) {
        characterMapper.create(name, className);
    }

    public void update(String name, JsonNode request) {
        characterMapper.update(name, request);
    }
}
