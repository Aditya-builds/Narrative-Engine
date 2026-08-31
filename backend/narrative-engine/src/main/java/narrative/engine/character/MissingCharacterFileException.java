package narrative.engine.character;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

public class MissingCharacterFileException extends WebApplicationException {

    public MissingCharacterFileException(String characterKey, String fileKey, String fileName) {
        super(
                "Character '" + characterKey + "' is missing '" + fileName + "' (key: " + fileKey + ")",
                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .type(MediaType.APPLICATION_JSON)
                        .entity(Map.of(
                                "error",
                                "Character '" + characterKey + "' is missing '" + fileName + "' (key: " + fileKey + ")"))
                        .build());
    }
}
