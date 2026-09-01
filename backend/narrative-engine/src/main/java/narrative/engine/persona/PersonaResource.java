package narrative.engine.persona;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class PersonaResource {

    private final PersonaMapper personaMapper;

    @Inject
    public PersonaResource(PersonaMapper personaMapper) {
        this.personaMapper = personaMapper;
    }

    @GET
    @Path("/personas/{personaKey}")
    public ObjectNode getPersona(@PathParam("personaKey") String personaKey) {
        return personaMapper.map(personaKey);
    }

    @POST
    @Path("/create_new_persona/{personaName}/{characterClass}")
    public Response createPersona(
            @PathParam("personaName") String personaName,
            @PathParam("characterClass") String characterClass) {
        personaMapper.create(personaName, characterClass);
        return Response.status(Response.Status.CREATED)
                .entity(Map.of("message", "successful creation"))
                .build();
    }

    @PUT
    @Path("/update_persona/{personaName}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updatePersona(
            @PathParam("personaName") String personaName,
            JsonNode body) {
        personaMapper.update(personaName, body);
        return Response.ok(Map.of("message", "successful update")).build();
    }
}
