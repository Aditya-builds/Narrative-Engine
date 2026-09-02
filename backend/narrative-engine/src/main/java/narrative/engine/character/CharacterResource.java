package narrative.engine.character;

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
public class CharacterResource {

    private final CharacterMapper characterMapper;
    private final CharacterLoader characterLoader;
    private final IdempotencyStore idempotencyStore;

    @Inject
    public CharacterResource(
            CharacterMapper characterMapper,
            CharacterLoader characterLoader,
            IdempotencyStore idempotencyStore) {
        this.characterMapper = characterMapper;
        this.characterLoader = characterLoader;
        this.idempotencyStore = idempotencyStore;
    }

    @GET
    @Path("/characters")
    public List<String> listCharacters() {
        return characterMapper.listNames();
    }

    @GET
    @Path("/characters/{characterKey}/portrait")
    @Produces({"image/jpeg", "image/png", "image/webp", "image/gif", MediaType.WILDCARD})
    public Response getPortrait(@PathParam("characterKey") String characterKey) {
        return Portraits.response(characterLoader.findPortrait(characterKey));
    }

    @GET
    @Path("/characters/{characterKey}")
    public ObjectNode getCharacter(@PathParam("characterKey") String characterKey) {
        return characterMapper.map(characterKey);
    }

    @POST
    @Path("/create_new_character/{characterName}/{characterClass}")
    public Response createCharacter(
            @PathParam("characterName") String characterName,
            @PathParam("characterClass") String characterClass,
            @HeaderParam("Idempotency-Key") String idempotencyKey) {
        IdempotencyStore.Cached cached = idempotencyStore.get("character", idempotencyKey);
        if (cached != null) {
            return Response.status(cached.status()).entity(cached.entity()).build();
        }
        characterMapper.create(characterName, characterClass);
        Map<String, String> entity = Map.of("message", "successful creation");
        idempotencyStore.put("character", idempotencyKey, Response.Status.CREATED.getStatusCode(), entity);
        return Response.status(Response.Status.CREATED).entity(entity).build();
    }

    @PUT
    @Path("/update_character/{characterName}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateCharacter(
            @PathParam("characterName") String characterName,
            JsonNode body) {
        characterMapper.update(characterName, body);
        return Response.ok(Map.of("message", "successful update")).build();
    }
}
