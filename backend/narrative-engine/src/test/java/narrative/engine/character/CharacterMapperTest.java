package narrative.engine.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @TempDir
    Path root;

    private CharacterMapper mapper;
    private CharacterLoader loader;

    @BeforeEach
    void setUp() throws Exception {
        TestCharacters.writeCharacter(root, "Aurora", CharacterClass.MAGE, objectMapper);
        loader = TestCharacters.loader(root, objectMapper);
        mapper = TestCharacters.mapper(root, objectMapper);
    }

    @Test
    void listNamesIncludesAurora() {
        assertEquals(List.of("Aurora"), mapper.listNames());
    }

    @Test
    void mapCombinesManifestAndDomainFiles() {
        ObjectNode combined = mapper.map("aurora");
        assertEquals("Aurora", combined.path("name").asText());
        assertTrue(combined.path("files").path("stats.json").path("attributes").has("intelligence"));
        assertFalse(combined.has("stats"));
    }

    @Test
    void mapWhenFilesIsJsonNull() throws Exception {
        ObjectNode manifest = objectMapper.createObjectNode().put("name", "NullFiles");
        manifest.putNull("files");
        TestCharacters.writeJson(root.resolve("NullFiles").resolve("character.json"), manifest, objectMapper);
        assertEquals(0, mapper.map("NullFiles").path("files").size());
    }

    @Test
    void mapWhenFilesIsNull() throws Exception {
        ObjectNode manifest = objectMapper.createObjectNode().put("name", "Bare");
        TestCharacters.writeJson(root.resolve("Bare").resolve("character.json"), manifest, objectMapper);
        ObjectNode combined = mapper.map("Bare");
        assertTrue(combined.path("files").isObject());
        assertEquals(0, combined.path("files").size());
    }

    @Test
    void mapRejectsNonObjectManifestAndFiles() throws Exception {
        Files.createDirectories(root.resolve("Bad"));
        Files.writeString(root.resolve("Bad").resolve("character.json"), "[1]");
        assertThrows(IllegalStateException.class, () -> mapper.map("Bad"));

        ObjectNode notFiles = objectMapper.createObjectNode().put("name", "Odd");
        notFiles.put("files", "nope");
        TestCharacters.writeJson(root.resolve("Odd").resolve("character.json"), notFiles, objectMapper);
        assertThrows(IllegalStateException.class, () -> mapper.map("Odd"));
    }

    @Test
    void mapRejectsNonTextualFileEntry() throws Exception {
        ObjectNode manifest = objectMapper.createObjectNode().put("name", "Broken");
        manifest.putObject("files").put("stats", 3);
        TestCharacters.writeJson(root.resolve("Broken").resolve("character.json"), manifest, objectMapper);
        assertThrows(IllegalStateException.class, () -> mapper.map("Broken"));
    }

    @Test
    void createRequiresName() {
        assertThrows(InvalidCharacterRequestException.class, () -> mapper.create(null, "mage"));
        assertThrows(InvalidCharacterRequestException.class, () -> mapper.create("  ", "mage"));
    }

    @Test
    void createMageAndMelee() {
        mapper.create("  Spark  ", "mage");
        assertEquals("mage", mapper.map("Spark").path("class").asText());
        mapper.create("Steel", "melle");
        assertEquals("melee", mapper.map("Steel").path("class").asText());
        assertEquals("martial", mapper.map("Steel").path("files").path("abilities.json")
                .path("specialties").fieldNames().next());
    }

    @Test
    void updateRejectsBlankNameAndNonObjectBody() {
        assertThrows(InvalidCharacterRequestException.class, () -> mapper.update(" ", objectMapper.createObjectNode()));
        assertThrows(InvalidCharacterRequestException.class, () -> mapper.update("Aurora", null));
        assertThrows(InvalidCharacterRequestException.class, () -> mapper.update("Aurora", TextNode.valueOf("nope")));
    }

    @Test
    void updateRejectsRenames() {
        ObjectNode body = objectMapper.createObjectNode().put("name", "SomeoneElse");
        assertThrows(InvalidCharacterRequestException.class, () -> mapper.update("Aurora", body));
    }

    @Test
    void updateAllowsSameNameDifferentCase() {
        ObjectNode body = objectMapper.createObjectNode().put("name", "aurora").put("rank", "A");
        mapper.update("Aurora", body);
        assertEquals("A", mapper.map("Aurora").path("rank").asText());
    }

    @Test
    void updateNativeFilesByFilenameAndDomainKey() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("rank", "C");
        body.put("gender", "female");
        body.put("age", "20");
        body.put("location", "guildhall");
        body.put("description", "updated");
        body.put("openingMessage", "hello");
        body.put("worldId", "world");
        body.put("type", "main");
        body.put("class", "MAGE");
        ObjectNode files = body.putObject("files");
        files.putObject("stats.json").putArray("strengths").add("Ice");
        files.putObject("personality").putArray("likes").add("snow");
        mapper.update("Aurora", body);

        ObjectNode combined = mapper.map("Aurora");
        assertEquals("C", combined.path("rank").asText());
        assertEquals("mage", combined.path("class").asText());
        assertEquals("hello", combined.path("openingMessage").asText());
        assertEquals("Ice", combined.path("files").path("stats.json").path("strengths").get(0).asText());
        assertEquals("snow", combined.path("files").path("personality.json").path("likes").get(0).asText());
    }

    @Test
    void updateTranslatesExternalPayload() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("rank", "B");
        ObjectNode profile = body.putObject("profile");
        profile.putArray("personality").add("calm").add("focused");
        profile.putArray("values").add("loyalty");
        profile.put("speakingStyle", "Measured and quiet.");
        profile.put("background", "An ice mage of the guild.");
        ObjectNode appearance = body.putObject("appearance");
        appearance.put("hair", "silver");
        appearance.put("eyes", "violet");
        appearance.put("skin", "fair");
        appearance.put("face", "calm");
        appearance.put("build", "slender");
        appearance.put("description", "Ice mage");
        ArrayNode abilities = body.putArray("abilities");
        abilities.addObject().put("name", "Ice Magic").put("type", "elemental_magic");
        abilities.addObject().put("name", "Ice Lance").put("type", "offensive");
        abilities.addObject().put("name", "Frost Barrier").put("type", "defensive");
        abilities.addObject().put("name", "Absolute Zero").put("type", "high_level_magic");
        abilities.add("not-an-object");
        abilities.addObject().put("name", "  ").put("type", "offensive");
        ObjectNode defaultState = body.putObject("defaultState");
        defaultState.put("locationId", "guild_hall");
        defaultState.put("emotion", "calm");
        ArrayNode rels = body.putArray("defaultRelationships");
        rels.add("skip");
        rels.addObject().put("characterB", "  ");
        ObjectNode withRespect = rels.addObject();
        withRespect.put("characterB", "laxus");
        withRespect.putObject("metrics").put("respect", 75);
        ObjectNode withTrust = rels.addObject();
        withTrust.put("characterB", "erza");
        withTrust.putObject("metrics").put("trust", 40);
        rels.addObject().put("characterB", "natsu");
        body.set("seedMemories", objectMapper.createArrayNode().add("memory"));
        ObjectNode visual = body.putObject("visualIdentity");
        visual.put("visualDescription", "frost mage");
        visual.putArray("accessories").add("staff");

        mapper.update("Aurora", body);

        ObjectNode combined = mapper.map("Aurora");
        assertEquals("An ice mage of the guild.", combined.path("description").asText());
        assertEquals("guildhall", combined.path("location").asText());
        assertEquals("calm", combined.path("defaultState").path("emotion").asText());
        assertEquals("silver", combined.path("files").path("appearance.json").path("physicalFeatures").path("hair").asText());
        assertEquals("slender", combined.path("files").path("appearance.json").path("body").path("build").asText());
        assertEquals("calm", combined.path("files").path("personality.json").path("traits").get(0).asText());
        assertEquals("loyalty", combined.path("files").path("personality.json").path("values").get(0).asText());
        assertTrue(combined.path("files").path("personality.json").path("speech").path("style").asText().contains("Measured"));
        assertEquals("Ice Lance", combined.path("files").path("abilities.json")
                .path("specialties").path("arcane").path("offensive").path("B").get(0).asText());
        assertEquals("Frost Barrier", combined.path("files").path("abilities.json")
                .path("specialties").path("arcane").path("defensive").path("B").get(0).asText());
        assertEquals("Absolute Zero", combined.path("files").path("abilities.json")
                .path("specialties").path("arcane").path("offensive").path("A").get(0).asText());
        assertEquals(75, combined.path("files").path("relationships.json").path("relationships").path("laxus").asInt());
        assertEquals(40, combined.path("files").path("relationships.json").path("relationships").path("erza").asInt());
        assertEquals(0, combined.path("files").path("relationships.json").path("relationships").path("natsu").asInt());
        assertEquals("frost mage", combined.path("files").path("visual-identity.json").path("visualDescription").asText());
        assertEquals("staff", combined.path("files").path("visual-identity.json").path("accessories").get(0).asText());
    }

    @Test
    void updateNativeAppearancePersonalityRelationshipsVisualAndStats() {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode appearance = body.putObject("appearance");
        appearance.putObject("physicalFeatures").put("hair", "black");
        appearance.putObject("body").put("heightCm", 175);
        body.putObject("personality").putArray("traits").add("stern");
        body.putObject("relationships").put("laxus", 10);
        ObjectNode visual = body.putObject("visualIdentity");
        visual.put("canonicalReference", "references/main.jpg");
        visual.putArray("references");
        visual.put("hairDescription", "long");
        body.putObject("stats").putArray("weaknesses").add("fire");
        body.putObject("abilities").putArray("classes").add("mage");
        mapper.update("Aurora", body);

        ObjectNode combined = mapper.map("Aurora");
        assertEquals("black", combined.path("files").path("appearance.json").path("physicalFeatures").path("hair").asText());
        assertEquals(175, combined.path("files").path("appearance.json").path("body").path("heightCm").asInt());
        assertEquals("stern", combined.path("files").path("personality.json").path("traits").get(0).asText());
        assertEquals(10, combined.path("files").path("relationships.json").path("relationships").path("laxus").asInt());
        assertEquals("references/main.jpg", combined.path("files").path("visual-identity.json").path("canonicalReference").asText());
        assertEquals("long", combined.path("files").path("visual-identity.json").path("hairDescription").asText());
        assertEquals("fire", combined.path("files").path("stats.json").path("weaknesses").get(0).asText());
    }

    @Test
    void updateWrappedRelationshipsAndLocationAliases() {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("relationships").putObject("relationships").put("mira", 22);
        body.putObject("defaultState").put("locationId", "guild-hall");
        mapper.update("Aurora", body);
        assertEquals(22, mapper.map("Aurora").path("files").path("relationships.json").path("relationships").path("mira").asInt());
        assertEquals("guildhall", mapper.map("Aurora").path("location").asText());

        ObjectNode hall = objectMapper.createObjectNode();
        hall.putObject("defaultState").put("locationId", "guild hall");
        mapper.update("Aurora", hall);
        assertEquals("guildhall", mapper.map("Aurora").path("location").asText());

        ObjectNode forest = objectMapper.createObjectNode();
        forest.putObject("defaultState").put("locationId", "forest");
        mapper.update("Aurora", forest);
        assertEquals("forest", mapper.map("Aurora").path("location").asText());
    }

    @Test
    void updateHighLevelDefensiveAndSpecialtyFallback() throws Exception {
        ObjectNode abilities = (ObjectNode) loader.loadFile("Aurora", "abilities", "abilities.json");
        abilities.set("specialties", objectMapper.createObjectNode());
        TestCharacters.writeJson(root.resolve("Aurora").resolve("abilities.json"), abilities, objectMapper);

        ObjectNode body = objectMapper.createObjectNode().put("rank", "S");
        ArrayNode list = body.putArray("abilities");
        list.addObject().put("name", "Ward").put("type", "high_level_defensive");
        mapper.update("Aurora", body);
        assertEquals("Ward", mapper.map("Aurora").path("files").path("abilities.json")
                .path("specialties").path("arcane").path("defensive").path("S").get(0).asText());
    }

    @Test
    void updateUsesIceSpecialtyWhenPresent() throws Exception {
        ObjectNode abilities = objectMapper.createObjectNode();
        abilities.putArray("classes").add("mage");
        ObjectNode ice = abilities.putObject("specialties").putObject("ice");
        ice.set("offensive", objectMapper.createObjectNode().set("B", objectMapper.createArrayNode()));
        ice.set("defensive", objectMapper.createObjectNode().set("B", objectMapper.createArrayNode()));
        TestCharacters.writeJson(root.resolve("Aurora").resolve("abilities.json"), abilities, objectMapper);

        ObjectNode body = objectMapper.createObjectNode().put("rank", "B");
        body.putArray("abilities").addObject().put("name", "Ice Lance").put("type", "offensive");
        mapper.update("Aurora", body);
        assertEquals("Ice Lance", mapper.map("Aurora").path("files").path("abilities.json")
                .path("specialties").path("ice").path("offensive").path("B").get(0).asText());
    }

    @Test
    void updateUsesExistingNonIceSpecialtyAndInvalidRank() throws Exception {
        ObjectNode abilities = objectMapper.createObjectNode();
        abilities.putArray("classes").add("mage");
        abilities.putObject("specialties").putObject("lightning")
                .set("offensive", objectMapper.createObjectNode().set("E", objectMapper.createArrayNode()));
        TestCharacters.writeJson(root.resolve("Aurora").resolve("abilities.json"), abilities, objectMapper);

        ObjectNode body = objectMapper.createObjectNode().put("rank", "Z");
        body.putArray("abilities").addObject().put("name", "Spark").put("type", "offensive");
        mapper.update("Aurora", body);
        assertEquals("Spark", mapper.map("Aurora").path("files").path("abilities.json")
                .path("specialties").path("lightning").path("offensive").path("E").get(0).asText());
    }

    @Test
    void updateNextRanksFromEachBase() {
        for (String[] pair : new String[][] {
                {"E", "D"}, {"D", "C"}, {"C", "B"}, {"B", "A"}, {"A", "S"}
        }) {
            ObjectNode body = objectMapper.createObjectNode().put("rank", pair[0]);
            body.putArray("abilities").addObject().put("name", "Ultima-" + pair[0]).put("type", "high");
            mapper.update("Aurora", body);
            assertEquals("Ultima-" + pair[0], mapper.map("Aurora").path("files").path("abilities.json")
                    .path("specialties").path("arcane").path("offensive").path(pair[1]).get(0).asText());
        }
    }

    @Test
    void updateRejectsUnknownAndNonObjectFiles() {
        ObjectNode unknown = objectMapper.createObjectNode();
        unknown.putObject("files").putObject("nope.json");
        assertThrows(InvalidCharacterRequestException.class, () -> mapper.update("Aurora", unknown));

        ObjectNode notObject = objectMapper.createObjectNode();
        notObject.set("files", objectMapper.createArrayNode());
        assertThrows(InvalidCharacterRequestException.class, () -> mapper.update("Aurora", notObject));

        ObjectNode patchNotObject = objectMapper.createObjectNode();
        patchNotObject.putObject("files").put("stats.json", "nope");
        assertThrows(InvalidCharacterRequestException.class, () -> mapper.update("Aurora", patchNotObject));
    }

    @Test
    void updateRejectsMissingFilesMapAndNonTextualFileNames() throws Exception {
        ObjectNode noFiles = objectMapper.createObjectNode().put("name", "Aurora");
        TestCharacters.writeJson(root.resolve("Aurora").resolve("character.json"), noFiles, objectMapper);
        assertThrows(IllegalStateException.class, () -> mapper.update("Aurora", objectMapper.createObjectNode().put("rank", "B")));

        TestCharacters.writeCharacter(root, "Aurora", CharacterClass.MAGE, objectMapper);
        ObjectNode badMap = (ObjectNode) loader.loadManifest("Aurora").deepCopy();
        ((ObjectNode) badMap.get("files")).put("stats", 1);
        TestCharacters.writeJson(root.resolve("Aurora").resolve("character.json"), badMap, objectMapper);
        assertThrows(IllegalStateException.class, () -> mapper.update("Aurora", objectMapper.createObjectNode().put("rank", "B")));
    }

    @Test
    void updateRejectsNonObjectManifestAndNonObjectDomainFile() throws Exception {
        Files.writeString(root.resolve("Aurora").resolve("character.json"), "[1]");
        assertThrows(IllegalStateException.class, () -> mapper.update("Aurora", objectMapper.createObjectNode()));

        TestCharacters.writeCharacter(root, "Aurora", CharacterClass.MAGE, objectMapper);
        Files.writeString(root.resolve("Aurora").resolve("stats.json"), "[1]");
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("files").putObject("stats.json").put("x", 1);
        assertThrows(IllegalStateException.class, () -> mapper.update("Aurora", body));
    }

    @Test
    void updateHandlesNullAndNonTextualOptionalFields() {
        ObjectNode body = objectMapper.createObjectNode();
        body.putNull("name");
        body.put("nameIgnored", 1);
        body.set("name", objectMapper.getNodeFactory().numberNode(3));
        body.set("class", objectMapper.getNodeFactory().numberNode(1));
        body.put("rank", "   ");
        body.putNull("files");
        body.putObject("defaultState").put("emotion", "wary");
        body.putObject("appearance").putObject("measurements").put("waistCm", 60);
        body.putNull("visualIdentity");
        body.putNull("relationships");
        mapper.update("Aurora", body);
        assertEquals("wary", mapper.map("Aurora").path("defaultState").path("emotion").asText());
        assertEquals(60, mapper.map("Aurora").path("files").path("appearance.json").path("measurements").path("waistCm").asInt());
    }

    @Test
    void updateEmptyRelationshipAndVisualFallbacks() {
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("defaultRelationships").addObject().put("characterB", "");
        ObjectNode visual = body.putObject("visualIdentity");
        visual.put("canonicalReference", "ref.png");
        mapper.update("Aurora", body);
        assertEquals("ref.png", mapper.map("Aurora").path("files").path("visual-identity.json").path("canonicalReference").asText());
    }

    @Test
    void updateIgnoresNonObjectTopLevelDomainValuesAndEmptyPatches() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("personality", "nope");
        body.put("abilities", "nope");
        body.put("stats", "nope");
        body.putObject("profile");
        body.putObject("appearance");
        mapper.update("Aurora", body);
        assertEquals("focused", mapper.map("Aurora").path("files").path("personality.json").path("traits").get(0).asText());
    }

    @Test
    void updateSkipsAbilitiesWhenFileMissingFromManifest() throws Exception {
        ObjectNode manifest = (ObjectNode) loader.loadManifest("Aurora").deepCopy();
        ((ObjectNode) manifest.get("files")).remove("abilities");
        TestCharacters.writeJson(root.resolve("Aurora").resolve("character.json"), manifest, objectMapper);
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("abilities").addObject().put("name", "Ice Lance").put("type", "offensive");
        mapper.update("Aurora", body);
        assertTrue(Files.readString(root.resolve("Aurora").resolve("abilities.json")).contains("arcane"));
    }

    @Test
    void updateMergesRepeatedPatchesToSameFile() {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode profile = body.putObject("profile");
        profile.putArray("personality").add("calm");
        body.putObject("personality").putArray("likes").add("tea");
        mapper.update("Aurora", body);
        ObjectNode personality = (ObjectNode) mapper.map("Aurora").path("files").path("personality.json");
        assertEquals("calm", personality.path("traits").get(0).asText());
        assertEquals("tea", personality.path("likes").get(0).asText());
    }

    @Test
    void mapMissingCharacter() {
        assertThrows(CharacterNotFoundException.class, () -> mapper.map("Nope"));
    }
}
