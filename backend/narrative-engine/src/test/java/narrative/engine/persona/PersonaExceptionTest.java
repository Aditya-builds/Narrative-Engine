package narrative.engine.persona;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PersonaExceptionTest {

    @Test
    void notFoundIs404() {
        PersonaNotFoundException error = new PersonaNotFoundException("Nova");
        assertEquals(404, error.getResponse().getStatus());
        assertEquals("Persona with key Nova not found", error.getMessage());
    }

    @Test
    void alreadyExistsIs409() {
        PersonaAlreadyExistsException error = new PersonaAlreadyExistsException("Nova");
        assertEquals(409, error.getResponse().getStatus());
    }

    @Test
    void invalidRequestIs400() {
        InvalidPersonaRequestException error = new InvalidPersonaRequestException("bad");
        assertEquals(400, error.getResponse().getStatus());
        assertEquals("bad", error.getMessage());
    }

    @Test
    void missingFileIs500() {
        MissingPersonaFileException error = new MissingPersonaFileException("Nova", "stats", "stats.json");
        assertEquals(500, error.getResponse().getStatus());
        assertEquals("Persona 'Nova' is missing 'stats.json' (key: stats)", error.getMessage());
    }
}
