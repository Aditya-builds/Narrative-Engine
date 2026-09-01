package narrative.engine.persona;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public class InvalidPersonaRequestException extends WebApplicationException {

    public InvalidPersonaRequestException(String message) {
        super(
                message,
                Response.status(Response.Status.BAD_REQUEST)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", message))
                        .build());
    }
}
