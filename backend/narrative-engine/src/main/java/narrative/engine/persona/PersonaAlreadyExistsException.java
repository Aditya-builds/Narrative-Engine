package narrative.engine.persona;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public class PersonaAlreadyExistsException extends WebApplicationException {

    public PersonaAlreadyExistsException(String personaKey) {
        super(
                "Persona '" + personaKey + "' already exists",
                Response.status(Response.Status.CONFLICT)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", "Persona '" + personaKey + "' already exists"))
                        .build());
    }
}
