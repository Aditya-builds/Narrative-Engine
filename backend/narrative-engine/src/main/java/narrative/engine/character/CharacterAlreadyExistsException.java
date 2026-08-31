package narrative.engine.character;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public class CharacterAlreadyExistsException extends WebApplicationException {

    public CharacterAlreadyExistsException(String characterKey) {
        super(
                "Character '" + characterKey + "' already exists",
                Response.status(Response.Status.CONFLICT)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", "Character '" + characterKey + "' already exists"))
                        .build());
    }
}
