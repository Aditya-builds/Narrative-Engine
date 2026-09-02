package narrative.engine.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdempotencyStoreTest {

    @Test
    void storesAndReturnsCachedResponse() {
        IdempotencyStore store = new IdempotencyStore();
        Map<String, String> entity = Map.of("message", "successful creation");
        assertNull(store.get("character", null));
        assertNull(store.get("character", "  "));
        store.put("character", " key-1 ", 201, entity);
        IdempotencyStore.Cached cached = store.get("character", "key-1");
        assertEquals(201, cached.status());
        assertEquals(entity, cached.entity());
        assertNull(store.get("persona", "key-1"));
    }
}
