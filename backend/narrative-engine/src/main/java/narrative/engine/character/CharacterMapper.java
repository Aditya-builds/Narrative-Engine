package narrative.engine.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class CharacterMapper {

    private static final String TEMPLATE_CHARACTER = "Aurora";

    private final CharacterLoader characterLoader;
    private final ObjectMapper objectMapper;

    @Inject
    public CharacterMapper(CharacterLoader characterLoader, ObjectMapper objectMapper) {
        this.characterLoader = characterLoader;
        this.objectMapper = objectMapper;
    }

    public ObjectNode map(String characterKey) {
        JsonNode manifest = characterLoader.loadManifest(characterKey);
        if (!manifest.isObject()) {
            throw new IllegalStateException("character.json for '" + characterKey + "' must be a JSON object");
        }

        ObjectNode combined = (ObjectNode) manifest.deepCopy();
        JsonNode files = combined.remove("files");
        if (files == null || files.isNull()) {
            combined.putObject("files");
            return combined;
        }
        if (!files.isObject()) {
            throw new IllegalStateException("files in character.json for '" + characterKey + "' must be an object");
        }

        ObjectNode filesByName = combined.putObject("files");
        files.fields().forEachRemaining(entry -> setFile(filesByName, characterKey, entry));
        return combined;
    }

    public void create(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidCharacterRequestException("Provide a unique name");
        }
        name = name.trim();

        String id = name.toLowerCase(Locale.ROOT);
        JsonNode templateManifest = characterLoader.loadManifest(TEMPLATE_CHARACTER);
        JsonNode templateFiles = templateManifest.get("files");
        if (templateFiles == null || !templateFiles.isObject()) {
            throw new IllegalStateException("Aurora character.json is missing a files object");
        }

        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put("id", id);
        manifest.put("name", name);
        manifest.put("rank", "");
        manifest.put("gender", "");
        manifest.put("age", "");
        manifest.set("files", templateFiles.deepCopy());

        Map<String, JsonNode> files = new LinkedHashMap<>();
        templateFiles.fields().forEachRemaining(entry -> {
            String fileKey = entry.getKey();
            String fileName = entry.getValue().asText();
            JsonNode template = characterLoader.loadFile(TEMPLATE_CHARACTER, fileKey, fileName);
            files.put(fileName, emptyCopy(fileName, template));
        });

        characterLoader.create(name, manifest, files);
    }

    public void update(String name, JsonNode request) {
        if (name == null || name.isBlank()) {
            throw new InvalidCharacterRequestException("Provide a character name");
        }
        final String characterName = name.trim();
        if (request == null || !request.isObject()) {
            throw new InvalidCharacterRequestException("Request body must be a JSON object");
        }

        JsonNode manifestNode = characterLoader.loadManifest(characterName);
        if (!manifestNode.isObject()) {
            throw new IllegalStateException("character.json for '" + characterName + "' must be a JSON object");
        }
        ObjectNode manifest = (ObjectNode) manifestNode.deepCopy();

        JsonNode requestedName = request.get("name");
        if (requestedName != null && !requestedName.isNull() && !characterName.equals(requestedName.asText())) {
            throw new InvalidCharacterRequestException("name cannot be changed");
        }

        for (String field : List.of("rank", "gender", "age")) {
            JsonNode value = request.get(field);
            if (value != null && !value.isNull()) {
                manifest.put(field, value.asText());
            }
        }

        JsonNode filesMap = manifest.get("files");
        if (filesMap == null || !filesMap.isObject()) {
            throw new IllegalStateException("character.json for '" + characterName + "' is missing a files object");
        }

        Map<String, String> keyByFileName = new LinkedHashMap<>();
        filesMap.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) {
                throw new IllegalStateException(
                        "files." + entry.getKey() + " in character.json for '" + characterName + "' must be a filename string");
            }
            keyByFileName.put(entry.getValue().asText(), entry.getKey());
        });

        Map<String, JsonNode> updatedFiles = new LinkedHashMap<>();
        JsonNode providedFiles = request.get("files");
        if (providedFiles != null && !providedFiles.isNull()) {
            if (!providedFiles.isObject()) {
                throw new InvalidCharacterRequestException("files must be a JSON object");
            }
            providedFiles.fields().forEachRemaining(entry -> {
                String fileName = entry.getKey();
                String fileKey = keyByFileName.get(fileName);
                if (fileKey == null) {
                    throw new InvalidCharacterRequestException("Unknown file '" + fileName + "'");
                }
                JsonNode patch = entry.getValue();
                if (patch == null || !patch.isObject()) {
                    throw new InvalidCharacterRequestException(fileName + " must be a JSON object");
                }
                JsonNode current = characterLoader.loadFile(characterName, fileKey, fileName);
                if (!current.isObject()) {
                    throw new IllegalStateException(fileName + " for '" + characterName + "' must be a JSON object");
                }
                ObjectNode merged = (ObjectNode) current.deepCopy();
                merge(merged, patch);
                updatedFiles.put(fileName, merged);
            });
        }

        characterLoader.save(characterName, manifest, updatedFiles);
    }

    private void setFile(ObjectNode filesByName, String characterKey, Map.Entry<String, JsonNode> entry) {
        String fileKey = entry.getKey();
        JsonNode fileNameNode = entry.getValue();
        if (fileNameNode == null || !fileNameNode.isTextual()) {
            throw new IllegalStateException(
                    "files." + fileKey + " in character.json for '" + characterKey + "' must be a filename string");
        }

        String fileName = fileNameNode.asText();
        filesByName.set(fileName, characterLoader.loadFile(characterKey, fileKey, fileName));
    }

    private void merge(ObjectNode target, JsonNode source) {
        source.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            JsonNode value = entry.getValue();
            JsonNode existing = target.get(key);
            if (value.isObject() && existing != null && existing.isObject()) {
                merge((ObjectNode) existing, value);
            } else {
                target.set(key, value.deepCopy());
            }
        });
    }

    private JsonNode emptyCopy(String fileName, JsonNode template) {
        if ("relationships.json".equals(fileName)) {
            ObjectNode relationships = objectMapper.createObjectNode();
            relationships.putObject("relationships");
            return relationships;
        }
        return emptyValues(template);
    }

    private JsonNode emptyValues(JsonNode node) {
        if (node == null || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (node.isObject()) {
            ObjectNode empty = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry -> empty.set(entry.getKey(), emptyValues(entry.getValue())));
            return empty;
        }
        if (node.isArray()) {
            ArrayNode empty = objectMapper.createArrayNode();
            boolean objectsOnly = node.size() > 0;
            for (JsonNode item : node) {
                if (!item.isObject()) {
                    objectsOnly = false;
                    break;
                }
            }
            if (objectsOnly) {
                for (JsonNode item : node) {
                    empty.add(emptyValues(item));
                }
            }
            return empty;
        }
        if (node.isNumber()) {
            return objectMapper.getNodeFactory().numberNode(0);
        }
        if (node.isBoolean()) {
            return objectMapper.getNodeFactory().booleanNode(false);
        }
        return objectMapper.getNodeFactory().textNode("");
    }
}
