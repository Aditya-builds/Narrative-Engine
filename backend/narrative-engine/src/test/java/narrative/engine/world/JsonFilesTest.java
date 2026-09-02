package narrative.engine.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonFilesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path dir;

    @Test
    void writesThenOverwritesAtomically() throws Exception {
        Path file = dir.resolve("stats.json");
        ObjectNode first = mapper.createObjectNode().put("rank", "E");
        JsonFiles.writeAtomic(mapper, file, first);
        assertEquals("E", mapper.readTree(file.toFile()).path("rank").asText());
        assertFalse(Files.exists(file.resolveSibling("stats.json.tmp")));
        assertFalse(Files.exists(file.resolveSibling("stats.json.lock")));

        ObjectNode second = mapper.createObjectNode().put("rank", "A");
        JsonFiles.writeAtomic(mapper, file, second);
        assertEquals("A", mapper.readTree(file.toFile()).path("rank").asText());
    }

    @Test
    void rejectsReadOnlyTarget() throws Exception {
        Path file = dir.resolve("locked.json");
        JsonFiles.writeAtomic(mapper, file, mapper.createObjectNode().put("ok", true));
        assertTrue(file.toFile().setReadOnly());
        try {
            assertThrows(
                    Exception.class,
                    () -> JsonFiles.writeAtomic(mapper, file, mapper.createObjectNode().put("ok", false)));
        } finally {
            assertTrue(file.toFile().setWritable(true));
        }
    }
}
