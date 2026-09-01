package narrative.engine.persona;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public class MissingPersonaFileException extends WebApplicationException {

    public MissingPersonaFileException(String personaKey, String fileKey, String fileName) {
        super(
                "Persona '" + personaKey + "' is missing '" + fileName + "' (key: " + fileKey + ")",
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of(
                                "error",
                                "Persona '" + personaKey + "' is missing '" + fileName + "' (key: " + fileKey + ")"))
                        .build());
    }
}
