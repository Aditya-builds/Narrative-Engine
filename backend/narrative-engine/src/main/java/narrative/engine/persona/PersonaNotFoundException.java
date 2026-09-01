package narrative.engine.persona;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public class PersonaNotFoundException extends WebApplicationException {

    public PersonaNotFoundException(String personaKey) {
        super(
                "Persona with key " + personaKey + " not found",
                Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", "Persona with key " + personaKey + " not found"))
                        .build());
    }
}
