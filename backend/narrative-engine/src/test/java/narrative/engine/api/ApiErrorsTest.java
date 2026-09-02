package narrative.engine.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiErrorsTest {

    @Test
    void bodyKeepsBackwardCompatibleFields() {
        Map<String, Object> body = ApiErrors.body(404, "/characters/Nova", "missing");
        assertEquals("missing", body.get("error"));
        assertEquals("missing", body.get("detail"));
        assertEquals("missing", body.get("message"));
        assertEquals(404, body.get("status"));
        assertEquals("NOT_FOUND", body.get("errorCode"));
        assertEquals("/characters/Nova", body.get("path"));
        assertTrue(body.get("timestamp") instanceof String);
    }

    @Test
    void errorCodesMatchHttpStatus() {
        assertEquals("BAD_REQUEST", ApiErrors.errorCode(400));
        assertEquals("CONFLICT", ApiErrors.errorCode(409));
        assertEquals("RATE_LIMITED", ApiErrors.errorCode(429));
        assertEquals("ERROR", ApiErrors.errorCode(418));
    }

    @Test
    void messagePrefersErrorThenDetail() {
        assertEquals("boom", ApiErrors.messageFrom(Map.of("error", "boom")));
        assertEquals("late", ApiErrors.messageFrom(Map.of("detail", "late")));
        assertEquals("Request failed", ApiErrors.messageFrom(null));
        assertTrue(ApiErrors.alreadyEnveloped(ApiErrors.body(500, "/", "x")));
        assertFalse(ApiErrors.alreadyEnveloped(Map.of("error", "x")));
    }

    @Test
    void responseBuildsJsonEntity() {
        var response = ApiErrors.response(jakarta.ws.rs.core.Response.Status.NOT_FOUND, "/x", "gone");
        assertEquals(404, response.getStatus());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getEntity();
        assertEquals("gone", body.get("error"));
        assertEquals("NOT_FOUND", body.get("errorCode"));
    }
}
