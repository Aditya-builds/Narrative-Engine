package narrative.engine.character;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public class InvalidCharacterRequestException extends WebApplicationException {

    public InvalidCharacterRequestException(String message) {
        super(
                message,
                Response.status(Response.Status.BAD_REQUEST)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of("error", message))
                        .build());
    }
}
