package narrative.engine.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharacterClassDefaultsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mageDefaultsUseArcaneSpecialtyAndRobes() {
        ObjectNode manifest = CharacterClassDefaults.manifest(mapper, CharacterClass.MAGE, "Nova");
        assertEquals("Nova", manifest.path("name").asText());
        assertEquals("mage", manifest.path("class").asText());
        assertEquals("E", manifest.path("rank").asText());
        assertEquals("guildhall", manifest.path("location").asText());
        assertTrue(manifest.path("description").asText().contains("mage"));

        var files = CharacterClassDefaults.files(mapper, CharacterClass.MAGE);
        assertEquals("arcane", files.get("abilities.json").path("specialties").fieldNames().next());
        assertEquals(40, files.get("stats.json").path("attributes").path("vigor").asInt());
        assertEquals("training staff", files.get("equipment.json").path("weapons").get(0).asText());
        assertEquals("plain robes", files.get("equipment.json").path("clothing").path("default").asText());
        assertEquals("focused", files.get("personality.json").path("traits").get(0).asText());
        assertTrue(files.get("relationships.json").path("relationships").isObject());
        assertEquals("", files.get("visual-identity.json").path("canonicalReference").asText());
        assertEquals(0, files.get("appearance.json").path("body").path("heightCm").asInt());
        assertEquals(Map.of(
                "stats", "stats.json",
                "abilities", "abilities.json",
                "personality", "personality.json",
                "relationships", "relationships.json",
                "appearance", "appearance.json",
                "equipment", "equipment.json",
                "visualIdentity", "visual-identity.json"
        ).entrySet(), CharacterClassDefaults.FILE_MAP.entrySet());
    }

    @Test
    void meleeDefaultsUseMartialSpecialtyAndBlade() {
        ObjectNode manifest = CharacterClassDefaults.manifest(mapper, CharacterClass.MELEE, "Blade");
        assertTrue(manifest.path("description").asText().contains("melee"));

        var files = CharacterClassDefaults.files(mapper, CharacterClass.MELEE);
        assertEquals("martial", files.get("abilities.json").path("specialties").fieldNames().next());
        assertEquals(70, files.get("stats.json").path("attributes").path("vigor").asInt());
        assertEquals("training blade", files.get("equipment.json").path("weapons").get(0).asText());
        assertEquals("light training gear", files.get("equipment.json").path("armor").get(0).asText());
        assertEquals("disciplined", files.get("personality.json").path("traits").get(0).asText());
        assertEquals("simple tunic", files.get("equipment.json").path("clothing").path("default").asText());
    }

    @Test
    void privateConstructorIsInvocable() throws Exception {
        Constructor<CharacterClassDefaults> constructor = CharacterClassDefaults.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
