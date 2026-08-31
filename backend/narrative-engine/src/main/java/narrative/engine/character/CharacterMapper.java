package narrative.engine.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@ApplicationScoped
public class CharacterMapper {

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

    public void create(String name, String className) {
        if (name == null || name.isBlank()) {
            throw new InvalidCharacterRequestException("Provide a unique name");
        }
        name = name.trim();

        CharacterClass characterClass = CharacterClass.from(className);
        String id = name.toLowerCase(Locale.ROOT);
        ObjectNode manifest = CharacterClassDefaults.manifest(objectMapper, characterClass, id, name);
        Map<String, JsonNode> files = CharacterClassDefaults.files(objectMapper, characterClass);
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

        for (String field : List.of("rank", "gender", "age", "description")) {
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

}
