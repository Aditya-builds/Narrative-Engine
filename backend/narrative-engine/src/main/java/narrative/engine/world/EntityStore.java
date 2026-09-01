package narrative.engine.world;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface EntityStore {

    String resolveKey(String key);

    List<String> listKeys();

    JsonNode loadManifest(String key);

    JsonNode loadFile(String key, String fileKey, String fileName);

    void create(String key, JsonNode manifest, Map<String, JsonNode> files);

    void save(String key, JsonNode manifest, Map<String, JsonNode> files);
}
