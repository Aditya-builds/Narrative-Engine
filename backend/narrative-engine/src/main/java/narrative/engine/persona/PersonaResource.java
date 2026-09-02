package narrative.engine.persona;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import narrative.engine.api.IdempotencyStore;
import narrative.engine.world.Portraits;

import java.util.List;
import java.util.Map;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class PersonaResource {

    private final PersonaMapper personaMapper;
    private final PersonaLoader personaLoader;
    private final IdempotencyStore idempotencyStore;

    @Inject
    public PersonaResource(
            PersonaMapper personaMapper, PersonaLoader personaLoader, IdempotencyStore idempotencyStore) {
        this.personaMapper = personaMapper;
        this.personaLoader = personaLoader;
        this.idempotencyStore = idempotencyStore;
    }

    @GET
    @Path("/personas")
    public List<String> listPersonas() {
        return personaMapper.listNames();
    }

    @GET
    @Path("/personas/{personaKey}/portrait")
    @Produces({"image/jpeg", "image/png", "image/webp", "image/gif", MediaType.WILDCARD})
    public Response getPortrait(@PathParam("personaKey") String personaKey) {
        return Portraits.response(personaLoader.findPortrait(personaKey));
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
            @PathParam("characterClass") String characterClass,
            @HeaderParam("Idempotency-Key") String idempotencyKey) {
        IdempotencyStore.Cached cached = idempotencyStore.get("persona", idempotencyKey);
        if (cached != null) {
            return Response.status(cached.status()).entity(cached.entity()).build();
        }
        personaMapper.create(personaName, characterClass);
        Map<String, String> entity = Map.of("message", "successful creation");
        idempotencyStore.put("persona", idempotencyKey, Response.Status.CREATED.getStatusCode(), entity);
        return Response.status(Response.Status.CREATED).entity(entity).build();
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
