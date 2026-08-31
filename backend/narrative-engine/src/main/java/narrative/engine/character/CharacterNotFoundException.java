package narrative.engine.character;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public class CharacterNotFoundException extends WebApplicationException {

    public CharacterNotFoundException(String characterKey) {
        super(
                "Character with key " + characterKey + " not found",
                Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", "Character with key " + characterKey + " not found"))
                        .build());
    }
}
