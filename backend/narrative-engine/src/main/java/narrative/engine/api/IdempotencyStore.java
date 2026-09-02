package narrative.engine.api;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class IdempotencyStore {

    public record Cached(int status, Object entity) {}

    private final ConcurrentHashMap<String, Cached> cache = new ConcurrentHashMap<>();

    public Cached get(String scope, String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return cache.get(scope + ":" + key.trim());
    }

    public void put(String scope, String key, int status, Object entity) {
        if (key == null || key.isBlank()) {
            return;
        }
        cache.put(scope + ":" + key.trim(), new Cached(status, entity));
    }
}
