package narrative.engine.api;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiErrors {

    private ApiErrors() {}

    public static Map<String, Object> body(int status, String path, String message) {
        String text = (message == null || message.isBlank()) ? "Request failed" : message;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("path", path == null || path.isBlank() ? "/" : path);
        body.put("status", status);
        body.put("errorCode", errorCode(status));
        body.put("message", text);
        body.put("error", text);
        body.put("detail", text);
        return body;
    }

    public static Response response(Response.Status status, String path, String message) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(body(status.getStatusCode(), path, message))
                .build();
    }

    public static String errorCode(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 429 -> "RATE_LIMITED";
            case 500 -> "INTERNAL_ERROR";
            case 502 -> "BAD_GATEWAY";
            case 503 -> "UNAVAILABLE";
            case 504 -> "TIMEOUT";
            default -> "ERROR";
        };
    }

    static String messageFrom(Object entity) {
        if (entity instanceof Map<?, ?> map) {
            Object error = map.get("error");
            if (error != null && !error.toString().isBlank()) {
                return error.toString();
            }
            Object detail = map.get("detail");
            if (detail != null && !detail.toString().isBlank()) {
                return detail.toString();
            }
            Object message = map.get("message");
            if (message != null && !message.toString().isBlank()) {
                return message.toString();
            }
        }
        if (entity instanceof String text && !text.isBlank()) {
            return text;
        }
        return "Request failed";
    }

    static boolean alreadyEnveloped(Object entity) {
        return entity instanceof Map<?, ?> map
                && map.containsKey("timestamp")
                && map.containsKey("errorCode")
                && map.containsKey("status");
    }
}
