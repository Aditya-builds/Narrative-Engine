package narrative.engine.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import narrative.engine.world.EntityStore;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class CharacterMapper {

    private final EntityStore entityStore;
    private final ObjectMapper objectMapper;

    public CharacterMapper(EntityStore entityStore, ObjectMapper objectMapper) {
        this.entityStore = entityStore;
        this.objectMapper = objectMapper;
    }

    @Inject
    public CharacterMapper(CharacterLoader characterLoader, ObjectMapper objectMapper) {
        this((EntityStore) characterLoader, objectMapper);
    }

    public ObjectNode map(String characterKey) {
        String directory = entityStore.resolveKey(characterKey);
        JsonNode manifest = entityStore.loadManifest(directory);
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
        files.fields().forEachRemaining(entry -> setFile(filesByName, directory, entry));
        return combined;
    }

    public void create(String name, String className) {
        if (name == null || name.isBlank()) {
            throw new InvalidCharacterRequestException("Provide a unique name");
        }
        name = name.trim();

        CharacterClass characterClass = CharacterClass.from(className);
        ObjectNode manifest = CharacterClassDefaults.manifest(objectMapper, characterClass, name);
        Map<String, JsonNode> files = CharacterClassDefaults.files(objectMapper, characterClass);
        entityStore.create(name, manifest, files);
    }

    public void update(String name, JsonNode request) {
        if (name == null || name.isBlank()) {
            throw new InvalidCharacterRequestException("Provide a character name");
        }
        if (request == null || !request.isObject()) {
            throw new InvalidCharacterRequestException("Request body must be a JSON object");
        }

        final String characterName = entityStore.resolveKey(name.trim());
        JsonNode manifestNode = entityStore.loadManifest(characterName);
        if (!manifestNode.isObject()) {
            throw new IllegalStateException("character.json for '" + characterName + "' must be a JSON object");
        }
        ObjectNode manifest = (ObjectNode) manifestNode.deepCopy();

        JsonNode requestedName = request.get("name");
        if (requestedName != null && !requestedName.isNull() && requestedName.isTextual()) {
            String bodyName = requestedName.asText().trim();
            String storedName = manifest.path("name").asText(characterName);
            if (!characterName.equalsIgnoreCase(bodyName) && !storedName.equalsIgnoreCase(bodyName)) {
                throw new InvalidCharacterRequestException("name cannot be changed");
            }
        }

        applyManifestFields(manifest, request);

        JsonNode filesMap = manifest.get("files");
        if (filesMap == null || !filesMap.isObject()) {
            throw new IllegalStateException("character.json for '" + characterName + "' is missing a files object");
        }

        Map<String, String> fileNameByKey = new LinkedHashMap<>();
        Map<String, String> keyByFileName = new LinkedHashMap<>();
        filesMap.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) {
                throw new IllegalStateException(
                        "files." + entry.getKey() + " in character.json for '" + characterName + "' must be a filename string");
            }
            fileNameByKey.put(entry.getKey(), entry.getValue().asText());
            keyByFileName.put(entry.getValue().asText(), entry.getKey());
        });

        Map<String, ObjectNode> patches = new LinkedHashMap<>();
        collectTranslatedPatches(patches, fileNameByKey, characterName, manifest, request);
        collectTopLevelFilePatches(patches, fileNameByKey, request);
        collectFilesObjectPatches(patches, fileNameByKey, keyByFileName, request);

        Map<String, JsonNode> updatedFiles = new LinkedHashMap<>();
        for (var entry : patches.entrySet()) {
            String fileName = entry.getKey();
            String fileKey = keyByFileName.get(fileName);
            if (fileKey == null) {
                throw new InvalidCharacterRequestException("Unknown file '" + fileName + "'");
            }
            JsonNode current = entityStore.loadFile(characterName, fileKey, fileName);
            if (!current.isObject()) {
                throw new IllegalStateException(fileName + " for '" + characterName + "' must be a JSON object");
            }
            ObjectNode merged = (ObjectNode) current.deepCopy();
            merge(merged, entry.getValue());
            updatedFiles.put(fileName, merged);
        }

        entityStore.save(characterName, manifest, updatedFiles);
    }

    private static final List<String> MANIFEST_TEXT_FIELDS =
            List.of("rank", "gender", "age", "location", "description", "openingMessage", "worldId", "type");

    private static final Set<String> RANK_KEYS = Set.of("E", "D", "C", "B", "A", "S");

    private static final Set<String> VISUAL_DESCRIPTION_FIELDS = Set.of(
            "visualDescription",
            "faceDescription",
            "hairDescription",
            "eyeDescription",
            "skinDescription",
            "bodyDescription",
            "clothingDescription",
            "artStyle",
            "negativePrompt");

    private void applyManifestFields(ObjectNode manifest, JsonNode request) {
        for (String field : MANIFEST_TEXT_FIELDS) {
            putText(manifest, field, request.get(field));
        }

        JsonNode classValue = request.get("class");
        if (classValue != null && !classValue.isNull() && classValue.isTextual()) {
            manifest.put("class", CharacterClass.from(classValue.asText()).id());
        }

        JsonNode profile = request.get("profile");
        if (profile != null && profile.isObject()) {
            putText(manifest, "description", profile.get("background"));
        }

        JsonNode defaultState = request.get("defaultState");
        if (defaultState != null && defaultState.isObject()) {
            manifest.set("defaultState", defaultState.deepCopy());
            JsonNode locationId = defaultState.get("locationId");
            if (locationId != null && locationId.isTextual()) {
                manifest.put("location", normalizeLocation(locationId.asText()));
            }
        }

        JsonNode memories = request.get("seedMemories");
        if (memories != null && !memories.isNull()) {
            manifest.set("seedMemories", memories.deepCopy());
        }
    }

    private void collectTranslatedPatches(
            Map<String, ObjectNode> patches,
            Map<String, String> fileNameByKey,
            String characterName,
            ObjectNode manifest,
            JsonNode request) {
        addPatch(patches, fileNameByKey.get("personality"), fromProfile(request.get("profile")));
        addPatch(patches, fileNameByKey.get("appearance"), fromExternalAppearance(request.get("appearance")));
        addPatch(
                patches,
                fileNameByKey.get("abilities"),
                fromExternalAbilities(request.get("abilities"), characterName, fileNameByKey, manifest));
        addPatch(
                patches,
                fileNameByKey.get("relationships"),
                fromDefaultRelationships(request.get("defaultRelationships")));
        addPatch(patches, fileNameByKey.get("visualIdentity"), fromExternalVisualIdentity(request.get("visualIdentity")));
    }

    private void collectTopLevelFilePatches(Map<String, ObjectNode> patches, Map<String, String> fileNameByKey, JsonNode request) {
        for (var entry : fileNameByKey.entrySet()) {
            String domainKey = entry.getKey();
            JsonNode value = request.get(domainKey);
            if (value == null || value.isNull()) {
                continue;
            }
            JsonNode patch = switch (domainKey) {
                case "appearance" -> nativeOrExternalAppearance(value);
                case "abilities" -> value.isObject() ? value : null;
                case "relationships" -> nativeRelationships(value);
                case "visualIdentity" -> nativeOrExternalVisualIdentity(value);
                case "personality" -> value.isObject() ? value : null;
                default -> value.isObject() ? value : null;
            };
            addPatch(patches, entry.getValue(), patch);
        }
    }

    private void collectFilesObjectPatches(
            Map<String, ObjectNode> patches,
            Map<String, String> fileNameByKey,
            Map<String, String> keyByFileName,
            JsonNode request) {
        JsonNode providedFiles = request.get("files");
        if (providedFiles == null || providedFiles.isNull()) {
            return;
        }
        if (!providedFiles.isObject()) {
            throw new InvalidCharacterRequestException("files must be a JSON object");
        }
        providedFiles.fields().forEachRemaining(entry -> {
            String fileName = resolveFileName(entry.getKey(), fileNameByKey, keyByFileName);
            JsonNode patch = entry.getValue();
            if (patch == null || !patch.isObject()) {
                throw new InvalidCharacterRequestException(fileName + " must be a JSON object");
            }
            addPatch(patches, fileName, patch);
        });
    }

    private String resolveFileName(
            String key, Map<String, String> fileNameByKey, Map<String, String> keyByFileName) {
        if (keyByFileName.containsKey(key)) {
            return key;
        }
        if (fileNameByKey.containsKey(key)) {
            return fileNameByKey.get(key);
        }
        throw new InvalidCharacterRequestException("Unknown file '" + key + "'");
    }

    private void addPatch(Map<String, ObjectNode> patches, String fileName, JsonNode patch) {
        if (fileName == null || patch == null || patch.isNull() || !patch.isObject() || patch.isEmpty()) {
            return;
        }
        ObjectNode existing = patches.get(fileName);
        if (existing == null) {
            patches.put(fileName, (ObjectNode) patch.deepCopy());
        } else {
            merge(existing, patch);
        }
    }

    private JsonNode fromProfile(JsonNode profile) {
        if (profile == null || !profile.isObject()) {
            return null;
        }
        ObjectNode patch = objectMapper.createObjectNode();
        JsonNode personality = profile.get("personality");
        if (personality != null && personality.isArray()) {
            patch.set("traits", personality.deepCopy());
        }
        JsonNode values = profile.get("values");
        if (values != null && values.isArray()) {
            patch.set("values", values.deepCopy());
        }
        JsonNode speakingStyle = profile.get("speakingStyle");
        if (speakingStyle != null && speakingStyle.isTextual() && !speakingStyle.asText().isBlank()) {
            ObjectNode speech = patch.putObject("speech");
            speech.put("style", speakingStyle.asText());
        }
        return patch;
    }

    private JsonNode nativeOrExternalAppearance(JsonNode appearance) {
        if (appearance == null || !appearance.isObject()) {
            return null;
        }
        if (appearance.has("physicalFeatures") || appearance.has("body") || appearance.has("measurements")) {
            return appearance;
        }
        return fromExternalAppearance(appearance);
    }

    private JsonNode fromExternalAppearance(JsonNode appearance) {
        if (appearance == null || !appearance.isObject()) {
            return null;
        }
        if (appearance.has("physicalFeatures") || appearance.has("body") || appearance.has("measurements")) {
            return null;
        }

        ObjectNode patch = objectMapper.createObjectNode();
        ObjectNode features = objectMapper.createObjectNode();
        putText(features, "hair", appearance.get("hair"));
        putText(features, "eyes", appearance.get("eyes"));
        putText(features, "skin", appearance.get("skin"));
        putText(features, "face", appearance.get("face"));
        if (!features.isEmpty()) {
            patch.set("physicalFeatures", features);
        }

        JsonNode build = appearance.get("build");
        if (build != null && build.isTextual()) {
            patch.putObject("body").put("build", build.asText());
        }
        putText(patch, "description", appearance.get("description"));
        return patch;
    }

    private JsonNode fromExternalAbilities(
            JsonNode abilities,
            String characterName,
            Map<String, String> fileNameByKey,
            ObjectNode manifest) {
        if (abilities == null || !abilities.isArray()) {
            return null;
        }

        String fileName = fileNameByKey.get("abilities");
        if (fileName == null) {
            return null;
        }
        JsonNode current = entityStore.loadFile(characterName, "abilities", fileName);
        String specialty = specialtyKey(current);
        String rank = normalizeRank(manifest.path("rank").asText("E"));
        String highRank = nextRank(rank);

        ObjectNode offensive = objectMapper.createObjectNode();
        ObjectNode defensive = objectMapper.createObjectNode();
        offensive.set(rank, objectMapper.createArrayNode());
        defensive.set(rank, objectMapper.createArrayNode());
        offensive.set(highRank, objectMapper.createArrayNode());

        abilities.forEach(ability -> {
            if (ability == null || !ability.isObject()) {
                return;
            }
            String abilityName = text(ability.get("name"));
            if (abilityName.isBlank()) {
                return;
            }
            String type = text(ability.get("type")).toLowerCase(Locale.ROOT);
            if (type.contains("elemental") || "ice magic".equalsIgnoreCase(abilityName)) {
                return;
            }
            boolean defensiveAbility = type.contains("defensive");
            boolean highLevel = type.contains("high");
            String targetRank = highLevel ? highRank : rank;
            ObjectNode side = defensiveAbility ? defensive : offensive;
            JsonNode rankList = side.get(targetRank);
            if (rankList == null || !rankList.isArray()) {
                rankList = objectMapper.createArrayNode();
                side.set(targetRank, rankList);
            }
            ((ArrayNode) rankList).add(abilityName);
        });

        ObjectNode patch = objectMapper.createObjectNode();
        ObjectNode specialtyNode = patch.putObject("specialties").putObject(specialty);
        specialtyNode.set("offensive", offensive);
        specialtyNode.set("defensive", defensive);
        return patch;
    }

    private String specialtyKey(JsonNode abilities) {
        JsonNode specialties = abilities.path("specialties");
        if (specialties.has("ice")) {
            return "ice";
        }
        Iterator<String> names = specialties.fieldNames();
        if (names.hasNext()) {
            return names.next();
        }
        return "arcane";
    }

    private JsonNode nativeRelationships(JsonNode relationships) {
        if (relationships == null || !relationships.isObject()) {
            return null;
        }
        if (relationships.has("relationships")) {
            return relationships;
        }
        ObjectNode patch = objectMapper.createObjectNode();
        patch.set("relationships", relationships.deepCopy());
        return patch;
    }

    private JsonNode fromDefaultRelationships(JsonNode defaultRelationships) {
        if (defaultRelationships == null || !defaultRelationships.isArray()) {
            return null;
        }
        ObjectNode map = objectMapper.createObjectNode();
        defaultRelationships.forEach(rel -> {
            if (rel == null || !rel.isObject()) {
                return;
            }
            String other = text(rel.get("characterB"));
            if (other.isBlank()) {
                return;
            }
            JsonNode metrics = rel.get("metrics");
            int affinity = 0;
            if (metrics != null && metrics.isObject()) {
                if (metrics.has("respect")) {
                    affinity = metrics.get("respect").asInt();
                } else if (metrics.has("trust")) {
                    affinity = metrics.get("trust").asInt();
                }
            }
            map.put(other, affinity);
        });
        if (map.isEmpty()) {
            return null;
        }
        ObjectNode patch = objectMapper.createObjectNode();
        patch.set("relationships", map);
        return patch;
    }

    private JsonNode nativeOrExternalVisualIdentity(JsonNode visual) {
        if (visual == null || !visual.isObject()) {
            return null;
        }
        if (visual.has("canonicalReference") || visual.has("references")) {
            ObjectNode patch = visual.deepCopy();
            copyVisualDescriptions(patch, visual);
            return patch;
        }
        return fromExternalVisualIdentity(visual);
    }

    private JsonNode fromExternalVisualIdentity(JsonNode visual) {
        if (visual == null || !visual.isObject()) {
            return null;
        }
        if (visual.has("canonicalReference") || visual.has("references")) {
            return null;
        }
        ObjectNode patch = objectMapper.createObjectNode();
        copyVisualDescriptions(patch, visual);
        JsonNode accessories = visual.get("accessories");
        if (accessories != null && accessories.isArray()) {
            patch.set("accessories", accessories.deepCopy());
        }
        return patch;
    }

    private void copyVisualDescriptions(ObjectNode target, JsonNode visual) {
        for (String field : VISUAL_DESCRIPTION_FIELDS) {
            putText(target, field, visual.get(field));
        }
    }

    private void putText(ObjectNode target, String field, JsonNode value) {
        if (value != null && !value.isNull() && value.isTextual() && !value.asText().isBlank()) {
            target.put(field, value.asText());
        }
    }

    private String text(JsonNode value) {
        return value != null && value.isTextual() ? value.asText().trim() : "";
    }

    private String normalizeLocation(String locationId) {
        String value = locationId.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        if (value.equals("guild_hall") || value.equals("guild hall")) {
            return "guildhall";
        }
        return locationId.trim();
    }

    private String normalizeRank(String rank) {
        String value = rank == null ? "E" : rank.trim().toUpperCase(Locale.ROOT);
        return RANK_KEYS.contains(value) ? value : "E";
    }

    private String nextRank(String rank) {
        return switch (rank) {
            case "E" -> "D";
            case "D" -> "C";
            case "C" -> "B";
            case "B" -> "A";
            case "A" -> "S";
            default -> "S";
        };
    }

    private void setFile(ObjectNode filesByName, String characterKey, Map.Entry<String, JsonNode> entry) {
        String fileKey = entry.getKey();
        JsonNode fileNameNode = entry.getValue();
        if (fileNameNode == null || !fileNameNode.isTextual()) {
            throw new IllegalStateException(
                    "files." + fileKey + " in character.json for '" + characterKey + "' must be a filename string");
        }

        String fileName = fileNameNode.asText();
        filesByName.set(fileName, entityStore.loadFile(characterKey, fileKey, fileName));
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
