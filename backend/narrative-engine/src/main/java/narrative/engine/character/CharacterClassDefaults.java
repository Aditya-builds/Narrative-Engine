package narrative.engine.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class starters for new characters. These are generic mage / melee defaults,
 * not copies of Aurora or Laxus.
 */
final class CharacterClassDefaults {

    static final Map<String, String> FILE_MAP = new LinkedHashMap<>();

    static {
        FILE_MAP.put("stats", "stats.json");
        FILE_MAP.put("abilities", "abilities.json");
        FILE_MAP.put("personality", "personality.json");
        FILE_MAP.put("relationships", "relationships.json");
        FILE_MAP.put("appearance", "appearance.json");
        FILE_MAP.put("equipment", "equipment.json");
        FILE_MAP.put("visualIdentity", "visual-identity.json");
    }

    private CharacterClassDefaults() {}

    static ObjectNode manifest(ObjectMapper mapper, CharacterClass characterClass, String id, String name) {
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("id", id);
        manifest.put("name", name);
        manifest.put("class", characterClass.id());
        manifest.put("rank", "E");
        manifest.put("gender", "");
        manifest.put("age", "");
        manifest.put("description", description(characterClass, name));

        ObjectNode files = manifest.putObject("files");
        FILE_MAP.forEach(files::put);
        return manifest;
    }

    static Map<String, JsonNode> files(ObjectMapper mapper, CharacterClass characterClass) {
        Map<String, JsonNode> files = new LinkedHashMap<>();
        files.put("stats.json", stats(mapper, characterClass));
        files.put("abilities.json", abilities(mapper, characterClass));
        files.put("personality.json", personality(mapper, characterClass));
        files.put("relationships.json", relationships(mapper));
        files.put("appearance.json", appearance(mapper));
        files.put("equipment.json", equipment(mapper, characterClass));
        files.put("visual-identity.json", visualIdentity(mapper));
        return files;
    }

    private static String description(CharacterClass characterClass, String name) {
        return switch (characterClass) {
            case MAGE -> name
                    + " is a newly created mage. They fight with intelligence and arcane power,"
                    + " starting at rank E with a training staff and little physical strength.";
            case MELEE -> name
                    + " is a newly created melee fighter. They fight up close with strength and endurance,"
                    + " starting at rank E with a training blade and little magical ability.";
        };
    }

    private static ObjectNode stats(ObjectMapper mapper, CharacterClass characterClass) {
        ObjectNode stats = mapper.createObjectNode();
        ObjectNode attributes = stats.putObject("attributes");
        ArrayNode strengths = stats.putArray("strengths");
        ArrayNode weaknesses = stats.putArray("weaknesses");

        if (characterClass == CharacterClass.MAGE) {
            attributes.put("vigor", 40);
            attributes.put("mind", 70);
            attributes.put("endurance", 40);
            attributes.put("strength", 25);
            attributes.put("dexterity", 45);
            attributes.put("intelligence", 75);
            attributes.put("faith", 50);
            attributes.put("arcane", 70);
            strengths.add("Magical aptitude");
            strengths.add("Tactical awareness");
            weaknesses.add("Low physical strength");
            weaknesses.add("Limited close-quarters durability");
        } else {
            attributes.put("vigor", 70);
            attributes.put("mind", 35);
            attributes.put("endurance", 70);
            attributes.put("strength", 75);
            attributes.put("dexterity", 55);
            attributes.put("intelligence", 30);
            attributes.put("faith", 35);
            attributes.put("arcane", 20);
            strengths.add("Physical power");
            strengths.add("Close-quarters combat");
            weaknesses.add("Limited ranged magic");
            weaknesses.add("Lower arcane resistance");
        }
        return stats;
    }

    private static ObjectNode abilities(ObjectMapper mapper, CharacterClass characterClass) {
        ObjectNode abilities = mapper.createObjectNode();
        abilities.putArray("classes").add(characterClass.id());

        ObjectNode specialties = abilities.putObject("specialties");
        String specialtyKey = characterClass == CharacterClass.MAGE ? "arcane" : "martial";
        ObjectNode specialty = specialties.putObject(specialtyKey);
        specialty.set("offensive", emptyRanks(mapper));
        specialty.set("defensive", emptyRanks(mapper));
        return abilities;
    }

    private static ObjectNode emptyRanks(ObjectMapper mapper) {
        ObjectNode ranks = mapper.createObjectNode();
        for (String rank : new String[] {"E", "D", "C", "B", "A", "S"}) {
            ranks.putArray(rank);
        }
        return ranks;
    }

    private static ObjectNode personality(ObjectMapper mapper, CharacterClass characterClass) {
        ObjectNode personality = mapper.createObjectNode();
        ArrayNode traits = personality.putArray("traits");
        personality.putArray("likes");
        personality.putArray("dislikes");
        ObjectNode speech = personality.putObject("speech");
        personality.putArray("gestures");

        if (characterClass == CharacterClass.MAGE) {
            traits.add("focused");
            traits.add("curious");
            speech.put("tone", "measured");
            speech.put("style", "precise");
            speech.put("verbosity", "moderate");
        } else {
            traits.add("disciplined");
            traits.add("direct");
            speech.put("tone", "plain");
            speech.put("style", "blunt");
            speech.put("verbosity", "low");
        }
        return personality;
    }

    private static ObjectNode relationships(ObjectMapper mapper) {
        ObjectNode relationships = mapper.createObjectNode();
        relationships.putObject("relationships");
        return relationships;
    }

    private static ObjectNode appearance(ObjectMapper mapper) {
        ObjectNode appearance = mapper.createObjectNode();
        ObjectNode body = appearance.putObject("body");
        body.put("heightCm", 0);
        body.put("weightKg", 0);
        body.put("build", "");

        ObjectNode measurements = appearance.putObject("measurements");
        measurements.put("chestCm", 0);
        measurements.put("waistCm", 0);
        measurements.put("hipsCm", 0);

        ObjectNode features = appearance.putObject("physicalFeatures");
        features.put("hair", "");
        features.put("eyes", "");
        features.put("skin", "");
        features.put("face", "");
        return appearance;
    }

    private static ObjectNode equipment(ObjectMapper mapper, CharacterClass characterClass) {
        ObjectNode equipment = mapper.createObjectNode();
        ArrayNode weapons = equipment.putArray("weapons");
        ArrayNode armor = equipment.putArray("armor");
        equipment.putArray("accessories");
        ObjectNode clothing = equipment.putObject("clothing");

        if (characterClass == CharacterClass.MAGE) {
            weapons.add("training staff");
            clothing.put("default", "plain robes");
            clothing.put("combat", "battle robes");
            clothing.put("formal", "formal robes");
        } else {
            weapons.add("training blade");
            armor.add("light training gear");
            clothing.put("default", "simple tunic");
            clothing.put("combat", "padded combat gear");
            clothing.put("formal", "formal tunic");
        }
        return equipment;
    }

    private static ObjectNode visualIdentity(ObjectMapper mapper) {
        ObjectNode visual = mapper.createObjectNode();
        visual.put("canonicalReference", "");
        visual.putArray("references");
        return visual;
    }
}
