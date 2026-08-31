package narrative.engine.character;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CharacterExceptionTest {

    @Test
    void notFoundIs404() {
        CharacterNotFoundException error = new CharacterNotFoundException("Nova");
        assertEquals(404, error.getResponse().getStatus());
        assertEquals("Character with key Nova not found", error.getMessage());
    }

    @Test
    void alreadyExistsIs409() {
        CharacterAlreadyExistsException error = new CharacterAlreadyExistsException("Nova");
        assertEquals(409, error.getResponse().getStatus());
    }

    @Test
    void invalidRequestIs400() {
        InvalidCharacterRequestException error = new InvalidCharacterRequestException("bad");
        assertEquals(400, error.getResponse().getStatus());
        assertEquals("bad", error.getMessage());
    }

    @Test
    void missingFileIs500() {
        MissingCharacterFileException error = new MissingCharacterFileException("Nova", "stats", "stats.json");
        assertEquals(500, error.getResponse().getStatus());
        assertEquals("Character 'Nova' is missing 'stats.json' (key: stats)", error.getMessage());
    }
}
