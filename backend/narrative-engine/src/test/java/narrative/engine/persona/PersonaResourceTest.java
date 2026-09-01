package narrative.engine.persona;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import narrative.engine.character.IsolatedCharacterStorageResource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(IsolatedCharacterStorageResource.class)
class PersonaResourceTest {

    @Test
    void getExistingPersona() {
        given()
                .when().get("/personas/Aurora")
                .then()
                .statusCode(200)
                .body("name", equalTo("Aurora"))
                .body("class", equalTo("mage"))
                .body("files.'stats.json'.attributes.intelligence", equalTo(75));
    }

    @Test
    void getIsCaseInsensitive() {
        given()
                .when().get("/personas/aurora")
                .then()
                .statusCode(200)
                .body("name", equalTo("Aurora"));
    }

    @Test
    void getMissingPersona() {
        given()
                .when().get("/personas/DoesNotExist")
                .then()
                .statusCode(404)
                .body("error", equalTo("Persona with key DoesNotExist not found"));
    }

    @Test
    void createMageThenGetAndRejectDuplicate() {
        String name = unique("Mage");
        given()
                .when().post("/create_new_persona/{name}/{class}", name, "mage")
                .then()
                .statusCode(201)
                .body("message", equalTo("successful creation"));

        given()
                .when().get("/personas/{name}", name)
                .then()
                .statusCode(200)
                .body("class", equalTo("mage"))
                .body("rank", equalTo("E"));

        given()
                .when().post("/create_new_persona/{name}/{class}", name, "mage")
                .then()
                .statusCode(409)
                .body("error", equalTo("Persona '" + name + "' already exists"));
    }

    @Test
    void createMeleeAndTypoClass() {
        String melee = unique("Melee");
        given()
                .when().post("/create_new_persona/{name}/{class}", melee, "melee")
                .then()
                .statusCode(201);

        given()
                .when().get("/personas/{name}", melee)
                .then()
                .statusCode(200)
                .body("class", equalTo("melee"))
                .body("files.'equipment.json'.weapons[0]", equalTo("training blade"));

        String typed = unique("Typo");
        given()
                .when().post("/create_new_persona/{name}/{class}", typed, "melle")
                .then()
                .statusCode(201);
        given()
                .when().get("/personas/{name}", typed)
                .then()
                .statusCode(200)
                .body("class", equalTo("melee"));
    }

    @Test
    void createRejectsUnknownClassAndBlankName() {
        given()
                .when().post("/create_new_persona/{name}/{class}", "Hero", "archer")
                .then()
                .statusCode(400)
                .body("error", equalTo("Class must be mage or melee"));
    }

    @Test
    void updateNativePatchThenGet() {
        String name = unique("Patch");
        given().when().post("/create_new_persona/{name}/{class}", name, "mage").then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "rank": "B",
                          "gender": "female",
                          "files": {
                            "stats.json": { "strengths": ["frost"] },
                            "personality": { "traits": ["calm"] }
                          }
                        }
                        """)
                .when().put("/update_persona/{name}", name)
                .then()
                .statusCode(200)
                .body("message", equalTo("successful update"));

        given()
                .when().get("/personas/{name}", name)
                .then()
                .statusCode(200)
                .body("rank", equalTo("B"))
                .body("gender", equalTo("female"))
                .body("files.'stats.json'.strengths[0]", equalTo("frost"))
                .body("files.'personality.json'.traits[0]", equalTo("calm"));
    }

    @Test
    void updateExternalSchema() {
        String name = unique("Ext");
        given().when().post("/create_new_persona/{name}/{class}", name, "mage").then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "name": "%s",
                          "class": "mage",
                          "rank": "B",
                          "profile": {
                            "personality": ["calm", "intelligent"],
                            "background": "A guild mage.",
                            "speakingStyle": "Quiet."
                          },
                          "appearance": {
                            "hair": "silver",
                            "eyes": "violet",
                            "build": "slender"
                          },
                          "abilities": [
                            { "name": "Ice Lance", "type": "offensive" },
                            { "name": "Frost Barrier", "type": "defensive" }
                          ],
                          "defaultState": { "locationId": "guild_hall", "emotion": "calm" },
                          "defaultRelationships": [
                            { "characterB": "laxus", "metrics": { "respect": 75 } }
                          ],
                          "visualIdentity": {
                            "visualDescription": "ice mage",
                            "accessories": ["staff"]
                          },
                          "openingMessage": "Hello."
                        }
                        """.formatted(name))
                .when().put("/update_persona/{name}", name.toLowerCase())
                .then()
                .statusCode(200);

        given()
                .when().get("/personas/{name}", name)
                .then()
                .statusCode(200)
                .body("description", equalTo("A guild mage."))
                .body("location", equalTo("guildhall"))
                .body("openingMessage", equalTo("Hello."))
                .body("files.'appearance.json'.physicalFeatures.hair", equalTo("silver"))
                .body("files.'personality.json'.traits[0]", equalTo("calm"))
                .body("files.'abilities.json'.specialties.arcane.offensive.B[0]", equalTo("Ice Lance"))
                .body("files.'relationships.json'.relationships.laxus", equalTo(75))
                .body("files.'visual-identity.json'.visualDescription", equalTo("ice mage"));
    }

    @Test
    void updateRejectsRenameUnknownFileAndBadBody() {
        String name = unique("Bad");
        given().when().post("/create_new_persona/{name}/{class}", name, "mage").then().statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body("{\"name\":\"Other\"}")
                .when().put("/update_persona/{name}", name)
                .then()
                .statusCode(400)
                .body("error", equalTo("name cannot be changed"));

        given()
                .contentType(ContentType.JSON)
                .body("{\"files\":{\"missing.json\":{}}}")
                .when().put("/update_persona/{name}", name)
                .then()
                .statusCode(400)
                .body("error", equalTo("Unknown file 'missing.json'"));

        given()
                .contentType(ContentType.JSON)
                .body("[1]")
                .when().put("/update_persona/{name}", name)
                .then()
                .statusCode(400);

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().put("/update_persona/MissingHero")
                .then()
                .statusCode(404);
    }

    @Test
    void personaStoreIsSeparateFromCharacters() {
        String name = unique("OnlyPersona");
        given().when().post("/create_new_persona/{name}/{class}", name, "mage").then().statusCode(201);

        given()
                .when().get("/personas/{name}", name)
                .then()
                .statusCode(200)
                .body("name", equalTo(name));

        given()
                .when().get("/characters/{name}", name)
                .then()
                .statusCode(404);
    }

    private static String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
